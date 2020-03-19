package com.asamm.osmTools.utils;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.*;

/**
 * Created by menion on 19. 7. 2014.
 * Class is part of Locus project
 */
public class Logger {

    private static final java.util.logging.Logger LOG = create();

    static {
        LOG.setUseParentHandlers(false);

        // set custom format
        MyFormatter formatter = new MyFormatter();
        ConsoleHandler handler = new ConsoleHandler();
        handler.setFormatter(formatter);
        LOG.addHandler(handler);
    }

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
        e.printStackTrace();
    }


    // CREATING LOGGER

    private static FileHandler mFh;

    public static java.util.logging.Logger create() {

        try {
            java.util.logging.Logger logger = java.util.logging.Logger.getLogger("log");
            createLogDir();
            return logger;
        } catch(Exception e) {
            e.printStackTrace();
            throw new IllegalStateException("Can not create logger");
        }
    }

    public static void createLogDir() {
        File logDirF = new File(Consts.DIR_LOGS);
        if (!logDirF.exists()) {
            logDirF.mkdirs();
        }
    }

    private static class MyFormatter extends Formatter {

        // create a DateFormat to format the logger timestamp.
        private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy hh:mm:ss.SSS");

        public String format(LogRecord record) {
            StringBuilder builder = new StringBuilder(1000);
            builder.append(df.format(new Date(record.getMillis()))).append(" | ");
            builder.append(record.getLevel()).append(" - ");
            builder.append("[").append(record.getSourceClassName());
            if (record.getSourceMethodName().length() > 0) {
                builder.append(".").append(record.getSourceMethodName());
            }
            builder.append("] - ");
            builder.append(formatMessage(record));
            builder.append("\n");
            return builder.toString();
        }

        public String getHead(Handler h) {
            return super.getHead(h);
        }

        public String getTail(Handler h) {
            return super.getTail(h);
        }
    }
}
