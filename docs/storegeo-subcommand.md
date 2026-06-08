# `storegeo` subcommand

Generates country/region boundaries for Locus Store region definitions and writes them into the Locus Store geo database.

```
java -jar OsmToolsBasic.jar [global options] storegeo [-cf <config_store_geodb.xml>]
```

> ⚠️ **Legacy / obsolete.** Not part of the modern `lomaps` pipeline. The current address/POI flow produces boundaries as GeoJSON instead. Kept for backward compatibility.

---

## What it does

For each map pack in the config XML:
1. `actionExtractOsm` — extracts boundary data from the source planet PBF.
2. `actionCountryBorder` — filters admin levels 2/3/4 (+ continent features) and runs the Osmosis `loMapsDb` plugin, writing boundaries to the Locus Store region DB (`StorageType.STORE_REGION_DB`).

## Requires

- `osmosis` with the Locus `loMapsDb` plugin (`cmdConfig.osmosis`)
- Source OSM planet/region PBF

## Options

| Flag | Short | Default | Description |
|---|---|---|---|
| `--config_file` | `-cf` | `config/config_store_geodb.xml` | Boundary-generation config XML |

The config XML defines `<mapPack>` elements (`regionId`, `sourceId`, `type=storeGeoDb`, …) and child `<map>` entries (`regionId`, `regionCode`, …).

## Config reference

Loaded from `config/app_config.yaml` (kotlinx-serialization).

| Key | Req. / default | Purpose |
|---|---|---|
| `cmdConfig.osmosis` | required | Path to the osmosis binary (with the `loMapsDb` plugin) |

> `cmdConfig` is present in the committed `app_config.yaml`. Map packs/regions come from the `-cf` XML, not from YAML.

## Example

```bash
java -jar OsmToolsBasic.jar storegeo -cf config/config_store_geodb.xml
```