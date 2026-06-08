# LoMaps generator
Command-line tools for generation LoMaps and offline POIs.

## Installation
The steps described below expect installation on Ubuntu 

### Required SW


- install [osmium](https://osmcode.org/osmium-tool/) used for extraction planet file to the countries and states

    ```sudo apt-get install osmium-tools```
	
- install [pyhgtmap](https://github.com/agrenott/pyhgtmap/) - pyhgtmap is a fork of the original [phyghtmap](http://katze.tfiu.de/projects/phyghtmap/) tool, 
 which doesn't seem to be maintained anymore. It is used for generation of contour-lines from elevation hgt files.  

    ```
  sudo apt update
  sudo apt install python3 python3-pip python3-venv
  sudo apt install pipx
  #Install pyhgtmap in local env
  pipx install pyhgtmap
   ~/.local/bin/pipx ensurepath
     ```
	
- install [ogr2ogr](https://gdal.org/programs/ogr2ogr.html) set of tools. Generator uses ogr2ogr to cut global SHP files with coastlines into states areas

    ```sudo apt install libpq-dev gdal-bin libgdal-dev``` 
	
- install [spatialite](https://www.gaia-gis.it/fossil/libspatialite) spatialite libraries to generate Address and POI database
    ```
    sudo apt-get install spatialite-bin
    sudo apt-get install libsqlite3-mod-spatialite
    ``` 

- install python lomaps tools script required for OSM update of planet file, extract marked trails or convert 
  SHP Land Polygons to the OSM format
  ```
  git clone https://github.com/asamm/lomaps-generator.git
  cd lomaps-generator
  # lomaps-generator-tools to any folder - the project folder is expected
  cd lomaps-generator-tools
  # init python env
  python3 -m venv venv
  source venv/bin/activate
  pip install .
  ``` 
- install Asamm Fork of `planetiler-openmaptiles` available at https://github.com/asamm/planetiler-openmaptiles

### Static data

LoMaps generator requires lot's of static data that are vital for generation. All needed data are available on NAS `NAS\content\maps\lomaps_generator\` Download the whole folder and do following steps:
##### Unpack .hgt files
- hgt files defines Digital Model Terrain and are used by `phyghtmap` tool for contour-lines generation. 
- unpack the hgt data
	```
	cd hgt
	7z x hgt.7z.0
	```

##### Update coastline SHP - optional 

To correctly handle sea areas and coastlines are during generation created land polygons.  These are generated from land-polygons available for downloading https://osmdata.openstreetmap.de/data/land-polygons.html This step is optional - the land-polygons are available in LoMaps generator NAS folder. To update the land polygons:
- download land polygons in Shapefile format, Projection: WGS84 https://osmdata.openstreetmap.de/info/formats.html# (choose "Large polygons are split" option) 
- unpack data and copy .shp files to the `coastlines\land-polygons\` -> replace `land_polygons.shp` file

### Configuration
LoMaps generator requires several configuration files. These are available in the `config` folder of LoMaps generator.
- `app_config.yaml` - main configuration file of LoMaps generator - defines paths to data, tools, etc.
- `config.xml` - configuration of LoMaps generator - defines which maps are generated
- `default_store_item_definition.json` - configuration of Locus Store item - defines LoMaps

#### Edit app_config.yaml
- edit paths where planet file is stored and URL to download planet file
- set path to `planetiler-openmaptiles`

## Setting S3 Environment Variables Permanently (Linux)

To make the S3 credentials available every time you open a terminal, add them to `~/.bashrc`:

1. Open the file in a text editor:
   ```bash
   vi ~/.bashrc

2. Add the following lines at the end of the file, replacing the placeholders with your actual S3 credentials:
   ```bash
   export S3_ACCESS_KEY=your-access-key
   export S3_SECRET_KEY=your-secret-key

--- 

## Usage

The tool is a single jar (`OsmToolsBasic.jar`) with several subcommands:

```
java -jar OsmToolsBasic.jar [global options] <subcommand> [subcommand options]
```

### Global options

| Flag | Short | Default | Description |
|---|---|---|---|
| `--debug` | `-d` | false | Verbose/debug logging |
| `--overwrite` | `-ow` | false | Overwrite output files if they exist |
| `--ls_environment` | `-e` | `PROD` | Locus Store environment (`PROD`, `DEV`) |

### Subcommands

| Subcommand | Purpose | Docs |
|---|---|---|
| `lomaps` | Generate LoMaps vector maps (offline) or online planet tiles | [docs/lomaps-subcommand.md](docs/lomaps-subcommand.md) |
| `overview_map` | Generate the global overview Mapsforge map (zoom 1–9) | [docs/overview-map-subcommand.md](docs/overview-map-subcommand.md) |
| `terrain_rgb` | Prepare terrain-RGB / bathymetry elevation tiles, HGT, S3 upload | [docs/terrain-rgb-subcommand.md](docs/terrain-rgb-subcommand.md) |
| `update_planet` | Download / incrementally update the OSM planet file | [docs/update-planet-subcommand.md](docs/update-planet-subcommand.md) |
| `clean_old` | Delete intermediate folders from previous generations | [docs/clean-old-subcommand.md](docs/clean-old-subcommand.md) |
| `storegeo` | (legacy) Generate Locus Store region boundaries | [docs/storegeo-subcommand.md](docs/storegeo-subcommand.md) |

`--help` is available on the root command and every subcommand:

```
java -jar OsmToolsBasic.jar lomaps --help
```

## Generation

### Required configuration
For every generation it is required to increase the Locus Store version of generated LoMaps (every version has an internal Locus Store id). Edit
`config/default_store_item_definition.json` and increase the `version.code` value by one.

### Start generation
Generation is driven by `lomaps --mode <offline|online>` — the mode expands to a fixed action pipeline (the old per-step `--actions` flag has been removed). Add `--release` to upload the result. Example:

```
java -jar OsmToolsBasic.jar lomaps --mode offline --version 2025.06.16 \
  --config_file config/config_2025.xml \
  --hgt_dir /mnt/backup/hgt/vectorMaps/hgt/ \
  --release --store_uploader /osm_tools/locusStoreUploader/locusStoreUploader.jar
```

See [docs/lomaps-subcommand.md](docs/lomaps-subcommand.md) for all options and the full offline/online pipelines.


## Locus Store Uploader
Generated maps are uploded into Locus Store using [locus-store-uploader](https://github.com/asamm/locus-store-uploader) - tool. This script is included in LoMaps generator folder. 

To update the generator - please build the project [locus-store-uploader](https://github.com/asamm/locus-store-uploader) and use the new .jar file for `--storeUploader` cmd parameter



