/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.utils;


import com.asamm.osmTools.Main;
import com.asamm.osmTools.mapConfig.ItemMap;

import java.io.File;

/**
 *
 * @author volda
 */
public class AmazonHandler {
    //private static final String awsBucket = "locus-test";
    private static final String awsBucket = "locus-vector-maps";
//    private AmazonS3 awsClient;
    
    
    // define singleton AmazonHandler
    private static AmazonHandler mInstance;
    public static AmazonHandler getInstance() {
        if (mInstance == null){
            mInstance = new AmazonHandler();
            
        }
        return mInstance;
    }
    
    //
    private AmazonHandler(){
//        awsClient = new AmazonS3Client(
//                new BasicAWSCredentials("AKIAI7ZL7NL2JBTKGJMQ",
//                    "PjIkRukWZ9THUJVtfGqXwbXx0ukKscA3owmoGDws"));
//
//
//        //check bucket
//        try {
//            if (!awsClient.doesBucketExist(awsBucket)) {
//                throw new IllegalArgumentException("uploadToAws: bucket "+ awsBucket +" does not exist!!");
//            }
//
//        } catch (AmazonServiceException ase){
//            Main.LOG.severe(ase.getMessage());
//
//        }
    }
    
    
    public boolean uploadToAws(ItemMap map, String relPath) {
//        try {
//            File file = new File(map.getPathResult());
//            //check if file exists
//            if (!file.exists()) {
//                Main.LOG.severe("uploadToAWS: Source file: ("+ file +") does not exist.");
//                return false;
//            }
//
//            // write to log and start stop watch
//            TimeWatch time = new TimeWatch();
//            Main.mySimpleLog.print("\nUploading: "+map.getName()+" ...");
//            Main.LOG.info("Uploading file: "+file.getAbsolutePath());
//
//            PutObjectRequest por = new PutObjectRequest(awsBucket, relPath, file);
//
//            awsClient.putObject(por);
//
//            Main.mySimpleLog.print("\t\t\tdone "+time.getElapsedTimeSec()+" sec");
//
//            return true;
//
//        } catch (AmazonServiceException ase) {
//            System.out.println("Caught an AmazonServiceException, which means your request made it " +
//                    "to Amazon S3, but was rejected with an error response for some reason.");
//            System.out.println("Error Message:    " + ase.getMessage());
//            System.out.println("HTTP Status Code: " + ase.getStatusCode());
//            System.out.println("AWS Error Code:   " + ase.getErrorCode());
//            System.out.println("Error Type:       " + ase.getErrorType());
//            System.out.println("Request ID:       " + ase.getRequestId());
//        } catch (AmazonClientException ace) {
//            System.out.println("Caught an AmazonClientException, which means the client encountered " +
//                    "an internal error while trying to " +
//                    "communicate with S3, " +
//                    "such as not being able to access the network.");
//            System.out.println("Error Message: " + ace.getMessage());
//        }
        return false;
    }
    
    
    public boolean isMapUploaded(ItemMap map, String relPath) {
//        try {
//
//            ObjectMetadata objMeta = awsClient.getObjectMetadata(new GetObjectMetadataRequest(awsBucket, relPath));
//
//            Main.LOG.info("Aws MEta data obtained");
//
//            if (!objMeta.getETag().equalsIgnoreCase(map.getResultMD5Hash())) {
//                // old object exist, but wrong size, delete and upload again
//                Main.LOG.warning("Object "+ relPath+ " has different md5hash then generated result map. AWS object will be deleted and upload again."
//                        +"\nLocal MD5: " + map.getResultMD5Hash()
//                        +"\nAWS object ETag: " + objMeta.getETag());
//                //awsClient.deleteObject(awsBucket, relPath);
//                return false;
//            }
//
//            // valid object already exist
            return true;
//
//         } catch (AmazonServiceException ase) {
//            if (ase.getStatusCode() == 404) {
//                // Exception on get metadata on non exist object, return false object does not exist
//                Main.LOG.info("Aws does not contain object: " + relPath);
//
//                return false;
//            }
//            else {
//                System.out.println("Caught an AmazonServiceException, which means your request made it " +
//                    "to Amazon S3, but was rejected with an error response for some reason.");
//                System.out.println("Error Message:    " + ase.getMessage());
//                System.out.println("HTTP Status Code: " + ase.getStatusCode());
//                System.out.println("AWS Error Code:   " + ase.getErrorCode());
//                System.out.println("Error Type:       " + ase.getErrorType());
//                System.out.println("Request ID:       " + ase.getRequestId());
//
//                throw new IllegalArgumentException ("Error occured when getting meta data for object "+ relPath);
//            }
//
//        } catch (AmazonClientException ace) {
//            System.out.println("Caught an AmazonClientException, which means the client encountered " +
//                    "an internal error while trying to " +
//                    "communicate with S3, " +
//                    "such as not being able to access the network.");
//            System.out.println("Error Message: " + ace.getMessage());
//
//            throw new IllegalArgumentException ("Error occured when getting meta data for object "+ relPath);
//        }
    }
}
