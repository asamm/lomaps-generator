package com.asamm.osmTools.server;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import java.io.File;
import java.io.IOException;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Class for generation definition JSON for upload of vector files to GCS and GAE
 */
public class UploadDefinitionCreator {

    private static final String TAG = UploadDefinitionCreator.class.getSimpleName();

    /***************** CONSTS *****************/
    //name of item is created from name of map + this postfix
    private static final String ITEM_NAME_VECTOR_POSTFIX = " - offline vector map";
    // define folder that contains vector maps in client
    private static final String CLIENT_VECTOR_MAP_DESTINATION = "mapsVector/";
    // define if created/updated version will be in the end set as active version
    private static final boolean SET_NEW_VERSION_AS_ACTIVE = true;


    private static UploadDefinitionCreator instance = null;

    /**
     * JSON object that hold whole definition of files / store item for uploading
     */
    private JSONArray jsonItems;

    private UploadDefinitionCreator() {

        jsonItems = new JSONArray();

    }


    public static UploadDefinitionCreator getInstace () {
        if (instance == null){
            instance = new UploadDefinitionCreator();
        }
        return instance;
    }

    public void addMap (ItemMap map){

        Logger.i(TAG, "creating json definition for " + map.getName());

        JSONObject jsonItem = createJsonItem(map);

        //Logger.i(TAG, "Add json item into array " + jsonItem.toJSONString());

        jsonItems.add(jsonItem);
    }


