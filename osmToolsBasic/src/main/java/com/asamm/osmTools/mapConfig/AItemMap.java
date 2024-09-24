package com.asamm.osmTools.mapConfig;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.config.Action;
import com.asamm.osmTools.utils.Consts;
import com.asamm.osmTools.utils.Utils;
import org.kxml2.io.KXmlParser;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by menion on 20. 7. 2014.
 * Class is part of Locus project
 */
public class AItemMap {

    // parent MapPack of this object
    private ItemMapPack parent;
    // name of this object
    private String name;
    // types (actions) that should be performed
    private List<Action> actions;
    // source for map data
    private String sourceId;
    // ID of region
    private String regionId;
    // ID of parent region
    private String parentRegionId;
    // directory name
    private String dir;
    // folder for generation
    private String dirGen;
    // address DB boundary admin level for address region boundaries
    private String countryName;
    // when extract map from planet some big areas can be outside the border and are removed. Set true to close
    private boolean clipIncompleteEntities;
    // prefered language for generating
    private String prefLang;
    // ISO Alpha2 country code used only for creation store region DB
    private String regionCode;
    // type of contour lines in meters or feet
    private ContourUnit contourUnit = ContourUnit.METER;
    // step of contour lines in meters or feet
    private String contourStep;

    private String contourSource = Parameters.contourSource;

    // URL source for map file
    private String url;
    private String cycleNode;
    private String contourSep;
    private String forceType;
    // define internal for map file used during generating
    private String forceInterval;
    // parameter if we need coastline
    private boolean hasSea;

    public AItemMap(ItemMapPack parent) {
        setDefaults();

        // set values from parent
        if (parent != null) {
            this.parent = parent;
            name = parent.getName();
            actions = parent.getActionsCopy();
            sourceId = parent.getSourceId();
            regionId = parent.getRegionId();
            parentRegionId = parent.getParentRegionId();
            dir = parent.getDir();
            dirGen = parent.getDirGen();
            countryName = parent.getCountryName();
            prefLang = parent.getPrefLang();
            regionCode = parent.getRegionCode();
            url = parent.getUrl();
            cycleNode = parent.getCycleNode();
            contourUnit = parent.getContourUnit();
            contourSource = parent.getContourSource();
            forceType = parent.getForceType();
            forceInterval = parent.getForceInterval();
            hasSea = parent.hasSea();
        }
    }

    private void setDefaults() {
        parent = null;
        name = "";
        actions = new ArrayList<>();
        sourceId = "";
        regionId = "";
        parentRegionId = "";
        dir = "";
        dirGen = "";
        countryName = "";
        clipIncompleteEntities = false;
        prefLang = "";
        regionCode = "";
        url = "";
        cycleNode = "";
        contourSep = "";
        forceType = "";
        forceInterval = "";
        hasSea = false;
    }

    public void validate() {
        // check actions
        if (actions.size() == 0) {
            throw new IllegalArgumentException("No defined actions for '" + name + "'");
        }

        // check name
        if (getName().length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. Name is empty");
        }

        // check dir
        if (dir.length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. " +
                    "Invalid argument dir: " + dir + ", name:" + name);
        }

        // check extract action
        if (hasAction(Action.EXTRACT) && (getSourceId() == null)) {
            throw new IllegalArgumentException("Input XML is not valid. MapPack "
                    + getName() + " sourceId is empty, name:" + name);
        }

