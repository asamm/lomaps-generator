package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.Parameters;
import com.asamm.osmTools.utils.Logger;

import java.io.IOException;

/**
 * Created by menion on 10/19/14.
 */
public class CmdUpload  extends  Cmd{

    private static final String TAG = CmdUpload.class.getSimpleName();

    public CmdUpload() {
        super(null, ExternalApp.STORE_UPLOAD);
    }

    public String execute(int numRepeat) throws IOException, InterruptedException {

        try {
            return super.execute();
        } catch (Exception e) {
            if (numRepeat > 0){
                numRepeat--;

                Logger.w(TAG, "Re-execute upload run: " + getCmdLine());
                execute(numRepeat);
            }
            else {
                throw e;
            }
        }
        return null;
    }

    public void  createCmd (){

        if (Parameters.getIsDev()){
            addCommand("--isDev");
        }

        // define that is process for upload
        addCommand("--upload");

        // set definition file
        addCommand("--uploadDef");
        addCommand(Parameters.getUploadDefinitionJsonPath());
    }

}
