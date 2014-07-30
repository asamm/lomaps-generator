/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.utils.Utils;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipException;

/**
 *
 * @author volda
 */
public class CreateXml {
    StringBuilder sb;
    String awsBucketUrl;
    public CreateXml() {
        
        
    }

    public void startWrite() {
        // write xml header
        sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\"?>\n<maps>");
    } 
    
    public void addMap(MapSource ms, ItemMap map) throws ZipException, IOException{
        
        // START MAP tag
        sb.append("\n\t<map file=\""+map.getGeneratedFileNamePart()+"\" name=\""+map.getName()+"\">");
                
        addMainSub(ms, map);

        
        //ADD URL TAG
        String relPath;
        if ((relPath = map.getRelativeResultsPath()) == null){
            throw new IllegalArgumentException("Create XML: no relative path for "+map.getName());
        }
        relPath = Utils.changeSlashToUnix(relPath);
        sb.append("\n\t\t<webUrl><![CDATA[http://server.asamm.com/LocusServer/Main?action=get_vector_map_data&map="
                    +relPath+"]]></webUrl>");
        
        //DATA tag
        System.out.println(map.getPathResult());
        long fileSize = Utils.getZipEntrySize(new File(map.getPathResult()));
        long fileSizeZip = new File (map.getPathResult()).length();
        //long lastChange = new File (map.resultPath).lastModified();
        
        sb.append("\n\t\t<data sizeMap=\""+fileSize+"\" sizeZip=\""+fileSizeZip+"\" dateMap=\""+Parameters.getSourceDataLastModifyDate()+"\"/>");
        
        //Close map tag
        sb.append("\n\t</map>");
    }
    
    public void addMainSub(MapSource ms, ItemMap map){
        File file = new File(map.getPathResult());
        File parentDir = new File(file.getParent());
        String parentDirName = parentDir.getName();
        
        //System.out.println("Parent name: "+parentName);
        
        ItemMapPack mp = ms.getMapPackByDir(parentDirName);
        if (mp == null){
            throw new IllegalArgumentException("Create XML: Cant find mappack "+parentDirName);
        }
        
        if (mp.getParent() == null){
            sb.append("\n\t\t<areaMain dir=\""+ parentDirName+"\" name=\""+mp.getName()+"\"/>");
        }
        // if exist parent print areaMain and also areaSub
        if (mp.getParent() != null){
            File parentParentdir = new File(parentDir.getParent());
            sb.append("\n\t\t<areaMain dir=\""+ parentParentdir.getName()+"\" name=\""+mp.getParent().getName()+"\"/>");
            sb.append("\n\t\t<areaSub dir=\""+ parentDirName +"\" name=\""+mp.getName()+"\"/>");
        }
        
    }
    
    public void finish() throws IOException {
        sb.append("\n</maps>");
        FileUtils.writeStringToFile(new File(Parameters.outputXml), sb.toString(), false);
        Main.LOG.info("Maps xml generated succesfully");
    }
}
