    /*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package com.asamm.osmTools.cmdCommands;

    import com.asamm.osmTools.mapConfig.MapSource;

/**
 *
 * @author volda
 */
public class CmdExtract extends Cmd {

    public CmdExtract(MapSource ms, String sourceId) {
        super(ms.getMapById(sourceId), ExternalApp.OSMOSIS);
    }
}
