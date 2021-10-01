# LoMaps generator
Command-line tools for generation LoMaps and offline POIs.

## Installation
The steps described below expect installation on Ubuntu 

### Required SW


- install [osmium](https://osmcode.org/osmium-tool/) used for extraction planet file to the countries and states

	```sudo apt-get install osmium-tools```
	
- install [phyghtmap](http://katze.tfiu.de/projects/phyghtmap/) used for generation of contour-lines from elevation hgt files.  

	```sudo apt-get install phyghtmap```
	
- install [ogr2ogr](http://katze.tfiu.de/projects/phyghtmap/) set of tools. Generator uses ogr2ogr to cut global SHP files with coastlines into states areas

	```sudo apt-get install ogr2ogr``` 
	
- install [spatialite](https://www.gaia-gis.it/fossil/libspatialite) spatialite libraries to generate Address and POI database
	```
	sudo apt-get install spatialite-bin
	sudo apt-get install libsqlite3-mod-spatialite
	``` 
	
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
- actions: generation of LoMaps has several phases and generator can perform only specific ones. The letter defines action to perform during generation, when:
	- d - downloads planet file
	- e - extracts planet files into regions and countries
	- c - contour lines (create map with contour lines)
	- t - marked trails - crate map with the marked trails
	- g - generate - do generation itself
	- u - upload maps to the Locus Store
- version - used date in format yyyy.mm.dd (it's name of version in Locus Store and reflect how old are data used for generation
- hgtdir - path to folder with elevation data
- storeUploader - path to the .jar file of locus store uploader
- type - possible values 
	- `lomaps` - generate lomaps
	- `storegeo` - used for generation country boundaries for Locus Store regions definition. It's very likely obsolete now


  

## Locus Store Uploader
Generated maps are uploded into Locus Store using [locus-store-uploader](https://github.com/asamm/locus-store-uploader) - tool. This script is included in LoMaps generator folder. 

To update the generator - please build the project [locus-store-uploader](https://github.com/asamm/locus-store-uploader) and use the new .jar file for `--storeUploader` cmd parameter



