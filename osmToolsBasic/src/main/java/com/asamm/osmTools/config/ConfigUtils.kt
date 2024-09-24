package com.asamm.osmTools.config

object ConfigUtils {


    /**
     * Define that actions will be performed based on command line definition of actions in[cliActions]
     * For specific CLI actions, additional actions are added
     */
    fun addAdditionalActions(cliActions: MutableSet<Action>) {

        // for MapsForge generation add Coastline action, transform and merge
        when {
            cliActions.contains(Action.GENERATE_MAPSFORGE) -> {
                cliActions.add(Action.COASTLINE)
                cliActions.add(Action.TRANSFORM)
                cliActions.add(Action.MERGE)

                cliActions.add(Action.COMPRESS)
                cliActions.add(Action.CREATE_JSON)
            }
        }
    }
}