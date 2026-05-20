package com.asamm.osmTools.generator.lomaps;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.cmdCommands.CmdUpload;
import com.asamm.osmTools.compress.MapCompress;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.server.UploadDefinitionCreator;
import com.asamm.osmTools.utils.*;

import java.util.List;

/**
 * Post-processing steps that run after all per-map work is complete.
 */
class PostPipeline {

    private static final String TAG = PostPipeline.class.getSimpleName();

    private final MapSource mapSource;

    PostPipeline(MapSource mapSource) {
        this.mapSource = mapSource;
    }

    void run(List<Action> actions) {
        if (actions.contains(Action.CREATE_JSON)) createJson();
        if (actions.contains(Action.COMPRESS))    compress();
        if (actions.contains(Action.UPLOAD))      upload();
    }

    private void createJson() {
        Logger.i(TAG, "================ CREATE JSON ================");
        new UploadDefinitionCreator().generateJsonUploadDefinition(mapSource);
    }

    private void compress() {
        Logger.i(TAG, "================ COMPRESS ================");
        new MapCompress().compressAllMaps(mapSource);
    }

    private void upload() {
        Logger.i(TAG, "================ UPLOAD ================");
        TimeWatch time = new TimeWatch();
        Main.mySimpleLog.print("Upload data....");
        new CmdUpload().upload(1);
        Main.mySimpleLog.print("\t\t\tdone " + time.getElapsedTimeSec() + " sec");
    }
}