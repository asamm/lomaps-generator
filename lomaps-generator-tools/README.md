# LoMaps Tools

Custom tools for generation of Mapsforge and MVT LoMaps.

## Project Utils

The package contains several modules:
- `lomaps_tools`: Core functionality
- `shp2osm`: Utilities for converting shape files to OSM format
- `tourist`: Tools for parsing tourist,cycling relation and generate ways from original relation because Mapsforge does not support relations
- `update`: for update of OSM planet file

## Installation

### From Source

```bash
git clone #TODO path of repo
cd lomaps-generator-tools
pip install -e .
```
## Usage
After installation, you can use the command-line tool:

```bash
lomaps-generator-tools --help
```

