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
        require(File(map.pathContour).exists()) {
            "Soubor vrstevnic: " +
                    map.pathContour + " neexistuje"
        }

        addCommand("--read-pbf")
        addCommand(map.pathContour)
    }

    private fun addReadXml() {
        require(File(map.pathContour).exists()) {
            "Soubor vrstevnic: " +
                    map.pathContour + " neexistuje"
        }
        addCommand("--read-xml")
        addCommand(map.pathContour)
    }


    protected fun addWritePbf(omitMetadata: Boolean) {
        addCommand("--write-pbf")
        addCommand(map.pathContour + ".sorted")
        if (omitMetadata) {
            addCommand("omitmetadata=true")
        }
    }

    private fun addWriteXml() {
        addCommand("--wx")
        addCommand(map.pathContour + ".sorted")
        addCommand("compressionMethod=bzip2")
    }

    fun rename() {
        //ocekavam, ze probehlo sortovani a prejmenuji mapu zpet na puvodni jmeno bez postfixu sorted
        val fSorted = File(map.pathContour + ".sorted")
        val fUnsorted = File(map.pathContour)

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
        fSorted.renameTo(File(map.pathContour))
    }
}

