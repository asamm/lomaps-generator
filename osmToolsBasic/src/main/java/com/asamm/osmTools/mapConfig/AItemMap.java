package com.asamm.osmTools.mapConfig;

import com.asamm.osmTools.Parameters;
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
    private ItemMapPack mParent;
    // name of this object
    private String mName;
    // types (actions) that should be performed
    private List<Parameters.Action> mActions;
    // source for map data
    private String mSourceId;
    // ID of region
    private String mRegionId;
    // directory name
    private String mDir;
    // folder for generation
    private String mDirGen;
    // address DB boundary admin level for address region boundaries
    private String mCountryName;
    // when extract map from planet some big areas can be outside the border and are removed. Set true to close
    private boolean mClipIncompleteEntities;
    // prefered language for generating
    private String mPrefLang;


    // URL source for map file
    private String mUrl;
    private String mCycleNode;
    private String mContourSep;
    private String mForceType;
    // define internal for map file used during generating
    private String mForceInterval;
    // parameter if we need coastline
    private boolean mHasSea;

    public AItemMap(ItemMapPack parent) {
        setDefaults();

        // set values from parent
        if (parent != null) {
            mParent = parent;
            mName = parent.getName();
            mActions = parent.getActionsCopy();
            mSourceId = parent.getSourceId();
            mRegionId = parent.getRegionId();
            mDir = parent.getDir();
            mDirGen = parent.getDirGen();
            mCountryName = parent.getCountryName();
            mPrefLang = parent.getPrefLang();
            mUrl = parent.getUrl();
            mCycleNode = parent.getCycleNode();
            mContourSep = parent.getContourSep();
            mForceType = parent.getForceType();
            mForceInterval = parent.getForceInterval();
            mHasSea = parent.hasSea();
        }
    }

    private void setDefaults() {
        mParent = null;
        mName = "";
        mActions = new ArrayList<>();
        mSourceId = "";
        mRegionId = "";
        mDir = "";
        mDirGen = "";
        mCountryName = "";
        mClipIncompleteEntities = false;
        mPrefLang = "";
        mUrl = "";
        mCycleNode = "";
        mContourSep = "";
        mForceType = "";
        mForceInterval = "";
        mHasSea = false;
    }

    public void validate() {
        // check actions
        if (mActions.size() == 0) {
            throw new IllegalArgumentException("No defined actions for '" + mName + "'");
        }

        // check name
        if (getName().length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. Name is empty");
        }

        // check dir
        if (mDir.length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. " +
                    "Invalid argument dir: " + mDir + ", name:" + mName);
        }

        // check extract action
        if (hasAction(Parameters.Action.EXTRACT) && (getSourceId() == null)) {
            throw new IllegalArgumentException("Input XML is not valid. MapPack "
                    + getName() + " sourceId is empty, name:" + mName);
        }

        // check download action
        if (hasAction(Parameters.Action.DOWNLOAD) && getUrl().length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. MapPack "
                    + getName() + " - url is empty, name:" + mName);
        }

        // check contour action
        if (hasAction(Parameters.Action.CONTOUR)){
            if (getContourSep().length() == 0) {
                throw new IllegalArgumentException("Input XML is not valid - map "
                        + getName() + " has not tag contourSep, name:" + mName);
            }
        }
    }

    public void fillAttributes(KXmlParser parser) {
        // parse name
        if (parser.getAttributeValue(null, "name") != null) {
            mName = parser.getAttributeValue(null, "name");
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
                    mActions.clear();
                }
            }

            // add new actions
            for (int i = startIndex, m = sepActions.length; i < m; i++) {

                // search for correct action
                boolean added = false;
                Parameters.Action[] possibleActions = Parameters.Action.values();
                for (int j = 0, n = possibleActions.length; j < n; j++) {
                    if (possibleActions[j].getLabel().equalsIgnoreCase(sepActions[i])) {
                        mActions.add(possibleActions[j]);
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
            mSourceId = parser.getAttributeValue(null, "sourceId");
        }

        // regionId
        if (parser.getAttributeValue(null, "regionId") != null) {
            if (mRegionId  != null && mRegionId.length() > 0){
                mRegionId = mRegionId + "." + parser.getAttributeValue(null, "regionId");
            }else {
                mRegionId = parser.getAttributeValue(null, "regionId");
            }
        }

        // dir
        String attrValue = parser.getAttributeValue(null, "dir");
        if (attrValue != null) {
            attrValue = Utils.changeSlash(attrValue);
            mDir = mDir.length() > 0 ?
                    mDir + Consts.FILE_SEP + attrValue : attrValue;
            mDir = Consts.fixDirectoryPath(mDir);
        }

        // dirGen
        attrValue = parser.getAttributeValue(null, "dirGen");
        if (attrValue != null) {
            attrValue = Utils.changeSlash(attrValue);
            mDirGen = (mDirGen != null) ? mDirGen + Consts.FILE_SEP + attrValue : attrValue;
        } else {
            mDirGen = mDir;
        }
        mDirGen = Consts.fixDirectoryPath(mDirGen);

        // addressRegionLevel
        attrValue = parser.getAttributeValue(null, "countryName");
        if (attrValue != null){
            mCountryName = attrValue;
        }

        //clipIncompleteEntities
        attrValue = parser.getAttributeValue(null, "clipEntities");
        if (attrValue != null){
            if (attrValue.equals("0")){
                mClipIncompleteEntities = false;
            }
            else if (attrValue.equals("1")){
                mClipIncompleteEntities = true;
            }
            else {
                throw new IllegalArgumentException("Invalid value 'clipEntities' value:" + attrValue  +
                        " Set '0' for not clipping or '1' for clip the incomplete elements");
            }
        }

        if (parser.getAttributeValue(null, "prefLang") != null) {
            mPrefLang = parser.getAttributeValue(null, "prefLang");
        }

        // other basis parameters
        if (parser.getAttributeValue(null, "url") != null) {
            mUrl = parser.getAttributeValue(null, "url");
        }
        if (parser.getAttributeValue(null, "cyclo_node") != null) {
            mCycleNode = parser.getAttributeValue(null, "cyclo_node");
        }
        if (parser.getAttributeValue(null, "coastline") != null) {
            String coastline = parser.getAttributeValue(null, "coastline");
            mHasSea = coastline.equalsIgnoreCase("yes");
        }
        if (parser.getAttributeValue(null, "contourSep") != null) {
            mContourSep = parser.getAttributeValue(null, "contourSep");
        }
        if (parser.getAttributeValue(null, "forceType") != null) {
            mForceType = parser.getAttributeValue(null, "forceType");
        }
        if (parser.getAttributeValue(null, "forceInterval") != null) {
            mForceInterval = parser.getAttributeValue(null, "forceInterval");
        }
    }

    /**************************************************/
    /*               GETTERS & SETTERS                */
    /**************************************************/

    public ItemMapPack getParent() {
        return mParent;
    }

    public String getName() {
        return mName;
    }

    public boolean hasAction(Parameters.Action action) {
        return mActions.contains(action);
    }

    public List<Parameters.Action> getActionsCopy() {
        return new ArrayList<>(mActions);
    }

    public String getSourceId() {
        return mSourceId;
    }

    public String getRegionId() {
        return mRegionId;
    }

    public String getParentRegionId () {
        int index = mRegionId.lastIndexOf(".");
        if (index == -1){
            return mRegionId;
        }

        return mRegionId.substring(0, index);
    }

    public String getDir() {
        return mDir;
    }

    public String getDirGen() {
        return mDirGen;
    }


    /**
     * Get readable name of country in which is item.
     *
     * @return name of country
     */
    public String getCountryName() {
        return mCountryName;
    }

    /**
     * Define if during extraction should osmosis clip the areas that can be partialy outside the border poly
     * @return
     */
    public boolean getClipIncompleteEntities () {
        return mClipIncompleteEntities;
    }

    public void setAddressRegionLevel(String countryName) {
        this.mCountryName = countryName;
    }

    public String getPrefLang() { return mPrefLang;   }

    public String getUrl() {
        return mUrl;
    }

    public String getCycleNode() {
        return mCycleNode;
    }

    public String getContourSep() {
        return mContourSep;
    }

    public String getForceType() {
        return mForceType;
    }

    public String getForceInterval() {
        return mForceInterval;
    }

    public boolean hasSea() {
        return mHasSea;
    }



}
