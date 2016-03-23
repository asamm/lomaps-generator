package com.asamm.osmTools.generatorDb.plugin;

import com.asamm.osmTools.generatorDb.utils.Utils;

import java.io.File;

public abstract class AConfiguration {

    public enum GenerateType {
		ADDRESS, COUNTRY_BOUNDARY, POI;

        public static GenerateType createFromValue(String type) {
            if (type == null) {
                return null;
            }

            if (type == null || type.length() == 0) {
                throw new IllegalArgumentException(
                        "type parameter is empty");
            }

            // set type
            if (type.equals("address")) {
                return  GenerateType.ADDRESS;
            } else if (type.equals("poi")) {
                return GenerateType.POI;
            }
            else if (type.equals("country")){
                return GenerateType.COUNTRY_BOUNDARY;
            }
            else {
                throw new IllegalArgumentException(
                        "type parameter '" + type + "' incorrect. Supported types are 'address' or 'poi'");
            }
        }
	}

    public enum DataContainerType {
        HDD, RAM
    }
	
	// generate type
	protected GenerateType genType;

    // data container type (HDD/RAM)
    protected DataContainerType dataContainerType;


    // ABSTRACT METHODS

    public abstract void validate() ;

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



    /**
     * Check if file on specified path exist and create file object if exists
     * @param file path to file to create
     * @return file if path is valid
     */
    protected static File checkFile(String file) {
        if (file == null || file.length() == 0) {
            throw new IllegalArgumentException(
                    "file parameter \'" + file + "\' invalid");
        }

        // create and check file
        File f = new File(file);
        if (f.isDirectory()) {
            throw new IllegalArgumentException(
                    "file parameter points to a directory, must be a file, param:" + file);
        } else if (f.exists() && !f.canWrite()) {
            throw new IllegalArgumentException(
                    "file parameter points to a file we have no write permissions, param: " + file);
        }
        return f;
    }

}
