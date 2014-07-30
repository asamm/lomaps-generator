/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.server;

import java.util.ArrayList;

/**
 *
 * @author voldapet
 */
public class StoreItem {

    String name;
    /**
     * Type of item (vector, etc) defined in StoreConst
     */
    int itemType;
    /**
     * Versioning of item
     */
    int itemVersion;
    /**
     * For computing of amount of credit
     */
    double fileSize;
    /**
     * String on the amazon
     */
    String url;
    ArrayList<String> regionIds;

    public StoreItem() {
        reset();
    }

    public StoreItem(String name, int itemType) {

        reset();

        this.name = name;
        this.itemType = itemType;
    }

    public void reset() {
        name = "";
        itemVersion = -1;
        fileSize = -1.0;
        url = "";
        regionIds = new ArrayList<String>();
    }

    /**
     * ***********************************************
     */
    /*              GETTERS AND SETTERS               */
    /**
     * ***********************************************
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        if (name != null && !name.isEmpty()) {
            this.name = name;
        }
    }

    public int getItemType() {
        return itemType;
    }

    public void setItemType(int itemType) {
        this.itemType = itemType;
    }

    public int getItemVersion() {
        return itemVersion;
    }

    public void setItemVersion(int itemVersion) {
        this.itemVersion = itemVersion;
    }

    public double getFileSize() {
        return fileSize;
    }

    public void setFileSize(double fileSize) {
        this.fileSize = fileSize;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        if (url != null && !url.isEmpty()) {
            this.url = url;
        }
    }

    public ArrayList<String> getRegionIds() {
        return regionIds;
    }

    public void setRegionIds(ArrayList<String> regionIds) {
        if (regionIds != null) {
            this.regionIds = regionIds;
        }

    }
}
