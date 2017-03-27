package com.asamm.osmTools.generatorDb.utils;

import com.asamm.osmTools.generatorDb.address.House;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.buffer.BufferParameters;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import com.vividsolutions.jts.simplify.DouglasPeuckerSimplifier;
import com.vividsolutions.jts.util.GeometricShapeFactory;
import gnu.trove.set.hash.THashSet;
import org.wololo.geojson.GeoJSON;
import org.wololo.jts2geojson.GeoJSONReader;
import org.wololo.jts2geojson.GeoJSONWriter;

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
     *
     * @param relId only for testing
     * @param outer lines that create outer border
     * @param inner lines of holes
     * @param isCloseRing set true if wants to fixes not closed ring or invalid geoms
     * @return
     */
    public static MultiPolygon createMultiPolygon
                (long relId, LineMerger outer, LineMerger inner, boolean isCloseRing){

        List<LineString> outerLines = new ArrayList<LineString>(outer.getMergedLineStrings());
        List<LineString> innerLines = new ArrayList<LineString>(inner.getMergedLineStrings());

        int outerLinesSize = outerLines.size();
        int innerLinesSize = innerLines.size();

        List<Polygon> polygons = new ArrayList<>();

        for (int i=0, size = outerLines.size(); i < size; i++){
            Polygon poly = null;
            LinearRing innerRing = null;
            LinearRing outerRing = null;

            Coordinate[] outerCoord = outerLines.get(i).getCoordinates();

            if (outerCoord.length < 3){
                // atleast 3 coordinates are needed to close the line into ring
                continue;
            }

            if ( !CoordinateArrays.isRing(outerCoord)){
                // probably some border relation. Try to close it but it's question it is good approach
                if (isCloseRing){
                    outerCoord = closeLine(outerCoord);
                }
                else{
                    continue;
                }
            }
            outerRing = geometryFactory.createLinearRing(outerCoord);

            if (innerLinesSize > i){
                Coordinate[] innerCoord = innerLines.get(i).getCoordinates();
                if (innerCoord.length > 3){
                    if ( !CoordinateArrays.isRing(innerCoord)){
                        innerCoord = closeLine(innerCoord);
                    }
                    innerRing = geometryFactory.createLinearRing(innerCoord);
                }
            }

            if (innerRing != null){
                poly = geometryFactory.createPolygon(outerRing, new LinearRing[] {innerRing});
            }
            else {
                // create poly only from outerring
                poly = geometryFactory.createPolygon(outerRing);
            }

            polygons.add(poly);
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
    public static MultiPolygon createMultiPolyFromOuterWay(WayEx wayEx, boolean isCloseRing){

        //Logger.i(TAG, "Create Multipolygon for simple way, id: " + way.getId());
        Polygon poly = createPolyFromOuterWay(wayEx, isCloseRing);
        if (poly == null){
            return null;
        }
        return geometryFactory.createMultiPolygon(new Polygon[]{poly});
    }

    public static Polygon createPolyFromOuterWay (WayEx wayEx, boolean isFixInvalidPoly){

        Coordinate[] coordinates = wayEx.getCoordinates();

        if (coordinates.length < 3){
           return null;
        }

        if ( !CoordinateArrays.isRing(coordinates)){
            if (isFixInvalidPoly) {
                coordinates = closeLine(coordinates);
            }
            else {
                return null;
            }
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
        if (geometry == null){
            return mls;
        }
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
     * Union two multipolygons into one mutlipolygon
     *
     * @param mp1 first mp to union
     * @param mp2 seconf mp to union
     * @return multipolygon that is union of geoms
     */
    public static MultiPolygon unionMultiPolygon (MultiPolygon mp1, MultiPolygon mp2){

        Geometry geom =  mp1.union(mp2);

        if (geom instanceof  MultiPolygon){
            return (MultiPolygon) geom;
        }
        else if (geom instanceof  Polygon){
            return geometryFactory.createMultiPolygon( new Polygon[]{(Polygon) geom});
        }
        else {

            throw new IllegalArgumentException("Can not union two multipolygons into multipoly");
        }

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

    /**
     * Convert simple line into Multiline
     * @param lineString
     * @return
     */
    public static MultiLineString LineToMultiLine(LineString lineString) {

        return  geometryFactory.createMultiLineString(new LineString[]{lineString});
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


        Polygon poly = null;
        try {
            poly = gsf.createRectangle();
        }
        catch (Exception e) {
            Logger.i("GeomUtils", "Can not create circle from center point: "  + center.toString());
            throw new IllegalArgumentException("Can not create circle");
        }

        return poly;
    }

    public static Polygon createRectangle (Coordinate center, double distance){
        return createCircle(center, distance, 4);
    }


    // BUFFER

    /**
     * Create buffer around given geometry. Please note that Distance is very approximately converted into deg. It's not
     * precise at all.
     * @param geometry geometry to buffer
     * @param distance width of buffer in meters
     * @return geometry represents the buffered geom
     */
    public static MultiPolygon bufferGeom (MultiPolygon geometry, double distance){

        if (geometry == null){
            return null;
        }

        double distanceDeg = Utils.distanceToDeg(geometry.getCoordinate(), distance);
        Geometry geomBuf =  geometry.buffer(distanceDeg, BufferParameters.CAP_FLAT);

        MultiPolygon mp;
        if (geomBuf instanceof Polygon) {
            mp = geometryFactory.createMultiPolygon(new Polygon[]{(Polygon) geomBuf});
        } else {
            mp = (MultiPolygon) geomBuf;
        }

        return mp;
    }


    // SIMPLIFICATION

    /**
     *  Simplify geometry and preserve the geom
     *
     * @param multiPolygon Multipolygon to simplify
     * @param distanceTolerance All vertices in the simplified geometry will be within this
     * distance of the original geometry.
     *
     * @return simplified geom
     */
    public static MultiPolygon simplifyMultiPolygon(MultiPolygon multiPolygon, double distanceTolerance) {

        if (multiPolygon == null){
            return null;
        }

        double distanceDeg = Utils.distanceToDeg(multiPolygon.getCoordinate(), distanceTolerance);
        Geometry geometry = DouglasPeuckerSimplifier.simplify(multiPolygon, distanceDeg);

        MultiPolygon mp;
        if (geometry instanceof Polygon) {
            mp = geometryFactory.createMultiPolygon(new Polygon[]{(Polygon) geometry});
        } else {
            mp = (MultiPolygon) geometry;
        }

        return mp;
    }

    /**
     * Convert simple polygon into multipolygon object
     * @param polygon
     * @return
     */
    public static MultiPolygon polygonToMultiPolygon (Polygon polygon){
        return geometryFactory.createMultiPolygon(new Polygon[]{polygon});
    }

    // GEOJSON UTILS

    /**
     * Create GeoJson string from JTS geometry
     * @param geometry geom to convert
     * @return geoJson string
     */
    public static String geomToGeoJson (Geometry geometry){

        if (geometry == null){
            return "";
        }

        GeoJSONWriter writer = new GeoJSONWriter();
        GeoJSON json = writer.write(geometry);
        return json.toString();
    }

    /**
     * Convert geoJson into JTS geometry
     *
     * @param geoJsonString geoJson string to convert
     * @return jts geometry
     */
    public static Geometry geoJsonToGeom (String geoJsonString){

        GeoJSONReader jsonReader = new GeoJSONReader();
        return jsonReader.read(geoJsonString);
    }
}
