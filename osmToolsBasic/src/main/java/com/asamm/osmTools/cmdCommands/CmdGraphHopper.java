package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.mapConfig.ItemMap;

/**
 * Created by menion on 20. 7. 2014.
 * Class is part of Locus project
 */
public class CmdGraphHopper extends Cmd {

    public CmdGraphHopper(ItemMap map) {
        super(map, ExternalApp.GRAPHOPPER);

        // check parameters
        checkFileLocalPath();

        // add commands required for generating
        addCommand("import");
        addCommand(getMap().getPathSource());
    }
}
