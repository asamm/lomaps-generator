/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools;

import com.asamm.osmTools.generator.GenLoMaps;
import com.asamm.osmTools.generator.GenStoreRegionDB;
import com.asamm.osmTools.utils.*;

import java.util.logging.Handler;
import java.util.logging.Logger;

/**
 * @author volda
 */
public class Main {

    private static final String TAG = Main.class.getSimpleName();

    public static final Logger LOG = com.asamm.osmTools.utils.Logger.create();
    public static final MyLogger mySimpleLog
            = new MyLogger(Consts.DIR_LOGS + Consts.FILE_SEP + "osm2vec_simple.log");
    public static final MyLogger myRunTimeLog
            = new MyLogger(Consts.DIR_LOGS + Consts.FILE_SEP + "osm2vec_runTime.log");
//
//    /**
//     * @param args the command line arguments
//     */
//    public static void main(String[] args) {
//
//        //start timer
//        TimeWatch timer = new TimeWatch();
//
//        // set static variables and read cmd parameters
//        try {
//            // basic preparations
//            Parameters.parseArgs(args);
//            Parameters.initialize();
//
//            // handle all possible actions
//            if (Parameters.getGenType() == Parameters.GenType.LOMAPS) {
//                GenLoMaps genLoMaps = new GenLoMaps();
//                genLoMaps.process();
//            } else if (Parameters.getGenType() == Parameters.GenType.STORE_GEOCODING) {
//                GenStoreRegionDB genStoreGeo = new GenStoreRegionDB();
//                genStoreGeo.process();
//            }
//
//            // do some logging
//            LOG.info("\nDONE! Elepsed time: " + timer.getElapsedTimeSec() +
//                    " sec; i.e.: " + timer.getElapsedTimeHuman());
//            mySimpleLog.print("\n\nDONE! Elepsed time: " + timer.getElapsedTimeSec()
//                    + " sec; i.e.: " + timer.getElapsedTimeHuman());
//            mySimpleLog.closeWriter();
//            myRunTimeLog.closeWriter();
//
//            // send email (if requested)
//            if (Parameters.isMailing()) {
//                String zipRunTimeLog = "osm2vec_runTime.zip";
//                try {
//                    myRunTimeLog.closeWriter();
//                    // compress runTime log
//                    Utils.compressFile(myRunTimeLog.getPath(), zipRunTimeLog);
//                } finally {
//                    String emailTo = "petr.voldan@gmail.com";
//                    String subject = "com.asamm.osm2vec.osm2vec succesfully finished on " + Utils.getHostname();
//                    String text = ("\n\nDONE! Elepsed time: " + timer.getElapsedTimeSec()
//                            + " sec; i.e.: " + timer.getElapsedTimeHuman());
//                    MailHandler mh = new MailHandler();
//                    mh.sendEmail(emailTo, subject, text, zipRunTimeLog);
//                }
//            }
//        } catch (Exception e) {
//            com.asamm.osmTools.utils.Logger.e(TAG, "main()", e);
//            String zipRunTimeLog = Consts.DIR_BASE + "osm2vec_runTime.zip";
//            try {
//                Main.LOG.severe(e.toString());
//                myRunTimeLog.print(e.toString());
//                e.printStackTrace();
//
//                myRunTimeLog.closeWriter();
//                // compress runTime log
//                Utils.compressFile(myRunTimeLog.getPath(), zipRunTimeLog);
//
//                if (Parameters.isMailing()) {
//
//                    String emailTo = "petr.voldan@gmail.com";
//                    String subject = "OSM Tools ERROR on " + Utils.getHostname();
//                    String text = "Exception heppend when run: \n" + e.toString();
//                    MailHandler mh = new MailHandler();
//                    System.out.println("Sending email ...");
//                    mh.sendEmail(emailTo, subject, text, zipRunTimeLog);
//                }
//            } catch (Exception mailExcep) {
//                mailExcep.printStackTrace();
//            }
//        } finally {
//            mySimpleLog.closeWriter();
//            myRunTimeLog.closeWriter();
//            //close handlers for LOG logger
//            for (Handler h : LOG.getHandlers()) {
//                h.flush();
//                h.close();
//            }
//        }
//    }
}
