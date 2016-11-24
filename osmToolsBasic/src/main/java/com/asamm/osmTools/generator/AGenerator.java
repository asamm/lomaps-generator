package com.asamm.osmTools.generator;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.cmdCommands.CmdCountryBorders;
import com.asamm.osmTools.cmdCommands.CmdExtract;
import com.asamm.osmTools.generatorDb.plugin.ConfigurationCountry;
import com.asamm.osmTools.mapConfig.ItemMap;
import com.asamm.osmTools.mapConfig.ItemMapPack;
import com.asamm.osmTools.mapConfig.MapSource;
import com.asamm.osmTools.utils.Logger;
import com.asamm.osmTools.utils.TimeWatch;
import org.apache.commons.io.IOUtils;
import org.kxml2.io.KXmlParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

/**
 * Created by voldapet on 2016-09-16 .
 */
public abstract class AGenerator {

    private static final String TAG = AGenerator.class.getSimpleName();



    /**
     * Read base definition XML config file and create structure of mapPacks and maps to generate
     * @param xmlFile  xml config file to parse
     * @return
     * @throws IOException
     * @throws XmlPullParserException
     */
    protected MapSource parseConfigXml(File xmlFile) throws IOException, XmlPullParserException {

        MapSource mapSource = new MapSource();

        FileInputStream fis = null;
        try {

            // test if config file exist
            if (!xmlFile.exists()){
                throw new IllegalArgumentException("Config file "+ xmlFile.getAbsolutePath() + " does not exist!" );
            }

            // prepare variables
            fis = new FileInputStream(xmlFile);
            KXmlParser parser = new KXmlParser();
            parser.setInput(fis, "utf-8");
            int tag;
            String tagName;

            ItemMapPack mapPack = null;

            // finally parse data
            while ((tag = parser.next()) != KXmlParser.END_DOCUMENT) {
                if (tag == KXmlParser.START_TAG) {
                    tagName = parser.getName();
                    if (tagName.equalsIgnoreCase("maps")) {
                        String rewriteFiles = parser.getAttributeValue(null, "rewriteFiles");
                        Parameters.setRewriteFiles(
                                rewriteFiles != null && rewriteFiles.equalsIgnoreCase("yes"));
                    } else if (tagName.equalsIgnoreCase("mapPack")) {
                        mapPack = new ItemMapPack(mapPack);
                        mapPack.fillAttributes(parser);
                    } else if (tagName.equalsIgnoreCase("map")) {
                        ItemMap map = new ItemMap(mapPack);
                        // set variables from xml to map object
                        map.fillAttributes(parser);
                        // set variables with path to files
                        map.setPaths();
                        // set boundaries from polygons

                        map.setBoundsFromPolygon();



                        // finally add map to container
                        mapPack.addMap(map);

                        //***ONLY FOR CREATION ORDERS HOW TO CREATE NEW REGION ON NEW SERVER **/
//                        StringBuilder sb = new StringBuilder("regionData.add(new String[]{");
//                                sb.append("\"").append(mapPack.regionId).append("\", ")
//                                   .append("\"").append(map.regionId).append("\", ")
//                                   .append("\"").append(map.name).append("\"});");
//                        System.out.println(sb.toString());
                    }
                } else if (tag == KXmlParser.END_TAG) {
                    tagName = parser.getName();
                    if (tagName.equals("mapPack")) {
                        if (mapPack.getParent() != null) {
                            mapPack.getParent().addMapPack(mapPack);
                            mapPack = mapPack.getParent();
                        } else {
                            // validate mapPack and place it into list
                            mapSource.addMapPack(mapPack);
                            mapPack = null;
                        }
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(fis);
        }
        return mapSource;
    }


    // ACTION EXTRACT

    public void actionExtract(ItemMapPack mp, final MapSource ms)
            throws IOException, InterruptedException {
        Logger.d(TAG, "actionExtract(" + mp + ", " + ms + ")");
        // create hashTable where identificator is sourceId of map and values is an list of
        // all map with same sourceId
        Map<String, List<ItemMap>> mapTableBySourceId = new Hashtable<>();

        // fill hash table with values
        for (int i = 0, m = mp.getMapsCount(); i < m; i++) {
            ItemMap actualMap = mp.getMap(i);

            if (actualMap.hasAction(Parameters.Action.EXTRACT)) {
                List<ItemMap> itemsToExtract = mapTableBySourceId.get(actualMap.getSourceId());
                if (itemsToExtract == null) {
                    itemsToExtract = new ArrayList<>();
                    mapTableBySourceId.put(actualMap.getSourceId(), itemsToExtract);
                }

                // test if file for extract exist. If yes don't add it into ar
                String writeFileLocation = actualMap.getPathSource();
                if (!new File(writeFileLocation).exists()){
                    itemsToExtract.add(actualMap);
                } else {
                    Logger.i(TAG, "Map for extraction: " +writeFileLocation+ " already exist. No action performed" );
                }
            }
        }

        // get valid sources and sort them by availability
        Iterator<String> keys = mapTableBySourceId.keySet().iterator();
        List<String> sources = new ArrayList<>();
        while (keys.hasNext()) {
            // get content and check if we need to process any maps
            String key = keys.next();
            List<ItemMap> ar = mapTableBySourceId.get(key);
            if (ar.isEmpty()) {
                continue;
            }

            // add to list
            sources.add(key);
        }

        // sort by availability
        Collections.sort(sources, new Comparator<String>() {

            @Override
            public int compare(String source1, String source2) {
                // get required parameters
                String file1 = ms.getMapById(source1).getPathSource();
                boolean ex1 = new File(file1).exists();
                String file2 = ms.getMapById(source2).getPathSource();
                boolean ex2 = new File(file2).exists();

                // compare data
                if (ex1) {
                    return -1;
                } else if (ex2) {
                    return 1;
                } else {
                    return 0;
                }
            }
        });

        // finally handle data
        for (int i = 0, m = sources.size(); i < m; i++) {
            String sourceId = sources.get(i);
            List<ItemMap> ar = mapTableBySourceId.get(sourceId);

            CmdExtract ce =  new CmdExtract(ms, sourceId);
            ce.addReadSource();
            ce.addTee(ar.size());
            ce.addBuffer();

            // add all maps
            for (ItemMap map : ar) {
                ce.addBoundingPolygon(map);
                ce.addWritePbf(map.getPathSource(), true);
            }

            // write to log and start stop watch
            TimeWatch time = new TimeWatch();
            Logger.i(TAG, "Extracting maps from source: " + sourceId);
            Logger.i(TAG, ce.getCmdLine());
            Main.mySimpleLog.print("\nExtract Maps from: " + sourceId + " ...");

            // now create simple array
            ce.execute();
            Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
        }

        // execute extract also on sub-packs
        for (int i = 0, m = mp.getMapPackCount(); i < m; i++) {
            actionExtract(mp.getMapPack(i), ms);
        }
    }


    // COUNTRY BOUNDARY


    /**
     * Create country boundaries for map items in mappack
     *
     * @param mp map pack to create countries for it's items
     */
    protected void actionCountryBorder(
            ItemMapPack mp, MapSource mapSource, ConfigurationCountry.StorageType storageType) throws IOException, InterruptedException {


        Logger.i(TAG, "actionCountryBorder, source: " + mp.getName());

        // map where key is mappack id and value is list of map to generate boundaries from source
        Map<String, List<ItemMap>> mapTableBySourceId = prepareCountriesForSource(mp, storageType);


        // for every source run generation of country borders
        for (Map.Entry<String, List<ItemMap>> entry : mapTableBySourceId.entrySet()) {

            ItemMap sourceMap = mapSource.getMapById(entry.getKey());
            List<ItemMap> mapToCreate = entry.getValue();

            if (mapToCreate.size() == 0){
                continue;
            }

            // filter only boundary values from source
            CmdCountryBorders cmdCBfilter = new CmdCountryBorders(sourceMap, storageType);
            cmdCBfilter.addTaskFilter();
            Logger.i(TAG, "Filter for generation country bound, command: " + cmdCBfilter.getCmdLine());
            cmdCBfilter.execute();

            CmdCountryBorders cmdBorders = new CmdCountryBorders(sourceMap, storageType);
            cmdBorders.addGeneratorCountryBoundary();
            cmdBorders.addCountries(mapToCreate);
            Logger.i(TAG, "Generate country boundary, command: " + cmdBorders.getCmdLine() );
            cmdBorders.execute();

            // delete tmp file
            cmdBorders.deleteTmpFile();
        }
    }

    /**
     * For every source prepare list of countries that can be generated from source
     * @param mp source mappack that can be used as source for generated counties boundaries
     * @return
     */
    private Map<String, List<ItemMap>> prepareCountriesForSource (
            ItemMapPack mp, ConfigurationCountry.StorageType storageType) {

        // map where key is mappack id and value is list of map to generate boundaries from source
        Map<String, List<ItemMap>> mapTableBySourceId = new HashMap<>();

        // fill hash table with values in first step proccess map in mappack
        for (ItemMap map : mp.getMaps()) {



            if (!map.hasAction(Parameters.Action.GENERATE)
                    && !map.hasAction(Parameters.Action.ADDRESS_POI_DB)
                    && !map.hasAction(Parameters.Action.STORE_GEO_DB)) {
                continue;
            }

            if (storageType == ConfigurationCountry.StorageType.GEOJSON && new File(map.getPathCountryBoundaryGeoJson()).exists()) {
                // boundary geom for this map exists > skip it
                Logger.i(TAG, "Country boundaries exits for map: " + map.getNameReadable());
                continue;
            }

            // get the source item for map
            String sourceId = map.getSourceId();
            // get the list of countries that will be created from source
            List<ItemMap> mapsFromSource = mapTableBySourceId.get(sourceId);
            if (mapsFromSource == null) {
                mapsFromSource = new ArrayList<>();
            }
            mapsFromSource.add(map);
            mapTableBySourceId.put(sourceId, mapsFromSource);
        }

        // now get submaps for mappack and their mappack...
        for (ItemMapPack mapPack : mp.getMapPacks()){

            // for every map pack get country boundaries that will be generated
            Map<String, List<ItemMap>> sourcesSubMap = prepareCountriesForSource(mapPack,storageType);

            // combine result with parent source map
            for (Map.Entry<String, List<ItemMap>> entry : sourcesSubMap.entrySet()) {

                List<ItemMap> subMaps = mapTableBySourceId.get(entry.getKey());
                if (subMaps == null) {
                    subMaps = new ArrayList<>();
                }
                subMaps.addAll(entry.getValue());
                mapTableBySourceId.put(entry.getKey(), subMaps);
            }
        }

        return mapTableBySourceId;
    }


}
