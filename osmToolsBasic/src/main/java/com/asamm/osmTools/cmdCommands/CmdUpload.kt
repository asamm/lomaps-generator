package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.osmTools.utils.Logger
import com.asamm.store.LocusStoreEnv

/**
 * Created by menion on 10/19/14.
 */
class CmdUpload : Cmd(ExternalApp.STORE_UPLOAD) {

    private val TAG: String = CmdUpload::class.java.simpleName

    fun execute(numRepeat: Int): String? {
        var localNumRepeat = numRepeat
        try {
            return super.execute()
        } catch (e: Exception) {
            if (localNumRepeat > 0) {
                localNumRepeat--

                Logger.w(TAG, "Re-execute upload run: " + getCmdLine())
                execute(localNumRepeat)
            } else {
                throw e
            }
        }
        return null
    }

    fun createCmd() {
        if (AppConfig.config.locusStoreEnv == LocusStoreEnv.DEV) {
            addCommand("--isDev")
        }

        // define that is process for upload
        addCommand("--upload")

        // set definition file
        addCommand("--uploadDef")
        addCommand(AppConfig.config.storeUploadDefinitionJson.toString())
    }
}
