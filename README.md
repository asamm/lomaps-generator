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


## Generation

### Required configuration
For every generation is requited to increase a Locus Store version of generated LoMaps (every version has an internal Locus Store id). Edit file:
`config/default_store_item_definition.json` and increase by one the value `version.code`
 

### Start generation
To start generation run:

```
java -jar OsmToolsBasic_0.5.2.jar --actions dectag --email no --version 2021.09.24 --hgtdir ./hgt/ --type lomaps --storeUploader ./locusStoreUploader/locucStoreUploader_0.2.3.jar  
```

##### Parameters
- `--config_file` path to `config.xml` file - if not set, the generator will search for `config.xml` in current folder
- `--actions`: generation of LoMaps has several phases and generator can perform only specific ones. The letter defines action to perform during generation, when:
	- `extract` - extracts planet files into regions and countries
	- `contour` - contour lines (create map with contour lines)
	- `tourist` - marked trails - crate map with the marked trails
    - `address_poi_db` - create address database (and also POI V1 database for LM Classic)
    - `poi_db` - create POI database
	- `generate_mapsforge` - generate mapsforge maps for android
    - `generate_mbtiles` - generate mbtiles maps for iOS
    - `generate_mbtiles_online` - generate tourist, contours as additional source for standard openmaptiles 
    - `upload_maptiler` - upload generated online mbtiles to maptiler cloud
	- `upload` - upload maps to the Locus Store
- `--version` - used date in format yyyy.mm.dd (it's name of version in Locus Store and reflect how old are data used for generation
- `--hgt_dir` - path to folder with elevation data
- `--store_uploader` - path to the .jar file of locus store uploader

Sub command `type` - possible values
  - `lomaps` - generate lomaps
  - `storegeo` - used for generation country boundaries for Locus Store regions definition. It's very likely obsolete now


  

## Locus Store Uploader
Generated maps are uploded into Locus Store using [locus-store-uploader](https://github.com/asamm/locus-store-uploader) - tool. This script is included in LoMaps generator folder. 

To update the generator - please build the project [locus-store-uploader](https://github.com/asamm/locus-store-uploader) and use the new .jar file for `--storeUploader` cmd parameter



