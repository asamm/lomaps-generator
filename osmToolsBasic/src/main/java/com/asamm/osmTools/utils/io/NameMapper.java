package com.asamm.osmTools.utils.io;

/**
 * Call-back for filtering and renaming ZIP entries while packing or unpacking.
 *
 * @author Rein Raudjärv
 *
 * @see ZipUtil
 */
public interface NameMapper {

    /**
     * @param name
     *            original name.
     * @return name to be stored in the ZIP file or the destination directory,
     *         <code>null</code> means that the entry will be skipped.
     */
    String map(String name);
}
