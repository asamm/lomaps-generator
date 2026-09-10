# Changelog
All notable changes to this project will be documented in this file.

## [1.0.1] - 2026-06-17

### Changed
- uploader of online PMTiles uses custom bbox dev settings for upload a smaller tile set to S3 for testing, instead of the whole planet.
- POI V2 database initialization is lazy — the init script runs once per process instead of relying on a mutable flag.

## [1.0.0] - 2026-06-08

Major reworking of how maps are generated. Highlights:

### Added
- **`overview_map` subcommand + overview-map system** — builds a global overview Mapsforge map (zoom 1–9) from Natural Earth (GeoPackage + base-map SHP) and RESOLVE ecoregions, converted to OSM PBF (new GPKG/SHP readers, centerline extractor, `OsmPbfWriter`) and merged into the planet.
- **`terrain_rgb` subcommand + elevation module** — terrain-RGB tiles from Mapterhorn, GEBCO **bathymetry** (ocean-floor) tiles, HGT export, and S3 upload.
- **Online generation** — planet **PMTiles** via planetiler, S3 versioned publishing (`OnlinePlanetVersionsManager`), and PMTiles→MBTiles extraction.
- **Bundled mapsforge-tiler** (composite build) writing planet & per-region Mapsforge maps directly; per-region maps now **batch-extracted from the planet map**.
- **Residential-area layer** built from a GeoPackage source.
- Log rotation.

### Changed
- **CLI** — a single `--mode offline|online` replaces the pe    r-step `--actions` flag; `--release` alone drives upload (Locus Store for offline, S3 for online).
- **Pipeline** split into `PlanetPipeline` → `MapPipeline` → `PostPipeline`.
- Generation scoped to **tile coverage** (POI V2, metadata, `maxBaseZoom`).
- Whole-planet generation via `--bounds=world` instead of a world polygon.
- Much of the codebase converted **Java→Kotlin** (e.g. `ItemMap`).
- [data-writer] improved processing of invalid OSM geometries when generating address DB.

### Removed
- Coastline/ocean SHP handling, the separate `merge` action, and obsolete XML config parameters.

## [0.8.0] - 2026-03-27

### Added
- add generator off terrain-rgb tiles from elevation .hgt files

### Changed 
- [data-writer] - improve procession of invalid OSM geometries when generate address DB