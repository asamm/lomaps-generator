/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands

import com.asamm.osmTools.mapConfig.MapSource

/**
 *
 * @author volda
 */
class CmdExtract(ms: MapSource, sourceId: String?) : Cmd(ExternalApp.OSMOSIS),
    CmdOsmosis
