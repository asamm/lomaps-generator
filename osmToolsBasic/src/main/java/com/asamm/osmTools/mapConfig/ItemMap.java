/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.mapConfig;

import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.sea.Boundaries;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;
import lombok.Getter;
import lombok.Setter;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.io.FileUtils;
import org.kxml2.io.KXmlParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * @author volda
 */
public class ItemMap extends AItemMap {

    private static final String TAG = ItemMap.class.getSimpleName();

    // BASIC PARAMETERS

    // unique ID of item
    @Getter
    private String id;
    // name of file
    @Getter
    private String name;
    // how will item called in the store
    @Getter
    private String nameReadable;

    // name of item for generating (useful for separating languages)
    private String nameGen;

    // PATH PARAMETERS
    private final PathResolver pathResolver;

    @Setter
    private String resultMD5hash;

    // bounds of this map generated from polygon file
    @Getter
    private Boundaries boundary;

    @Getter
    @Setter
    private boolean isMerged = false;

    // MAIN PART

    public ItemMap(ItemMapPack mpParent) {
        super(mpParent);

         pathResolver = new PathResolver(this);

    }
//
//    public String getRelativeResultsPath() {
//        if (pathResult != null) {
//            int lastIndex = (Consts.DIR_BASE + "_result").length();
//            if (lastIndex == -1) {
//                return null;
//            }
//            return pathResult.toString().substring(lastIndex + 1);
//        }
//        return null;
//    }

