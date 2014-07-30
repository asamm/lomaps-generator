package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.generatorDb.DataWriterDefinition;
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
    private File mFileTempDb;

    public CmdAddressPoiDb(ItemMap map) {
        super(map, ExternalApp.OSMOSIS);

        // set parameters
        mFileTempMap = new File(Consts.DIR_TMP, "temp_map_simple.osm.pbf");
        mFileTempDb = new File(Consts.DIR_TMP, map.getName() + ".osm.db");
    }

    public File getFileTempDb() {
        return mFileTempDb;
    }

    public void addTaskSimplify(DataWriterDefinition definition) throws IOException {
        FileUtils.deleteQuietly(mFileTempMap);

        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("accept-nodes");
        addListOfTags(definition, Consts.EntityType.POIS);
        addCommand("--tf");
        addCommand("reject-ways");
        addCommand("outPipe.0=Nodes");

        // add second task
        addReadSource();
        addCommand("--tf");
        addCommand("reject-relations");
        addCommand("--tf");
        addCommand("accept-ways");
        addListOfTags(definition, Consts.EntityType.WAYS);
        addCommand("--used-node");
        addCommand("idTrackerType=Dynamic");
        addCommand("outPipe.0=Ways");

        // add merge task
        addCommand("--merge");
        addCommand("inPipe.0=Nodes");
        addCommand("inPipe.1=Ways");

        // add export path
        addWritePbf(mFileTempMap.getAbsolutePath(), true);
    }

    public void addGeneratorDb() {
        FileUtils.deleteQuietly(mFileTempDb);

        addReadPbf(mFileTempMap.getAbsolutePath());
        addCommand("--" + DataPluginLoader.PLUGIN_COMMAND);
        addCommand("-type=poi");
        addCommand("-fileDb=" + mFileTempDb);
        addCommand("-fileConfig=" + Parameters.getConfigApDbPath());
    }

    private void addListOfTags(DataWriterDefinition definition, Consts.EntityType type) {
        // prepare list of tags
        List<DataWriterDefinition.DbRootSubContainer> nodes = definition.getRootSubContainers();
        Hashtable<String, String> nodesPrep = new Hashtable<String, String>();
        for (DataWriterDefinition.DbRootSubContainer dbDef : nodes) {
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
