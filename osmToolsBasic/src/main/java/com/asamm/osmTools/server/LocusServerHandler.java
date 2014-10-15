/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.server;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.utils.HttpConnection;
import com.asamm.osmTools.utils.Utils;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.Hashtable;
import java.util.zip.ZipException;

/**
 * Class has functions for uploading information about generated map in to LocusServer
 * @author voldapet
 */
public class LocusServerHandler {
    
    
    private static LocusServerHandler instance;
    
    
    private LocusServerHandler () {
        // TODO part for encoding key
        // String key = ...
    }
    
    public static LocusServerHandler getInstance () {
        if (instance == null){
            instance = new LocusServerHandler();
        }
        
        return instance;
    }
    
    
    public boolean uploadInfoToLocusServer(ItemMap map) throws ZipException, IOException {
        File file = new File(map.getPathResult());
        
        //check if file exists 
        if (!file.exists()) {
            Main.LOG.severe("uploadToLocusStore: Source file: ("+ file.getAbsolutePath() +") does not exist.");
            return false;
        }
        
        final String uploadUrl = getUploadUrl();
        
        // ================ Create data object =======
        String jsonData = createJsonString(map);
        System.out.println(jsonData);
        
        Hashtable<String, String> postParameters =  new Hashtable<String, String>();
        postParameters.put(LocusServerConst.JSON_DATA, jsonData);
        
        // create connection
        HttpConnection httpConnection = new HttpConnection(uploadUrl, HttpConnection.TYPE_POST, postParameters);
        
        httpConnection.doRequest(new HttpConnection.ResponseHandler() {

            @Override
            public void onResult(int statusCode, String message, byte[] data) {
                Main.LOG.fine(message);
                
                
                
                if (statusCode == HttpURLConnection.HTTP_OK){
                    String response = new String(data);
                    System.out.println(response);
                    
                    // TODO response is OK check jSOn
                } 
                else {
                    String msg  = "Wrong response code from connection on URL: " + uploadUrl
                            + "\n Status code: " + statusCode
                            + "\n Response message: " + message;
                    throw new IllegalArgumentException (msg);
                }                
            }

            @Override
            public void onFailure(Exception e) {
                Main.LOG.severe("Error occurs during Http connection on URL: " + uploadUrl);
                throw new IllegalArgumentException ("Error occurs during Http connection on URL: " + uploadUrl);
            }
        });
        
        return false;
        
    }
    
    private String getUploadUrl (){
        StringBuilder sb = new StringBuilder();
        sb.append(LocusServerConst.PROTOCOL).append("://");
        sb.append(LocusServerConst.HOST).append("/");
        sb.append(LocusServerConst.SECTION_DATA).append("?");
        sb.append(LocusServerConst.ACTION).append("=").append(LocusServerConst.ACTION_DATA_ADD_ITEM_VECTOR);
        
        return sb.toString();
    }
    
    private String createJsonString(ItemMap map) throws ZipException, IOException{
        
        File file = new File(map.getPathResult());
        
                
        String relPath;
        if ((relPath = map.getRelativeResultsPath()) == null){
            throw new IllegalArgumentException("Uplad google server: Can not create relative path for "+map.getName());
        }
        relPath = Utils.changeSlashToUnix(relPath);
        
        System.out.println(map.getClientDestinationPath());
        
        JSONObject jsonObj = new JSONObject();
        jsonObj.put(LocusServerConst.ACTION_DATA_NAME, map.getName() + " - offline vector map");
        jsonObj.put(LocusServerConst.TYPE, LocusServerConst.ITEM_TYPE_VECTOR);
        jsonObj.put(LocusServerConst.ACTION_DATA_FILE_NAME, file.getName());
        jsonObj.put(LocusServerConst.ACTION_DATA_FILE_SIZE, file.length());
        jsonObj.put(LocusServerConst.ACTION_DATA_FILE_SIZE_FINAL, Utils.getZipEntrySize(file));
        jsonObj.put(LocusServerConst.ACTION_DATA_FILE_MD5_HASH,map.getResultMD5Hash());
        jsonObj.put(LocusServerConst.ACTION_DATA_FILE_PATH, relPath);
        jsonObj.put(LocusServerConst.ACTION_DATA_DESTINATION_PATH, map.getClientDestinationPath());
        jsonObj.put(LocusServerConst.ACTION_DATA_VERSION_NAME, Parameters.getVersionName());
        jsonObj.put(LocusServerConst.ACTION_DATA_VERSION_CODE, Parameters.VERSION_CODE);
        jsonObj.put(LocusServerConst.ACTION_DATA_REGION_ID, map.getRegionId());
        jsonObj.put(LocusServerConst.ACTION_DATA_PROVIDER_ID, LocusServerConst.STORE_DATA_PROVIDER_ID_ASAMMSW);
        jsonObj.put(LocusServerConst.ACTION_DATA_USAGE_ID, LocusServerConst.STORE_DATA_USAGE_ID_MAPS_VECTOR);
        jsonObj.put(LocusServerConst.ACTION_DATA_SUPPORTED_APK, LocusServerConst.supportedVersions);
        
        jsonObj.put(LocusServerConst.ACTION_DATA_DESCRIPTION, Parameters.VECTOR_DESCRIPTION);
        jsonObj.put(LocusServerConst.ACTION_DATA_PREFERED_LANG, map.getPrefLang());
        
        // set welcome present based on the size of zip file
        long length1GB = 1024*1024*1024;
        if (file.length() > length1GB) {
            jsonObj.put(LocusServerConst.ACTION_DATA_CAN_BE_WELCOME_PRESENT, false);
        } else {
            jsonObj.put(LocusServerConst.ACTION_DATA_CAN_BE_WELCOME_PRESENT, Parameters.CAN_BE_WELCOME_PRESENT);
        }
        
       
        jsonObj.put(LocusServerConst.ACTION_DATA_ICON, Parameters.VECTOR_IC0N);
        JSONArray list = new JSONArray();
        for (int i = 0; i < Parameters.VECTOR_IMAGES.length; i++ ){
            list.add(Parameters.VECTOR_IMAGES[i]);
        }
        
        jsonObj.put(LocusServerConst.ACTION_DATA_IMAGES, list);
        
        
        StringWriter sw = new StringWriter();
        jsonObj.writeJSONString(sw);
        
        return sw.toString();
        
    }
}
