package com.asamm.osmTools.generatorDb.address;

import com.asamm.osmTools.generatorDb.GeneratorAddress;
import com.asamm.osmTools.generatorDb.data.OsmConst.OSMTagKey;
import com.asamm.osmTools.generatorDb.data.WayEx;
import com.asamm.osmTools.generatorDb.dataContainer.ADataContainer;
import com.asamm.osmTools.generatorDb.index.IndexController;
import com.asamm.osmTools.generatorDb.utils.GeomUtils;
import com.asamm.osmTools.generatorDb.utils.OsmUtils;
import com.asamm.osmTools.generatorDb.utils.Utils;
import com.asamm.osmTools.utils.Logger;
import com.vividsolutions.jts.geom.*;
import com.vividsolutions.jts.geom.prep.PreparedGeometry;
import com.vividsolutions.jts.geom.prep.PreparedGeometryFactory;
import com.vividsolutions.jts.operation.linemerge.LineMerger;
import gnu.trove.list.TLongList;
import gnu.trove.list.array.TLongArrayList;
import org.apache.commons.lang3.StringUtils;
import org.openstreetmap.osmosis.core.domain.v0_6.*;

import java.util.List;

/**
 * Created by voldapet on 2015-08-14 .
 */
public class BoundaryController {

    private static final String TAG = BoundaryController.class.getSimpleName();

    /** Needed for generation boundaries from relation. Ways with this ids very already use for generation of bounds*/
    private TLongList processedWays;

    /** Instance of geometry factory for creation boundary geoms*/
    private GeometryFactory geometryFactory;

    public BoundaryController() {
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

        if (entity == null || entity.getType() == EntityType.Node){
            return null;
        }

        String place = OsmUtils.getTagValue(entity, OSMTagKey.PLACE);
        String boundaryTag = OsmUtils.getTagValue(entity, OSMTagKey.BOUNDARY);
        if ( !Utils.objectEquals("administrative", boundaryTag) && place == null ){
            // is not administrative nor the place
            return null;
        }

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

            MultiPolygon geom = GeomUtils.createMultiPolyFromOuterWay(wayEx);
            if (geom == null){
                return null;
            }
            boundary.setGeom(GeomUtils.createMultiPolyFromOuterWay(wayEx));
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
            }

            // for relation create multipolygon
            boundary.setGeom(GeomUtils.createMultiPolygon(entity.getId(), outerMerger, innerMerger));
        }

        // try to recognize type of place (its only for place relation/ways
        City.CityType cityType = City.CityType.createFromPlaceValue(place);

        boundary.setAdminLevel(parseBoundaryAdminLevel(entity));
        boundary.setShortName(OsmUtils.getTagValue(entity, OSMTagKey.SHORT_NAME));
        boundary.setCityType(cityType);
        boundary.setNamesInternational(OsmUtils.getNamesLangMutation(entity, bName));

