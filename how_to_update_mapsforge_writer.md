## How to update mapforge part

Due to different map tile size it's needed to change some classes in mapsforge generator

1. Clone fresh mapsforge src
2. Replace following constants in `org.mapsforge.map.writer.util.constants`
```
// Asamm customization due to 512
public static final int DEFAULT_TILE_SIZE = 512;
 
org.mapsforge.core.util.MercatorProjection
// Asamm customizaiton due to 512 tiles
private static final int DUMMY_TILE_SIZE = 512; 
```
3. Due to changes in tiles size is needed to replace tile size for following methods in `org.mapsforge.core.util.MercatorProjection`

```
public static long getMapSizeWithScaleFactor(double scaleFactor, int tileSize) {

	// TODO asamm workaround due to changed tileSize
	tileSize = 256;

	if (scaleFactor < 1) {
		throw new IllegalArgumentException("scale factor must not < 1 " + scaleFactor);
	}
	return (long) (tileSize * (Math.pow(2, scaleFactorToZoomLevel(scaleFactor))));
}
```
```
public static long getMapSize(byte zoomLevel, int tileSize) {

	// TODO asamm workaround due to changed tileSize
	tileSize = 256;

	if (zoomLevel < 0) {
		throw new IllegalArgumentException("zoom level must not be negative: " + zoomLevel);
	}
	return (long) tileSize << zoomLevel;
}
```