    @Override
    public void validate() {
        // validate parent data
        super.validate();

        // check base parameters
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Input XML is not valid. " +
                    "Invalid argument file: " + name);
        }

        // check country name
        if (hasAction(Action.ADDRESS_POI_DB) || hasAction(Action.GENERATE_MAPSFORGE)) {
            if (getCountryName() == null || getCountryName().isEmpty()) {
                throw new IllegalArgumentException("Input XML is not valid. " +
                        "Nor readable name nor country name is not defined for map or it's parent - name:" + name);
            }
        }
    }

    // GETTERS & SETTERS

    public Path getPathSource() {
        return pathResolver.getPath(PathType.EXTRACT, name + ".osm.pbf");
    }

    public Path getPathGenerate() {
        if (nameGen != null && !nameGen.isEmpty()) {
            return pathResolver.getPath(PathType.MAPSFORGE_GENERATE, nameGen + ".osm.map");
        }
        return pathResolver.getPath(PathType.MAPSFORGE_GENERATE, name + ".osm.map");
    }

    public Path getPathGenMlOutdoor() {
        return pathResolver.getPath(PathType.MAPLIBRE_ONLINE_OUTDOOR, name + "_lm_outdoor.mbtiles");
    }

    public Path getPathGenMlOpenMapTiles() {
        return pathResolver.getPath(PathType.MAPLIBRE_ONLINE_OPENMAPTILES, name + "_openmaptiles.mbtiles");
    }

    public Path getPathAddressPoiDb() {
        return pathResolver.getPath(PathType.ADDRESS_POI_DB, name + ".osm.db");
    }

    public Path getPathMerge() {
        return pathResolver.getPath(PathType.MERGE, name + ".osm.pbf");
    }

    public Path getPathPolygon() {
        return pathResolver.getPath(PathType.POLYGON, name + ".poly");
    }

    public Path getPathJsonPolygon() {
        return Utils.changeFileExtension(getPathPolygon(), ".json");
    }

    public Path getPathCountryBoundaryGeoJson() {
        return Utils.changeFileExtension(getPathPolygon(), "_country.geojson");
    }

    public Path getPathContour() {
        return pathResolver.getPath(PathType.CONTOUR, name + ".osm.pbf");
    }

    public Path getPathResult() {
        if (nameGen != null && !nameGen.isEmpty()) {
            return pathResolver.getPath(PathType.MAPSFORGE_RESULT, nameGen + ".zip");
        }
        return pathResolver.getPath(PathType.MAPSFORGE_RESULT, name + ".zip");
    }

    public Path getPathShp() {
        return pathResolver.getPath(PathType.SHP, name + ".shp");
    }

    public Path getPathCoastline() {
        return pathResolver.getPath(PathType.COASTLINE, name + ".osm.pbf");
    }

    public Path getPathTourist() {
        return pathResolver.getPath(PathType.TOURIST, name + ".osm.pbf");
    }

    public Path getPathTranform() {
        return pathResolver.getPath(PathType.TRANSFORM, name + ".osm.pbf");
    }

    // BASIC PARAMETERS

    /**
     * Get readable name of country in which is item. If country name is not defined returns readable name of map item
     *
     * @return name of country in readable form or empty string if is not defined
     */
    @Override
    public String getCountryName() {

        String countryName = super.getCountryName();
        if (countryName == null || countryName.isEmpty()) {
            return nameReadable;
        } else {
            return countryName;
        }
    }

    //  PARSE FUNCTIONS

    public void fillAttributes(KXmlParser parser) {
        // fill base parameters
        super.fillAttributes(parser);

        // set other private values
        if (parser.getAttributeValue(null, "id") != null) {
            id = parser.getAttributeValue(null, "id");
        }
        if (parser.getAttributeValue(null, "file") != null) {
            name = Utils.changeSlash(parser.getAttributeValue(null, "file"));
        }
        if (parser.getAttributeValue(null, "name") != null) {
            nameReadable = Utils.changeSlash(parser.getAttributeValue(null, "name"));
            if (nameReadable.isEmpty()) {
                Logger.w(TAG, "Config.xml not valid: Missing attribute name on line : " + parser.getLineNumber());
            }
        }
        if (parser.getAttributeValue(null, "fileGen") != null) {
            nameGen = Utils.changeSlash(parser.getAttributeValue(null, "fileGen"));
        }

        // test if MAP are valid
        validate();
    }

    // VARIOUS TOOLS

    public void setBoundsFromPolygon() throws IOException {

        File polyFile = getPathPolygon().toFile();
        if (!polyFile.exists()) {
            boundary = null;
            return;
        }

        double maxLatitude = -90.0;
        double maxLongitude = -180.0;
        double minLatitude = 90.0;
        double minLongitude = 180.0;

        //Scanner scan = null;
        BufferedReader br = null;
        try {
            //scan = new Scanner (new BufferedReader(new FileReader(polyFile)));
            br = new BufferedReader(new FileReader(polyFile));
            String line;
            while ((line = br.readLine()) != null) {
                //remove white space before and ond end of string line then
                // split string based on whitespace (regular expresion \\s+
                String[] cols = line.trim().split("\\s+");
                if (cols.length != 2) {
                    continue;
                }
                if (Utils.isNumeric(cols[0]) && Utils.isNumeric(cols[1])) {
                    double lon = Double.parseDouble(cols[0]);
                    double lat = Double.parseDouble(cols[1]);

                    maxLongitude = Math.max(lon, maxLongitude);
                    maxLatitude = Math.max(lat, maxLatitude);
                    minLongitude = Math.min(lon, minLongitude);
                    minLatitude = Math.min(lat, minLatitude);
                }
            }
            boundary = new Boundaries(minLongitude, maxLongitude, minLatitude, maxLatitude);
        } finally {
            if (br != null) {
                br.close();
            }
        }
    }

    /**
     * Read definition of map polygon from GeoJson
     */
    public JSONObject getItemAreaGeoJson() {

        // read json file with area definition
        File fileJsonPolyg = getPathJsonPolygon().toFile();
        if (!fileJsonPolyg.exists()) {
            throw new IllegalArgumentException("JSON polygon file doesn't exist " + fileJsonPolyg.getAbsolutePath());
        }

        String jsonPolygon = "";
        try {
            jsonPolygon = FileUtils.readFileToString(fileJsonPolyg, UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Can not read JSON polygon file " + fileJsonPolyg.getAbsolutePath());
        }
        // replace line brakes
        JSONParser parser = new JSONParser(net.minidev.json.parser.JSONParser.DEFAULT_PERMISSIVE_MODE);
        JSONObject obj = null;
        try {
            obj = (JSONObject) parser.parse(jsonPolygon);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        return obj;
    }

    @Override
    public String toString() {
        return "ItemMap{" +
                "mId='" + id + '\'' +
                ", mName='" + name + '\'' +
                ", mNameGen='" + nameGen + '\'' +
                ", mResultMD5hash='" + resultMD5hash + '\'' +
                ", mBounds=" + boundary +
                ", isMerged=" + isMerged +
                '}';
    }
}
