    /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

    import com.asamm.osmTools.mapConfig.ItemMap;
    import com.asamm.osmTools.mapConfig.MapSource;
    import com.asamm.osmTools.utils.Utils;
    import net.minidev.json.JSONArray;
    import net.minidev.json.JSONObject;

    import java.io.File;

    /**
 *
 * @author volda
 */
public class CmdExtractOsmium extends Cmd {

    private static String CONFIG_TMP_JSON_FILE = "osmium_extract_config.json";

    /*
     * Json objects for oonfiguration file
     */
    JSONObject configJ = new JSONObject();
    JSONArray extractsJ = new JSONArray();

    ItemMap map;

    public CmdExtractOsmium(MapSource ms, String sourceId) {
        super(ExternalApp.OSMIUM);

        map = ms.getMapByIdSafe(sourceId);
        initJsonConfig();
    }

    private void initJsonConfig (){
        configJ = new JSONObject();
        extractsJ = new JSONArray();

        configJ.put("extracts", extractsJ);
    }

    public boolean hasMapForExtraction(){
        return (extractsJ.size() > 0);
    }

    public void addExtractMap (ItemMap map){


        // create needed parent folders
        Utils.createParentDirs(map.getPathSource());

        JSONObject extractJ = new JSONObject();
        extractJ.put("output", map.getPathSource().toString() );

        JSONObject polygonJ = new JSONObject();
        polygonJ.put("file_name", map.getPathPolygon().toString());
        polygonJ.put("file_type", "poly");
        extractJ.put("polygon", polygonJ);

        // add to the main array
        extractsJ.add(extractJ);
    }

    /**
     * Write temporary config json file to the hdd
     */
    private void writeConfigJsonFile(){
        Utils.writeStringToFile(new File(CONFIG_TMP_JSON_FILE), configJ.toJSONString(), false);
    }

    public void deleteConfigJsonFile() {
        File fileToDelete = new File(CONFIG_TMP_JSON_FILE);
        if (fileToDelete.exists()){
            fileToDelete.delete();
        }
    }

    public void createCmd (boolean completeRelations) {
        // prepare config file
        writeConfigJsonFile();

        addCommand("extract");
        addCommand("-c");
        addCommand(CONFIG_TMP_JSON_FILE);

        if (completeRelations){
            addCommand("--strategy");
            addCommand("smart");  //alternatives: simple | complete_ways | smart
        }
        else {
            addCommand("--strategy");
            addCommand("simple");  //alternatives: simple | complete_ways | smart
        }
        addCommand("-v");
        addCommand(map.getPathSource().toString());
        addCommand("--fsync");
    }
}
