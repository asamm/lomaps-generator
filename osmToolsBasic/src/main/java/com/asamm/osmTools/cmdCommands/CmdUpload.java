package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.mapConfig.ItemMap;

import java.io.File;

/**
 * Created by menion on 10/19/14.
 */
public class CmdUpload  extends  Cmd{

    public enum UploadAction {
        CREATE_ITEM,

        UPDATE_ITEM
    }

    private UploadAction mUploadAction;

    public CmdUpload(UploadAction uploadAction) {
        super(null, ExternalApp.STORE_UPLOAD);

        mUploadAction = uploadAction;


        // test if python is installed in defined dir
        if (!new File(Parameters.getPythonDir()).exists()){
            throw new IllegalArgumentException ("Python in location" + Parameters.getPythonDir() +"  does not exist!");
        }

        //test if shp2osm.py script exist
        if (!new File (Parameters.getShp2osmDir()).exists()){
            throw new IllegalArgumentException ("Shp2Osm script in location" + Parameters.getShp2osmDir() +"  does not exist!");
        }

    }

    public void  createCmd (){

        if (mUploadAction == UploadAction.CREATE_ITEM){
            addCommand("-c");
        }
        else if (mUploadAction == UploadAction.UPDATE_ITEM) {
            addCommand("-u");
        }
        else {
            throw new IllegalArgumentException ("Upload fails  unknown action " + mUploadAction);
        }

        addCommand("--isDev");
        addCommand("false");

        // set definition file
        addCommand("--defFile");
        addCommand(Parameters.getUploadDefinitionJsonPath());
    }

}
