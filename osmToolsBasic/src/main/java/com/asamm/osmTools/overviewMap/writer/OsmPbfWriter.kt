package com.asamm.osmTools.overviewMap.writer

import com.asamm.osmTools.overviewMap.OverviewMapFeature
import com.asamm.osmTools.utils.Logger
import crosby.binary.file.BlockOutputStream
import crosby.binary.osmosis.OsmosisSerializer
import org.locationtech.jts.geom.GeometryCollection
import org.locationtech.jts.geom.LinearRing
import org.locationtech.jts.geom.LineString
import org.locationtech.jts.geom.MultiLineString
import org.locationtech.jts.geom.MultiPoint
import org.locationtech.jts.geom.MultiPolygon
import org.locationtech.jts.geom.Point
import org.locationtech.jts.geom.Polygon
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData
import org.openstreetmap.osmosis.core.domain.v0_6.EntityType
import org.openstreetmap.osmosis.core.domain.v0_6.Node
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser
import org.openstreetmap.osmosis.core.domain.v0_6.Relation
import org.openstreetmap.osmosis.core.domain.v0_6.RelationMember
import org.openstreetmap.osmosis.core.domain.v0_6.Tag
import org.openstreetmap.osmosis.core.domain.v0_6.Way
import org.openstreetmap.osmosis.core.domain.v0_6.WayNode
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Date

/**
 * Writes Natural Earth features as an OSM PBF file using Osmosis + crosby.binary serializer.
 *
 * Converts JTS geometries into OSM nodes, ways, and relations:
 * - Point → Node
 * - LineString → Way + Nodes
 * - Polygon → Relation (type=multipolygon) + outer Way + optional inner Ways + Nodes
 * - MultiPolygon → multiple Relations (type=multipolygon), each with outer/inner Ways + Nodes
 * - MultiLineString → multiple Ways + Nodes
 *
 * PBF format requires sorted output: all Nodes first, then Ways, then Relations.
 */
