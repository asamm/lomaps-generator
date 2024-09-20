package com.asamm.osmTools.generatorDb;

import com.asamm.osmTools.generatorDb.data.AOsmObject;
import com.asamm.osmTools.generatorDb.data.OsmPoi;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.db.ADatabaseHandler;
import com.asamm.osmTools.generatorDb.db.DatabasePoi;
import com.asamm.osmTools.generatorDb.input.definition.WriterPoiDefinition;
import org.openstreetmap.osmosis.core.domain.v0_6.Entity;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;

import java.io.File;
import java.util.List;

public class GeneratorPoi extends AGenerator {

//	private static final Logger log = Logger.getLogger(GeneratorPoi.class);

    // output DB file
    private File outputDb;

    // handler for tags
    private WriterPoiDefinition writerPoiDefinition;

    public GeneratorPoi(File outputDbFile, WriterPoiDefinition nodeHandler) throws Exception {
        this.outputDb = outputDbFile;
        this.writerPoiDefinition = nodeHandler;

        // initialize generator
        initialize();
    }

    @Override
    protected ADatabaseHandler prepareDatabase() throws Exception {
        return new DatabasePoi(outputDb, writerPoiDefinition);
    }

    @Override
    public void proceedData(ADataContainer dc) {
        // handle nodes
        List<Node> nodes = dc.getNodes();
        for (Node node : nodes) {
            // nodes was not tested during loading test it now
            if (writerPoiDefinition.isValidNode(node)) {
                addNodeImpl(node, db);
            }
        }

        // handle ways
        List<WayEx> ways = dc.getWays();
        for (int i = 0, m = ways.size(); i < m; i++) {
            addWayImp(ways.get(i), db);
        }
    }

    protected AOsmObject addNodeImpl(Node node, ADatabaseHandler db) {
        return addObject(node, db);
    }

    protected AOsmObject addWayImp(WayEx way, ADatabaseHandler db) {
        return addObject(way, db);
    }

    private AOsmObject addObject(Entity entity, ADatabaseHandler db) {
        // generate OSM POI object
        OsmPoi poi = OsmPoi.create(entity, writerPoiDefinition);
        if (poi == null) {
            return null;
        }

        // add to database
        ((DatabasePoi) db).insertObject(poi);
        return poi;
    }
}
