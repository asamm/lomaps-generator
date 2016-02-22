package com.asamm.osmTools.generatorDb.utils;

import com.asamm.osmTools.generatorDb.address.House;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import com.vividsolutions.jts.util.GeometricShapeFactory;
import gnu.trove.set.hash.THashSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Created by voldapet on 2015-12-03
 * Set of method that helps to create JTS geometry object from OSM entities.
 */
public class GeomUtils {

    private static final GeometryFactory geometryFactory = new GeometryFactory();

    // for conversion double lat lon to integer
    private static final int COORDINATE_POW = 100000;


    /**
     * Convert point and its coordinate into simplified version written as two integers
     * @param point point to convert
     * @return aray with [lon, lat] values
     */
    public static int[] pointToIntegerValues(Point point){

        int lonI = (int) Math.round((point.getX()) * COORDINATE_POW);
        int latI = (int) Math.round((point.getY()) * COORDINATE_POW);

        return  new int[]{lonI, latI};
    }


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
    public static Coordinate[] closeLine (Coordinate[] coordinates){
        // close lineString
        final int size = coordinates.length;
        Coordinate[] closed = Arrays.copyOf(coordinates, size + 1);
        closed[size] = coordinates[0];

        return  closed;
    }

    /**
     * Fix invalid geometry topology using zero buffer trick
     * @param geometry geometry to fix
     * @return fixed geometry as multipolygon
     */
    public static MultiPolygon fixInvalidGeom (Geometry geometry){
        // Logger.i(TAG, "Fix invalid geom: " + geomToGeoJson(multiPolygon));

        Geometry geom = geometry.buffer(0.0);

        int numOfGeom = geom.getNumGeometries();
        Polygon[] polygons = new Polygon[numOfGeom];

        for (int i=0; i< numOfGeom; i++){
            polygons[i] = (Polygon) geom.getGeometryN(i);
        }
        MultiPolygon mp = geometryFactory.createMultiPolygon(polygons);
        //Logger.i(TAG, "Fixed multiPoly: " + geomToGeoJson(multiPolygon));
        return mp;
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

    /**
     * Convert geometry into MultiLineString object
     * @param geometry geometry to convert
     * @return multilinestring or throw exception of geometry is not possible to convert
     */
    public static MultiLineString geometryToMultilineString (Geometry geometry) {

        MultiLineString mls = null;
        if (geometry instanceof Point){
            // create empty multipoly
            mls = geometryFactory.createMultiLineString(new LineString[]{});
        }
        else if (geometry instanceof MultiLineString){
            mls = (MultiLineString) geometry;
        }
        else if ((geometry instanceof LineString)){
            LineString ls = (LineString) geometry;
            mls = geometryFactory.createMultiLineString(new LineString[]{ls});
        }
        else if (geometry instanceof  GeometryCollection){
            // try to separate linestring from elements
            LineMerger lineMerger = new LineMerger();
            int size = geometry.getNumGeometries();
            for (int i=0; i < size; i++){
                Geometry geom = geometry.getGeometryN(i);
                if (geom instanceof  LineString){
                    lineMerger.add(geom);
                }
            }
            Collection<LineString> lineStrings = lineMerger.getMergedLineStrings();
            mls = mergeLinesToMultiLine(lineStrings);
        }

        else {
            throw new IllegalArgumentException("Can not convert geom to multilinestring. Geometry: " + geometry.toString());
        }
        return mls;
    }

    /**
     * Create MultiPolygon from center points of houses
     * @param houses houses to convert their geometries into multipoint
     * @return multipoint of centers of houses
     */
    public static MultiPoint housesToMultiPoint (THashSet<House> houses){

        Point[] points = new Point[houses.size()];
        int counter = 0;
        for (House house : houses){
            points[counter] = house.getCenter();
            counter++;
        }

        return geometryFactory.createMultiPoint(points);
    }

    /**
     * Create multiLine string from LineStrings
     * @param lineStrings lines to merge
     * @return
     */
    public static MultiLineString mergeLinesToMultiLine(Collection<LineString> lineStrings) {

        MultiLineString mls = null;
        int linesSize = lineStrings.size();
        if (linesSize == 1){
            mls = geometryFactory.createMultiLineString(new LineString[]{lineStrings.iterator().next()});
        }
        else if (linesSize > 1) {
            mls = (MultiLineString) geometryFactory.buildGeometry(lineStrings);
        }
        return mls;
    }

    public static Polygon createCircle (Coordinate center, double distance, int numPoints){

        //Coordinate offsets (from center) in radians
        double dLat = distance / Utils.SPHERE_RADIUS;
        double dLon = distance / (Utils.SPHERE_RADIUS*Math.cos(Math.PI * center.y / 180));

        dLat = Utils.toDeg(dLat);
        dLon = Utils.toDeg(dLon);

        GeometricShapeFactory gsf = new GeometricShapeFactory();

        gsf.setCentre(center);
        gsf.setWidth(dLon * 2);
        gsf.setHeight(dLat * 2 );
        gsf.setNumPoints(numPoints);

        return gsf.createRectangle();
    }

    public static Polygon createRectangle (Coordinate center, double distance){
        return createCircle(center, distance, 4);
    }


    // SIMPLIFICATION

    /**
     *
     * @param multiPolygon Multipolygon to simplify
     * @return
     */
    public static MultiPolygon simplifyCityRegionGeom(MultiPolygon multiPolygon) {

        if (multiPolygon == null){
            return null;
        }

        double distanceDeg = Utils.distanceToDeg(multiPolygon.getCoordinate(), 50);
        Geometry geometry = DouglasPeuckerSimplifier.simplify(multiPolygon, distanceDeg);

        MultiPolygon mp;
        if (geometry instanceof Polygon) {
            mp = geometryFactory.createMultiPolygon(new Polygon[]{(Polygon) geometry});
        } else {
            mp = (MultiPolygon) geometry;
        }

        return mp;
    }

}
