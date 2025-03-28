package com.asamm.osmTools.generatorDb.utils;

import com.asamm.osmTools.utils.Logger;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.Geometry;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.operation.distance.DistanceOp;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.lang.reflect.Field;
import java.text.Normalizer;
import java.util.zip.Deflater;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class Utils {

    public static final float SPHERE_RADIUS =  6372800;

    //private static final GeometryFactory geometryFactory = new GeometryFactory();

    /**
     * Compare object using equals.
     * @param a
     * @param b
     * @return return true of object are equals. If both null than return also true
     */
    public static boolean objectEquals(Object a, Object b){
        if(a == null){
            return b == null;
        } else {
            return a.equals(b);
        }
    }

    /**
     * Function use java.text.Normalizer and replace all diacritics with their codes
     * then are these codes using regular expression replaced with empty string.
     * In second replace all special character with space
     *
     * @param text String for normalization
     * @return Normalized string
     */
    public static String normalizeNames(String text) {
        if (text == null){
            return null;
        }

        // remove diacritics
        String textNorm =  Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");

        return textNorm.replaceAll("[~!@#$%^&*()\\-_+=`\\|\\\\:;'<>,.\\/?]", " ").replaceAll("[ ]{2,}", " ").trim();

    }

    /**************************************************/
    /*            COMPRESS UTILS
    /**************************************************/

    /**
     * Compress simple byte array
     *
     * @param input byte array to compress
     * @return compressed byte array
     */
    public static byte[] compressByteArray (byte[] input){

        if (input == null || input.length == 0){
            return null;
        }

        Deflater compressor = new Deflater();
        compressor.setLevel(Deflater.BEST_COMPRESSION);

        compressor.setInput(input);
        compressor.finish();

        ByteArrayOutputStream bos = new ByteArrayOutputStream(input.length);

        byte[] buf = new byte[256];
        while (!compressor.finished()) {
            int count = compressor.deflate(buf);
            bos.write(buf, 0, count);
        }
        closeStream(bos);
        return bos.toByteArray();
    }

    //*************************************************
    // OTHER TOOLS
    //*************************************************

    public static void closeStream(Closeable stream) {
        try {
            if (stream != null) {
                stream.close();
            }
        } catch (Exception e) {
            Logger.e("UTILS", "Error closing stream: " + e.getMessage());
        }
    }

    /**************************************************/
    /*            MAP UTILS
    /**************************************************/

    /**
     *Compute distance between points
     * @param p1 Start point
     * @param p2 End point
     * @return distance in meters on sphere
     */
    public static double getDistance(Point p1, Point p2){
        return getDistance(p1.getY(), p1.getX(), p2.getY(), p2.getX());
    }

    public static double getDistance(Coordinate c1, Coordinate c2){
        return getDistance(c1.y, c1.x, c2.y, c2.x);
    }

    public static double getDistance(double lat1, double lon1, double lat2, double lon2){
        //  for how to see: http://www.movable-type.co.uk/scripts/latlong.html
        // use simplified version that use OsmAnd

        // for haversine use R = 6372.8 km instead of 6371 km
        double dLat = toRadians(lat2-lat1);
        double dLon = toRadians(lon2-lon1);
        double a = Math.sin(dLat/2) * Math.sin(dLat/2) +
                Math.cos(toRadians(lat1)) * Math.cos(toRadians(lat2)) *
                        Math.sin(dLon/2) * Math.sin(dLon/2);
        //double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a));
        //return R * c * 1000;
        // simplyfy haversine:
        return (2 * SPHERE_RADIUS * Math.asin(Math.sqrt(a)));
    }


    /**
     * Compute unacurate distance in deg from distance in meters for specified
     * point on sphere
     * @param center
     */
    public static double distanceToDeg (Coordinate center, double distanceM){
        //Coordinate offsets (from center) in radians
        double dLat = distanceM / SPHERE_RADIUS;
        double dLon = distanceM / (SPHERE_RADIUS*Math.cos(Math.PI * center.y / 180));

        dLat = toDeg(dLat);
        dLon = toDeg(dLon);

        return Math.sqrt(dLat * dLat + dLon*dLon);
    }

    /**
     * Compute the shortest distance between two geoms. It usable to get forexample
     * distance between closest point on street and other point
     *
     * @param geom1 first geom to test distance
     * @param geom2 next geometry
     * @return distance in meters
     */
    public static double getDistanceNearest (Geometry geom1, Geometry geom2){
        Coordinate[] coordinates = DistanceOp.nearestPoints(geom1, geom2);
        return getDistance(coordinates[0].y , coordinates[0].x, coordinates[1].y , coordinates[1].x);
    }


    /**
     * Convert deg angle to radians
     * @param deg angle in degrees
     * @return angle in radians
     */
    private static double toRadians(double deg) {
        return deg / 180.0 * Math.PI;
    }

    /**
     * Convert distance in radians to degrees
     * @param rad value in radians
     * @return values in degrees
     */
    public static double toDeg (double rad){
        return rad * 180 / Math.PI;
    }


    public static Coordinate offset (Coordinate coordinate, double offsetY, double offsetX ) {

        //Coordinate offsets in radians
        double dLat = offsetY / SPHERE_RADIUS;
        double dLon = offsetX / (SPHERE_RADIUS*Math.cos(Math.PI*coordinate.y/180));

        //OffsetPosition, decimal degrees
        double latO = coordinate.y + dLat * 180/Math.PI;
        double lonO = coordinate.x + dLon * 180/Math.PI;

        return new Coordinate(lonO, latO);
    }


    /**
     * Convert distance in meters to degres in specific point on sphere
     * It's not accurate
     * @param center aproximate place on the sphere where want to convert the distance
     * @param distance distance in meters to convert
     * @return distance in degrees in  dX and dY (dLon, dLat)
     */
    public static double[] metersToDlatDlong (Coordinate center, double distance){

        //Coordinate offsets (from center) in radians
        double dLat = distance / SPHERE_RADIUS;
        double dLon = distance / (SPHERE_RADIUS*Math.cos(Math.PI * center.y / 180));

        dLat = toDeg(dLat);
        dLon = toDeg(dLon);

        return new double[]{dLon,dLat};
    }

    /**
     * Don't use it for some precise conversion. It's very  very inacurate it does not respect the
     * azimut of "line"
     * @param meters distance in meters
     * @return distance in degrees
     */
    public static double metersToDeg (double meters){
        return meters * 180 / (6378000 * Math.PI);
    }


    public static boolean isNumeric(String str)
    {
        try
        {
            double d = Double.parseDouble(str);
        }
        catch(NumberFormatException nfe)
        {
            return false;
        }
        return true;
    }

    public static boolean isInteger(String str)  {

        try  {
            int i = Integer.parseInt(str);
        }
        catch(NumberFormatException nfe)
        {
            return false;
        }
        return true;
    }

    public static short intToShort (int number) throws IllegalArgumentException {

        if (number > Short.MAX_VALUE || number < Short.MIN_VALUE) {
            throw new IllegalArgumentException("Can not cast value to short, value: " + number);
        }
        return (short) number;
    }

    public static void printUsedMemory() {
        Runtime runtime = Runtime.getRuntime();
        int mb = 1024 * 1024;
        long usedMem = runtime.totalMemory() - runtime.freeMemory();
        Logger.i("Utils", "Used memory: " + usedMem / mb);
    }

    public static void checkGarbageMemory(){
        long limit = 3 * 1024 * 1024 * 1024;
        long freeMem = Runtime.getRuntime().freeMemory();
        if (freeMem < limit);{
            Logger.i("Utils", "Free memory: " +freeMem/1024/1024 + "MB, do GC");
            System.gc();
        }
    }

    // TO STRING
    private static final String NEW_LINE = System.getProperty("line.separator");
    public static String toString(Object obj, String prefix) {
        // add base
        StringBuilder result = new StringBuilder();
        result.append(prefix);
        if (obj == null) {
            result.append(" empty object!");
            return result.toString();
        }

        // handle existing object
        result.append(obj.getClass().getName()).append(" [").append(NEW_LINE);

        // determine fields declared in this class only (no fields of superclass)
        Field[] fields = obj.getClass().getDeclaredFields();

        // print field names paired with their values
        for (Field field : fields) {
            result.append(prefix).append("    ");
            try {
                result.append(field.getName());
                result.append(": ");
                // set accessible for private fields
                field.setAccessible(true);
                // requires access to private field:
                result.append(field.get(obj));
            } catch (Exception ex) {
                ex.printStackTrace();
            }

            result.append(NEW_LINE);
        }
        result.append(prefix).append("]");
        return result.toString();
    }

    public static String toString(Object obj) {
        return toString(obj, "");
    }
}
