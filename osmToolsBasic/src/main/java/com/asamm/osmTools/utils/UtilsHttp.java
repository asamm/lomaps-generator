/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.utils;

import com.asamm.osmTools.Main;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLConnection;

/**
 *
 * @author volda
 */
public class UtilsHttp {

    private static final String TAG = UtilsHttp.class.getSimpleName();

    public static boolean downloadFile(String pathLocal, String pathUrl) {
        Logger.i(TAG, "downloadFile(" + pathLocal + ", " + pathUrl + ")");

        try {
            // create connection
            URL url = new URL(pathUrl);

            // if URL start with HTTP use HttpUrlConnection
            if (pathUrl.startsWith("http")) {
                HttpURLConnection httpConn = (HttpURLConnection) url.openConnection();
                httpConn.setRequestMethod("GET");
                httpConn.connect();

                if (httpConn.getResponseCode() == 200) {
                    return loadUrlData(pathLocal, httpConn);
                } else {
                    Logger.e(TAG, "downloadFile(), conn:" + httpConn.getResponseCode());
                }
            }
        } catch (Exception e) {
            Logger.e(TAG, "", e);
        }
        return false;
    }

    private static boolean loadUrlData(String pathLocal, URLConnection conn) {
        InputStream is = null;
        FileOutputStream fos = null;

        byte[] buffer = new byte[10 * 1024 * 1024];
        int lastNotified = 0;
        try {
            TimeWatch time = new TimeWatch();
            Main.mySimpleLog.print("\nDownloading: "+conn.getURL()+" ...");

            // create output file
            File file = new File(pathLocal + "T");
            if (file.exists()) {
                file.delete();
            } else {
                file.getParentFile().mkdirs();
            }

            // create output stream
            fos = new FileOutputStream(file);

            // start downloading 
            is = conn.getInputStream();
            int read = 0;
            long total = 0;
            while ((read = is.read(buffer)) != -1) {
                total += read;
                fos.write(buffer, 0, read);

                // small notification
                int sizeInMb = (int) (total / 1024.0 / 1024.0);
                if (sizeInMb % 50 == 0 && sizeInMb > lastNotified) {
                    if (lastNotified != 0) {
                        System.out.print(" | ");
                    } else {
                        System.out.print("  done:");
                    }
                    lastNotified = sizeInMb;
                    System.out.print(sizeInMb + " MB");
                }
            }

            // finally end line
            System.out.println("");

            // close and finish streams
            fos.flush();
            fos.close();
            fos = null;

            // print results
            Logger.i(TAG, "  loadUrlData(), done, read:" + (total / 1024 / 1024) + "MB");
            Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
            // file downloaded correctly, set as final 
            return file.renameTo(new File(pathLocal));
        } catch (Exception e) {
            Logger.e(TAG, "loadUrlData()", e);
        } finally {
            try {
                if (is != null) {
                    is.close();
                }
                if (fos != null) {
                    fos.close();
                }
            } catch (Exception e) {
                Logger.e(TAG, "loadUrlData()", e);
            }
        }
        return false;
    }
}
