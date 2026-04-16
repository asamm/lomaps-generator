package com.asamm.osmTools.overviewMap.centerline

import com.asamm.osmTools.utils.Logger
import org.locationtech.jts.densify.Densifier
import org.locationtech.jts.geom.Coordinate
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.GeometryFactory
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Polygon
import org.locationtech.jts.geom.prep.PreparedGeometryFactory
import org.locationtech.jts.simplify.DouglasPeuckerSimplifier
import org.locationtech.jts.triangulate.VoronoiDiagramBuilder
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.exp

/**
 * Configuration for centerline extraction.
 *
 * @param segmentizeMaxLen max segment length for boundary densification (in geometry units, degrees for WGS84)
 * @param maxPoints point threshold before iterative simplification kicks in
 * @param simplificationStep Douglas-Peucker tolerance increment per iteration. Higher
 * @param smoothSigma Gaussian smoothing sigma (in coordinate positions); 0 disables smoothing
 * @param maxPaths number of longest paths to evaluate before picking the least-curved one
 */
data class CenterlineParams(
    val segmentizeMaxLen: Double = 1.0,
    val maxPoints: Int = 3000,
    val simplificationStep: Double = 0.05,
    //val smoothSigma: Double = 2.5,
    val smoothSigma: Double = 2.0,
    val maxPaths: Int = 4,
)

/**
 * Extracts a centerline [LineString] from a [Polygon] using Voronoi diagrams.
 *
 * The algorithm densifies the polygon boundary, computes the Voronoi diagram of
 * the boundary points, filters to edges inside the polygon (forming a skeleton),
 * then selects the longest, least-curved path through the skeleton and smooths it.
 *
 * Ported from the Python `label_centerlines` library
 * (https://github.com/unvt/label_centerlines).
 */
