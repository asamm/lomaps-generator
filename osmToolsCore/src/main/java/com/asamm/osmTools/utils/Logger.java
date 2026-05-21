package com.asamm.osmTools.utils;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.logging.*;

public class Logger {

    private static final java.util.logging.Logger LOG;
    private static final ConsoleHandler consoleHandler;
    private static volatile boolean configured = false;

    static {
        LOG = java.util.logging.Logger.getLogger("osmtools");
        LOG.setUseParentHandlers(false);
        LOG.setLevel(Level.INFO);

        consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(new MyFormatter());
        consoleHandler.setLevel(Level.ALL); // logger level is the gate, not the handler
        LOG.addHandler(consoleHandler);
    }

    public static void i(String TAG, String msg) {
        LOG.logp(Level.INFO, TAG, "", msg);
    }

    public static void d(String TAG, String msg) {
        LOG.logp(Level.CONFIG, TAG, "", msg);
    }

    public static void w(String TAG, String msg) {
        LOG.logp(Level.WARNING, TAG, "", msg);
    }

    public static void w(String TAG, String msg, Throwable e) {
        LOG.logp(Level.WARNING, TAG, "", msg, e);
    }

    public static void e(String TAG, String msg) {
        LOG.logp(Level.SEVERE, TAG, "", msg);
    }

    public static void e(String TAG, String msg, Throwable e) {
        LOG.logp(Level.SEVERE, TAG, "", msg, e);
        e.printStackTrace();
    }

    /**
     * Wire up file-based logging. Called once after AppConfig is loaded.
     *
     * Each run writes to {@code <logDir>/latest.log}. On startup any existing
     * {@code latest.log} is archived as {@code osmtools_<datetime>.log} (using
     * the file's last-modified time as the timestamp). Old archived files beyond
     * {@code maxFiles} are deleted, oldest first.
     *
     * @param logDir   directory to write log files into
     * @param maxFiles number of archived run logs to keep (not counting latest.log)
     * @param verbose  when true, DEBUG (CONFIG-level) messages are emitted
     */
    public static synchronized void configure(String logDir, int maxFiles, boolean verbose) {
        if (configured) return;
        configured = true;

        LOG.setLevel(verbose ? Level.ALL : Level.INFO);

        try {
            File dir = new File(logDir);
            if (!dir.exists()) dir.mkdirs();

            archiveLatest(dir);
            pruneOldLogs(dir, maxFiles);

            File latestLog = new File(dir, "latest.log");
            FileHandler fileHandler = new FileHandler(latestLog.getPath(), false);
            fileHandler.setFormatter(new MyFormatter());
            fileHandler.setLevel(Level.ALL);
            LOG.addHandler(fileHandler);

        } catch (IOException ex) {
            LOG.log(Level.WARNING, "Could not configure file logging in '" + logDir + "': " + ex.getMessage());
        }
    }

    /** Rename latest.log to osmtools_<last-modified-datetime>.log so this run gets a fresh file. */
    private static void archiveLatest(File dir) {
        File latest = new File(dir, "latest.log");
        if (!latest.exists()) return;

        String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date(latest.lastModified()));
        File archived = new File(dir, "osmtools_" + timestamp + ".log");
        // Avoid clobbering if two runs finish within the same second
        if (archived.exists()) {
            archived = new File(dir, "osmtools_" + timestamp + "_1.log");
        }
        latest.renameTo(archived);
    }

    /** Delete the oldest osmtools_*.log archives, keeping at most {@code keep} files. */
    private static void pruneOldLogs(File dir, int keep) {
        File[] archives = dir.listFiles(f -> f.getName().startsWith("osmtools_") && f.getName().endsWith(".log"));
        if (archives == null || archives.length <= keep) return;

        Arrays.sort(archives, Comparator.comparingLong(File::lastModified));
        for (int i = 0; i < archives.length - keep; i++) {
            archives[i].delete();
        }
    }

    /** Returns the underlying JUL logger (used by Main.LOG and similar holders). */
    public static java.util.logging.Logger create() {
        return LOG;
    }

    private static class MyFormatter extends Formatter {

        private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss.SSS");

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder(256);
            sb.append(df.format(new Date(record.getMillis()))).append(" | ");
            sb.append(record.getLevel()).append(" - ");
            sb.append("[").append(record.getSourceClassName());
            if (!record.getSourceMethodName().isEmpty()) {
                sb.append(".").append(record.getSourceMethodName());
            }
            sb.append("] - ");
            sb.append(formatMessage(record));
            sb.append("\n");
            return sb.toString();
        }
    }
}