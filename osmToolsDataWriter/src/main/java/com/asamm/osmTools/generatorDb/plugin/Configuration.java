package com.asamm.osmTools.generatorDb.plugin;

import java.io.File;

public class Configuration {

    public enum GenerateType {
		ADDRESS, POI
	}

    public enum DataContainerType {
        HDD, RAM
    }
	
	// generate type
	private GenerateType genType;
	
	// source file
	private File fileDb;
    private File fileConfig;

	// testing part
	public boolean testing;
	public int reportFrom;
	public int reportCount;

    // data container type (HDD/RAM)
    private DataContainerType dataContainerType;

	// GENERATE TYPE
	
	/**
	 * Return type of data that will be generated
	 * @return
	 */
	public GenerateType getGenerateType() {
		return genType;
	}
	
	/**
	 * Set data type, that will be generated
	 * @param type
	 */
	public void setGenerateType(String type) {
        // check parameter
		if (type == null || type.length() == 0) {
			throw new IllegalArgumentException(
					"type parameter is empty");
		}
		
		// set type
		if (type.equals("address")) {
			this.genType = GenerateType.ADDRESS;
		} else if (type.equals("poi")) {
			this.genType = GenerateType.POI;
		} else {
			throw new IllegalArgumentException(
					"type parameter '" + type + "' incorrect. Supported types are 'address' or 'poi'");
		}
	}
	
	// DATABASE FILE SOURCE
	
	/**
	 * @return the outputFile
	 */
	public File getFileDatabase() {
		return this.fileDb;
	}

	/**
	 * Convenience method.
	 * 
	 * @param file
	 *            the path to the output file
	 */
	public void setFileDatabase(String file) {
		this.fileDb = checkFile(file);
	}


    public File getFileConfig() {
        return this.fileConfig;
    }

    public void setFileConfig(String file) {
        if (getGenerateType() == GenerateType.POI) {
            this.fileConfig = checkFile(file);
        }
    }


    public DataContainerType getDataContainerType() {
        return dataContainerType;
    }

    public void setDataContainerType(String dataContainerType) {
        // set type
        if (dataContainerType.equalsIgnoreCase("hdd")) {
            this.dataContainerType = DataContainerType.HDD;
        } else if (dataContainerType.equalsIgnoreCase("ram")) {
            this.dataContainerType = DataContainerType.RAM;
        } else {
            throw new IllegalArgumentException(
                    "dataContainerType parameter '" + dataContainerType + "' incorrect. Supported types are 'hdd' or 'ram'");
        }
    }


    private File checkFile(String file) {
		if (file == null || file.length() == 0) {
			throw new IllegalArgumentException(
					"file parameter \'" + file + "\' invalid");
		}

		// create and check file
		File f = new File(file);
		if (f.isDirectory()) {
			throw new IllegalArgumentException(
					"output file parameter points to a directory, must be a file");
		} else if (f.exists() && !f.canWrite()) {
			throw new IllegalArgumentException(
					"output file parameter points to a file we have no write permissions");
		}
		return f;
	}

	/**
	 * After all parameters are loaded and set, confirm loading by this method.
	 * It checks all parameters and throw exception in case, something
	 * important is missing.
	 */
	public void validate() {
		if (getFileDatabase() == null) {
			throw new IllegalArgumentException(
					"invalid parameters, missing database file");
		}
		
		if (getGenerateType() == GenerateType.POI) {
			if (getFileConfig() == null) {
				throw new IllegalArgumentException(
						"invalid parameters, missing config file");
			}	
		}
	}

}