//        if (hasChildRelation){
//            Logger.i(TAG, "Administrative/place entity id: " + entity.getId() +" has other relation as member. " +
//                    "Check created geometry: " + boundary.toGeoJsonString());
//        }
//        if (boundary.getName().equals("Delmenhorst")){
//            Logger.i(TAG, "Administrative/place entity: " + boundary.toString());
//        }

        //Logger.i(TAG, "Administrative/place entity: " + boundary.toString());

        IndexController.getInstance().insertBoundary(boundary.getGeom().getEnvelopeInternal(), boundary);
        return boundary;
    }

    /**
     * Parse the tag that contains information about admin level
     *
     * @param entity entity to get admin level tag value
     * @return admin level value or -1 if admin level is not defined or value is not integer
     */
    private int parseBoundaryAdminLevel(Entity entity) {

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


    /**
     * Compare how ideal is boundary for city. The priority is evaluated as integer value from 0 - 36
     * @param boundary boundary to compare
     * @param city city to compare with boundary
     * @return priority the lower is better
     */
    public int getCityBoundaryPriority(Boundary boundary, City city) {

        boolean hasSameName = boundary.getName().equalsIgnoreCase(city.getName());

        if (!hasSameName && boundary.getShortName().length() > 0){
            // try to compare short name with city name
            hasSameName = boundary.getShortName().equalsIgnoreCase(city.getName());
        }

        int adminLevelPriority = getAdminLevelPriority(boundary); // can be maximal 6
        if(hasSameName) {
            if(boundary.getCityType() != null) {
                //boundary was created from relation that has tag place. So it's the best boundary for city
                return 0;
            }

            // IMPORTANT comparison based on admin center id was limited because bigger region can have defined the admin but smaller not
            else if(boundary.getAdminLevel() >= 8 && city.getOsmId() == boundary.getAdminCenterId()){
                return adminLevelPriority;  // return 1 - 6
            }
            return 10 + adminLevelPriority; // return 11 - 16,
        }
        else {
            //boundary and city has different name
            if( boundary.getAdminLevel() >= 8 && city.getOsmId() == boundary.getAdminCenterId()) {
                return 20 + adminLevelPriority;
            }
            else {
                return 30  + adminLevelPriority;
            }
            // TODO consider if is needed to compare adminCenterId. It makes troubles for UpperOsterreich
            // due to this has LINZ as the best boundary whole upperosterreich region
            //return 30  + adminLevelPriority;
        }
    }

    /**
     * Says how priority has admin_level for boundary. The lower priority is better. OA inspiration
     *
     * @param boundary boundary to check
     * @return return number from 1 - 6 as mark that says how "good" is admin level for boundary.
     * The lower value has better priority
     */
    private int getAdminLevelPriority(Boundary boundary) {

        int adminLevelPriority = 5;

        if(boundary.getAdminLevel() > 0) {

            int adminLevel = boundary.getAdminLevel();

            if(adminLevel == 8) { //the best level city boundary
                adminLevelPriority = 1;
            }
            else if(adminLevel == 7) {
                adminLevelPriority = 2;
            }
            else if(adminLevel == 6) {
                adminLevelPriority = 3;
            }
            else if(adminLevel == 9) {
                adminLevelPriority = 4;
            }
            else if(adminLevel == 10) {
                adminLevelPriority = 5;
            }
            else {
                adminLevelPriority = 6;
            }
        }
        return adminLevelPriority;
    }
//
//    /**
//     * Get priority of parent boundary for city. Priority relate on type of city
//     * @param city City for which compare the priority of possible parent boundaries
//     * @param boundary Parent boundary to check
//     * @return the number form 1 - 6. When lower is better
//     */
//    private int getParentAdminLevelPriority(City city, Boundary boundary) {
//
//        int adminLevelPriority = 5;
//        int adminLevel = boundary.getAdminLevel();
//
//        if (adminLevel > 0) {
//            City.CityType type = city.getType();
//            if (type == City.CityType.CITY || type == City.CityType.TOWN){
//                // FOR Cities and Town boundaries prefer the county or higher region
//                if(adminLevel == 7) {
//                    adminLevelPriority = 1;
//                }
//                else if(adminLevel == 6) {
//                    adminLevelPriority = 2;
//                }
//                else if(adminLevel == 5) {
//                    adminLevelPriority = 3;
//                }
//                else {
//                    adminLevelPriority = 4;
//                }
//            }
//
//            else {
//                // find priority for village, hamlet or suburb
//                if(adminLevel == 8) {
//                    adminLevelPriority = 1;
//                }
//                else if(adminLevel == 7) {
//                    adminLevelPriority = 2;
//                }
//                else if(adminLevel == 6) {
//                    adminLevelPriority = 3;
//                }
//                else if(adminLevel == 9) {
//                    adminLevelPriority = 4;
//                }
//                else if(adminLevel == 10) {
//                    adminLevelPriority = 5;
//                }
//                else {
//                    adminLevelPriority = 6;
//                }
//            }
//        }
//
//        return adminLevelPriority;
//    }


    /**
     * Compare name of boundary with name of city and try find city with similar name as boundary
     * @param boundary
     * @param city
     * @return true if city has similar name as boundary
     */
    public boolean hasSimilarName(Boundary boundary, City city){

        String bName = boundary.getName().toLowerCase();
        String bShortName = boundary.getShortName().toLowerCase();
        String cName = city.getName().toLowerCase();

        if (bName.length() == 0 || cName.length() == 0){
            return false;
        }

        if (bName.startsWith(cName+" ") || bName.endsWith(" "+ cName) || bName.contains( " " + cName + " ")){
            return true;
        }

        if (bShortName.startsWith(cName+" ") || bShortName.endsWith(" "+ cName) || bShortName.contains( " " + cName + " ")){
            return true;
        }

        if (cName.startsWith(bName+" ") || cName.endsWith(" "+ bName) || cName.contains( " " + bName + " ")){
            return true;
        }

        //compare using similarity
        double similarity = StringUtils.getJaroWinklerDistance(bName, cName);
        if (similarity > 0.89){
            return true;
        }

        similarity = StringUtils.getJaroWinklerDistance(bShortName, cName);
        if (similarity > 0.89){
            return true;
        }

        return false;
    }

//    /**
//     * It can happen that some small village or hamlet can have similar city as boundary for big area. It's
//     * needed to limit such villages and do not allow to assign such boundaries for small city even of they
//     * have similar name. Boundary with admin level > 5 can be automatically used for every city level
//     * @param boundary boundary that should be assigned to the city
//     * @param city city that we found as possible center city
//     * @return true if city level can be used for boundary
//     */
//    public boolean canBeSetAsCenterCity(Boundary boundary, City city) {
//
//        if (boundary.getAdminLevel() > 5){
//            // this is not so big boundary automatically use it
//            return true;
//        }
//
//        if (city.getType().getTypeCode() <= City.CityType.VILLAGE.getTypeCode()){
//            return true;
//        }
//        // this city is SUBURB, HAMLET OR DISTRICT and boundary is something on admin <= 5 do not allow to use it as center
//        return false;
//    }

    /**
     * Look for parent city for city.
     * @param dc temporary data container
     * @param city city to find parent
     * @return parent city or null if was not possible to find parent city
     */
    public City findParentCity(ADataContainer dc, City city) {

        City parentCity = null;

        City.CityType type = city.getType();
        // find parent city only for villages subburs etc.
        if (type == City.CityType.CITY || type == City.CityType.TOWN){
            return null;
        }

        PreparedGeometry pgCenter = PreparedGeometryFactory.prepare(city.getCenter());

        List<Boundary> parentBoundaries = IndexController.getInstance().getBoundaryGeomIndex()
                .query(pgCenter.getGeometry().getEnvelopeInternal());

        // from the list of boundaries from query find such that cover the center of city and find the one with the best admin level
        Boundary parentBoundary = null;
        int parentBoundaryPriority = 6;

        for (Boundary boundary : parentBoundaries){
            if (pgCenter.intersects(boundary.getGeom())){
                int priority = getAdminLevelPriority(boundary);

                if (parentBoundary == null){
                    parentBoundary = boundary;
                    parentBoundaryPriority = priority;
                }
                else if (priority < parentBoundaryPriority) {
                    parentBoundary = boundary;
                    parentBoundaryPriority = priority;
                }
            }
        }

         // get the center city for the parent boundary
        if (parentBoundary != null){
            parentCity = dc.getCenterCityBoundaryMap().getKey(parentBoundary);

            if (parentCity != null && parentCity.getOsmId() == city.getOsmId()){
                // find the same city return null
                return null;
            }
        }
        return parentCity;
    }

    /**
     * Find admin region in which is city
     *
     * @param city to find region for
     * @return parent region in which city is in or null if there is no region for city
     */
    public Region findParentRegion (City city){

        PreparedGeometry pgCenter = PreparedGeometryFactory.prepare(city.getCenter());

        List<Region> parentRegions = IndexController.getInstance().getRegionGeomIndex()
                .query(pgCenter.getGeometry().getEnvelopeInternal());
        Region parentRegion = null;
        for (Region region : parentRegions){

            if (pgCenter.intersects(region.getGeom())){
                if (parentRegion != null){
                    // there is other region where city is in. compare it with previous by name and by area
                    if (city.getIsIn().toLowerCase().contains(region.getName().toLowerCase())){
                        parentRegion = region;
                    }
                    else if (region.getGeom().getArea() > parentRegion.getGeom().getArea()){
                        parentRegion = region;
                    }
                }
                else {
                    parentRegion = region;
                }
            }
        }

        return parentRegion;
    }



}
