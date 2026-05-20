package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.address.ResidentialAreaCreator;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.dataContainer.DataContainerHdd;
import com.asamm.osmTools.generatorDb.input.definition.WriterResidentialDefinition;
import com.asamm.osmTools.generatorDb.osmgeom.JtsGeometryConverter;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import org.locationtech.jts.geom.Polygon;
import org.openstreetmap.osmosis.core.container.v0_6.EntityContainer;
import org.openstreetmap.osmosis.core.container.v0_6.NodeContainer;
import org.openstreetmap.osmosis.core.container.v0_6.RelationContainer;
import org.openstreetmap.osmosis.core.container.v0_6.WayContainer;
import org.openstreetmap.osmosis.core.domain.v0_6.*;
import org.openstreetmap.osmosis.core.task.v0_6.Sink;
import org.openstreetmap.osmosis.core.task.v0_6.SinkSource;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public class DataResidentialTask implements SinkSource {

    private static final String TAG = DataResidentialTask.class.getSimpleName();

    private Sink sink;

    private ADataContainer dc = null;

    public DataResidentialTask(ConfigurationResidential config) {
        try {
            WriterResidentialDefinition wrd = new WriterResidentialDefinition(config);
            dc = new DataContainerHdd(wrd);
        } catch (Exception e) {
            throw new RuntimeException("Cannot initialize residential task", e);
        }
    }

    @Override
    public void process(EntityContainer entityContainer) {
        Entity entity = entityContainer.getEntity();
        switch (entity.getType()) {
            case Bound:
                break;
            case Node:
                dc.addNode((Node) entity);
                break;
            case Way:
                dc.addWay((Way) entity);
                break;
            case Relation:
                dc.addRelation((Relation) entity);
                break;
        }
    }

    @Override
    public void initialize(Map<String, Object> metaData) {}

    @Override
    public void complete() {

        Logger.i(TAG, "=== Step 1 - Create residential polygons ===");
        ResidentialAreaCreator residentialC = new ResidentialAreaCreator(dc);
        List<Polygon> residentialPolygons = residentialC.generate();

        Logger.i(TAG, "=== Step 2 - Export data ===");
        Tag tagResidentialVillage = new Tag("lm_landuse", "residential_village");
        Tag tagResidentialCity = new Tag("lm_landuse", "residential_city");
        List<Tag> tagsVillage = Arrays.asList(new Tag[]{tagResidentialVillage});
        List<Tag> tagsCity = Arrays.asList(new Tag[]{tagResidentialCity});

        JtsGeometryConverter jtsGeometryConverter = new JtsGeometryConverter();
        double minCityArea = Utils.metersToDeg(1500) * Utils.metersToDeg(1500);
        for (Polygon poly : residentialPolygons) {
            if (poly.getArea() > minCityArea) {
                jtsGeometryConverter.addPolygon(poly, tagsCity);
            } else {
                jtsGeometryConverter.addPolygon(poly, tagsVillage);
            }
        }

        Collection<NodeContainer> nodes = jtsGeometryConverter.getNodes().values();
        Collection<WayContainer> ways = jtsGeometryConverter.getWays().values();
        Collection<RelationContainer> relations = jtsGeometryConverter.getRelations().values();

        for (NodeContainer nodeContainer : nodes) sink.process(nodeContainer);
        for (WayContainer wayContainer : ways) sink.process(wayContainer);
        for (RelationContainer relationContainer : relations) sink.process(relationContainer);

        sink.complete();
        Logger.i(TAG, "Sink completed");
        dc.destroy();
        Logger.i(TAG, "DC destroyed");
    }

    @Override
    public void setSink(Sink sink) {
        this.sink = sink;
    }

    @Override
    public void close() {
        sink.close();
    }
}