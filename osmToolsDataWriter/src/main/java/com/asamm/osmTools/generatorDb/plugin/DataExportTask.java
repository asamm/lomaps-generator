package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.utils.Logger;
import org.openstreetmap.osmosis.core.container.v0_6.*;
import org.openstreetmap.osmosis.core.domain.v0_6.CommonEntityData;
import org.openstreetmap.osmosis.core.domain.v0_6.Node;
import org.openstreetmap.osmosis.core.domain.v0_6.OsmUser;
import org.openstreetmap.osmosis.core.domain.v0_6.Tag;
import org.openstreetmap.osmosis.core.lifecycle.ReleasableIterator;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.core.task.v0_6.SinkSource;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by voldapet on 14/1/2017.
 */
public class DataExportTask implements SinkSource {

    private static final String TAG = DataGeneratorTask.class.getSimpleName();

    private Sink sink;


    @Override
    public void process(EntityContainer entityContainer) {
        // read entities and process generation
    }

    @Override
    public void initialize(Map<String, Object> metaData) {
        // nothing to do
    }

    @Override
    public void complete() {

        Logger.i(TAG, "-------- COMPLETE data export task -----");

        List<Tag> tags = new ArrayList<>();
        Tag tag = new Tag("testkey","thisisvalue");
        tags.add(tag);
        CommonEntityData ced = new CommonEntityData(1,1,new Date(1),new OsmUser(1,"asamm"),1,tags);
        Node node = new Node(ced, 50.1, 10.1);
        NodeContainerFactory nodeContainerFactory = new NodeContainerFactory();

        NodeContainer nodeContainer = (NodeContainer) nodeContainerFactory.createContainer(node);

        sink.process(nodeContainer);
    }

    @Override
    public void release() {
        Logger.i(TAG, "-------- SINK RELESE -----");
        sink.release();
    }

    @Override
    public void setSink(Sink sink) {
        this.sink = sink;
    }

}
