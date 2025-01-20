package com.asamm.osmTools.server;

import com.asamm.locus.api.v2.server.admin.StoreAdminFile;
import com.asamm.locus.api.v2.server.admin.StoreAdminItem;
import com.asamm.locus.api.v2.server.admin.StoreAdminItemListing;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.config.AppConfig;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.utils.Consts;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.Utils;
import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONStyle;
import net.minidev.json.parser.JSONParser;
import net.minidev.json.parser.ParseException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

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
    private static final String STORE_ITEM_DEFINITION_PATH = Consts.DIR_BASE + "config" + Consts.FILE_SEP + "default_store_item_definition.json";
    ;

    /**
     * JSON object that hold whole definition of files / store item for uploading
     */
    private List<StoreAdminItem> storeItems;

    /**
     * Contains definition for upload that are common for all maps
     */
    private JSONObject defJson;

    private UploadDefinitionCreator() {

        storeItems = new ArrayList<>();

        JSONParser parser = new JSONParser(JSONParser.DEFAULT_PERMISSIVE_MODE);

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

    /**
     * Add new map to the JSON definition generator
     * @param map map to add into upload definition
     */
    public void addMap (ItemMap map){

        Logger.i(TAG, "creating storeAdmin item for " + map.getName());

        storeItems.add(mapToStoreAdminItem(map));
    }

    /**
     * Write generated upload json into def file
     */
    public void writeToJsonDefFile() {

        String defString  =  getDefinitionJsonString();
        //Logger.i(TAG, defString);
        File defFile = AppConfig.config.getStoreUploadDefinitionJson().toFile();

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

        if (storeItems.size() == 0){
            // no map was created into JSON object so there is nothing to write into file
            Logger.w(TAG,"Array of items definition for upload is empty - nothing to write into upload definition JSON");
            return "";
        }

        JSONArray jsonArray = new JSONArray();

        for (StoreAdminItem sai : storeItems){
            jsonArray.add(sai.toJson()) ;
        }

        return jsonArray.toJSONString();
    }

    /**
     * Create Store Item defintion that contains needed definition for upload one map
     * @param map map to create item definition
     * @return
     */
    private StoreAdminItem mapToStoreAdminItem(ItemMap map){

        // create copy from common definition json
        StoreAdminItem sai = new StoreAdminItem(defJson);

        File resultFile = map.getPathResult().toFile();

        if (!resultFile.exists()){
            throw new IllegalArgumentException("Create definition upload JSON failed: File for uploading does not exist:  "+map.getPathResult());
        }

        // set map name to listing
        String name = map.getNameReadable() + ITEM_NAME_VECTOR_POSTFIX;
        for (StoreAdminItemListing sail : sai.getListings()){
            sail.setName(name);
        }

        // compute loCoins
        sai.setLoCoins(computeLocoins(resultFile));

        sai.setRegionDatastoreIds(Arrays.asList(map.getRegionId()));

        // Version
        sai.getVersion().setName(AppConfig.config.getVersion());
        sai.getVersion().setStoreAdminFile(createJsonFile(map));

        // put polygon definition into item obj.
        sai.setItemArea(map.getItemAreaGeoJson());

        return sai;
    }


    /**
     * Prepare Admin file obj where are defined important information for ItemVersionFile
     * @param map map we create upload definition for
     * @return item file definition
     */
    private StoreAdminFile createJsonFile (ItemMap map){

        StoreAdminFile saf = new StoreAdminFile();
        saf.setClientDeleteSource(true);
        saf.setClientFileUnpack(true);
        saf.setLocationPath(map.getPathResult().toString());
        saf.setClientDestination(getClientDestinationPath(map));

        return saf;
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
}