class CenterlineExtractor(
    private val params: CenterlineParams = CenterlineParams(),
) {

    companion object {
        private const val TAG = "CenterlineExtractor"
    }

    private val geometryFactory = GeometryFactory()

    /**
     * Extract a centerline from a single [Polygon].
     * Returns null if the polygon is degenerate or too small to produce a skeleton.
     */
    fun extract(polygon: Polygon): LineString? {
        return try {
            doExtract(polygon)
        } catch (e: Exception) {
            Logger.w(TAG, "Centerline extraction failed for polygon: ${e.message}")
            null
        }
    }

    /**
     * Extract centerlines from a [MultiPolygon], returning one per sub-polygon.
     * Null entries are omitted from the result.
     */
    fun extractAll(multiPolygon: MultiPolygon): List<LineString> {
        return (0 until multiPolygon.numGeometries).toList().parallelStream()
            .map { i ->
                val geom = multiPolygon.getGeometryN(i)
                if (geom is Polygon) extract(geom) else null
            }
            .filter { it != null }
            .map { it!! }
            .toList()
    }

    private fun doExtract(polygon: Polygon): LineString? {
        // Step 1: Densify boundary
        val densifiedCoords = densifyBoundary(polygon)

        // Step 2: Simplify if too many points
        val coords = simplifyIfNeeded(densifiedCoords, polygon)
        if (coords.size < 4) {
            Logger.d(TAG, "Polygon too small after simplification (${coords.size} points)")
            return null
        }

        // Step 3-4: Compute Voronoi and extract unique edges
        val voronoiEdges = computeVoronoiEdges(coords, polygon)

        // Step 5: Filter edges by containment
        val filteredEdges = filterEdgesByContainment(voronoiEdges, polygon)
        if (filteredEdges.isEmpty()) {
            Logger.d(TAG, "No Voronoi edges inside polygon after filtering")
            return null
        }

        // Step 6: Build weighted graph
        val (graph, idToCoord) = buildGraph(filteredEdges)

        // Step 7: Find end nodes
        val endNodes = graph.endNodes()
        if (endNodes.size < 2) {
            Logger.d(TAG, "Too few end nodes (${endNodes.size}) for centerline extraction")
            return null
        }

        // Step 8: Dijkstra between all end-node pairs → longest paths
        val longestPaths = findLongestPaths(graph, endNodes, params.maxPaths)
        if (longestPaths.isEmpty()) {
            Logger.d(TAG, "No paths found between end nodes")
            return null
        }

        // Step 9: Select least-curved path
        val bestPath = selectLeastCurved(longestPaths, idToCoord)

        // Step 10: Gaussian smooth
        val pathCoords = bestPath.map { idToCoord[it]!! }
        val smoothed = if (params.smoothSigma > 0 && pathCoords.size > 2) {
            gaussianSmooth(pathCoords, params.smoothSigma)
        } else {
            pathCoords
        }

        return geometryFactory.createLineString(smoothed.toTypedArray())
    }

    // ── Step 1: Densify boundary ─────────────────────────────────────────────

    private fun densifyBoundary(polygon: Polygon): Array<Coordinate> {
        val ring = polygon.exteriorRing
        val densified = Densifier.densify(ring, params.segmentizeMaxLen)
        val coords = densified.coordinates
        // Remove closing coordinate (last == first in a ring)
        return if (coords.size > 1 && coords.first().equals2D(coords.last())) {
            coords.copyOfRange(0, coords.size - 1)
        } else {
            coords
        }
    }

    // ── Step 2: Simplify if needed ───────────────────────────────────────────

    private fun simplifyIfNeeded(coords: Array<Coordinate>, polygon: Polygon): Array<Coordinate> {
        if (coords.size <= params.maxPoints) return coords

        var tolerance = params.simplificationStep
        var simplified = coords
        var ring = polygon.exteriorRing

        while (simplified.size > params.maxPoints) {
            val simplifiedGeom = DouglasPeuckerSimplifier.simplify(ring, tolerance)
            simplified = simplifiedGeom.coordinates
            // Remove closing coordinate
            if (simplified.size > 1 && simplified.first().equals2D(simplified.last())) {
                simplified = simplified.copyOfRange(0, simplified.size - 1)
            }
            tolerance += params.simplificationStep
            if (tolerance > 100) break // safety limit
        }

        Logger.d(TAG, "Simplified boundary from ${coords.size} to ${simplified.size} points (tolerance=$tolerance)")
        return simplified
    }

    // ── Steps 3-4: Voronoi edges ─────────────────────────────────────────────

    private fun computeVoronoiEdges(
        boundaryCoords: Array<Coordinate>,
        polygon: Polygon,
    ): List<Pair<Coordinate, Coordinate>> {
        val builder = VoronoiDiagramBuilder()
        builder.setSites(geometryFactory.createMultiPointFromCoords(boundaryCoords))

        // Expand clip envelope slightly to avoid edge artifacts
        val env = polygon.envelopeInternal.copy()
        env.expandBy(env.width * 0.1, env.height * 0.1)
        builder.setClipEnvelope(env)

        val diagram = builder.getDiagram(geometryFactory) as GeometryCollection

        // Extract unique edges from Voronoi cell boundaries
        val seen = mutableSetOf<Pair<Long, Long>>()
        val edges = mutableListOf<Pair<Coordinate, Coordinate>>()

        for (i in 0 until diagram.numGeometries) {
            val cell = diagram.getGeometryN(i)
            if (cell !is Polygon) continue
            val ring = cell.exteriorRing.coordinates
            for (j in 0 until ring.size - 1) {
                val a = ring[j]
                val b = ring[j + 1]
                val edgeKey = canonicalEdgeKey(a, b)
                if (seen.add(edgeKey)) {
                    edges.add(a to b)
                }
            }
        }

        return edges
    }

    /** Canonical key for an edge using packed coordinate bits, avoiding z=NaN issues. */
    private fun canonicalEdgeKey(a: Coordinate, b: Coordinate): Pair<Long, Long> {
        val ka = packCoord(a)
        val kb = packCoord(b)
        return if (ka <= kb) ka to kb else kb to ka
    }

    private fun packCoord(c: Coordinate): Long {
        // XOR the bit representations to create a single Long key
        return java.lang.Double.doubleToLongBits(c.x) xor (java.lang.Double.doubleToLongBits(c.y) * 31)
    }

    // ── Step 5: Filter by containment ────────────────────────────────────────

    private fun filterEdgesByContainment(
        edges: List<Pair<Coordinate, Coordinate>>,
        polygon: Polygon,
    ): List<Pair<Coordinate, Coordinate>> {
        val prepared = PreparedGeometryFactory().create(polygon)
        return edges.filter { (a, b) ->
            prepared.containsProperly(geometryFactory.createPoint(a)) &&
                prepared.containsProperly(geometryFactory.createPoint(b))
        }
    }

    // ── Step 6: Build graph ──────────────────────────────────────────────────

    private fun buildGraph(
        edges: List<Pair<Coordinate, Coordinate>>,
    ): Pair<WeightedGraph, Map<Int, Coordinate>> {
        val graph = WeightedGraph()
        val coordToId = mutableMapOf<Pair<Double, Double>, Int>()
        val idToCoord = mutableMapOf<Int, Coordinate>()
        var nextId = 0

        fun getOrCreateId(c: Coordinate): Int {
            val key = c.x to c.y
            return coordToId.getOrPut(key) {
                val id = nextId++
                idToCoord[id] = c
                id
            }
        }

        for ((a, b) in edges) {
            val idA = getOrCreateId(a)
            val idB = getOrCreateId(b)
            val weight = a.distance(b)
            graph.addEdge(idA, idB, weight)
        }

        return graph to idToCoord
    }

    // ── Steps 7-8: Longest paths between end nodes ──────────────────────────

    private fun findLongestPaths(
        graph: WeightedGraph,
        endNodes: List<Int>,
        maxPaths: Int,
    ): List<List<Int>> {
        val paths = mutableListOf<Pair<Double, List<Int>>>()

        for (i in endNodes.indices) {
            for (j in i + 1 until endNodes.size) {
                val result = graph.dijkstra(endNodes[i], endNodes[j]) ?: continue
                paths.add(result)
            }
        }

        // Sort by distance descending, take top N, extract paths
        return paths
            .sortedByDescending { it.first }
            .take(maxPaths)
            .map { it.second }
    }

    // ── Step 9: Least-curved path ────────────────────────────────────────────

    private fun selectLeastCurved(
        paths: List<List<Int>>,
        idToCoord: Map<Int, Coordinate>,
    ): List<Int> {
        if (paths.size == 1) return paths[0]

        return paths.minByOrNull { path -> pathAngleSum(path, idToCoord) } ?: paths[0]
    }

    /** Sum of absolute angular changes at each interior vertex of the path. */
    private fun pathAngleSum(path: List<Int>, idToCoord: Map<Int, Coordinate>): Double {
        if (path.size < 3) return 0.0
        var sum = 0.0
        for (i in 1 until path.size - 1) {
            val a = idToCoord[path[i - 1]]!!
            val b = idToCoord[path[i]]!!
            val c = idToCoord[path[i + 1]]!!
            sum += absoluteAngle(a, b, c)
        }
        return sum
    }

    /** Absolute angle (radians) between vectors (a→b) and (b→c). */
    private fun absoluteAngle(a: Coordinate, b: Coordinate, c: Coordinate): Double {
        val v1x = a.x - b.x
        val v1y = a.y - b.y
        val v2x = c.x - b.x
        val v2y = c.y - b.y
        val cross = v1x * v2y - v1y * v2x
        val dot = v1x * v2x + v1y * v2y
        return abs(atan2(cross, dot))
    }

    // ── Step 10: Gaussian smoothing ──────────────────────────────────────────

    private fun gaussianSmooth(coords: List<Coordinate>, sigma: Double): List<Coordinate> {
        val kernel = makeGaussianKernel(sigma)
        val xs = DoubleArray(coords.size) { coords[it].x }
        val ys = DoubleArray(coords.size) { coords[it].y }
        val smoothedX = convolve1d(xs, kernel)
        val smoothedY = convolve1d(ys, kernel)
        return List(coords.size) { i -> Coordinate(smoothedX[i], smoothedY[i]) }
    }

    private fun makeGaussianKernel(sigma: Double): DoubleArray {
        val radius = ceil(3.0 * sigma).toInt()
        val size = 2 * radius + 1
        val kernel = DoubleArray(size) { i ->
            val x = (i - radius).toDouble()
            exp(-0.5 * x * x / (sigma * sigma))
        }
        val sum = kernel.sum()
        for (i in kernel.indices) kernel[i] /= sum
        return kernel
    }

    /** 1D convolution with reflect boundary mode (matching scipy default). */
    private fun convolve1d(values: DoubleArray, kernel: DoubleArray): DoubleArray {
        val radius = kernel.size / 2
        val n = values.size
        val result = DoubleArray(n)
        for (i in 0 until n) {
            var acc = 0.0
            for (k in kernel.indices) {
                var j = i + k - radius
                // Reflect at boundaries
                if (j < 0) j = -j
                if (j >= n) j = 2 * n - 2 - j
                j = j.coerceIn(0, n - 1) // safety clamp
                acc += values[j] * kernel[k]
            }
            result[i] = acc
        }
        return result
    }
}
