/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.utils;

import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import org.apache.commons.io.FileUtils;

/**
 *
 * @author volda
 */
public class MyLogger {

    static private Logger logger;
    private File fileToWrite;
    
    private SimpleDateFormat sdf;
    public Writer out = null;
   
    
    public MyLogger(String filePath) {
        fileToWrite = new File(filePath);
        //crate dirs for logging
        try {
            FileUtils.forceMkdir(fileToWrite);
        } catch (IOException e) {
            e.printStackTrace();
        }
        fileToWrite = new File(fileToWrite.getAbsolutePath());
        File logDir = fileToWrite.getParentFile();
        File files[] = logDir.listFiles();
        String logNamePart = fileToWrite.getName().substring(0 ,fileToWrite.getName().lastIndexOf("."));
        //System.out.println(logNamePart);
        int hiCount = 0;
        for (File file : files){
            if (file.getName().startsWith(logNamePart)){
                //System.out.println(fileToWrite.getName());
                String fileName = file.getName();
                String lastCount = fileName.substring(fileName.lastIndexOf("_")+1,fileName.lastIndexOf("."));
                if (Utils.isNumeric(lastCount)){
                    Integer count = Integer.parseInt(lastCount);
                    if (count > hiCount){
                        hiCount = count;
                    }
                }
            }
        }

        if (hiCount > 0){                
            hiCount++;
            filePath = logDir.getAbsolutePath() + Consts.FILE_SEP + logNamePart +"_"+
                        hiCount + filePath.substring(filePath.lastIndexOf("."));
            
        } else {
            filePath = logDir.getAbsolutePath() + Consts.FILE_SEP + logNamePart +
                    "_1"+filePath.substring(filePath.lastIndexOf("."));
        } 
        
        fileToWrite= new File(filePath);
        
        try {
            FileOutputStream fos = new FileOutputStream(fileToWrite);
            out = new OutputStreamWriter(fos, "UTF-8");
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            out.write("Starting simple log: " + sdf.format(new Date())+"\n");
            out.flush();
            //out.close();
            
            
        } catch (Exception e){
            Main.LOG.severe(e.toString());

        } 
    }
    

    
    public String getPath(){
        return fileToWrite.getAbsolutePath();
    }
    public void print(String str){
        
       
        try {
            //FileOutputStream fos = new FileOutputStream(fileToWrite, true);
            //out = new OutputStreamWriter(fos, "UTF-8");
            out.write(str);
            out.flush();
        } catch (Exception e){
            Main.LOG.severe(e.toString());
            e.printStackTrace();
        }
    }
    
    public void closeWriter(){
        try {
            out.close();
        } catch (Exception e){
            Main.LOG.severe(e.toString());
            e.printStackTrace();
        }
    } 
    
     
}
