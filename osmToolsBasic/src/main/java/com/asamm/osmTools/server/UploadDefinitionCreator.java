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
import java.io.FileReader;
import java.io.IOException;
import java.util.Iterator;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Class for generation definition JSON for upload of vector files to GCS and GAE
 */
public class UploadDefinitionCreator {

    private static final String TAG = UploadDefinitionCreator.class.getSimpleName();

    /***************** CONSTS *****************/
    //name of item is created from name of map + this postfix
    private static final String ITEM_NAME_VECTOR_POSTFIX = " - LoMaps";
    // define folder that contains vector maps in client
    private static final String CLIENT_VECTOR_MAP_DESTINATION = "mapsVector/";


    private static UploadDefinitionCreator instance = null;

    // JSON file with general settings for upload
    private static final String STORE_ITEM_DEFINITION_PATH = "storeUpload/store_item_definition.json";

    /**
     * JSON object that hold whole definition of files / store item for uploading
     */
    private JSONArray jsonItems;

    /**
     * Contains definition for upload that are common for all maps
     */
    private JSONObject defJson;



    private UploadDefinitionCreator() {

        jsonItems = new JSONArray();


        JSONParser parser = new JSONParser();

        Object obj = null;
        try {
            // load general definition that is common for all uploaded LoMaps
            obj = parser.parse(new FileReader(STORE_ITEM_DEFINITION_PATH));
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ParseException e) {
            e.printStackTrace();
        }

        defJson = (JSONObject) obj;

        Logger.i(TAG, defJson.toJSONString());
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

    /**
     * Prepare JSON string from whole definition
     * @return
     */
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

    /**
     * Create JSON that contains needed definition for upload one map
     * @param map map to create upload JSON
     * @return
     */
    private JSONObject createJsonItem (ItemMap map){

        // create copy from common definition json
        JSONObject jsonItem = new JSONObject(defJson);

        File resultFile = new File(map.getPathResult());

        if (!resultFile.exists()){
            throw new IllegalArgumentException("Create definition upload JSON failed: File for uploading does not exist:  "+map.getPathResult());
        }

        JSONArray listing = new JSONArray();
        JSONArray listingDef = (JSONArray) jsonItem.get(LocusServerConst.LISTING);
        String name = map.getNameReadable() + ITEM_NAME_VECTOR_POSTFIX;

        Iterator<JSONObject> iterator = listingDef.iterator();
        while (iterator.hasNext()){
            // create copy for language of listing
            JSONObject langJson = new JSONObject(iterator.next());
            langJson.put(LocusServerConst.NAME,name);
            listing.add(langJson);
        }
        // assing new listing to the item json
        jsonItem.put(LocusServerConst.LISTING, listing);

        // compute loCoins
        jsonItem.put(LocusServerConst.LOCOINS, computeLocoins(resultFile) );

        jsonItem.put(LocusServerConst.REGION_IDS, getItemRegion(map.getRegionId()));

        jsonItem.put(LocusServerConst.PREFERED_LANG, map.getPrefLang());

        // ------- VERSION --- add JSON object of version
        JSONObject jsonVersionDef = (JSONObject) jsonItem.get(LocusServerConst.VERSION);
        jsonItem.put(LocusServerConst.VERSION, createJsonVersion(jsonVersionDef, map));

        // put polygon definition into item obj.
        jsonItem.put(LocusServerConst.ITEM_AREA, map.getItemAreaGeoJson());

        return jsonItem;
    }



    private JSONObject createJsonVersion(JSONObject jsonVersion, ItemMap map){

        jsonVersion = new JSONObject(jsonVersion);

        jsonVersion.put(LocusServerConst.NAME, Parameters.getVersionName());
        jsonVersion.put(LocusServerConst.FILE, createJsonFile(map));

        //Logger.i (TAG, createJsonFile(map).toJSONString());
        return jsonVersion;
    }


    /**
     * Prepare JSON object where are defined important information for ItemVersionFile
     * @param map
     * @return
     */
    private JSONObject createJsonFile (ItemMap map){

        JSONObject jsonFile =  new JSONObject();
        jsonFile.put(LocusServerConst.FILE_DELETE_SOURCE, true);
        jsonFile.put(LocusServerConst.FILE_UNPACK, true);
        jsonFile.put(LocusServerConst.FILE_REFRESH_MAPS, true);
        jsonFile.put(LocusServerConst.LOCATIONS, map.getPathResult());
        jsonFile.put(LocusServerConst.DESTINATION_PATH, getClientDestinationPath(map));

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
        double loCoins = fileSize/1024.0/1024.0 / 8;  // 8 it is because 1MB costed about 0.1 Locoin but we increase price

        float locoinsRounded = (float) Math.ceil(loCoins/5) * 5; // round up to number multiply by 5


        //Logger.i(TAG, "File size in MB: " + fileSize/1024.0/1024.0 + ", Locoins computed: " + loCoins + ", loCoins rounded: " + locoinsRounded);

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
        //long length1GB = 1024*1024*1024;
        //return resultFile.length() <= length1GB;

        //all vector maps can be welcome present
        return true;
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
     * Prepare JSONarray from simple array of regionIds
     * @return
     */
    private JSONArray getItemRegion (String regionId){
        JSONArray jsonRegions = new JSONArray();
        jsonRegions.add(regionId);
        return  jsonRegions;
    }
}
