package com.asamm.osmTools.cmdCommands;

import com.asamm.locus.features.dbAddressPoi.DbAddressPoiConst;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.generatorDb.WriterPoiDefinition;
import com.asamm.osmTools.generatorDb.plugin.DataPluginLoader;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.utils.Consts;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.List;

/**
 * Created by menion on 28.7.14.
 */
public class CmdAddressPoiDb extends Cmd {

    private File mFileTempMap;

    /**
     * Definition where will be poiDb created
     */
    private File mFilePoiDb;

    public CmdAddressPoiDb(ItemMap map) {
        super(map, ExternalApp.OSMOSIS);

        // set parameters
        mFileTempMap = new File(Consts.DIR_TMP, "temp_map_simple.osm.pbf");
        //mFileTempDb = new File(Consts.DIR_TMP, map.getName() + ".osm.db");
        mFilePoiDb = new File(map.getPathAddressPoiDb());
    }

    public File getFileTempDb() {
        return mFilePoiDb;
    }

    public void addTaskSimplify(WriterPoiDefinition definition) throws IOException {
        //FileUtils.deleteQuietly(mFilePoiDb);

        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("accept-nodes");
        addListOfTags(definition, DbAddressPoiConst.EntityType.POIS);
        addCommand("--tf");
        addCommand("reject-ways");
        addCommand("outPipe.0=Nodes");

        // add second task
        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("accept-ways");
        addListOfTags(definition, DbAddressPoiConst.EntityType.WAYS);
        addCommand("--used-node");
//        addCommand("idTrackerType=Dynamic");
        addCommand("outPipe.0=Ways");

        // add merge task
        addCommand("--merge");
        addCommand("inPipe.0=Nodes");
        addCommand("inPipe.1=Ways");

        // add export path
        addWritePbf(mFileTempMap.getAbsolutePath(), true);
    }

    public void addTaskSimplifyForAddress(WriterPoiDefinition definition) throws IOException {

        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("accept-nodes");
        addCommand("place=*");
        addCommand("--tf");
        addCommand("reject-ways");
        addCommand("outPipe.0=Nodes");

        // add second task
        addReadSource();
        addCommand("--tf");
        addCommand("accept-ways");
        addCommand("highway=*");
        addCommand("boundary=*");
        addCommand("place=*");
        addCommand("*=street");
        addCommand("*=associatedStreet");
        addCommand("--used-node");
//        addCommand("idTrackerType=Dynamic");
        addCommand("outPipe.0=Ways");

        // add third task
//        addReadSource();
//        addCommand("--tf");
//        addCommand("accept-relations");
//        addCommand("highway=*");
//        addCommand("boundary=*");
//        addCommand("place=*");
//        addCommand("*=street");
//        addCommand("*=associatedStreet");
//        addCommand("--used-node");
//        addCommand("--tf");
//        addCommand("reject-ways");
//        addCommand("--used-node");
////        addCommand("idTrackerType=Dynamic");
//        addCommand("outPipe.0=Relations");

        // add merge task
        addCommand("--merge");
        addCommand("inPipe.0=Nodes");
        addCommand("inPipe.1=Ways");
//        addCommand("inPipe.2=Relations");

        // add export path
        addWritePbf(mFileTempMap.getAbsolutePath(), true);
    }

    public void addGeneratorDb() {
        FileUtils.deleteQuietly(mFilePoiDb);

        addReadPbf(mFileTempMap.getAbsolutePath());
        addCommand("--" + DataPluginLoader.PLUGIN_COMMAND);
        addCommand("-type=poi");
        addCommand("-fileDb=" + mFilePoiDb);
        addCommand("-fileConfig=" + Parameters.getConfigApDbPath());

    }

    /**
     * Prepare cmd line to run osmosis for generation post address database
     */
    public void addGeneratorAddress () {

        addReadPbf(getMap().getPathSource());
        //addReadPbf(mFileTempMap.getAbsolutePath());
        addCommand("--" + DataPluginLoader.PLUGIN_COMMAND);
        addCommand("-type=address");
        addCommand("-fileDb=" + mFilePoiDb);

        File file = new File(getMap().getPathSource());

        int size = (int) (file.length() / 1024L / 1024L);
        if (size <= 400) {
            addCommand("-dataContainerType=ram");
        }
        else {
            addCommand("-dataContainerType=hdd");
        }
    }

    private void addListOfTags(WriterPoiDefinition definition, DbAddressPoiConst.EntityType type) {
        // prepare list of tags
        List<WriterPoiDefinition.DbRootSubContainer> nodes = definition.getRootSubContainers();
        Hashtable<String, String> nodesPrep = new Hashtable<String, String>();
        for (WriterPoiDefinition.DbRootSubContainer dbDef : nodes) {
            // check type
            if (!dbDef.isValidType(type)) {
                continue;
            }

            // add to data
            String value = nodesPrep.get(dbDef.key);
            if (value == null) {
                nodesPrep.put(dbDef.key, dbDef.value);
            } else {
                nodesPrep.put(dbDef.key, value + "," + dbDef.value);
            }
        }

        // finally add all key/value pairs
        Enumeration<String> nodesKeys = nodesPrep.keys();
        while (nodesKeys.hasMoreElements()) {
            String key = nodesKeys.nextElement();
            String value = nodesPrep.get(key);
            addCommand(key + "=" + value);
        }
    }
}
