package com.asamm.osmTools.generator.lomaps;

import com.asamm.locus.features.loMaps.LoMapsDbConst;
import com.asamm.osmTools.cmdCommands.CmdUpload;
import com.asamm.osmTools.compress.MapCompress;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.config.AppConfig;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.server.UploadDefinitionCreator;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.db.DatabaseData;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.io.WKTWriter;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
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

    void run(List<Action> actions) throws Exception {
        if (actions.contains(Action.ADDRESS_POI_DB) || actions.contains(Action.GENERATE_MAPSFORGE)) {
            insertMetaDataForAllMaps();
        }
        if (actions.contains(Action.CREATE_JSON)) {
            createJson();
        }
        if (actions.contains(Action.COMPRESS)) {
            compress();
        }
        if (actions.contains(Action.UPLOAD)) {
            upload();
        }
    }

    // ---- INSERT METADATA ----

    private void insertMetaDataForAllMaps() throws Exception {
        Logger.i(TAG, "================ INSERT METADATA ================");
        for (ItemMap map : mapSource.getGetAllMaps()) {
            insertMetaData(map);
        }
    }

    private void insertMetaData(ItemMap map) throws Exception {
        if (!map.hasAction(Action.GENERATE_MAPSFORGE) || map.isPlanet()) return;

        Geometry geom = map.getTileCoverageGeometry();

        insertMetadata(map, map.getPathAddressPoiDb().toFile(), geom);
        insertMetadata(map, map.getPathAddressDb().toFile(), geom);
    }

    private void insertMetadata(ItemMap map, File dbFile, Geometry geom) throws Exception {
        if (dbFile == null || !dbFile.exists()) {
            Logger.w(TAG, "DB file for metadata doesn't exist: " + dbFile);
            return;
        }

        SimpleDateFormat sdf = new SimpleDateFormat("yyyy.MM.dd");
        Date dateVersion = sdf.parse(AppConfig.config.getVersion());

        DatabaseData dbData = new DatabaseData(dbFile);
        WKTWriter wktWriter = new WKTWriter();

        dbData.insertData(LoMapsDbConst.VAL_AREA, wktWriter.write(geom));
        dbData.insertData(LoMapsDbConst.VAL_COUNTRY, map.getCountryName());
        dbData.insertData(LoMapsDbConst.VAL_DESCRIPTION, AppConfig.config.getMapsforgeConfig().getMapMetaDataDescription());
        dbData.insertData(LoMapsDbConst.VAL_LANGUAGES, map.getPrefLang());
        dbData.insertData(LoMapsDbConst.VAL_OSM_DATE, String.valueOf(dateVersion.getTime()));
        dbData.insertData(LoMapsDbConst.VAL_REGION_ID, map.getRegionId());
        dbData.insertData(LoMapsDbConst.VAL_VERSION, AppConfig.config.getVersion());
        dbData.insertData(LoMapsDbConst.VAL_DB_POI_VERSION, String.valueOf(AppConfig.config.getPoiAddressConfig().getDbPoiVersion()));
        dbData.insertData(LoMapsDbConst.VAL_DB_ADDRESS_VERSION, String.valueOf(AppConfig.config.getPoiAddressConfig().getDbAddressVersion()));

        dbData.destroy();
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
        Logger.i(TAG, "Upload data....");
        new CmdUpload().upload(1);
    }
}