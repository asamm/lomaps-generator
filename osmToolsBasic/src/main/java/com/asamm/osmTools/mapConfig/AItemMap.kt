package com.asamm.osmTools.mapConfig

import com.asamm.osmTools.config.Action
import com.asamm.osmTools.utils.Consts
import com.asamm.osmTools.utils.Utils
import org.kxml2.io.KXmlParser

open class AItemMap(
    /** Parent MapPack of this object */
    val parent: ItemMapPack? = null
) {

    /** Name of this object */
    open var name: String = parent?.name ?: ""

    /** Types (actions) that should be performed */
    private val actions: MutableList<Action> = parent?.getActionsCopy()?.toMutableList() ?: mutableListOf()

    /** Source for map data */
    var sourceId: String = parent?.sourceId ?: ""

    /** ID of region */
    var regionId: String = parent?.regionId ?: ""

    /** ID of parent region — if not set explicitly, derived from regionId by stripping the last segment */
    private var _parentRegionId: String = parent?.parentRegionId ?: ""
    val parentRegionId: String
        get() = _parentRegionId.ifEmpty {
            val idx = regionId.lastIndexOf('.')
            if (idx == -1) regionId else regionId.substring(0, idx)
        }

    /** Directory name */
    var dir: String = parent?.dir ?: ""

    /** Readable name of the country this item belongs to */
    open var countryName: String = parent?.countryName ?: ""

    /** Preferred language for generating */
    var prefLang: String = parent?.prefLang ?: ""

    /** ISO Alpha-2 country code, used for store region DB creation */
    var regionCode: String = parent?.regionCode ?: ""

    // --- Actions ---

    fun hasAction(action: Action): Boolean = action in actions

    fun getActionsCopy(): List<Action> = actions.toList()

    // --- Validation ---

    open fun validate() {
        require(actions.isNotEmpty()) { "No defined actions for '$name'" }
        require(name.isNotEmpty()) { "Input XML is not valid. Name is empty" }
        require(dir.isNotEmpty()) { "Input XML is not valid. Invalid argument dir: $dir, name: $name" }
        if (hasAction(Action.EXTRACT_OSM_PLANET)) {
            require(sourceId.isNotEmpty()) { "Input XML is not valid. MapPack '$name' sourceId is empty" }
        }
    }

    // --- Parsing ---

    open fun fillAttributes(parser: KXmlParser) {
        parser.attr("name")?.let { name = it }
        parser.attr("type")?.let { parseActions(it) }
        parser.attr("sourceId")?.let { sourceId = it }
        parser.attr("regionId")?.let { regionId = it }
        parser.attr("parentRegionId")?.let { _parentRegionId = it }
        parser.attr("dir")?.let { raw ->
            val normalized = Utils.changeSlash(raw)
            dir = Consts.fixDirectoryPath(if (dir.isEmpty()) normalized else "$dir${Consts.FILE_SEP}$normalized")
        }
        parser.attr("countryName")?.let { countryName = it }
        parser.attr("prefLang")?.let { prefLang = it }
        parser.attr("regionCode")?.let { regionCode = it }
    }

    private fun parseActions(typeValue: String) {
        val parts = typeValue.split("|")
        val startIndex = if (parts.firstOrNull() == "+") 1 else { actions.clear(); 0 }
        parts.drop(startIndex).forEach { label ->
            val action = Action.entries.firstOrNull { it.label.equals(label, ignoreCase = true) }
                ?: throw IllegalArgumentException("Invalid 'type' value: $label")
            actions.add(action)
        }
    }
}

internal fun KXmlParser.attr(name: String): String? = getAttributeValue(null, name)