        // check download action
        if (hasAction(Action.DOWNLOAD) && getUrl().length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. MapPack "
                    + getName() + " - url is empty, name:" + name);
        }
    }

    public void fillAttributes(KXmlParser parser) {
        // parse name
        if (parser.getAttributeValue(null, "name") != null) {
            name = parser.getAttributeValue(null, "name");
        }

        // parse type (action)
        if (parser.getAttributeValue(null, "type") != null) {
            // parse data
            String actions = parser.getAttributeValue(null, "type");
            String[] sepActions = actions.split("\\|");

            // clear previous actions if any new exists
            int startIndex = 0;
            if (sepActions.length > 0) {
                if (sepActions[0].equals("+")) {
                    // keep old actions
                    startIndex = 1;
                } else {
                    // clear parent actions
                    this.actions.clear();
                }
            }

            // add new actions
            for (int i = startIndex, m = sepActions.length; i < m; i++) {

                // search for correct action
                boolean added = false;
                Action[] possibleActions = Action.values();
                for (int j = 0, n = possibleActions.length; j < n; j++) {
                    if (possibleActions[j].getLabel().equalsIgnoreCase(sepActions[i])) {
                        this.actions.add(possibleActions[j]);
                        added = true;
                        break;
                    }
                }

                // check result
                if (!added) {
                    throw new IllegalArgumentException("Invalid 'type' value:" + sepActions[i]);
                }
            }
        }

        // sourceId
        if (parser.getAttributeValue(null, "sourceId") != null) {
            sourceId = parser.getAttributeValue(null, "sourceId");
        }

        // regionId
        if (parser.getAttributeValue(null, "regionId") != null) {
            regionId = parser.getAttributeValue(null, "regionId");
        }

        // parentReegionId
        if (parser.getAttributeValue(null, "parentRegionId") != null) {
            parentRegionId = parser.getAttributeValue(null, "parentRegionId");
        }

        // dir
        String attrValue = parser.getAttributeValue(null, "dir");
        if (attrValue != null) {
            attrValue = Utils.changeSlash(attrValue);
            dir = dir.length() > 0 ?
                    dir + Consts.FILE_SEP + attrValue : attrValue;
            dir = Consts.fixDirectoryPath(dir);
        }

        // dirGen
        attrValue = parser.getAttributeValue(null, "dirGen");
        if (attrValue != null) {
            attrValue = Utils.changeSlash(attrValue);
            dirGen = (dirGen != null) ? dirGen + Consts.FILE_SEP + attrValue : attrValue;
        } else {
            dirGen = dir;
        }
        dirGen = Consts.fixDirectoryPath(dirGen);

        // addressRegionLevel
        attrValue = parser.getAttributeValue(null, "countryName");
        if (attrValue != null){
            countryName = attrValue;
        }

        //clipIncompleteEntities
        attrValue = parser.getAttributeValue(null, "clipEntities");
        if (attrValue != null){
            if (attrValue.equals("0")){
                clipIncompleteEntities = false;
            }
            else if (attrValue.equals("1")){
                clipIncompleteEntities = true;
            }
            else {
                throw new IllegalArgumentException("Invalid value 'clipEntities' value:" + attrValue  +
                        " Set '0' for not clipping or '1' for clip the incomplete elements");
            }
        }

        if (parser.getAttributeValue(null, "prefLang") != null) {
            prefLang = parser.getAttributeValue(null, "prefLang");
        }
        if (parser.getAttributeValue(null, "regionCode") != null) {
            regionCode = parser.getAttributeValue(null, "regionCode");
        }

        // other basis parameters
        if (parser.getAttributeValue(null, "url") != null) {
            url = parser.getAttributeValue(null, "url");
        }
        if (parser.getAttributeValue(null, "cyclo_node") != null) {
            cycleNode = parser.getAttributeValue(null, "cyclo_node");
        }
        if (parser.getAttributeValue(null, "coastline") != null) {
            String coastline = parser.getAttributeValue(null, "coastline");
            hasSea = coastline.equalsIgnoreCase("yes");
        }
        if (parser.getAttributeValue(null, "contourSep") != null) {
            contourSep = parser.getAttributeValue(null, "contourSep");
        }
        if (parser.getAttributeValue(null, "forceType") != null) {
            forceType = parser.getAttributeValue(null, "forceType");
        }
        if (parser.getAttributeValue(null, "forceInterval") != null) {
            forceInterval = parser.getAttributeValue(null, "forceInterval");
        }
        // read the type of contour lines
        if (parser.getAttributeValue(null, "contour_unit") != null) {
            contourUnit = ContourUnit.getFromValue(parser.getAttributeValue(null, "contour_unit"));
        }
        if (parser.getAttributeValue(null, "contour_step") != null) {
            contourStep = parser.getAttributeValue(null, "contour_step");
        }
        if (parser.getAttributeValue(null, "contour_source") != null) {
            contourSource = parser.getAttributeValue(null, "contour_source");
        }
    }

    /**************************************************/
    /*               GETTERS & SETTERS                */
    /**************************************************/

    public ItemMapPack getParent() {
        return parent;
    }

    public String getName() {
        return name;
    }

    public boolean hasAction(Action action) {
        return actions.contains(action);
    }

    public List<Action> getActionsCopy() {
        return new ArrayList<>(actions);
    }

    public String getSourceId() {
        return sourceId;
    }

    public String getRegionId() {
        return regionId;
    }

    public String getParentRegionId () {

        if (parentRegionId != null && parentRegionId.length() > 0){
            return parentRegionId;
        }

        // as fallback parse parent id from region id
        int index = regionId.lastIndexOf(".");
        if (index == -1){
            return regionId;
        }

        return regionId.substring(0, index);
    }

    public String getDir() {
        return dir;
    }

    public String getDirGen() {
        return dirGen;
    }


    /**
     * Get readable name of country in which is item.
     *
     * @return name of country
     */
    public String getCountryName() {
        return countryName;
    }

    /**
     * Define if during extraction should osmosis clip the areas that can be partialy outside the border poly
     * @return
     */
    public boolean getClipIncompleteEntities () {
        return clipIncompleteEntities;
    }

    public void setAddressRegionLevel(String countryName) {
        this.countryName = countryName;
    }

    public String getPrefLang() { return prefLang;   }

    public String getRegionCode() {return regionCode;}

    public String getUrl() {
        return url;
    }

    public String getCycleNode() {
        return cycleNode;
    }

    public String getForceType() {
        return forceType;
    }

    public String getForceInterval() {
        return forceInterval;
    }

    public boolean hasSea() {
        return hasSea;
    }


    public ContourUnit getContourUnit() {
        return contourUnit;
    }

    public void setContourUnit(ContourUnit contourUnit) {
        this.contourUnit = contourUnit;
    }

    public String getContourStep() {
        if (contourStep == null || contourStep.length() == 0){
            // contour step isn't defined use default values
            if (contourUnit == ContourUnit.FEET){
                return Parameters.contourStepFeet;
            }
            return Parameters.contourStepMeter;
        }
        return contourStep;
    }

    public void setContourStep(String contourStep) {
        this.contourStep = contourStep;
    }

    public String getContourSource() {
        return contourSource;
    }

    public void setContourSource(String contourSource) {
        if (contourSource != null && contourSource.length() > 0){
            this.contourSource = contourSource;
        }
    }
}
