/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Main;
import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.mapConfig.ItemMap;
import org.apache.commons.io.FileUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author volda
 */
abstract class Cmd {

    private static final String TAG = Cmd.class.getSimpleName();

    protected enum ExternalApp {

        NO_EXTERNAL_APP,

        OSMOSIS,

        GRAPHOPPER
    }

    // current map item
    private ItemMap mMap;
    // type of external app used at start
    private ExternalApp mExternalApp;
    // list of added commands
    private List<String> mCmdList;

    public Cmd(ItemMap map, ExternalApp externalApp) {
        this.mMap = map;
        this.mExternalApp = externalApp;
        this.mCmdList = new ArrayList<String>();

        // check map
        if (mMap == null) {
            throw new IllegalArgumentException("Map object not valid");
        }

        // add basic
        if (mExternalApp == ExternalApp.OSMOSIS) {
            addCommand(Parameters.getOsmosisExe());
        } else if (mExternalApp == ExternalApp.GRAPHOPPER) {
            // add required command to start
            addCommand(Parameters.getPreShellCommand());
            // add GraphHopper command
            addCommand(Parameters.getGraphHopperExe());
        }
    }

    public ItemMap getMap() {
        return mMap;
    }

    /**************************************************/
    /*                   ADD COMMANDS                 */
    /**************************************************/

    // READ OSM FILES

    public void addReadSource() {
        String source = getMap().getPathSource();
        if (source.endsWith(".pbf")) {
            addReadPbf(getMap().getPathSource());
        } else if (source.endsWith(".xml")) {
            addReadXml(getMap().getPathSource());
        } else {
            throw new IllegalArgumentException("Invalid source file: '" + source + "'");
        }
    }

    public void addReadPbf(String readPath) {
        // test new "fast method"
//        addCommand("--read-pbf-fast");
//        addCommand(readPath);
//        addCommand("workers=4");
        addCommand("--read-pbf");
        addCommand(readPath);
    }

    public void addReadXml(String readPath) {
        addCommand("--read-xml");
        addCommand(readPath);
    }

    // WRITE OSM FILES

    public void addWritePbf(String pathToWrite, boolean omitMetadata) throws IOException {
        // prepare directory
        prepareDirectory(pathToWrite);

        // add required commands
        addCommand("--write-pbf");
        addCommand(pathToWrite);
        if (omitMetadata){
            addCommand("omitmetadata=true");
        }
    }
    
    public void addWriteXml(String pathToWrite) throws IOException {
        // prepare directory
        prepareDirectory(pathToWrite);

        // add required commands
        addCommand("--wx");
        addCommand(pathToWrite);
    }

    protected void prepareDirectory(String pathToWrite) throws IOException {
        FileUtils.forceMkdir(new File(pathToWrite).getParentFile());
    }

    // ADD SPECIAL OSMOSIS COMMANDS

    public void addSort() {
        addCommand("--sort");
    }
    
    public void addBuffer() {
        addCommand("--buffer");
    }

    public void addTee(int mapCount) {
        addCommand("--tee");
        addCommand(String.valueOf(mapCount));
    }

    public void addBoundingPolygon(ItemMap map) {
        // test if polygon exist in specified path
        if (!new File(map.getPathPolygon()).exists() ) {
            throw new IllegalArgumentException("Bounding polygon: " + map.getPathPolygon() + " doesn't exists");
        }

        // finally add to command list
        addCommand("--bp");
        addCommand("file="+map.getPathPolygon());
        //addCommand("completeWays=yes"); workaround for russia where are long highways
    }

    protected void addCommand(String cmd) {
        // check command
        if (cmd == null || cmd.length() == 0) {
            return;
        }

        // add to the list
        mCmdList.add(cmd);
    }

    /**************************************************/
    /*                      TOOLS                     */
    /**************************************************/

    public String execute() throws IOException, InterruptedException {
        return runCommands(createArray());
    }

    public ProcessBuilder executePb() {
        return createProcessBuilder(createArray());
    }

    private String[] createArray() {
        // add ending command
        if (mExternalApp == ExternalApp.GRAPHOPPER) {
            addCommand(Parameters.getPostShellCommand());
        }

        // create array
        String[] cmdArray = new String[mCmdList.size()];
        cmdArray = mCmdList.toArray(cmdArray);
        return cmdArray;
    }
    
    private String  getCmdLine() {
        String line = "";
        for (String param : mCmdList){
            line += param + " ";
        }
        return line;
    }
    
//    public void xml2pbf (String input, String output) throws IOException, InterruptedException{
//        cmdList = new ArrayList<String>();
//        addCommand(Parameters.osmosisDir);
//        addReadXml(input);
//        addWritePbf(output, true);
//        run();
//    }

    private ProcessBuilder createProcessBuilder(String[] mCmdArray) {
        ProcessBuilder pb = new ProcessBuilder(mCmdArray);
        pb.redirectErrorStream(true);

        // set working directory based on external software
        if (mExternalApp == ExternalApp.OSMOSIS) {
            pb.directory(new File(Parameters.getOsmosisExe()).getParentFile().getParentFile());
        } else if (mExternalApp == ExternalApp.GRAPHOPPER) {
            pb.directory(new File(Parameters.getGraphHopperExe()).getParentFile());
        }

        // return builder
        return pb;
    }

    private String runCommands(String[] mCmdArray)
            throws IOException, InterruptedException {
         String line;
         String lastOutpuLine = null;
         BufferedReader stdInput = null;
         try {
            Main.myRunTimeLog.print(getCmdLine() + "\n");
            ProcessBuilder pb = createProcessBuilder(mCmdArray);

            Process runTime = pb.start();
            stdInput = new BufferedReader(new
                    InputStreamReader(runTime.getInputStream()));

            // read the output from the command
            while ((line = stdInput.readLine()) != null) {
                System.out.println(line);
                Main.myRunTimeLog.print(line +"\n");
                lastOutpuLine = line;
            }
            int exitVal = runTime.waitFor();

            // break program when wrong exit value
            if (exitVal != 0){
                String errorMsg = "exception happened when run cmd: \n" + getCmdLine();
                throw new IllegalArgumentException(errorMsg);
            }

            // return result
            mCmdList.clear();
            return lastOutpuLine;
        } finally {
            try {
                if (stdInput != null){
                    stdInput.close();
                }
            } catch (IOException ex) {
                throw new IOException(ex.toString());
            }
        }
    }

    protected void checkFileLocalPath() {
        if (!new File(mMap.getPathSource()).exists()){
            throw new IllegalArgumentException("Extracted map: " +
                    mMap.getPathSource() + " does not exist");
        }
    }
}