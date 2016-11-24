/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.sea;

/**
 *
 * @author volda
 */
public class Boundaries {

    private double minLon;
    private double maxLon;
    private double minLat;
    private double maxLat;

    // MIN LON

    public double getMinLon() {
        return minLon;
    }

    public void setMinLon(double minLon) {
        this.minLon = minLon;
    }

    // MAX LON

    public double getMaxLon() {
        return maxLon;
    }

    public void setMaxLon(double maxLon) {
        this.maxLon = maxLon;
    }

    // MIN LAT

    public double getMinLat() {
        return minLat;
    }

    public void setMinLat(double minLat) {
        this.minLat = minLat;
    }

    // MAX LAT

    public void setMaxLat(double maxLat) {
        this.maxLat = maxLat;
    }

    public double getMaxLat() {
        return maxLat;
    }

    @Override
    public String toString() {
        return "Boundaries{" +
                "minLon=" + minLon +
                ", maxLon=" + maxLon +
                ", minLat=" + minLat +
                ", maxLat=" + maxLat +
                '}';
    }
}
