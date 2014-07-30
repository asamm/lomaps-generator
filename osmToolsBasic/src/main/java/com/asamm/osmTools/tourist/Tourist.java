/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.tourist;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.cmdCommands.CmdTourist;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.utils.SparseArray;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.*;
import java.util.ArrayList;

/**
 *
 * @author volda
 */
public class Tourist {

    //Relations relations;
    SparseArray<Relation> relations;
    
    WayList wl;
    BufferedReader stdInput = null;
    
    FileOutputStream fos = null;
    OutputStreamWriter osw = null;
    BufferedWriter bw = null;
    
    public String xmlInput;
    public File xmlInputFile;
    public File xmlOutputFile;
    
    String strBound;
    ArrayList<Node> cycloJunctions;
    private boolean isNLBE;
    
    ItemMap map;
    
    public Tourist(ItemMap map) throws IOException {
        this.map = map;
       
        //input file
        xmlInputFile = new File(map.getPathSource());
       
        // where output will be stored
        FileUtils.forceMkdir(new File(map.getPathTourist()));
        xmlOutputFile = new File(map.getPathTourist());
        
        //create realtions obj
        relations = new SparseArray<Relation>();
        wl = new WayList();
        
        //is need to diverse normal cyclo and BENL cyclo
        isNLBE = (map.getCycleNode() != null && map.getCycleNode().equals("NLBE"));
        cycloJunctions = new ArrayList<Node>();
    }
    
    public void create () throws IOException, XmlPullParserException {
        Main.LOG.info("Start parsing file "+map.getPathSource()+" for tourist path");
        //newParse();
        parse();
        reorganize();
        Main.LOG.info("Start writing tourist path to file "+map.getPathTourist());
        parseWays();
    }
    
//    public void converse() {
//        //if (new)
//    }
   
    
    public void parse() throws IOException, XmlPullParserException {
         
        
         try {
            CmdTourist ct  =  new CmdTourist(map);
            ct.createCmd();
            ProcessBuilder pb = ct.executePb();
            
            Process runTime = pb.start();
            
             stdInput = new BufferedReader(new 
                 InputStreamReader(runTime.getInputStream()));
           
            KXmlParser parser = new KXmlParser();
            parser.setInput(stdInput);
            
            int tag;
            String tagName;
            Relation rel = null;
            Node node =  null;
            while ((tag = parser.next()) != KXmlParser.END_DOCUMENT) {
                if (tag == KXmlParser.START_TAG) {
                    tagName = parser.getName().toLowerCase();
                    
                    //store information about bounds of file 
                    //System.out.println(tagName);
                    if ((tagName.startsWith("bound"))){
                        //copy HEADIGNS
                        strBound = startTag2String(parser) + "/>";
                    }
                    // for NLBE cyclo map is needed to read node elements bacause need to
                    // find cyclo junction nodes. For others cyclo maps isn't need to read all nodes 
                    if (isNLBE && tagName.equals("node")) {
                        // set parameters for relation 
                        node = new Node();
                        node.fillAttributes(parser);
                    }
                    if (tagName.equals("relation")) {
                        // set parameters for relation 
                        if (rel == null) {
                            rel = new Relation();
                        }
                        rel.fillAttributes(parser);
                    }

                    if ((rel != null) && tagName.equals("member")) {
                        Member mem = new Member();
                        mem.fillAttributes(parser);
                        // add member to rhe relation members array
                        rel.members.add(mem);
                    }
                    
                    if ((rel != null) && tagName.equals("tag")){
                        Tag t = new Tag();
                        t.fillAttributes(parser);
                        rel.tags.setValue(t);
                    }
                    
                    // parse tags for cycle nodes for BE a NL maps
                    if ((node != null) && tagName.equals("tag")){
                        Tag t = new Tag();
                        t.fillAttributes(parser);
                        node.tags.setValue(t);
                    }
                    
                }
                else if (tag == KXmlParser.END_TAG){
                    tagName = parser.getName();
                    if (tagName.equals("relation")){
                        // I need to know (for future) which relation is parent for tags
                        rel.tags.setParentRelationId(rel.id);
                        relations.put(rel.id, rel);
                        //System.out.println(rel.members.size());
                        rel = null;
                    }
                    if (isNLBE && tagName.equals("node")){
                        if (node.isCycloJunction()){
                            node.setId(Parameters.touristNodeId);
                            Parameters.touristNodeId++;
                            cycloJunctions.add(node);
                        }
                        node = null;
                    }
                }
            }
            stdInput.close();

        }catch (Exception e) {
            System.out.println("exception happened: ");
            e.printStackTrace();
            System.exit(-1);
        }   
         
         finally {
            try {
                stdInput.close();
                
            } catch (IOException ex) {
                Main.LOG.severe(ex.toString());
            }
        }

    }
    
