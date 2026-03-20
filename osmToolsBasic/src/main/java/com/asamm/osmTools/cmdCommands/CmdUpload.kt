package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.config.AppConfig
import com.asamm.store.LocusStoreEnv

/**
 * Utils for Upload to Locus Store
 */
class CmdUpload : Cmd(ExternalApp.STORE_UPLOAD) {

    fun upload(numRepeat: Int = 1) {
        val cmd = builder()
            .addIf(AppConfig.config.locusStoreEnv == LocusStoreEnv.DEV, "--isDev")
            .add("--upload")
            .add("--uploadDef", AppConfig.config.storeUploadDefinitionJson.toString())
            .build()

        executeWithRetry(cmd, numRepeat)
    }
}