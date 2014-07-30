package com.asamm.osmTools.utils.io;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Created by menion on 26/07/14.
 */
public class ZipUtils {

    /**
     * Default compression level
     */
    private static final int DEFAULT_COMPRESSION_LEVEL = Deflater.BEST_COMPRESSION;
    /**
     * Unix path separator
     */
    private static final String PATH_SEPARATOR = "/";

    /**************************************************/
	/*                 COMPRESS PART                  */
    /**************************************************/

    /**
     * Compresses the given directory and all its sub-directories into a ZIP
     * file.
     * <p>
     * The ZIP file must not be a directory and its parent directory must exist.
     * Will not include the root directory name in the archive.
     *
     * @param sourceDir
     *            root directory.
     * @param targetZipFile
     *            ZIP file that will be created or overwritten.
     */
    public static void pack(final File sourceDir, final File targetZipFile,
                            final boolean preserveRoot) {
        if (preserveRoot) {
            final String parentName = sourceDir.getName();
            pack(sourceDir, targetZipFile, new NameMapper() {

                @Override
                public String map(String name) {
                    return parentName + PATH_SEPARATOR + name;
                }
            });
        } else
            pack(sourceDir, targetZipFile);
    }

    /**
     * Compresses the given directory and all its sub-directories into a ZIP
     * file.
     * <p>
     * The ZIP file must not be a directory and its parent directory must exist.
     * Will not include the root directory name in the archive.
     *
     * @param rootDir
     *            root directory.
     * @param zip
     *            ZIP file that will be created or overwritten.
     */
    public static void pack(File rootDir, File zip) {
        pack(rootDir, zip, DEFAULT_COMPRESSION_LEVEL);
    }

    /**
     * Compresses the given directory and all its sub-directories into a ZIP
     * file.
     * <p>
     * The ZIP file must not be a directory and its parent directory must exist.
     * Will not include the root directory name in the archive.
     *
     * @param rootDir
     *            root directory.
     * @param zip
     *            ZIP file that will be created or overwritten.
     * @param compressionLevel
     *            compression level
     */
    public static void pack(File rootDir, File zip, int compressionLevel) {
        pack(rootDir, zip, IdentityNameMapper.INSTANCE, compressionLevel);
    }

    /**
     * Compresses the given directory and all its sub-directories into a ZIP
     * file.
     * <p>
     * The ZIP file must not be a directory and its parent directory must exist.
     *
     * @param sourceDir
     *            root directory.
     * @param targetZip
     *            ZIP file that will be created or overwritten.
     */
    public static void pack(File sourceDir, File targetZip, NameMapper mapper) {
        pack(sourceDir, targetZip, mapper, DEFAULT_COMPRESSION_LEVEL);
    }

    /**
     * Compresses the given directory and all its sub-directories into a ZIP
     * file.
     * <p>
     * The ZIP file must not be a directory and its parent directory must exist.
     *
     * @param sourceDir
     *            root directory.
     * @param targetZip
     *            ZIP file that will be created or overwritten.
     * @param compressionLevel
     *            compression level
     */
    public static void pack(File sourceDir, File targetZip, NameMapper mapper,
                            int compressionLevel) {
//		Logger.d(TAG, "Compressing '{}' into '{}'.", sourceDir, targetZip);

        File[] listFiles = sourceDir.listFiles();
        if (listFiles == null) {
            if (!sourceDir.exists()) {
                throw new ZipException("Given file '" + sourceDir
                        + "' doesn't exist!");
            }
            throw new ZipException("Given file '" + sourceDir
                    + "' is not a directory!");
        } else if (listFiles.length == 0) {
            throw new ZipException("Given directory '" + sourceDir
                    + "' doesn't contain any files!");
        }
        ZipOutputStream out = null;
        try {
            FileUtils.forceMkdir(targetZip.getParentFile());
            out = new ZipOutputStream(new BufferedOutputStream(
                    new FileOutputStream(targetZip)));
            out.setLevel(compressionLevel);
            pack(sourceDir, out, mapper, "");
        } catch (IOException e) {
            throw rethrow(e);
        } finally {
            IOUtils.closeQuietly(out);
        }
    }

    /**
     * Compresses the given directory and all its sub-directories into a ZIP
     * file.
     *
     * @param dir
     *            root directory.
     * @param out
     *            ZIP output stream.
     * @param mapper
     *            call-back for renaming the entries.
     * @param pathPrefix
     *            prefix to be used for the entries.
     */
    private static void pack(File dir, ZipOutputStream out, NameMapper mapper,
                             String pathPrefix) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            throw new IOException("Given file is not a directory '" + dir + "'");
        }

        for (int i = 0; i < files.length; i++) {
            File file = files[i];
            boolean isDir = file.isDirectory();
            String path = pathPrefix + file.getName();
            if (isDir) {
                path += PATH_SEPARATOR;
            }

            // Create a ZIP entry
            String name = mapper.map(path);
            if (name != null) {
                ZipEntry zipEntry = new ZipEntry(name);
                if (!isDir) {
                    zipEntry.setSize(file.length());
                    zipEntry.setTime(file.lastModified());
                }

                out.putNextEntry(zipEntry);

                // Copy the file content
                if (!isDir) {
                    FileUtils.copyFile(file, out);
                }

                out.closeEntry();
            }

            // Traverse the directory
            if (isDir) {
                pack(file, out, mapper, path);
            }
        }
    }

    /**************************************************/
	/*                     TOOLS                      */
    /**************************************************/

    /**
     * Rethrow the given exception as a runtime exception.
     */
    private static ZipException rethrow(IOException e) {
        throw new ZipException(e);
    }

    public static class ZipException extends RuntimeException {

        private static final long serialVersionUID = -1774394461581674030L;

        public ZipException(String msg) {
            super(msg);
        }

        public ZipException(Exception e) {
            super(e);
        }
    }
}
