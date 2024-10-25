/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.Main
import com.asamm.osmTools.mapConfig.ItemMap
import java.io.File

/**
 * @author volda
 */
class CmdSort(val map: ItemMap) : Cmd(ExternalApp.OSMOSIS), CmdOsmosis {
    fun createCmdSort() {
        addReadPbf()
        //addReadXml();
        addSort()
        addWritePbf(true)
    }

    private fun addReadPbf() {
        require(map.pathContour.toFile().exists()) {
            "File with contour lines " + map.pathContour + " does not exist"
        }

        addCommand("--read-pbf")
        addCommand(map.pathContour.toString())
    }

    protected fun addWritePbf(omitMetadata: Boolean) {
        addCommand("--write-pbf")
        addCommand(map.pathContour.toString() + ".sorted")
        if (omitMetadata) {
            addCommand("omitmetadata=true")
        }
    }

    private fun addWriteXml() {
        addCommand("--wx")
        addCommand(map.pathContour.toString() + ".sorted")
        addCommand("compressionMethod=bzip2")
    }

    fun rename() {
        //ocekavam, ze probehlo sortovani a prejmenuji mapu zpet na puvodni jmeno bez postfixu sorted
        val fSorted = File(map.pathContour.toString() + ".sorted")
        val fUnsorted = map.pathContour.toFile()

        require(fSorted.exists()) {
            "Sorted contourline " +
                    map.pathContour + ".sorted does not exist."
        }
        if (fUnsorted.exists()) {
            fUnsorted.delete()
        }

        Main.LOG.info(
            "Rename contour line: " + fSorted.name +
                    " to: " + fUnsorted.name
        )
        fSorted.renameTo(map.pathContour.toFile())
    }
}

