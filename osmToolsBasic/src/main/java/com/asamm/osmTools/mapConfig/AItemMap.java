package com.asamm.osmTools.mapConfig;

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
    // address DB boundary admin level for address region boundaries
    private String countryName;
    // prefered language for generating
    private String prefLang;
    // ISO Alpha2 country code used only for creation store region DB
    private String regionCode;
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
            countryName = parent.getCountryName();
            prefLang = parent.getPrefLang();
            regionCode = parent.getRegionCode();
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
        countryName = "";
        prefLang = "";
        regionCode = "";
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
        if (hasAction(Action.EXTRACT_OSM_PLANET) && (getSourceId() == null)) {
            throw new IllegalArgumentException("Input XML is not valid. MapPack "
                    + getName() + " sourceId is empty, name:" + name);
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

        // addressRegionLevel
        attrValue = parser.getAttributeValue(null, "countryName");
        if (attrValue != null) {
            countryName = attrValue;
        }

        if (parser.getAttributeValue(null, "prefLang") != null) {
            prefLang = parser.getAttributeValue(null, "prefLang");
        }
        if (parser.getAttributeValue(null, "regionCode") != null) {
            regionCode = parser.getAttributeValue(null, "regionCode");
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

    public String getParentRegionId() {

        if (parentRegionId != null && parentRegionId.length() > 0) {
            return parentRegionId;
        }

        // as fallback parse parent id from region id
        int index = regionId.lastIndexOf(".");
        if (index == -1) {
            return regionId;
        }

        return regionId.substring(0, index);
    }

    public String getDir() {
        return dir;
    }

    /**
     * Get readable name of country in which is item.
     *
     * @return name of country
     */
    public String getCountryName() {
        return countryName;
    }

    public String getPrefLang() {
        return prefLang;
    }

    public String getRegionCode() {
        return regionCode;
    }

}
