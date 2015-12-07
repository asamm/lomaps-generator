package com.asamm.osmTools.generatorDb.utils;

import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.linemerge.LineMerger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by voldapet on 2015-12-03
 * Set of method that helps to create JTS geometry object from OSM entities.
 */
public class GeomUtils {

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * Create multi polygon geometry
     * @param relId only for testing
     * @param outer lines that create outer border
     * @param inner lines of holes
     * @return
     */
    public static MultiPolygon createMultiPolygon (long relId, LineMerger outer, LineMerger inner){

        List<LineString> outerLines = new ArrayList<LineString>(outer.getMergedLineStrings());
        List<LineString> innerLines = new ArrayList<LineString>(inner.getMergedLineStrings());

        int outerLinesSize = outerLines.size();
        int innerLinesSize = innerLines.size();

        List<Polygon> polygons = new ArrayList<>();

        for (int i=0, size = outerLines.size(); i < size; i++){
            Polygon poly = null;
            Coordinate[] outerCoord = outerLines.get(i).getCoordinates();

            if (outerCoord.length < 3){
                // atleast 3 cordinates are needed to close the line into ring
                continue;
            }

            if ( !CoordinateArrays.isRing(outerCoord)){
                // probably some border relation. Try to close it but it's question it is good approach
                outerCoord = closeLine(outerCoord);
            }
            LinearRing outerRing = geometryFactory.createLinearRing(outerCoord);

            if (innerLinesSize > i){
                Coordinate[] innerCoord = innerLines.get(i).getCoordinates();
                if ( !CoordinateArrays.isRing(innerCoord)){
                    innerCoord = closeLine(innerCoord);
                }
                //Logger.i(TAG, "Relation id: " +relId + "; geometry: " + Utils.geomToGeoJson(geometryFactory.createLineString(innerCoord)));
                LinearRing innerRing = geometryFactory.createLinearRing(innerCoord);
                poly = geometryFactory.createPolygon(outerRing, new LinearRing[] {innerRing});
            }
            else {
                poly = geometryFactory.createPolygon(outerRing);
            }

            if (poly != null){
                polygons.add(poly);
            }
        }

        MultiPolygon multiPolygon = geometryFactory.createMultiPolygon(polygons.toArray(new Polygon[polygons.size()]));
        if (!multiPolygon.isValid()) {
            multiPolygon = fixInvalidGeom(multiPolygon);
        }
        return multiPolygon;
    }

    /**
     * Close coordinates (add the first point as the last one)
     * @param coordinates list of coordinates to close
     * @return
     */
    private static Coordinate[] closeLine (Coordinate[] coordinates){
        // close lineString
        final int size = coordinates.length;
        Coordinate[] closed = Arrays.copyOf(coordinates, size + 1);
        closed[size] = coordinates[0];

        return  closed;
    }

    /**
     * Fix invalid geometry topology using zero buffer trick
     * @param multiPolygon geometry to fix
     * @return fixed geometry as multipolygon
     */
    private static MultiPolygon fixInvalidGeom (MultiPolygon multiPolygon){
        // Logger.i(TAG, "Fix invalid geom: " + geomToGeoJson(multiPolygon));

        Geometry geom = multiPolygon.buffer(0.0);

        int numOfGeom = geom.getNumGeometries();
        Polygon[] polygons = new Polygon[numOfGeom];

        for (int i=0; i< numOfGeom; i++){
            polygons[i] = (Polygon) geom.getGeometryN(i);
        }
        multiPolygon = geometryFactory.createMultiPolygon(polygons);
        //Logger.i(TAG, "Fixed multiPoly: " + geomToGeoJson(multiPolygon));
        return multiPolygon;
    }

    /**
     * Craete MultiPolygon object from closed way that define outer polygon
     * @param wayEx
     * @return
     */
    public static MultiPolygon createMultiPolyFromOuterWay(WayEx wayEx){

        //Logger.i(TAG, "Create Multipolygon for simple way, id: " + way.getId());
        Polygon poly = createPolyFromOuterWay(wayEx);
        if (poly == null){
            return null;
        }
        return geometryFactory.createMultiPolygon(new Polygon[]{poly});
    }

    public static Polygon createPolyFromOuterWay (WayEx wayEx){

        Coordinate[] coordinates = wayEx.getCoordinates();

        if (coordinates.length < 3){
           return null;
        }

        if ( !CoordinateArrays.isRing(coordinates)){
            coordinates = closeLine(coordinates);
        }

        return geometryFactory.createPolygon(coordinates);
    }

}
