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

    private String mDirGen;
    // URL source for map file
    private String mUrl;
    private String mCycleNode;
    private String mContourSep;
    private String mForceType;
    private String mForceInterval;
    // parameter if we need coastline
    private boolean mRequireCoastline;

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
            mUrl = parent.getUrl();
            mCycleNode = parent.getCycleNode();
            mContourSep = parent.getContourSep();
            mForceType = parent.getForceType();
            mForceInterval = parent.getForceInterval();
            mRequireCoastline = parent.requireCoastline();
        }
    }

    private void setDefaults() {
        mParent = null;
        mName = "";
        mActions = new ArrayList<Parameters.Action>();
        mSourceId = "";
        mRegionId = "";
        mDir = "";
        mDirGen = "";
        mUrl = "";
        mCycleNode = "";
        mContourSep = "";
        mForceType = "";
        mForceInterval = "";
        mRequireCoastline = false;
    }

    public boolean isValid() {
        // check actions
        if (mActions.size() == 0) {
            return false;
        }

        // check name
        if (getName().length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. Name is empty");
        }

        // check dir
        if (mDir.length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. " +
                    "Invalid argument dir: " + mDir);
        }

        // check extract action
        if (hasAction(Parameters.Action.EXTRACT) && (getSourceId() == null)) {
            throw new IllegalArgumentException("Input XML is not valid. MapPack "
                    + getName() + " sourceId is empty");
        }

        // check download action
        if (hasAction(Parameters.Action.DOWNLOAD) && getUrl().length() == 0) {
            throw new IllegalArgumentException("Input XML is not valid. MapPack "
                    + getName() + " - url is empty");
        }

        // check contour action
        if (hasAction(Parameters.Action.CONTOUR)){
            if (getContourSep().length() == 0) {
                throw new IllegalArgumentException("Input XML is not valid - map "
                        + getName() + " has not tag contourSep" );
            }
        }
        return true;
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
            for (int i = 0, m = sepActions.length; i < m; i++) {

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
            if (mRegionId  != null){
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

        // other basis parameters
        if (parser.getAttributeValue(null, "url") != null) {
            mUrl = parser.getAttributeValue(null, "url");
        }
        if (parser.getAttributeValue(null, "cyclo_node") != null) {
            mCycleNode = parser.getAttributeValue(null, "cyclo_node");
        }
        if (parser.getAttributeValue(null, "coastline") != null) {
            String coastline = parser.getAttributeValue(null, "coastline");
            mRequireCoastline = coastline.equalsIgnoreCase("yes");
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
        return new ArrayList<Parameters.Action>(mActions);
    }

    public String getSourceId() {
        return mSourceId;
    }

    public String getRegionId() {
        return mRegionId;
    }

    public String getDir() {
        return mDir;
    }

    public String getDirGen() {
        return mDirGen;
    }

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

    public boolean requireCoastline() {
        return mRequireCoastline;
    }
}
