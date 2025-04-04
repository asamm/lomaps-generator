package com.asamm.osmTools.mbtilesextract.tiles

data class TileCoord(val x: Int, val y: Int, val z: Int) {
}


data class Tile(val coord: TileCoord, val bytes: ByteArray) {
}