# `lomaps` subcommand

Generates LoMaps vector maps for the Locus Store (offline mode) or online planet-level tile maps (online mode).

```
java -jar OsmToolsBasic.jar [global options] lomaps --mode <offline|online> [options]
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
- **Default:** `config/config.xml` in the working directory

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
| 11 | Create upload JSON | base (always) |
| 12 | Compress maps | base (always) |
| 13 | Upload to Locus Store | `--release` flag |

### `online` pipeline

| Step | Action | Triggered by |
|---|---|---|
| 1 | Tourist data enrichment | base |
| 2 | Contour line generation | base |
| 3 | Overview map | dependency of `generate_pmtiles` |
| 4 | Planet PMTiles generation | base (`generate_pmtiles`) |
| 5 | Upload PMTiles to S3 | `--release` flag (`upload_s3`) |

---

## Config reference

Beyond the CLI options, the run is driven by `config/app_config.yaml` (kotlinx-serialization). Keys with a code default are optional in YAML; keys without one are required. CLI options override the corresponding YAML value where noted in the options table above.

| Key | Req. / default | Purpose |
|---|---|---|
| `mapsForgeDir` | default `./_mapsforge` | Offline map working/result root (CLI `-mf`) |
| `mbtilesDir` | default `./_mbtiles` | MBTiles working root (CLI `-mb`) |
| `planetDir` | default `./_planet` | Planet-data working root (CLI `-pd`) — **not** in committed YAML |
| `temporaryDir` | default `_temp` | Scratch dir |
| `mapsforgeConfig.tagMapping` | required | Tourist tag-mapping XML |
| `mapsforgeConfig.zoomInterval` | default `2,0,…,12,21` | Mapsforge zoom intervals |
| `planetConfig.*` | required | Planet file path/URL, planetiler dir, `planetExtendedId`, outdoor layers |
| `touristConfig.*` / `contourConfig.*` | required | Tourist & contour generation (`hgtDir` via CLI `-hgt`) |
| `poiAddressConfig.*` / `residentialConfig.*` | required | Address/POI DB and residential-layer inputs |
| `cmdConfig.*` | required | External tools (planetiler, osmosis, POI-V2 scripts; osmium/pyhgtmap/gdal resolved from PATH) |
| `storeUploaderPath` | default `""` | Store-uploader jar (CLI `-su`; needed for offline `--release`) |

**Online mode (`--mode online`) also uses:**

| Key | Req. / default | Purpose |
|---|---|---|
| `onlineLoMapsConfig.*` | required | S3 target + `s3pmtilesPath[Dev]`, version retention — **not** in committed YAML |
| `maptilerCloudConfig.*` | required | MapTiler tileset metadata |

> Sections absent from the committed `config/app_config.yaml` (`planetDir`, `onlineLoMapsConfig`) fall back to code defaults or must be added; the full production config is kept on the NAS (see README).

---

## Examples

```bash
# Generate offline maps for the current version (no upload)
java -jar OsmToolsBasic.jar lomaps --mode offline --version 2026.05.25

# Generate and release offline maps to Locus Store
java -jar OsmToolsBasic.jar lomaps --mode offline --version 2026.05.25 --release \
  --store_uploader /tools/store-uploader.jar

# Generate and release offline maps, overwrite existing files, target DEV store
java -jar OsmToolsBasic.jar -ow -e DEV lomaps --mode offline --version 2026.05.25 --release \
  --store_uploader /tools/store-uploader.jar

# Generate and publish online planet tiles
java -jar OsmToolsBasic.jar lomaps --mode online --version 2026.05.25 --release

# Generate online tiles without publishing (dry run)
java -jar OsmToolsBasic.jar lomaps --mode online --version 2026.05.25
```
