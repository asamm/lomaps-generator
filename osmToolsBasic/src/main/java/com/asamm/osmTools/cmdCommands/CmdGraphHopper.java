package com.asamm.osmTools.cmdCommands;

import com.asamm.osmTools.mapConfig.ItemMap;

/**
 * Created by menion on 20. 7. 2014.
 * Class is part of Locus project
 */
public class CmdGraphHopper extends Cmd {

    ItemMap map;
    public CmdGraphHopper(ItemMap map) {
        super(ExternalApp.GRAPHOPPER);

        this.map = map;
        // check parameters
        checkFileLocalPath(map);

        // add commands required for generating
        addCommand("import");
        addCommand(map.getPathSource().toString());
    }
}
