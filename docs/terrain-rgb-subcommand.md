# `terrain_rgb` subcommand

Prepares planet-coverage terrain-RGB elevation tiles (land from Mapterhorn, ocean floor from GEBCO), optionally converts them to HGT, and uploads to S3.

```
java -jar OsmToolsBasic.jar [global options] terrain_rgb [-t] [-b] [--generate_hgt] [-u]
```

Each flag is an independent step; combine them as needed. Output uses **Terrarium** RGB encoding.

---

## Flags

| Flag | Short | Step |
|---|---|---|
| `--terrain` | `-t` | Download Mapterhorn planet PMTiles, extract to `maxZoom`, simplify (round elevation + lossless WebP) → `planetFile` |
| `--bathymetry` | `-b` | Download GEBCO (elevation + TID), build ocean-floor terrain-RGB pyramid → `bathymetryPlanetFile` |
| `--generate_hgt` | | Convert the terrain PMTiles to SRTM-style HGT tiles → `hgtOutputDir` (SRTM-3 if source ≤ z11, else SRTM-1) |
| `--upload_to_s3` | `-u` | Upload the prepared PMTiles to S3 — uploads terrain if `-t`, bathymetry if `-b` (no-op + warning if neither) |

## Config reference

Loaded from `config/app_config.yaml` (kotlinx-serialization). Keys with a code default are optional in YAML; keys without one are required.

> ⚠️ The `terrainRgbConfig` and `onlineLoMapsConfig` sections are **not** in the committed `app_config.yaml` — they must be added to the runtime config (the full config is kept on the NAS). Adding a section means its required keys below must all be supplied.

**`terrainRgbConfig`**

| Key | Req. / default | Purpose |
|---|---|---|
| `planetFile` | required | Terrain-RGB output PMTiles |
| `gebcoElevationUrl` | required | GEBCO elevation NetCDF zip URL |
| `gebcoTidUrl` | required | GEBCO TID NetCDF zip URL |
| `maxZoom` | default 11 | Max zoom to extract |
| `mapterhornIndexUrl` | default (mapterhorn.com) | Mapterhorn download index |
| `mapterhornPlanetEntryName` | default `6-30-21.pmtiles` | Planet entry in the index |
| `mapterhornRawFile` | default `_planet/terrain_rgb/mapterhorn_raw.pmtiles` | Raw download |
| `hgtOutputDir` | default `_planet/terrain_rgb/hgt` | HGT output dir |
| `terrainResampling` | default `BILINEAR` | `NEAREST`/`BILINEAR`/`BICUBIC`/`LANCZOS` |
| `bathymetryPlanetFile` | default `_planet/bathymetry/bathymetry_terrain_rgb.pmtiles` | Bathymetry output |
| `bathymetryMaxZoom` | default 7 | Bathymetry max zoom |
| `gebcoWorkDir` / `gebcoElevationDir` / `gebcoTidDir` | `@Transient` (code-only, not YAML) | GEBCO work dirs |

**`onlineLoMapsConfig`** (S3 upload)

| Key | Req. / default | Purpose |
|---|---|---|
| `s3region` / `s3bucket` / `s3endpoint` | required | S3 target |
| `s3terrainRgbPath` / `s3terrainRgbPathDev` | required | Terrain upload prefix (Prod / Dev) |
| `s3bathymetryRgbPath` / `s3bathymetryRgbPathDev` | required | Bathymetry upload prefix (Prod / Dev) |
| `s3accessKey` / `s3secretKey` | env only (`S3_ACCESS_KEY`, `S3_SECRET_KEY`) | Credentials (`@Transient`) |

> Dev vs Prod prefix is selected by the global `-e/--ls_environment` flag. (This section also holds the `s3pmtiles*` keys used by `lomaps --mode online`.)

## Examples

```bash
# Land terrain only
java -jar OsmToolsBasic.jar terrain_rgb -t

# Ocean-floor bathymetry only
java -jar OsmToolsBasic.jar terrain_rgb -b

# Prepare both, generate HGT, and upload to S3 (Dev environment)
java -jar OsmToolsBasic.jar -e DEV terrain_rgb -t -b --generate_hgt -u

# HGT only (requires a previously prepared terrain planetFile)
java -jar OsmToolsBasic.jar terrain_rgb --generate_hgt
```