    public void parseWays() throws IOException, XmlPullParserException {
        
        try {
            CmdTourist ct  =  new CmdTourist(map);
            ct.createCmd();
            ProcessBuilder pb = ct.executePb();
            
            Process runTime = pb.start();
            
            stdInput = new BufferedReader(new InputStreamReader(runTime.getInputStream()));
           
            KXmlParser parser = new KXmlParser();
            parser.setInput(stdInput);
            
            // file for writing cyclo ways
            fos = new FileOutputStream(xmlOutputFile);
            osw = new OutputStreamWriter(fos, "UTF-8");
            bw = new BufferedWriter(osw);

            int tag;
            String tagName;
            String str = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                    + "\n<osm version=\"0.6\" generator=\"Asamm world2vec\">";
            //add information about bound
            str += strBound;
            
            //print cycloJunction nodes only for NLBE
            if (isNLBE){
                for (Node node : cycloJunctions){
                    str += node.toXmlString();
                    bw.write(str);
                    str="";
                    Parameters.touristNodeId++;
                }
            }
            
            Way way = null;
            while ((tag = parser.next()) != KXmlParser.END_DOCUMENT) {
                if (tag == KXmlParser.START_TAG) {
                    tagName = parser.getName().toLowerCase();
                    
                    if (tagName.equals("way")) {
                        // way is temporary variable...when loop is in way element
                        if (way == null){
                            // define new way and try to get tags from waylist - if way is cyclo
                            way = new Way();
                            way.inicializeTourist(parser, wl);
                            
                            // test way if is not in wayList then is normal way 
                            //  -> delete temporary way and skip this loop
                            if(!way.isInWayList){
                                way = null;
                                continue;
                            }
                        }
                    }
                    
                    if (way != null && tagName.equals("nd")){
                        Node nd = new Node();
                        nd.fillAttributes(parser);
                        // because this is not node but only reference for member of way set id based on ref tag
                        nd.setId(Long.parseLong(nd.ref));
                        way.nodes.add(nd);
                    }

                }
                else if (tag == KXmlParser.END_TAG){
                    tagName = parser.getName();
                    if (tagName.equals("way")){
                        // now I know all information about temporary way -> create xml from it
                        if (way != null ) {
                            str = way.toXml();
                        }
                        way = null;
                    }
                    if (tagName.equals("osm") ){
                        //add last osm tag into xml
                        str += endTag2String(parser);
                    }
                    
                    if (!str.isEmpty()){
                        //write string into bwputfile
                        bw.write(str);
                        str ="";
                    }
                    
                }
            }
            bw.flush();
            IOUtils.closeQuietly(bw);
            IOUtils.closeQuietly(fos);
            stdInput.close();
        } 
        catch (Exception e) {
            System.out.println("exception happened: ");
            e.printStackTrace();
            System.exit(-1);
        }        
        finally {
            IOUtils.closeQuietly(fos);
            IOUtils.closeQuietly(bw);
            stdInput.close();
        }
    }
    
    public void reorganize () {
        
        Relation rel;
        for (int i = 0; i < relations.size(); i++){
            
            rel = relations.valueAt(i);
            
            if (rel.tags.isRegularBycicle() || rel.tags.isMtb() || rel.tags.isHiking()){
               
                
                rel.tags.parseOsmcSymbol();
                //TODO tady jen tupe testuju jestli ma spravne tagy a kdyz nema, tak 
                //ji vyhodim. Do budoucna mozna nejaky sofis. system, ktery by treba odhadoval??
                // test if tags of this relation are valid
                if (!rel.tags.validate()){
                    continue;
                }
                
                // set parent tags. In this list is every cyclo relation parent for 
                // its members
                rel.parentTags = rel.tags;
                rel.membersToList(relations,wl);
                //System.out.println("Relation "+rel.id+" ma network: "+rel.tags.network);
            } 
        }
        
      
    }
    
    public static String startTag2String(KXmlParser parser) throws XmlPullParserException, IOException{
        String str;
        int tagNum = parser.getAttributeCount();
        str = "\n<"+parser.getName();
        for (int i = 0; i < tagNum; i++){
            str += " "+parser.getAttributeName(i)+"=";
            str += "\""+parser.getAttributeValue(i)+"\"";
        }
        if (!parser.isEmptyElementTag()){
            str += ">";
        }
       return str;
    }
    
    public static String endTag2String(KXmlParser parser){
        String tagName = parser.getName();
        String str="";
        if (tagName.equals("osm")){
            str += "\n</osm>";
            return str;
        }
        
        if (tagName.startsWith("bound")){
            str += "/>";    
            return str;
        }
        
        return str;
    }
    
    
  
}
