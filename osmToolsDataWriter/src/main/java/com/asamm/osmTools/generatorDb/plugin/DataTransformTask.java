package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.address.ResidentialAreaCreator;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.dataContainer.DataContainerHdd;
import com.asamm.osmTools.generatorDb.input.definition.WriterTransformDefinition;
import com.asamm.osmTools.generatorDb.osmgeom.JtsGeometryConverter;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.Polygon;
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

/**
 * Created by voldapet on 14/1/2017.
 */
public class DataTransformTask implements SinkSource {

    private static final String TAG = DataLoMapsDbGeneratorTask.class.getSimpleName();

    private Sink sink;

    // container for all data
    private ADataContainer dc = null;



    public DataTransformTask (ConfigurationTransform configTransform){

        try {
            // define generators parameters based on config that contains path to cmd parameters
            WriterTransformDefinition wtd = new WriterTransformDefinition(configTransform);

            //dc = new DataContainerRam(wtd);
            dc = new DataContainerHdd(wtd);

        }
        catch (Exception e) {
            throw new RuntimeException("Can not initialize  data transform task generator",e);
        }
    }

    @Override
    public void process(EntityContainer entityContainer) {
        // read entities and store them
        Entity entity = entityContainer.getEntity();
        switch (entity.getType()) {
            case Bound:
                break;
            case Node:
                Node node = (Node) entity;
                dc.addNode(node);
                break;
            case Way:
                Way way = (Way) entity;
                dc.addWay(way);
                break;
            case Relation:
                Relation relation = (Relation) entity;
                dc.addRelation(relation);
                break;
        }
    }

    @Override
    public void initialize(Map<String, Object> metaData) {
        // nothing to do
    }

    @Override
    public void complete() {

        Logger.i(TAG, "=== Step 1 - Create residential polygons ===");
        ResidentialAreaCreator residentialC = new ResidentialAreaCreator(dc);
        List<Polygon> residentialPolygons = residentialC.generate();

        Logger.i(TAG, "=== Step 2 - Export data ===");
        // prepare tags for two types of polygon
        Tag tagResidentialVillage = new Tag("lm_landuse","residential_village");
        Tag tagResidentialCity = new Tag("lm_landuse","residential_city");
        List<Tag> tagsVillage = Arrays.asList(new Tag[] {tagResidentialVillage});
        List<Tag> tagsCity = Arrays.asList(new Tag[] {tagResidentialCity});

        // convert JTS geom into OSM entities. Coverter convert and store data
        JtsGeometryConverter jtsGeometryConverter = new JtsGeometryConverter();
        double minCityArea = Utils.metersToDeg(1500) *  Utils.metersToDeg(1500);
        for (Polygon poly : residentialPolygons){
            if (poly.getArea() > minCityArea){
                jtsGeometryConverter.addPolygon(poly, tagsCity);
            }
            else {
                jtsGeometryConverter.addPolygon(poly, tagsVillage);
            }
        }

        // write data to the output pipe
        Collection<NodeContainer> nodes = jtsGeometryConverter.getNodes().values();
        Collection<WayContainer> ways = jtsGeometryConverter.getWays().values();
        Collection<RelationContainer> relations = jtsGeometryConverter.getRelations().values();

        for (NodeContainer nodeContainer : nodes){
            sink.process(nodeContainer);
        }
        for (WayContainer wayContainer : ways){
            sink.process(wayContainer);
        }
        for (RelationContainer relationContainer : relations){
            sink.process(relationContainer);
        }

        // close output pipe
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
