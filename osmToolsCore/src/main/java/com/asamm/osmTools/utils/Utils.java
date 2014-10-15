/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.UnknownHostException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 *
 * @author volda
 */
public class Utils {

    private static final String TAG = Utils.class.getSimpleName();

    public static String changeSlash(String name){
        if (name.contains("/")){
            return name.replace("/", Consts.FILE_SEP);
        }
        if (name.contains("\\")){
            return name.replace("\\", Consts.FILE_SEP);
        }
        return name;

    }
    public static String changeSlashToUnix (String name){
        if (name.contains(Consts.FILE_SEP)){
            return name.replace(Consts.FILE_SEP, "/");
        }
        return name;
    }

    public static void deleteFilesInDir(String pathToDir) {
        // check if folder exist
        File dir =  new File(pathToDir);
        if (!dir.exists() && !dir.isDirectory()){
            System.out.println("Path for deleting: "+ pathToDir+" does not exist or is not directory");
            return;
        }
        File[] fileList = dir.listFiles();
        for (int i=0; i < fileList.length ; i++ ){
            if (fileList[i].isFile()){
                fileList[i].delete();
            }
        }
    }

    public static String generateMD5hash(String pathToFile){

        File file = new File(pathToFile);
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(file);
            MessageDigest md = MessageDigest.getInstance("MD5");

            byte[] dataBytes = new byte[1024];

            int nread = 0;
            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            };
            byte[] mdbytes = md.digest();

            //convert the byte to hex format method 1
            StringBuffer sb = new StringBuffer();
            for (int i = 0; i < mdbytes.length; i++) {
                sb.append(Integer.toString((mdbytes[i] & 0xff) + 0x100, 16).substring(1));
            }

//            //convert the byte to hex format method 2
//            StringBuffer hexString = new StringBuffer(); 
//            for (int i=0;i<mdbytes.length;i++) {
//                    String hex=Integer.toHexString(0xff & mdbytes[i]);
//                    if(hex.length()==1) hexString.append('0');
//                    hexString.append(hex);
//            }
//            System.out.println("Digest(in hex format):: " + hexString.toString());

            return sb.toString();

        } catch (IOException e) {
            Logger.w(TAG, "generateMD5hash()", e);
            throw new RuntimeException("Unable to generate MD5hash for file: " +
                    file.getAbsolutePath(), e);
        } catch (NoSuchAlgorithmException e) {
            Logger.w(TAG, "generateMD5hash()", e);
            throw new RuntimeException("No such algorythm Unable to generate MD5hash for file: " +
                    file.getAbsolutePath(), e);
        } finally {
            IOUtils.closeQuietly(fis);
        }
    }



    public static void compressFile(String source, String target) throws IOException {
        List<String> files = new ArrayList<String>();
        files.add(source);
        compressFiles(files, target);
    }


    public static void compressFiles(List<String> files, String target) throws IOException {
        ZipOutputStream zos = null;
        try {
            FileUtils.forceMkdir(new File(target).getParentFile());
            zos = new ZipOutputStream(new FileOutputStream(target));
            zos.setLevel(9);

            // insert entry
            for (int i = 0, m = files.size(); i < m; i++) {
                File fileToCompress = new File(files.get(i));

                // check file
                if (!fileToCompress.exists()){
                    throw new IllegalArgumentException(
                            "Fie '" + fileToCompress + "' for compress do not exists");
                }

                // write data

                ZipEntry entry = new ZipEntry(fileToCompress.getName());
                // set the same last change value for entry as source file
                entry.setTime(fileToCompress.lastModified());
                zos.putNextEntry(entry);
                FileUtils.copyFile(fileToCompress, zos);
                zos.closeEntry();
            }

            // end packing
            zos.flush();
            IOUtils.closeQuietly(zos);
        } finally{
            IOUtils.closeQuietly(zos);
        }
    }

    public static String formatBytesToHuman(double bytes){
        String[] units = {"B","KB","MB", "GB", "TB"};
        // if is lower then zero
        bytes = Math.max(bytes, 0);
        int count = 0;
        while (bytes >= 1024){
            bytes = bytes / 1024;
            count ++;
        }
        //bytes = Math.roun
        return String.format("%.2f", bytes) + " " + units[count];
    }

    public static boolean isNumeric(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch(NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public static boolean createEmptyFile(String path){
        File file =  new File(path);
        if (!file.exists()){
            try {
                FileUtils.forceMkdir(file);
                return file.createNewFile();
            } catch (IOException ioe){
                throw new IllegalArgumentException("Error while creating a new empty file :" + ioe);
            }

        }
        return false;
    }

    public static boolean isSystemWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    public static boolean  isSystemUnix() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux");
    }

    public static String getHostname() throws UnknownHostException{
        String hostname = null;
        // try to guess value from operating system variable
        if (isSystemUnix()){
            hostname = System.getenv( "hostname" );
        }
        if (isSystemWindows()){
            hostname = System.getenv( "computername" );
        }
        if (hostname != null){
            return hostname;
        }
        java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
        return addr.getHostName();
    }

    public static long getZipEntrySize (File file) throws ZipException, IOException {
        ZipFile zipFile = new ZipFile(file);
        Enumeration e = zipFile.entries();

        long originalSize = 0;
        while (e.hasMoreElements()){
            ZipEntry entry = (ZipEntry)e.nextElement();
            originalSize += entry.getSize();
        }

        return originalSize;

    }

    public static String getEncoding(String data) {
        // <?xml version="1.0" encoding="ISO-8859-1" standalone="yes"?>
        // <?xml version="1.0" encoding="UTF-8"?>
        Pattern pat = Pattern.compile("<?xml version\\S+ encoding=\"(\\S+)\"");
        Matcher mat = pat.matcher(data);
        if (mat.find()) {
            MatchResult mr = mat.toMatchResult();
            return mr.group(1);
        } else {
            return "UTF-8";
        }
    }
}
