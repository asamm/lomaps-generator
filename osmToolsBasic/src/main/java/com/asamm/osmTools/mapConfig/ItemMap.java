/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.mapConfig;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.sea.Boundaries;
import com.asamm.osmTools.utils.Consts;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;
import net.minidev.json.JSONObject;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.io.FileUtils;
import org.kxml2.io.KXmlParser;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 *
 * @author volda
 */
public class ItemMap extends AItemMap {

    private static final String TAG = ItemMap.class.getSimpleName();


    // CONSTANTS

    private static final String DIR_COASTLINES =
            Consts.fixDirectoryPath("coastlines");
    private static final String DIR_CONTOURS =
            Consts.fixDirectoryPath("_contours");
    private static final String DIR_DOWNLOAD =
            Consts.fixDirectoryPath("_download");
    private static final String DIR_EXTRACT =
            Consts.fixDirectoryPath("_extract");
    private static final String DIR_GRAPHHOPPER =
            Consts.fixDirectoryPath("_graphHopper");
    private static final String DIR_ADDRESS_POI_DB =
            Consts.fixDirectoryPath("_address_poi_db");
    private static final String DIR_GENERATE =
            Consts.fixDirectoryPath("_generate");
    private static final String DIR_MERGE =
            Consts.fixDirectoryPath("_merge");
    private static final String DIR_POLYGONS =
            Consts.fixDirectoryPath("polygons");
    private static final String DIR_RESULT =
            Consts.fixDirectoryPath("_result");
    private static final String DIR_TOURIST =
            Consts.fixDirectoryPath("_tourist");
    /* folder where to store customized or transformed original OSM data (e.q. city residential areas */
    private static final String DIR_TRANSFORM = Consts.fixDirectoryPath("_transform");

    // BASIC PARAMETERS

    // unique ID of item
    private String id;
    // name of file
    private String name;
    // how will item called in the store
    private String nameReadable;
    // name of item for generating (useful for separating languages)
    private String nameGen;


    // PATH PARAMETERS

    // path to local source file
    private String pathSource;
    // path where map will be generated
    private String pathGenerate;
    // path where will be generated contours
    private String pathGenerateContour;
    // path where results from GraphHopper should be placed
    private String pathGraphHopper;
    // path to store unzipped generated Address/POI databases
    private String pathAddressPoiDb;
    // path where generated pdf files should be merged
    private String pathMerge;
    // path to polygon file
    private String pathPolygon;
    // path to file with generated contours
    private String pathContour;
    // path where should be placed generated result (zipped)
    private String pathResult;
    // path to shp files
    private String pathShp;
    // path to file with coastlines
    private String pathCoastline;
    // path for tourist data
    private String pathTourist;
    // path for transformed or customized data file
    private String pathTranform;


    private String resultMD5hash;
    // bounds of this map generated from polygon file
    private Boundaries bounds;
    public boolean isMerged;

    // MAIN PART

    public ItemMap(ItemMapPack mpParent) {
        super(mpParent);
        isMerged = false;
    }
    
    public String getRelativeResultsPath(){
        if (pathResult != null){
            int lastIndex = (Consts.DIR_BASE + "_result").length();
            if (lastIndex == -1){
                return null;
            }
            return  pathResult.substring(lastIndex + 1);
        }
        return null;
    }
    
