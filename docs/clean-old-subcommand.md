# `clean_old` subcommand

Deletes intermediate generation folders from previous runs to free disk space.

```
java -jar OsmToolsBasic.jar [global options] clean_old [-cf <config.xml>]
```

---

## What it does

1. Parses the map-config XML.
2. Selects the planet map **plus the first non-planet map** as representatives.
3. For those maps, recursively deletes the working/output folders for the current version:
   address DB, mapsforge (generate + result), address/POI DB, tourist, extract, POI v2 DB, mbtiles (generate + online).

Missing paths are logged and skipped; failures are non-fatal. Static data (polygons, residential, HGT) is never touched.

> ⚠️ Deletion is immediate and unrecoverable. There is **no** age- or "keep N latest"-based filtering — it purges the folders for the maps referenced in the config at the current version.

## Options

| Flag | Short | Default | Description |
|---|---|---|---|
| `--config_file` | `-cf` | `config.xml` | Map-config XML defining the maps |

## Config reference

Loaded from `config/app_config.yaml` (kotlinx-serialization). Folder roots for the purge:

| Key | Req. / default | Purpose |
|---|---|---|
| `mapsForgeDir` | default `./_mapsforge` | Root for offline map working/result folders |
| `mbtilesDir` | default `./_mbtiles` | Root for MBTiles working folders |
| `planetDir` | default `./_planet` | Root for planet-map working folders |

> `mapsForgeDir` and `mbtilesDir` are present in the committed `app_config.yaml`; `planetDir` is **not**, so it falls back to the code default `./_planet` unless you add it.

## Example

```bash
# Purge previous generation for maps in config.xml
java -jar OsmToolsBasic.jar clean_old

# Use a specific config file
java -jar OsmToolsBasic.jar clean_old -cf config/config_2026.xml
```