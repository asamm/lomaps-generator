package com.asamm.osmTools.utils;

import com.asamm.mapsforge.writer.extract.PolygonLoader;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class PolyUtils {

    /**
     * Parse an OSM Polygon Filter File (.poly) and write the result as a GeoJSON
     * FeatureCollection (WGS84 / CRS84) to geojsonFile.
     *
     * @param polyFile    path to the .poly source file
     * @param geojsonFile destination path for the GeoJSON output
     * @return the written geojsonFile as a {@link File}
     */
    public static File polyFileToGeoJson(Path polyFile, Path geojsonFile) {
        try {
            var geom = PolygonLoader.INSTANCE.load(polyFile);
            String geomJson = GeomUtils.geomToGeoJson(geom);
            String featureCollection =
                "{\n" +
                "  \"type\": \"Feature\",\n" +
                "  \"crs\": {\"type\": \"name\", \"properties\": {\"name\": \"urn:ogc:def:crs:OGC:1.3:CRS84\"}},\n" +
                "  \"properties\": {},\n" +
                "  \"geometry\": " + geomJson + "\n" +
                "}";
            Path parent = geojsonFile.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(geojsonFile, featureCollection);
            return geojsonFile.toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed to convert poly file to GeoJSON: " + polyFile, e);
        }
    }
}