package com.asamm.osmTools.utils;

import java.io.File;
import java.util.logging.FileHandler;
import java.util.logging.Level;

/**
 * Created by menion on 19. 7. 2014.
 * Class is part of Locus project
 */
public class Logger {

    private static final java.util.logging.Logger LOG = create();

    /**
     * Log an INFO message.
     * @param   msg     The string message (or a key in the message catalog)
     */
    public static void i(String TAG, String msg) {
        LOG.logp(Level.INFO, TAG, "", msg);
    }

    /**
     * Log a DEBUG message.
     * @param   msg     The string message (or a key in the message catalog)
     */
    public static void d(String TAG, String msg) {
        LOG.logp(Level.CONFIG, TAG, "", msg);
    }

    /**
     * Log a WARNING message.
     * @param   msg     The string message (or a key in the message catalog)
     */
    public static void w(String TAG, String msg) {
        LOG.logp(Level.WARNING, TAG, "", msg);
    }

    /**
     * Log a WARNING message.
     * @param   msg     The string message (or a key in the message catalog)
     */
    public static void w(String TAG, String msg, Throwable e) {
        LOG.logp(Level.WARNING, TAG, "", msg, e);
    }

    /**
     * Log a ERROR message.
     * @param   msg     The string message (or a key in the message catalog)
     */
    public static void e(String TAG, String msg) {
        LOG.logp(Level.SEVERE, TAG, "", msg);
    }

    /**
     * Log a ERROR message.
     * @param   msg     The string message (or a key in the message catalog)
     */
    public static void e(String TAG, String msg, Throwable e) {
        LOG.logp(Level.SEVERE, TAG, "", msg, e);
    }


    // CREATING LOGGER

    private static FileHandler mFh;

    public static java.util.logging.Logger create() {

        try {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger("log");
            createLogDir();
//            mFh = new FileHandler(Parameters.DIR_LOGS + Parameters.FILE_SEP + "world2vec.log");
//            mFh.setFormatter(new SimpleFormatter());
//            logger.addHandler(mFh);
            return logger;
        } catch(Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static void createLogDir() {
        File logDirF = new File(Consts.DIR_LOGS);
        if (!logDirF.exists()) {
            logDirF.mkdirs();
        }
    }
}
