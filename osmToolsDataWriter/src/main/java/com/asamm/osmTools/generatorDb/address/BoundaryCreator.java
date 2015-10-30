package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class BoundaryCreator {

    private static final String TAG = BoundaryCreator.class.getSimpleName();

    private TLongList processedWays;

    private GeometryFactory geometryFactory;

    public BoundaryCreator(int relationSize) {

        processedWays = new TLongArrayList();
        geometryFactory = new GeometryFactory();

    }


    /**
     * Create boundary object from relation or way. Only admin boundaries with all members and closed ways
     * are converted into boundary object
     * @param dc
     * @param entity
     * @return
     */
    public  Boundary create (ADataContainer dc, Entity entity) {

        if (entity.getType() == EntityType.Node){
            return null;
        }

        //TODO only testing
        boolean hasChildRelation = false;

        String place = OsmUtils.getTagValue(entity, OSMTagKey.PLACE);
        String boundaryTag = OsmUtils.getTagValue(entity, OSMTagKey.BOUNDARY);
        if ( !Utils.objectEquals("administrative", boundaryTag) && place == null ){
            // is not administrative nor the place
            return null;
        }

        // try to recognize type of place (its only for place relation/ways
        City.CityType cityType = City.CityType.createFromPlaceValue(place);

        LineMerger outerMerger = new LineMerger();
        LineMerger innerMerger = new LineMerger();

        // get name of boundary for further checking
        String bName = OsmUtils.getTagValue(entity, OSMTagKey.NAME);

        Boundary boundary = new Boundary(entity.getId());
        boundary.setName(bName);

        //Logger.i(TAG, "---- Create boundary for entity " + entity.getId() + ", name: " + bName);

        if (entity.getType() == EntityType.Way){

            Way way = (Way) entity;

            if (processedWays.contains(entity.getId())  || !way.isClosed()){
                // boundary from this way was already processed or way is not closed
                return  null;
            }

            WayEx wayEx = dc.getWay(entity.getId());
            if (wayEx == null || !wayEx.isValid()){
                return null;
            }

            boundary.setGeom(createFromOuterWay(wayEx, dc));
        }
        else if (entity.getType() == EntityType.Relation){

            Relation re = (Relation) entity;
            List<RelationMember> members = re.getMembers();

            for (RelationMember reMember : members) {

                if (reMember.getMemberType() == EntityType.Way){

                    WayEx wayEx = dc.getWay(reMember.getMemberId());

                    if (wayEx == null || !wayEx.isValid()){
                        // member way was not downloaded from cache (probably is way in different map
                        // such boundary is not useful

                        //return null;
                        continue;
                    }

                    if (reMember.getMemberRole().equals("inner")){
                        LineString lineString = geometryFactory.createLineString(wayEx.getCoordinates());
                        innerMerger.add(lineString);
                    }

                    else {
                        // get name of child way
                        String wName = OsmUtils.getTagValue(wayEx, OSMTagKey.NAME);

                        // if name are not equal keep the way for further check (it could be different suburb)
                        if (Utils.objectEquals(wName, bName) || wName == null) {
                            processedWays.add(wayEx.getId());
                        }

                        LineString lineString = geometryFactory.createLineString(wayEx.getCoordinates());
                        outerMerger.add(lineString);
                    }
                }

                else if (reMember.getMemberType() == EntityType.Node){
                    String memberRole = reMember.getMemberRole();
                    if (memberRole.equals("admin_centre") || memberRole.equals("admin_center")){
                        // this point has define role as center of relation
                        boundary.setAdminCenterId(reMember.getMemberId());
                    }
                    else if (memberRole.equals("label")){
                        // label is node where to draw label Has lower priority then admin_center
                        boundary.setAdminCenterId(reMember.getMemberId());
                    }
                }
                else if (reMember.getMemberType() == EntityType.Relation) {
                    hasChildRelation = true;

                }
            }

            // for relation create multipolygon
            boundary.setGeom(createMultiPolygon(entity.getId(), outerMerger, innerMerger));
        }

        boundary.setAdminLevel(extractBoundaryAdminLevel(entity));
        boundary.setShortName(OsmUtils.getTagValue(entity, OSMTagKey.SHORT_NAME));
        boundary.setCityType(cityType);

//        if (hasChildRelation){
//            Logger.i(TAG, "Administrative/place entity id: " + entity.getId() +" has other relation as member. " +
//                    "Check created geometry: " + boundary.toGeoJsonString());
//        }
//        if (boundary.getName().equals("Delmenhorst")){
//            Logger.i(TAG, "Administrative/place entity: " + boundary.toString());
//        }
        return boundary;
    }


    private int extractBoundaryAdminLevel(Entity entity) {

        int adminLevel = -1;
        try {
            String tag = OsmUtils.getTagValue(entity, OSMTagKey.ADMIN_LEVEL);
            if (tag == null) {
                return adminLevel;
            }
            return Integer.parseInt(tag);
        } catch (NumberFormatException ex) {
            return adminLevel;
        }
    }

    private MultiPolygon createMultiPolygon (long relId, LineMerger outer, LineMerger inner){

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
    private Coordinate[] closeLine (Coordinate[] coordinates){
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
    private MultiPolygon fixInvalidGeom (MultiPolygon multiPolygon){
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
     * @param dc
     * @return
     */
    private MultiPolygon createFromOuterWay (WayEx wayEx, ADataContainer dc){

        //Logger.i(TAG, "Create Multipolygon for simple way, id: " + way.getId());
        Polygon poly = geometryFactory.createPolygon(wayEx.getCoordinates());
        return geometryFactory.createMultiPolygon(new Polygon[]{poly});
    }

    private void createRegisterCity (String place){
        City.CityType ct = null;
        if (place != null){
            ct = City.CityType.createFromPlaceValue(place);
        }
    }
}
