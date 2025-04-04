package com.asamm.geoutils

import org.locationtech.jts.geom.*
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.IOException
import java.nio.file.Path

/**
 * Reader for OSM Polygon File Format (.poly files)
 * See: https://wiki.openstreetmap.org/wiki/Osmosis/Polygon_Filter_File_Format
 */
class PolyReader {

    private val geometryFactory = GeometryFactory()

    /**
     * Reads a .poly file and converts it to a JTS Polygon or MultiPolygon
     *
     * @param file The .poly file
     * @return The JTS geometry representing the polygon with holes
     */
    fun read(file: File): Geometry {
        if (!file.exists()) {
            throw IOException("File not found: ${file.absolutePath}")
        }

        return BufferedReader(FileReader(file)).use { reader ->
            // First line is the multipolygon name (ignore)
            reader.readLine()

            val polygons = mutableListOf<Polygon>()
            var line: String?

            // Process each polygon section until EOF
            while (reader.readLine().also { line = it } != null) {
                if (line == "END") break

                // Start of a new polygon section (name line)
                val outerRings = mutableListOf<LinearRing>()
                val holes = mutableMapOf<Int, MutableList<LinearRing>>()

                var isHole = false
                var currentRing = mutableListOf<Coordinate>()

                // Process all rings in this polygon section
                while (reader.readLine().also { line = it } != null) {
                    when {

                        line?.startsWith("!") == true -> {
                            isHole = true
                        }

                        line == "END" && currentRing.isNotEmpty() -> {
                            // Close the ring
                            if (!currentRing.first().equals2D(currentRing.last())) {
                                currentRing.add(currentRing.first()) // add first point to close the ring
                            }

                            val linearRing = geometryFactory.createLinearRing(currentRing.toTypedArray())

                            // Add the ring to the appropriate list (outer or hole)
                            if (isHole) {
                                holes.getOrPut(outerRings.size, { mutableListOf() }).add(linearRing)
                            } else {
                                outerRings.add(linearRing)
                            }
                            currentRing = mutableListOf()
                            isHole = false // Reset hole flag
                        }

                        line == "END" -> {
                            // End of this polygon section
                            break
                        }

                        line?.trim()?.isNotEmpty() == true -> {
                            val parts = line!!.trim().split(Regex("\\s+"))
                            if (parts.size >= 2) {
                                try {
                                    val x = parts[0].toDouble()
                                    val y = parts[1].toDouble()
                                    currentRing.add(Coordinate(x, y))
                                } catch (e: NumberFormatException) {
                                    // Skip invalid lines
                                }
                            }
                        }
                    }
                }

                // iterate over outer rings and create polygon with holes
                outerRings.forEachIndexed { index, outerRing ->
                    val holesList = holes[index] ?: emptyList()
                    if (holesList.isEmpty()) {
                        val polygon = geometryFactory.createPolygon(outerRing)
                        polygons.add(polygon)
                    } else {
                        val polygon = geometryFactory.createPolygon(outerRing, holesList.toTypedArray())
                        polygons.add(polygon)
                    }
                }
            }

            // Return appropriate geometry based on number of polygons
            when (polygons.size) {
                0 -> throw IOException("No valid polygons found in file")
                1 -> polygons[0]
                else -> geometryFactory.createMultiPolygon(polygons.toTypedArray())
            }
        }
    }

    /**
     * Reads a .poly file and converts it to a JTS Polygon or MultiPolygon
     *
     * @param path Path to the .poly file
     * @return The JTS geometry representing the polygon with holes
     */
    fun read(path: Path): Geometry {
        return read(path.toFile())
    }
}