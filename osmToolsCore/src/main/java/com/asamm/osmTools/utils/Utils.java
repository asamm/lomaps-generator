/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.utils;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.*;
import java.net.UnknownHostException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.*;

/**
 * @author volda
 */
public class Utils {

    private static final String TAG = Utils.class.getSimpleName();

    public static boolean isLocalDEV() {
        String env = System.getenv("ENV");
        if (env == null) {
            return false;
        }
        return System.getenv("ENV").equals("DEV");
    }

    public static String changeSlash(String name) {
        if (name.contains("/")) {
            return name.replace("/", Consts.FILE_SEP);
        }
        if (name.contains("\\")) {
            return name.replace("\\", Consts.FILE_SEP);
        }
        return name;

    }

    /**
     * Returns the size of {@code path} as a file, or 0 if missing/inaccessible.
     * @param path the file to get the size
     */
    public static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException e) {
            return 0;
        }
    }

    public static String changeSlashToUnix(String name) {
        if (name.contains(Consts.FILE_SEP)) {
            return name.replace(Consts.FILE_SEP, "/");
        }
        return name;
    }

    public static void deleteFilesInDir(String pathToDir) {
        deleteFilesInDir(Path.of(pathToDir));
    }

    public static void deleteFilesInDir(Path pathToDir) {
        // check if folder exist
        File dir = pathToDir.toFile();
        if (!dir.exists() && !dir.isDirectory()) {
            System.out.println("Path for deleting: " + pathToDir + " does not exist or is not directory");
            return;
        }
        File[] fileList = dir.listFiles();
        for (int i = 0; i < fileList.length; i++) {
            if (fileList[i].isFile()) {
                fileList[i].delete();
            }
        }
    }


    /**
     * Delete file quietly without throwing exception
     *
     * @param path path to file to delete
     */
    public static void deleteFileQuietly(Path path) {
        try {
            if (path.toFile().exists()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            Logger.w(TAG, "deleteFile(), Unable to delete file", e);
        }
    }

    public static void copyFile(Path source, Path target, boolean replaceExisting) {


        try {
            // create parent directories if not exists
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }

            if (replaceExisting) {
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.copy(source, target);
            }
        } catch (IOException e) {
            Logger.e(TAG, "Error copying file: " + source + " to file: " + target + " Error: " + e.getMessage());
            throw new IllegalArgumentException("Error copying file:  " + e.getMessage());
        }
    }

    /**
     * Rename file quietly without throwing exception.
     *
     * @param source          file to rename
     * @param target          new name of file
     * @param replaceExisting true if replace existing file
     */
    public static void renameFileQuitly(Path source, Path target, boolean replaceExisting) {
        try {
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
        } catch (IOException e) {
            Logger.e(TAG, "Error renaming file: " +source + " to file: "+target+" Error: "+ e.getMessage());
            throw new IllegalArgumentException("Error renaming file:  "+ e.getMessage());
        }
    }

    /**
     * Method change file extension. If file has no extension, new extension is added.
     * The original path is not changed only extension is changed
     *
     * @param pathToFile   path to file to change extension
     * @param newExtension new extension to set. Define extenstion also with dot (e.g. ".txt")
     * @return
     */
    public static Path changeFileExtension(Path pathToFile, String newExtension) {

        String fileName = pathToFile.getFileName().toString();
        int lastDotIndex = fileName.lastIndexOf(".");
        if (lastDotIndex != -1) {
            // delete file extension
            fileName = fileName.substring(0, lastDotIndex);
        }
        return pathToFile.getParent().resolve(fileName + newExtension);
    }

    public static String generateMD5hash(String pathToFile) {

        File file = new File(pathToFile);
        FileInputStream fis = null;

        try {
            fis = new FileInputStream(file);
            MessageDigest md = MessageDigest.getInstance("MD5");

            byte[] dataBytes = new byte[1024];

            int nread = 0;
            while ((nread = fis.read(dataBytes)) != -1) {
                md.update(dataBytes, 0, nread);
            }
            ;
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

    /**
     * Method unzip file to target directory
     *
     * @param zipFile         file to unzip
     * @param targetDirectory directory to unzip file
     * @throws IOException
     */
    public static void unzipFile(Path zipFile, Path targetDirectory) {
        File targetDir = targetDirectory.toFile();

        // Ensure the target directory exists
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(targetDir, entry.getName());

                // Prevent Zip Slip vulnerability
                String canonicalPath = newFile.getCanonicalPath();
                if (!canonicalPath.startsWith(targetDir.getCanonicalPath())) {
                    throw new IOException("Entry is outside of the target directory: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    // Ensure parent directories exist
                    new File(newFile.getParent()).mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        byte[] buffer = new byte[1024];
                        int length;
                        while ((length = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, length);
                        }
                    }
                }
                zis.closeEntry();
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Error occurred while unzipping file: " + e.getMessage());
        }
    }

    public static void compressFile(String source, String target) throws IOException {
        List<File> files = Arrays.asList(new File(source));
        compressFiles(files, new File(target));
    }


    public static void compressFiles(List<File> files, File target) throws IOException {
        ZipOutputStream zos = null;
        try {
            File parentFolder = target.getParentFile();
            if (parentFolder != null) {
                FileUtils.forceMkdir(target.getParentFile());
            }
            zos = new ZipOutputStream(new FileOutputStream(target));
            zos.setLevel(9);

            // insert entry
            for (File file : files){

                // check file
                if (!file.exists()) {
                    throw new IllegalArgumentException(
                            "Fie '" + file + "' for compress do not exists");
                }

                // write data

                ZipEntry entry = new ZipEntry(file.getName());
                // set the same last change value for entry as source file
                entry.setTime(file.lastModified());
                zos.putNextEntry(entry);
                FileUtils.copyFile(file, zos);
                zos.closeEntry();
            }

            // end packing
            zos.flush();
            IOUtils.closeQuietly(zos);
        } finally {
            IOUtils.closeQuietly(zos);
        }
    }

    public static String formatBytesToHuman(double bytes) {
        String[] units = {"B", "KB", "MB", "GB", "TB"};
        // if is lower then zero
        bytes = Math.max(bytes, 0);
        int count = 0;
        while (bytes >= 1024) {
            bytes = bytes / 1024;
            count++;
        }
        //bytes = Math.roun
        return String.format("%.2f", bytes) + " " + units[count];
    }

    public static boolean isNumeric(String str) {
        try {
            double d = Double.parseDouble(str);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public static boolean createEmptyFile(String path) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                FileUtils.forceMkdir(file);
                return file.createNewFile();
            } catch (IOException ioe) {
                throw new IllegalArgumentException("Error while creating a new empty file :" + ioe);
            }

        }
        return false;
    }

    public static boolean createParentDirs(Path path) {
        return createParentDirs(path.toString());
    }

    public static boolean createParentDirs(String path) {
        File file = new File(path);
        if (!file.exists()) {
            try {
                FileUtils.forceMkdir(file.getParentFile());
            } catch (IOException ioe) {
                throw new IllegalArgumentException("Error while creating directory structure :" + ioe);
            }
        }
        return false;
    }

    /**
     * Get file name without an extension
     *
     * @param path path to file to get name without an extension
     * @return name of the file without extension or empty string if the file has no extension
     */
    public static String getFileNamePart(Path path) {
        String fileName = path.getFileName().toString();
        int lastDotIndex = fileName.indexOf(".");
        if (lastDotIndex != -1) {
            // delete file extension
            return fileName.substring(0, lastDotIndex);
        }
        return "";
    }

    /**
     * Append custom string before an extension of file
     *
     * @param filePath path to file to rename
     * @param text     custom string to append before an extension
     * @return new path to file with appended text before an extension
     */
    public static Path appendBeforeExtension(Path filePath, String text) {
        String fileName = filePath.getFileName().toString();
        int dotIndex = fileName.indexOf('.');
        if (dotIndex == -1) {
            // if there is no extension append text to the end
            return filePath.getParent().resolve(fileName + text);
        }
        String newFileName = fileName.substring(0, dotIndex) + text + fileName.substring(dotIndex);
        return filePath.getParent().resolve(newFileName);
    }

    /**
     * Move file from source to target path
     *
     * @param source          path to source file
     * @param target          path to target file
     * @param replaceExisting true if replace existing file
     * @return true if file was moved successfully
     */
    public static boolean moveFile(Path source, Path target, boolean replaceExisting) {
        try {
            if (replaceExisting) {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.move(source, target);
            }
            return true;
        } catch (IOException e) {
            Logger.e(TAG, ("Error moving file: " + e.getMessage()));
            return false;
        }
    }


    public static boolean isSystemWindows() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("win");
    }

    public static boolean isSystemUnix() {
        String os = System.getProperty("os.name").toLowerCase();
        return os.contains("nix") || os.contains("nux");
    }

    public static String getHostname() throws UnknownHostException {
        String hostname = null;
        // try to guess value from operating system variable
        if (isSystemUnix()) {
            hostname = System.getenv("hostname");
        }
        if (isSystemWindows()) {
            hostname = System.getenv("computername");
        }
        if (hostname != null) {
            return hostname;
        }
        java.net.InetAddress addr = java.net.InetAddress.getLocalHost();
        return addr.getHostName();
    }

    public static long getZipEntrySize(File file) throws ZipException, IOException {
        ZipFile zipFile = new ZipFile(file);
        Enumeration e = zipFile.entries();

        long originalSize = 0;
        while (e.hasMoreElements()) {
            ZipEntry entry = (ZipEntry) e.nextElement();
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

    /**
     * Write string into file
     *
     * @param file   file to write text into
     * @param text   text to write
     * @param append true if append text in the end
     */
    public static void writeStringToFile(File file, String text, boolean append) {

        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file, append));
            writer.write(text);
        } catch (IOException e) {
            System.err.println("writeStringToFile(), e:" + e);
            e.printStackTrace();
        } finally {
            try {
                if (writer != null)
                    writer.close();
            } catch (IOException e) {
                System.err.println("writeStringToFile(), e:" + e);
                e.printStackTrace();
            }
        }
    }

    /**
     * Read file and get content as String. Be sure what you do because the memmory
     *
     * @param path     path to file to read its content
     * @param encoding encoding of string in file
     * @return *
     */
    public static String readFileToString(String path, Charset encoding) {
        byte[] encoded = new byte[0];
        try {
            encoded = Files.readAllBytes(Paths.get(path));
        } catch (IOException e) {
            System.err.println("readFileToString(), e:" + e);
            e.printStackTrace();
        }
        return new String(encoded, encoding);
    }

    public static String getEnvVariable(String name) {
        String value = System.getenv(name);
        if (value == null) {
            throw new IllegalArgumentException("Environment variable " + name + " is not set.");
        }
        return value;
    }
}
