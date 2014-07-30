/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.utils;

/**
 *
 * @author volda
 */
public class TimeWatch {

    private long startTime;
    private long endTime;
    private long elapsedTime;

    public TimeWatch() {
        startTime = System.currentTimeMillis();
        endTime = 0;
    }

    public void startCout() {
        startTime = System.currentTimeMillis();
    }

    public void stopCount() {
        endTime = System.currentTimeMillis();
        //elapsedTime = (endTime - startTime)/1000;
    }

    public long getElapsedTimeSec() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    public String getElapsedTimeHuman() {
        String time = "";
        elapsedTime = (System.currentTimeMillis() - startTime) / 1000;
        if (elapsedTime > 86400) {
            int days = (int) elapsedTime / 86400;
            elapsedTime = elapsedTime % 86400;
            time = days + " days, ";
        }

        if (elapsedTime > 3600) {
            int hours = (int) elapsedTime / 3600;
            elapsedTime = elapsedTime % 3600;
            time += hours + " hours, ";
        }

        if (elapsedTime > 60) {
            int minutes = (int) elapsedTime / 60;
            elapsedTime = elapsedTime % 60;
            time += minutes + " min, ";
        }

        time += elapsedTime + " sec";

        return time;
    }
}
