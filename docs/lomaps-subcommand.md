# `lomaps` subcommand

Generates LoMaps vector maps for the Locus Store (offline mode) or online planet-level tile maps (online mode).

```
OsmToolsBasic [global options] lomaps --mode <offline|online> [options]
```

---

## Global options

These options belong to the root command and must be placed **before** the `lomaps` subcommand.

| Flag | Short | Default | Description |
|---|---|---|---|
| `--debug` | `-d` | false | Enable verbose/debug logging |
| `--overwrite` | `-ow` | false | Overwrite output files if they already exist |
| `--ls_environment` | `-e` | `PROD` | Locus Store environment to upload to. Values: `PROD`, `DEV` |

---

## Subcommand options

### `--mode` `-m` _(required)_

Selects the generation pipeline.

| Value | Description |
|---|---|
| `offline` | Generates offline vector maps for Locus Store: tourist data → contours → OSM extract → address/POI DB → Mapsforge → MBTiles |
| `online` | Generates online planet-level tile maps: tourist data → contours → PMTiles → publish |

### `--version` `-v`

Map version string in `yyyy.MM.dd` format (e.g. `2026.05.25`). Used for versioning in Locus Store.

- **Required:** yes
- **Default:** _(empty — validation will fail if omitted)_

### `--release` `-r`

Releases the generated output after generation is complete.

- **Required:** no
- **Default:** `false`

Behaviour depends on `--mode`:

| Mode | Effect |
|---|---|
| `offline` | Uploads generated maps to Locus Store. Requires `--store_uploader`. |
| `online` | Publishes PMTiles to S3 and the tile server. |

### `--store_uploader` `-su`

Path to the Locus Store uploader `.jar` file.

- **Required:** only when `--release` is set with `--mode offline`
- **Default:** _(none)_
- The file must exist on disk.

### `--config_file` `-cf`

Path to the map configuration XML file that defines which maps are generated.

- **Required:** no
- **Default:** `config.xml` in the working directory

### `--hgt_dir` `-hgt`

Path to the directory containing HGT elevation data files (used for contour line generation).

- **Required:** no
- **Default:** `hgt/` in the working directory

### `--mapsforge_dir` `-mf`

Path to the directory where Mapsforge map files are stored/written.

- **Required:** no
- **Default:** value from `app_config.yaml` → `mapsForgeDir`

### `--mbtiles_dir` `-mb`

Path to the directory where MBTiles files are stored/written.

- **Required:** no
- **Default:** value from `app_config.yaml` → `mbtilesDir`

### `--planet_dir` `-pd`

Path to the directory where planet-level data files are stored.

- **Required:** no
- **Default:** value from `app_config.yaml` → `planetDir`

---

## Pipeline actions

The `--mode` flag maps to a fixed set of pipeline actions. Dependencies are resolved automatically.

### `offline` pipeline

Steps are grouped by pipeline phase. All planet-level steps run before per-map steps.

**Planet pipeline**

| Step | Action | Triggered by |
|---|---|---|
| 1 | Tourist data enrichment | base |
| 2 | Contour line generation | base |
| 3 | Residential layer | dependency of `generate_mapsforge` |
| 4 | Overview map | dependency of `generate_mbtiles` |
| 5 | Mapsforge map generation | base |

**Map pipeline** _(per map)_

| Step | Action | Triggered by |
|---|---|---|
| 6 | OSM planet extract | dependency of `address_poi_db` |
| 7 | Address & POI database | base |
| 8 | MBTiles generation | base |
| 9 | POI V2 database | dependency of `generate_mapsforge` / `generate_mbtiles` |

**Post pipeline**

| Step | Action | Triggered by |
|---|---|---|
| 10 | Insert metadata | `address_poi_db` or `generate_mapsforge` |
| 11 | Create upload JSON | dependency of `upload` (when `--release`) |
| 12 | Compress maps | dependency of `upload` (when `--release`) |
| 13 | Upload to Locus Store | `--release` flag |

### `online` pipeline

| Step | Action | Triggered by |
|---|---|---|
| 1 | Tourist data enrichment | base |
| 2 | Contour line generation | base |
| 3 | Overview map | dependency of `generate_pmtiles_online` |
| 4 | PMTiles generation & S3 publish | base + `--release` |

---

## Examples

```bash
# Generate offline maps for the current version (no upload)
OsmToolsBasic lomaps --mode offline --version 2026.05.25

# Generate and release offline maps to Locus Store
OsmToolsBasic lomaps --mode offline --version 2026.05.25 --release \
  --store_uploader /tools/store-uploader.jar

# Generate and release offline maps, overwrite existing files, target DEV store
OsmToolsBasic -ow -e DEV lomaps --mode offline --version 2026.05.25 --release \
  --store_uploader /tools/store-uploader.jar

# Generate and publish online planet tiles
OsmToolsBasic lomaps --mode online --version 2026.05.25 --release

# Generate online tiles without publishing (dry run)
OsmToolsBasic lomaps --mode online --version 2026.05.25
```
