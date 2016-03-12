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

import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
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

    // BASIC PARAMETERS

    // unique ID of item
    private String mId;
    // name of file
    private String mName;
    // how will item called in the store
    private String mNameReadable;
    // name of item for generating (useful for separating languages)
    private String mNameGen;
    // prefered language for generating
    private String mPrefLang;

    // PATH PARAMETERS

    // path to local source file
    private String mPathSource;
    // path where map will be generated
    private String mPathGenerate;
    // path where will be generated contours
    private String mPathGenerateContour;
    // path where results from GraphHopper should be placed
    private String mPathGraphHopper;
    // path to store unzipped generated Address/POI databases
    private String mPathAddressPoiDb;
    // path where generated pdf files should be merged
    private String mPathMerge;
    // path to polygon file
    private String mPathPolygon;
    // path to file with generated contours
    private String mPathContour;
    // path where should be placed generated result (zipped)
    private String mPathResult;
    // path for tourist data
    private String mPathTourist;
    // path to shp files
    private String mPathShp;
    // path to file with coastlines
    private String mPathCoastline;


    private String mResultMD5hash;
    // bounds of this map generated from polygon file
    private Boundaries mBounds;
    public boolean isMerged;

    // MAIN PART

    public ItemMap(ItemMapPack mpParent) {
        super(mpParent);
        isMerged = false;
    }
    
    public String getRelativeResultsPath(){
        if (mPathResult != null){
            int lastIndex = (Consts.DIR_BASE + "_result").length();
            if (lastIndex == -1){
                return null;
            }
            return  mPathResult.substring(lastIndex + 1);
        }
        return null;
    }
    
    public String getGeneratedFileNamePart(){
        File file = new File(mPathResult);
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
        if (mName == null || mName.length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. " +
                    "Invalid argument file: " + mName );
        }


        // check country name
        if (hasAction(Parameters.Action.ADDRESS_POI_DB) || hasAction(Parameters.Action.GENERATE)) {
            if (getCountryName().length() == 0 ) {
                throw new IllegalArgumentException("Input XML is not valid. " +
                        "Nor readable name nor country name is not defined for map or it's parent - name:" + mName);
            }
        }
    }

    public void setPaths(){
        String subPath = Parameters.getVersionName() + Consts.FILE_SEP + getDir() + mName;

        // define extract and generation path and also create directory structure if is needed
        if (hasAction(Parameters.Action.DOWNLOAD)) {
            mPathSource = Parameters.getDataDir() + DIR_DOWNLOAD +
                    subPath + "." + Parameters.mapOutputFormat;
        } else {
            mPathSource = Parameters.getDataDir() + DIR_EXTRACT +
                    subPath + "." + Parameters.mapOutputFormat;
        }

        // base paths
        mPathPolygon = Consts.DIR_BASE + DIR_POLYGONS +
                getDir() + mName + ".poly";
        mPathGraphHopper = Consts.DIR_BASE + DIR_GRAPHHOPPER +
                subPath + "-gh.zip";
        mPathAddressPoiDb = Consts.DIR_BASE + DIR_ADDRESS_POI_DB +
                subPath + ".osm.db";
        mPathContour = Parameters.getDataDir() + DIR_CONTOURS +
                getDir() + mName + ".osm.pbf" ;
        mPathTourist = Parameters.getDataDir() + DIR_TOURIST +
                subPath + ".osm.xml";
        mPathShp = Parameters.getDataDir() + DIR_COASTLINES + "_shp" + Consts.FILE_SEP +
                getDir() + mName + ".shp";
        mPathCoastline = Parameters.getDataDir() + DIR_COASTLINES + "_pbf" + Consts.FILE_SEP +
                getDir() + mName + ".osm.pbf";
        mPathMerge =  Parameters.getDataDir() + DIR_MERGE +
                subPath + "." + Parameters.mapOutputFormat;

        // parameters for generating
        if (hasAction(Parameters.Action.GENERATE)) {
            mPathGenerate = Consts.DIR_BASE + DIR_GENERATE +
                    Parameters.getVersionName() + Consts.FILE_SEP + getDirGen();
            mPathResult = Parameters.getDataDir() + DIR_RESULT +
                    Parameters.getVersionName() + Consts.FILE_SEP  + getDirGen();
            mPathGenerateContour = Parameters.getDataDir() + DIR_CONTOURS +
                    Consts.FILE_SEP + getDir() + mName + ".osm.map" ;

            // improve names
            if (mNameGen != null && mNameGen.length() > 0) {
                mPathGenerate += mNameGen +".osm.map";
                mPathResult += mNameGen + ".zip";
            } else {
                mPathGenerate += mName +".osm.map";
                mPathResult += mName + ".zip";
            }       
        }
    }

    /**************************************************/
    /*               GETTERS & SETTERS                */
    /**************************************************/

    // BASIC PARAMETERS

    public String getId() {
        return mId;
    }

    public String getName() {
        return mName;
    }

    public String getNameReadable() {
        return mNameReadable;
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
            return mNameReadable;
        }
        else{
            return countryName;
        }
    }

    public String getPrefLang() {
        return mPrefLang;
    }

    // PATH PARAMETERS

    public String getPathSource() {
        return mPathSource;
    }

    public String getPathGenerate() {
        return mPathGenerate;
    }

    public String getPathGenerateContour() {
        return mPathGenerateContour;
    }

    public String getPathGraphHopper() {
        return mPathGraphHopper;
    }

    public String getPathAddressPoiDb() {
        return mPathAddressPoiDb;
    }

    public String getPathMerge() {
        return mPathMerge;
    }

    public String getPathPolygon() {
        return mPathPolygon;
    }

    public String getPathJsonPolygon () {
        String str = mPathPolygon.substring(0, mPathPolygon.lastIndexOf("."));
        return str + ".json";
    }

    public String getPathCountryBoundaryGeoJson () {
        String str = mPathPolygon.substring(0, mPathPolygon.lastIndexOf("."));
        return str + "_country.geojson";
    }

    public String getPathContour() {
        return mPathContour;
    }

    public String getPathResult() {
        return mPathResult;
    }

    public String getPathTourist() {
        return mPathTourist;
    }

    public String getPathShp() {
        return mPathShp;
    }

    public String getPathCoastline() {
        return mPathCoastline;
    }

    public Boundaries getBoundary() {
        return mBounds;
    }

    public String getResultMD5Hash() {
        return mResultMD5hash;
    }

    public void setResultMD5hash(String resultMD5hash) {
        this.mResultMD5hash = resultMD5hash;
    }

    /**************************************************/
    /*                PARSE FUNCTIONS                 */
    /**************************************************/

    public void fillAttributes(KXmlParser parser) {
        // fill base parameters
        super.fillAttributes(parser);

        // set other private values
        if (parser.getAttributeValue(null, "id") != null) {
            mId = parser.getAttributeValue(null, "id");
        }
        if (parser.getAttributeValue(null, "file") != null) {
            mName = Utils.changeSlash(parser.getAttributeValue(null, "file"));
        }
        if (parser.getAttributeValue(null, "name") != null) {
            mNameReadable = Utils.changeSlash(parser.getAttributeValue(null, "name"));
        }
        if (parser.getAttributeValue(null, "fileGen") != null) {
            mNameGen = Utils.changeSlash(parser.getAttributeValue(null, "fileGen"));
        }
        if (parser.getAttributeValue(null, "prefLang") != null) {
            mPrefLang = parser.getAttributeValue(null, "prefLang");
        }

        // test if MAP are valid
        validate();
    }

    /**************************************************/
    /*                  VARIOUS TOOLS                 */
    /**************************************************/

    public void setBoundsFromPolygon() throws IOException {
        if (mPathPolygon == null ){
            mBounds = null;
            return;
        }
        File polyFile =  new File(mPathPolygon);
        if (!polyFile.exists()){
            mBounds = null;
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
            mBounds = new Boundaries();
            mBounds.setMinLon(minLongitude);
            mBounds.setMinLat(minLatitude);
            mBounds.setMaxLon(maxLongitude);
            mBounds.setMaxLat(maxLatitude);
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
        JSONParser parser=new JSONParser();
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
                "mId='" + mId + '\'' +
                ", mName='" + mName + '\'' +
                ", mNameGen='" + mNameGen + '\'' +
                ", mPrefLang='" + mPrefLang + '\'' +
                ", mPathSource='" + mPathSource + '\'' +
                ", mPathGenerate='" + mPathGenerate + '\'' +
                ", mPathGenerateContour='" + mPathGenerateContour + '\'' +
                ", mPathGraphHopper='" + mPathGraphHopper + '\'' +
                ", mPathAddressPoiDb='" + mPathAddressPoiDb + '\'' +
                ", mPathMerge='" + mPathMerge + '\'' +
                ", mPathPolygon='" + mPathPolygon + '\'' +
                ", mPathContour='" + mPathContour + '\'' +
                ", mPathResult='" + mPathResult + '\'' +
                ", mPathTourist='" + mPathTourist + '\'' +
                ", mPathShp='" + mPathShp + '\'' +
                ", mPathCoastline='" + mPathCoastline + '\'' +
                ", mResultMD5hash='" + mResultMD5hash + '\'' +
                ", mBounds=" + mBounds +
                ", isMerged=" + isMerged +
                '}';
    }


}
