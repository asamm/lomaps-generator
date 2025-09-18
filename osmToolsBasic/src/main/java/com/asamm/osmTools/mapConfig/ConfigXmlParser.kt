package com.asamm.osmTools.mapConfig

import com.asamm.osmTools.config.AppConfig
import org.apache.commons.io.IOUtils
import org.kxml2.io.KXmlParser
import org.xmlpull.v1.XmlPullParserException
import java.io.File
import java.io.FileInputStream
import java.io.IOException

object ConfigXmlParser {


    /**
     * Read base definition XML config file and create structure of mapPacks and maps to generate
     *
     * @param xmlFile xml config file to parse
     * @return
     * @throws IOException
     * @throws XmlPullParserException
     */
    @JvmStatic
    @Throws(IOException::class, XmlPullParserException::class)
    fun parseConfigXml(xmlFile: File): MapSource {
        val mapSource = MapSource()

        var fis: FileInputStream? = null
        try {
            // test if config file exist

            require(xmlFile.exists()) { "Config file " + xmlFile.absolutePath + " does not exist!" }

            // prepare variables
            fis = FileInputStream(xmlFile)
            val parser = KXmlParser()
            parser.setInput(fis, "utf-8")
            var tag: Int
            var tagName: String

            var mapPack: ItemMapPack? = null

            // finally parse data
            while ((parser.next().also { tag = it }) != KXmlParser.END_DOCUMENT) {
                if (tag == KXmlParser.START_TAG) {
                    tagName = parser.name
                    if (tagName.equals("maps", ignoreCase = true)) {
                        val rewriteFiles = parser.getAttributeValue(null, "rewriteFiles")
                        AppConfig.config.overwrite = (
                                rewriteFiles != null && rewriteFiles.equals("yes", ignoreCase = true)
                                )
                    } else if (tagName.equals("mapPack", ignoreCase = true)) {
                        mapPack = ItemMapPack(mapPack)
                        mapPack.fillAttributes(parser)
                    } else if (tagName.equals("map", ignoreCase = true)) {
                        val map = ItemMap(mapPack)
                        // set variables from xml to map object
                        map.fillAttributes(parser)

                        // set boundaries from polygons
                        map.setBoundsFromPolygon()

                        // finally add map to container
                        mapPack!!.addMap(map)

                        //***ONLY FOR CREATION ORDERS HOW TO CREATE NEW REGION ON NEW SERVER **/
//                        StringBuilder sb = new StringBuilder("regionData.add(new String[]{");
//                                sb.append("\"").append(mapPack.regionId).append("\", ")
//                                   .append("\"").append(map.regionId).append("\", ")
//                                   .append("\"").append(map.name).append("\"});");
//                        System.out.println(sb.toString());
                    }
                } else if (tag == KXmlParser.END_TAG) {
                    tagName = parser.name
                    if (tagName == "mapPack") {
                        if (mapPack!!.parent != null) {
                            mapPack.parent.addMapPack(mapPack)
                            mapPack = mapPack.parent
                        } else {
                            // validate mapPack and place it into list
                            mapSource.addMapPack(mapPack)
                            mapPack = null
                        }
                    }
                }
            }
        } finally {
            IOUtils.closeQuietly(fis)
        }
        return mapSource
    }

}