    public void writeToFile() {

        String defString  =  getDefinitionJsonString();
        //Logger.i(TAG, defString);
        File defFile = new File(Parameters.getUploadDefinitionJsonPath());


        try {
            FileUtils.writeStringToFile(defFile, defString, UTF_8);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getDefinitionJsonString (){

        if (jsonItems.size() == 0){
            // no map was created into JSON object so there is nothing to write into file
            Logger.w(TAG,"Array of items definition for upload is empty - nothing to write into upload definition JSON");
            return "";
        }

        JSONObject jsonDef = new JSONObject();

        jsonDef.put(LocusServerConst.JSON_ITEMS, jsonItems);
        return jsonDef.toJSONString();
    }

    private JSONObject createJsonItem (ItemMap map){

        JSONObject jsonItem = new JSONObject();

        File resultFile = new File(map.getPathResult());

        if (!resultFile.exists()){
            throw new IllegalArgumentException("Create definition upload JSON failed: File for uploading does not exist:  "+map.getPathResult());
        }

        jsonItem.put(LocusServerConst.ACTION_DATA_NAME, map.getNameReadable() + ITEM_NAME_VECTOR_POSTFIX );
        jsonItem.put(LocusServerConst.ACTION_DATA_ICON, LocusServerConst.VECTOR_IC0N_ID);

        jsonItem.put(LocusServerConst.ACTION_DATA_IMAGE_IDS, getItemImages());
        jsonItem.put(LocusServerConst.ACTION_DATA_DESCRIPTION, LocusServerConst.VECTOR_DESCRIPTION);

        jsonItem.put(LocusServerConst.ACTION_DATA_ITEM_TYPE, LocusServerConst.VECTOR_ITEM_TYPE_UNIT);
        jsonItem.put(LocusServerConst.ACTION_DATA_ENABLED, true);
        jsonItem.put(LocusServerConst.ACTION_DATA_AVAILABLE_FOR, LocusServerConst.VECTOR_AVAILABLE_FOR);
        jsonItem.put(LocusServerConst.ACTION_DATA_CAN_BE_WELCOME_PRESENT, canBeWelcomePresent(resultFile));

        // compute loCoins
        jsonItem.put(LocusServerConst.ACTION_DATA_LOCOINS, computeLocoins(resultFile) );

        jsonItem.put(LocusServerConst.ACTION_DATA_USAGE_IDS, getItemUsages());
        jsonItem.put(LocusServerConst.ACTION_DATA_PROVIDER_ID, LocusServerConst.VECTOR_PROVIDER_ID);
        jsonItem.put(LocusServerConst.ACTION_DATA_REGION_IDS, getItemRegion(map.getRegionId()));

        jsonItem.put(LocusServerConst.ACTION_DATA_PREFERED_LANG, map.getPrefLang());

        // add JSON object of version
        jsonItem.put(LocusServerConst.ACTION_DATA_VERSION, createJsonVersion(map));

        // put polygon definition into item obj.
        jsonItem.put(LocusServerConst.ACTION_DATA_ITEM_AREA, getItemPolygonJson(map));

        return jsonItem;
    }




    private JSONObject createJsonVersion (ItemMap map){
        JSONObject jsonVersion = new JSONObject();
        jsonVersion.put(LocusServerConst.ACTION_DATA_SUPPORTED_APK, createJsonSupportedApk());
        jsonVersion.put(LocusServerConst.ACTION_DATA_CODE, LocusServerConst.VECTOR_VERSION_CODE);
        jsonVersion.put(LocusServerConst.ACTION_DATA_NAME, Parameters.getVersionName());
        jsonVersion.put(LocusServerConst.ACTION_DATA_FILE, createJsonFile(map));

        jsonVersion.put(LocusServerConst.ACTION_DATA_SET_ACTIVE, SET_NEW_VERSION_AS_ACTIVE);

        return jsonVersion;
    }

    private JSONObject createJsonSupportedApk (){
        JSONObject  jsonApks =  new JSONObject(LocusServerConst.supportedVersions);
        return jsonApks;
    }

    /**
     * Prepare JSON object where are defined important information for ItemVersionFile
     * @param map
     * @return
     */
    private JSONObject createJsonFile (ItemMap map){

        JSONObject jsonFile =  new JSONObject();
        jsonFile.put(LocusServerConst.ACTION_DATA_FILE_DELETE_SOURCE, true);
        jsonFile.put(LocusServerConst.ACTION_DATA_FILE_UNPACK, true);
        jsonFile.put(LocusServerConst.ACTION_DATA_FILE_REFRESH_MAPS, true);
        jsonFile.put(LocusServerConst.LOCATIONS, map.getPathResult());
        jsonFile.put(LocusServerConst.ACTION_DATA_DESTINATION_PATH, getClientDestinationPath(map));

        return jsonFile;
    }

    /**
     * Define the price for map based on the size of result file
     * @param resultFile
     * @return the amounth of Locoins that will be set for item in store
     */
    private float computeLocoins (File resultFile){
        // compute value of loCoins
        long fileSize = resultFile.length();
        double loCoins = fileSize/1024.0/1024.0 * 0.1;  //0.1 it is because 1MB costs about 0.1 Locoin
        float locoinsRounded = (float) Math.ceil(loCoins);

        //Logger.i(TAG, "File size: " + fileSize + ", Locoins computed: " + loCoins + ", loCoins rounded: " + locoinsRounded);

        return locoinsRounded;
    }

    /**
     * Decide based on the file size if item can be welcome present
     * Only files smaller then 1GB can be welcome present
     * @param resultFile
     * @return
     */
    private boolean canBeWelcomePresent(File resultFile) {

        // set welcome present based on the size of zip file
        long length1GB = 1024*1024*1024;
        return resultFile.length() <= length1GB;
    }

    /**
     * Function create relative path which define where will be file
     * stored after downloading into Locus.
     * @return relative path in Locus
     */
    private String getClientDestinationPath (ItemMap map) {
        return Utils.changeSlashToUnix(CLIENT_VECTOR_MAP_DESTINATION + map.getDirGen());
    }

    /**
     * Prepare JSONarray from simple array of imageIds
     * @return
     */
    private JSONArray getItemImages (){
        JSONArray jsonImages = new JSONArray();
        for (int i=0; i < LocusServerConst.VECTOR_IMAGE_IDS.length; i++){
            jsonImages.add(LocusServerConst.VECTOR_IMAGE_IDS[i]);
        }
        return  jsonImages;
    }

    /**
     * Prepare JSONarray from simple array of regionIds
     * @return
     */
    private JSONArray getItemRegion (String regionId){
        JSONArray jsonRegions = new JSONArray();
        jsonRegions.add(regionId);
        return  jsonRegions;
    }


    /**
     * Prepare JSONarray from simple array of imageIds
     * @return
     */
    private JSONArray getItemUsages (){
        JSONArray jsonUsages = new JSONArray();
        for (int i=0; i < LocusServerConst.VECTOR_USAGE_IDS.length; i++){
            jsonUsages.add(LocusServerConst.VECTOR_USAGE_IDS[i]);
        }
        return  jsonUsages;
    }

    /**
     * Read definition of map polygon from GeoJson
     * @param map
     * @return
     */
    private JSONObject getItemPolygonJson (ItemMap map) {

        // read json file with area definition
        File fileJsonPolyg = new File(map.getPathJsonPolygon());
        if (!fileJsonPolyg.exists()){
            throw new IllegalArgumentException("JSON polygon file doesn't exist "+fileJsonPolyg.getAbsolutePath());
        }

        String jsonPolygon = "";
        try {
            jsonPolygon =  FileUtils.readFileToString(fileJsonPolyg,UTF_8);
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


}