    public String getGeneratedFileNamePart(){
        File file = new File(pathResult);
        String fileName = file.getName();
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex != -1){
            // delete file extension
            return fileName.substring(0, lastDotIndex);
        }
        return null;
    }

    @Override
    public void validate() {
        // validate parent data
        super.validate();

        // check base parameters
        if (name == null || name.length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. " +
                    "Invalid argument file: " + name);
        }


        // check country name
        if (hasAction(Parameters.Action.ADDRESS_POI_DB) || hasAction(Parameters.Action.GENERATE)) {
            if (getCountryName() == null || getCountryName().length() == 0 ) {
                throw new IllegalArgumentException("Input XML is not valid. " +
                        "Nor readable name nor country name is not defined for map or it's parent - name:" + name);
            }
        }
    }

    public void setPaths(){
        String subPath = Parameters.getVersionName() + Consts.FILE_SEP + getDir() + name;

        // define extract and generation path and also create directory structure if is needed
        if (hasAction(Parameters.Action.DOWNLOAD)) {
            pathSource = Parameters.getDataDir() + DIR_DOWNLOAD +
                    subPath + "." + Parameters.mapOutputFormat;
        } else {
            pathSource = Parameters.getDataDir() + DIR_EXTRACT +
                    subPath + "." + Parameters.mapOutputFormat;
        }

        // base paths
        pathPolygon = Consts.DIR_BASE + DIR_POLYGONS +
                getDir() + name + ".poly";
        pathGraphHopper = Consts.DIR_BASE + DIR_GRAPHHOPPER +
                subPath + "-gh.zip";
        pathAddressPoiDb = Consts.DIR_BASE + DIR_ADDRESS_POI_DB +
                subPath + ".osm.db";
        pathContour = Parameters.getDataDir() + DIR_CONTOURS +
                getDir() + name + ".osm.pbf" ;
        pathTourist = Parameters.getDataDir() + DIR_TOURIST +
                subPath + ".osm.xml";
        pathShp = Parameters.getDataDir() + DIR_COASTLINES + "_shp" + Consts.FILE_SEP +
                getDir() + name + ".shp";
        pathCoastline = Parameters.getDataDir() + DIR_COASTLINES + "_pbf" + Consts.FILE_SEP +
                getDir() + name + ".osm.pbf";
        pathMerge =  Parameters.getDataDir() + DIR_MERGE +
                subPath + "." + Parameters.mapOutputFormat;
        pathTranform =  Parameters.getDataDir() + DIR_TRANSFORM + Consts.FILE_SEP +
                getDir() + name + ".osm.pbf";

        // parameters for generating
        if (hasAction(Parameters.Action.GENERATE)) {
            pathGenerate = Consts.DIR_BASE + DIR_GENERATE +
                    Parameters.getVersionName() + Consts.FILE_SEP + getDirGen();
            pathResult = Parameters.getDataDir() + DIR_RESULT +
                    Parameters.getVersionName() + Consts.FILE_SEP  + getDirGen();
            pathGenerateContour = Parameters.getDataDir() + DIR_CONTOURS +
                    Consts.FILE_SEP + getDir() + name + ".osm.map" ;

            // improve names
            if (nameGen != null && nameGen.length() > 0) {
                pathGenerate += nameGen +".osm.map";
                pathResult += nameGen + ".zip";
            } else {
                pathGenerate += name +".osm.map";
                pathResult += name + ".zip";
            }       
        }
    }

    /**************************************************/
    /*               GETTERS & SETTERS                */
    /**************************************************/

    // BASIC PARAMETERS

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNameReadable() {
        return nameReadable;
    }

    /**
     * Get readable name of country in which is item. If country name is not defined returns readable name of map item
     *
     * @return name of country in readable form or empty string if is not defined
     */
    @Override
    public String getCountryName() {

        String countryName = super.getCountryName();
        if (countryName == null || countryName.length() == 0){
            return nameReadable;
        }
        else{
            return countryName;
        }
    }

    // PATH PARAMETERS

    public String getPathSource() {
        return pathSource;
    }

    public String getPathGenerate() {
        return pathGenerate;
    }

    public String getPathGenerateContour() {
        return pathGenerateContour;
    }

    public String getPathGraphHopper() {
        return pathGraphHopper;
    }

    public String getPathAddressPoiDb() {
        return pathAddressPoiDb;
    }

    public String getPathMerge() {
        return pathMerge;
    }

    public String getPathPolygon() {
        return pathPolygon;
    }

    public String getPathJsonPolygon () {
        String str = pathPolygon.substring(0, pathPolygon.lastIndexOf("."));
        return str + ".json";
    }

    public String getPathCountryBoundaryGeoJson () {
        String str = pathPolygon.substring(0, pathPolygon.lastIndexOf("."));
        return str + "_country.geojson";
    }

    public String getPathContour() {
        return pathContour;
    }

    public String getPathResult() {
        return pathResult;
    }

    public String getPathTourist() {
        return pathTourist;
    }

    public String getPathShp() {
        return pathShp;
    }

    public String getPathCoastline() {
        return pathCoastline;
    }

    public String getPathTranform () {
        return pathTranform;
    }



    // OTHER GETTERS
    public Boundaries getBoundary() {
        return bounds;
    }

    public String getResultMD5Hash() {
        return resultMD5hash;
    }

    public void setResultMD5hash(String resultMD5hash) {
        this.resultMD5hash = resultMD5hash;
    }

    /**************************************************/
    /*                PARSE FUNCTIONS                 */
    /**************************************************/

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
            if (nameReadable == null || nameReadable.length() == 0){
                Logger.w(TAG, "Config.xml not valid: Missing attribute name on line : " +parser.getLineNumber());
            }
        }
        if (parser.getAttributeValue(null, "fileGen") != null) {
            nameGen = Utils.changeSlash(parser.getAttributeValue(null, "fileGen"));
        }

        // test if MAP are valid
        validate();
    }

    /**************************************************/
    /*                  VARIOUS TOOLS                 */
    /**************************************************/

    public void setBoundsFromPolygon() throws IOException {

        if (pathPolygon == null ){
            bounds = null;
            return;
        }
        File polyFile =  new File(pathPolygon);
        if (!polyFile.exists()){
            bounds = null;
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
            while ((line = br.readLine()) != null){
                //remove white space before and ond end of string line then
                // split string based on whitespace (regular expresion \\s+
                String[] cols = line.trim().split("\\s+");
                if (cols.length != 2){
                    continue;
                }
                if (Utils.isNumeric(cols[0]) && Utils.isNumeric(cols[1])){
                    Double lon = Double.parseDouble(cols[0]);
                    Double lat = Double.parseDouble(cols[1]);

                    maxLongitude = Math.max(lon, maxLongitude);
                    maxLatitude = Math.max(lat, maxLatitude);
                    minLongitude = Math.min(lon, minLongitude);
                    minLatitude = Math.min(lat, minLatitude);
                }
            }
            bounds = new Boundaries();
            bounds.setMinLon(minLongitude);
            bounds.setMinLat(minLatitude);
            bounds.setMaxLon(maxLongitude);
            bounds.setMaxLat(maxLatitude);

        } finally {
            if (br != null){
                br.close();
            }
        }
    }

    /**
     * Read definition of map polygon from GeoJson
     * @return
     */
    public JSONObject getItemAreaGeoJson() {

        // read json file with area definition
        File fileJsonPolyg = new File(getPathJsonPolygon());
        if (!fileJsonPolyg.exists()){
            throw new IllegalArgumentException("JSON polygon file doesn't exist "+fileJsonPolyg.getAbsolutePath());
        }

        String jsonPolygon = "";
        try {
            jsonPolygon =  FileUtils.readFileToString(fileJsonPolyg, UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
            throw new IllegalArgumentException("Can not read JSON polygon file "+fileJsonPolyg.getAbsolutePath());
        }
        // replace line brakes
        JSONParser parser=new JSONParser(net.minidev.json.parser.JSONParser.DEFAULT_PERMISSIVE_MODE);
        JSONObject obj= null;
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
                ", mPathSource='" + pathSource + '\'' +
                ", mPathGenerate='" + pathGenerate + '\'' +
                ", mPathGenerateContour='" + pathGenerateContour + '\'' +
                ", mPathGraphHopper='" + pathGraphHopper + '\'' +
                ", mPathAddressPoiDb='" + pathAddressPoiDb + '\'' +
                ", mPathMerge='" + pathMerge + '\'' +
                ", mPathPolygon='" + pathPolygon + '\'' +
                ", mPathContour='" + pathContour + '\'' +
                ", mPathResult='" + pathResult + '\'' +
                ", mPathTourist='" + pathTourist + '\'' +
                ", mPathShp='" + pathShp + '\'' +
                ", mPathCoastline='" + pathCoastline + '\'' +
                ", mResultMD5hash='" + resultMD5hash + '\'' +
                ", mBounds=" + bounds +
                ", isMerged=" + isMerged +
                '}';
    }


}