class OsmPbfWriter(
    private val outputPath: Path,
    startNodeId: Long,
    startWayId: Long,
    startRelationId: Long,
) {
    companion object {
        private const val TAG = "OsmPbfWriter"
        private val EPOCH = Date(0)
        private val OSM_USER = OsmUser(0, "naturalearth")

        private fun clampLon(lon: Double) = lon.coerceIn(-180.0, 180.0)
        private fun clampLat(lat: Double) = lat.coerceIn(-90.0, 90.0)
    }

    private var nextNodeId = startNodeId
    private var nextWayId = startWayId
    private var nextRelationId = startRelationId

    // Collected entities to be sorted before writing
    private val nodes = mutableListOf<Node>()
    private val ways = mutableListOf<Way>()
    private val relations = mutableListOf<Relation>()

    fun write(features: List<OverviewMapFeature>) {
        Logger.i(TAG, "Converting ${features.size} features to OSM entities...")

        // Phase 1: Convert all features to OSM entities
        for (feature in features) {
            convertFeature(feature)
        }

        Logger.i(TAG, "Created ${nodes.size} nodes, ${ways.size} ways, ${relations.size} relations")

        // Phase 2: Sort entities by ID (PBF requirement)
        nodes.sortBy { it.id }
        ways.sortBy { it.id }
        relations.sortBy { it.id }

        // Phase 3: Write PBF
        writePbf()

        Logger.i(TAG, "PBF written: $outputPath")
    }

    /**
     * Converts a single OverviewFeature into OSM entities based on its geometry type.
     * Handles geometry types and creates appropriate nodes, ways, and relations with tags.
     */
    private fun convertFeature(feature: OverviewMapFeature) {
        val tags = feature.osmTags.map { (k, v) -> Tag(k, v) }
        when (val geom = feature.geometry) {
            is Point -> convertPoint(geom, tags)
            is MultiPoint -> {
                for (i in 0 until geom.numGeometries) {
                    convertPoint(geom.getGeometryN(i) as Point, tags)
                }
            }
            is LinearRing -> convertRing(geom, tags)
            is LineString -> convertLineString(geom, tags)
            is MultiLineString -> {
                for (i in 0 until geom.numGeometries) {
                    convertLineString(geom.getGeometryN(i) as LineString, tags)
                }
            }
            is Polygon -> convertPolygon(geom, tags)
            is MultiPolygon -> convertMultiPolygon(geom, tags)
            is GeometryCollection -> {
                for (i in 0 until geom.numGeometries) {
                    convertFeature(OverviewMapFeature(geom.getGeometryN(i), feature.osmTags, feature.sourceLayer))
                }
            }
        }
    }

    /** Converts a JTS Point to an OSM Node with the given tags. */
    private fun convertPoint(point: Point, tags: Collection<Tag>) {
        val node = Node(
            CommonEntityData(nextNodeId++, 1, EPOCH, OSM_USER, 0, tags),
            clampLat(point.y), clampLon(point.x)
        )
        nodes.add(node)
    }

    /** Converts LineString to OSM way */
    private fun convertLineString(line: LineString, tags: Collection<Tag>): Way {
        val wayNodeRefs = mutableListOf<WayNode>()
        for (i in 0 until line.numPoints) {
            val coord = line.getCoordinateN(i)
            val nodeId = nextNodeId++
            nodes.add(
                Node(
                    CommonEntityData(nodeId, 1, EPOCH, OSM_USER, 0, emptyList()),
                    clampLat(coord.y), clampLon(coord.x)
                )
            )
            wayNodeRefs.add(WayNode(nodeId))
        }

        val way = Way(
            CommonEntityData(nextWayId++, 1, EPOCH, OSM_USER, 0, tags),
            wayNodeRefs
        )
        ways.add(way)
        return way
    }

    /**
     * Converts a [LinearRing] to a closed OSM way
     */
    private fun convertRing(ring: LinearRing, tags: Collection<Tag>): Way {
        val wayNodeRefs = mutableListOf<WayNode>()
        val firstNodeId = nextNodeId
        // numPoints includes the closing duplicate — skip it (i < numPoints - 1)
        for (i in 0 until ring.numPoints - 1) {
            val coord = ring.getCoordinateN(i)
            val nodeId = nextNodeId++

            nodes.add(
                Node(
                    CommonEntityData(nodeId, 1, EPOCH, OSM_USER, 0, emptyList()),
                    clampLat(coord.y), clampLon(coord.x)
                )
            )
            wayNodeRefs.add(WayNode(nodeId))
        }
        // Close the ring by referencing the first node again (valid OSM closed way)
        wayNodeRefs.add(WayNode(firstNodeId))

        val way = Way(
            CommonEntityData(nextWayId++, 1, EPOCH, OSM_USER, 0, tags),
            wayNodeRefs
        )
        ways.add(way)
        return way
    }

    /** Converts a [Polygon] to an OSM multipolygon relation with outer/inner roles. */
    private fun convertPolygon(polygon: Polygon, tags: Collection<Tag>) {
        val members = mutableListOf<RelationMember>()

        // Outer ring — always role "outer"
        val outerWay = convertRing(polygon.exteriorRing, emptyList())
        members.add(RelationMember(outerWay.id, EntityType.Way, "outer"))

        // Inner rings (holes) — role "inner"
        for (i in 0 until polygon.numInteriorRing) {
            val innerWay = convertRing(polygon.getInteriorRingN(i), emptyList())
            members.add(RelationMember(innerWay.id, EntityType.Way, "inner"))
        }

        val relTags = tags.toMutableList()
        relTags.add(Tag("type", "multipolygon"))

        relations.add(
            Relation(
                CommonEntityData(nextRelationId++, 1, EPOCH, OSM_USER, 0, relTags),
                members
            )
        )
    }

    private fun convertMultiPolygon(multiPolygon: MultiPolygon, tags: Collection<Tag>) {
        for (i in 0 until multiPolygon.numGeometries) {
            convertPolygon(multiPolygon.getGeometryN(i) as Polygon, tags)
        }
    }

    private fun writePbf() {
        Files.createDirectories(outputPath.parent)

        val output = FileOutputStream(outputPath.toFile())
        val serializer = OsmosisSerializer(BlockOutputStream(output))

        try {
            serializer.initialize(emptyMap())

            // Write in required order: nodes, ways, relations
            for (node in nodes) {
                serializer.process(NodeContainer(node))
            }
            for (way in ways) {
                serializer.process(WayContainer(way))
            }
            for (relation in relations) {
                serializer.process(RelationContainer(relation))
            }

            serializer.complete()
        } finally {
            serializer.close()
        }
    }
}