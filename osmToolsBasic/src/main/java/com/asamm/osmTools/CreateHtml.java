/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.utils.Utils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.text.SimpleDateFormat;

/**
 *
 * @author volda
 */
public class CreateHtml {
    
    StringBuilder sb;
    BufferedInputStream bis;
    BufferedOutputStream bos;
    FileOutputStream fos;
    SimpleDateFormat sdf;
    
   
    
    public CreateHtml() throws IOException {
        
        // test if header html exists
        if (!new File(Parameters.htmlMapHeaderFile).exists()){
            throw new IllegalArgumentException("File with html header "+Parameters.htmlMapHeaderFile+" does not exist!");
        }
        
        //copy header into new file
        try {
            FileInputStream fis =  new FileInputStream(new File(Parameters.htmlMapHeaderFile));
            bis =  new BufferedInputStream(fis);
            fos = new FileOutputStream(new File(Parameters.htmlMapPath));
            bos = new BufferedOutputStream(fos);

            IOUtils.copy(bis, bos);
        } finally{
            IOUtils.closeQuietly(bis);
            IOUtils.closeQuietly(bos);
        }
        
        //define date format
        sdf = new SimpleDateFormat("yyyy-MM-dd");
    }
    
    public void startString() {
        // write xml header
        sb = new StringBuilder();
        sb.append("\n<body style=\"margin:40px;background-color:rgb(255, 255, 255);\">\n\n\n");
        sb.append("<ul id=\"qm0\" class=\"qmmc\">");
    } 
    
    public void writeString() throws IOException {
        sb.append("<li class=\"qmclear\">&nbsp;</li></ul>");
        sb.append( 
             "\n\n\n\n<!-- Create Menu Settings: (Menu ID, Is Vertical, Show Timer, Hide Timer, On Click ('all' or 'lev2'), Right to Left, Horizontal Subs, Flush Left, Flush Top) -->"
            + "\n<script type=\"text/javascript\">qm_create(0,false,0,500,'all',false,false,false,false);</script>"
            + "\n</body>"
            + "\n</html>");
        
        FileUtils.write(new File(Parameters.htmlMapPath), sb.toString(), true);
    }
    
    public void addDir(ItemMapPack mp){
        sb.append("\n<li><a class=\"qmparent\" href=\"javascript:void(0)\">"+mp.getName()+"</a>");
    }
    
    public void starDir(ItemMapPack mp){
        sb.append("\n<ul id=\"qm0\" class=\"qmmc\">");
    }
    
    public void endDir (){
        sb.append("\n</ul>");
                
    }
    public void endLi (){
        sb.append("\n</li>");
    }
    public void startUl(){
        sb.append("\n<ul>");
    }
    public void addMap(ItemMap map){
        sb.append("\n<li><a href=\"javascript:void(0)\"></a>");
        sb.append("\n<span>"
                + "<p id=\"map_title\">"+map.getName()+"</p>");
        sb.append(
                "\n<table  id=\"tab_body\">"
		+ "\n\t<tr><td align=\"right\">Size: </td>"
                + "\n\t <td align=\"left\"> "+
                    Utils.formatBytesToHuman(new File (map.getPathResult()).length())+"</td></tr>"
		+ "\n\t<tr><td align=\"right\">Crated: </td>"
                + "\n\t <td align=\"left\"> "+
                    sdf.format(new File (map.getPathResult()).lastModified())+"</td></tr>"
                + "\n</table>"
            );
        sb.append("\n</span>\n</li>");
                
        
    }
    public void endUl(){
        sb.append("\n</ul>");
    }
  
    
}
