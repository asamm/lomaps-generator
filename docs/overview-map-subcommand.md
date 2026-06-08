# `overview_map` subcommand

Generates a global overview Mapsforge map (zoom 1–9) from Natural Earth data merged into the planet PBF. Useful for low-zoom rendering of the whole world.

```
java -jar OsmToolsBasic.jar [global options] overview_map [-v <yyyy.MM.dd>] [-cf <config.xml>] [-hgt <dir>]
```

---

## What it does

1. Runs the planet pipeline for `TOURIST`, `CONTOUR`, `OVERVIEW_MAP`:
   - **TOURIST / CONTOUR** — prepare route and contour PBFs.
   - **OVERVIEW_MAP** — `OverviewMapBuilder` downloads Natural Earth GeoPackage, the shadedrelief World Base Map shapefiles, and RESOLVE Ecoregions, reads all layers in `OverviewMapLayers.ALL` (oceans, lakes, rivers, boundaries, places, roads, ferries, bathymetry, …) applying per-layer filters/mappers/zoom bounds, and writes an overview `.osm.pbf`.
2. Merges original planet + tourist + contour + overview PBFs into `planet.pathSource` (via osmium).
3. `MapsforgeTilerRunner.generateOverviewMap` writes the `.osm.map` (next to the overview PBF, `.osm.pbf` → `.osm.map`) using the overview tag-mapping and zoom intervals.

## Options

| Flag | Short | Default | Description |
|---|---|---|---|
| `--version` | `-v` | _(empty)_ | Map version, `yyyy.MM.dd` |
| `--config_file` | `-cf` | `config.xml` | Map-config XML (defines the planet `ItemMap`) |
| `--hgt_dir` | `-hgt` | `hgt` | HGT elevation dir (for contour generation) |

## Config reference

Loaded from `config/app_config.yaml` (kotlinx-serialization). Keys with a code default are optional in YAML; keys without one are required.

> ⚠️ The `overviewMapConfig` section is **not** in the committed `app_config.yaml`. If you add it, the three `start*Id` keys (no defaults) are required; everything else falls back to the code defaults below.

**`overviewMapConfig`**

| Key | Req. / default | Purpose |
|---|---|---|
| `startNodeId` / `startWayId` / `startRelationId` | required | Synthetic OSM ID ranges for NE features |
| `outputPbf` | default `_planet/overview/planet_overview.osm.pbf` | Overview PBF (and `.osm.map` sibling) |
| `tagMapping` | default `config/tag-mapping-overview-map.xml` | Tag mapping for the overview map |
| `zoomInterval` | default `2,0,3,5,4,6,8,7,9` | Mapsforge zoom intervals (z1–9) |
| `gpkgUrl` / `baseMapShpUrl` / `ecoregionsShpUrl` | defaults (NE / shadedrelief) | Source data downloads |
| `dataDir` | default `download/overview_map` | Downloaded/extracted data |

**Other sections** (present in the committed YAML)

| Key | Req. / default | Purpose |
|---|---|---|
| `planetConfig.planetExtendedId` | required | Planet map id looked up in the config XML |
| `contourConfig.hgtDir` | required | HGT dir (CLI `-hgt` overrides) |
| `mapsforgeConfig.mapDescription` | required | Comment embedded in the `.osm.map` |

> Layer definitions (sources, filters, zoom levels, tag mapping) live in `overviewMap/OverviewMapLayers.kt`.

## Example

```bash
java -jar OsmToolsBasic.jar overview_map -v 2026.06.01 -cf config/config_2026.xml -hgt /mnt/work/hgt
```