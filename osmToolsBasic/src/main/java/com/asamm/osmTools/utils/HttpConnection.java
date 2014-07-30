/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.utils;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Hashtable;
import java.util.Map;

import com.asamm.osmTools.Main;
import org.apache.commons.io.IOUtils;

/**
 *
 * @author voldapet
 */
public class HttpConnection {

    
    
    public interface ResponseHandler {
        public void  onResult (int statusCode, String message, byte[] data);
        public void onFailure (Exception e);
    }
    
    public static final String TYPE_GET = "GET";
    public static final String TYPE_POST = "POST";
    
    int timeout = 3000;
    String urlString;
    String type;
    String charset = "UTF-8";
    
    final String userAgent = "Map-server";
    
    Hashtable<String, String> urlParameters;
    
    
    public HttpConnection(String urlString, String type){
        this(urlString, type, null);
        
                
    }
    
    public HttpConnection (String urlString, String type, Hashtable<String, String> urlParameters){
        this.urlString = urlString;
        this.type = type;
        this.urlParameters = urlParameters;
    }
    
    
    public void doRequest(ResponseHandler resHandler){
        
        try {
            if (type.equals(TYPE_GET)){
                sendGet(resHandler);
            }
            if (type.equals(TYPE_POST)){
               sendPost(resHandler);
            }
        }
        catch (MalformedURLException ex) {
            Main.LOG.severe(ex.toString());
        } catch (IOException ex) {
            Main.LOG.severe(ex.toString());
        }
        
    }
    
    public void sendPost(ResponseHandler resHandler) throws MalformedURLException, IOException{
        
        URL url = new URL(urlString);
        HttpURLConnection connection =  (HttpURLConnection) url.openConnection();
        connection.setConnectTimeout(timeout);
        connection.setRequestProperty("Accept-Charset", charset);
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=" + charset);
        
        
        // Send post request and write parameters into output stream
        connection.setDoOutput(true);
        DataOutputStream dos = new DataOutputStream(connection.getOutputStream());
        String query = getQuery();
        dos.writeBytes(query);
        
        dos.flush();
        IOUtils.closeQuietly(dos);

        // get response code
        int responseCode =  connection.getResponseCode();
        System.out.println("response code: " +responseCode);
			
        if (responseCode == HttpURLConnection.HTTP_OK){
        
            BufferedInputStream bis = null;
		
            int buff = 8168;
	
            bis = new BufferedInputStream(connection.getInputStream(), 	buff);
            byte[] buffer = new byte[buff]; 
            ByteArrayOutputStream baos = new ByteArrayOutputStream(buff);

            int conSize = connection.getContentLength();
            System.out.println("Size [kb]: "+conSize /1024);
            int size;
            while ((size = bis.read(buffer)) >= 0){
                    baos.write(	buffer, 0, size);
            }
            baos.flush();
            IOUtils.closeQuietly(baos);

            resHandler.onResult(responseCode, connection.getResponseMessage(), baos.toByteArray());
        }
        
        else {
            resHandler.onResult(responseCode, connection.getResponseMessage(), null);
            Main.LOG.warning(connection.getResponseMessage());
        }
        
        
        // testing mozna odebrat
        connection.disconnect();
    }
     
    public void sendGet(ResponseHandler resHandler){
        
    }
    
    /**
     * Function read Hashtable with parameters and create URl string from it
     */
    private String getQuery() {
        if (urlParameters == null){
            return "";
        }
        
        String query = "";
        for (Map.Entry<String, String> entry : urlParameters.entrySet()){
            query += entry.getKey()+ "=";
            query += entry.getValue() + "&";
        }
        return query;
    }
}
