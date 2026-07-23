  # Online LoMaps generation — weekly scheduled release (Ubuntu)

Runs the **online** LoMaps generation with release every week on **Tuesday at 03:00**
using a systemd service + timer. In online mode the pipeline produces planet-level
PMTiles and, with `--release`, publishes them to S3.

Set up as **systemd user units** running under the `osmtools` account — no `sudo`
required (except a one-time `enable-linger`, see section 5).

---

## 1. The generation command
Online release is a single invocation of the `lomaps` subcommand:

```bash
java -jar OsmToolsBasic.jar lomaps --mode online --version <yyyy.MM.dd> --release
```

- `--mode online` → pipeline: `tourist → contour → overview_map → generate_pmtiles`
- `--release` → adds `upload_s3` (publishes PMTiles to S3)
- `--version` → map version string, format `yyyy.MM.dd`

Before generating, keep the planet file fresh:

```bash
java -jar OsmToolsBasic.jar update_planet
```

> Target the DEV store/S3 with the global flag `-e DEV` before the subcommand
> (default is `PROD`).

---

## 2. Wrapper script

systemd runs a script rather than the raw `java` line, so the version can be set
from the current date and the planet can be refreshed first. The Gradle build
generates this script for you — no need to maintain it by hand.

Build the jar together with the wrapper:

```bash
./gradlew :osmToolsBasic:buildWithOnlineReleaseShScript
```

This produces two scripts in `osmToolsBasic/build/libs/` next to `OsmToolsBasic.jar`:

- `clean_old.sh` — purges intermediate folders from the previous generation
  (incl. PMTiles), via the `clean_old` subcommand.
- `generate_online_release.sh` — refreshes the planet, generates online tiles,
  and publishes to S3.

The service runs them as two sequential actions (see section 3). The main
generation script:

```bash
#!/bin/bash
set -euo pipefail
# Export paths to find pyhgtmap / pyosmium tools
export PATH=$PATH:/home/osmtools/.local/bin
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR" || exit 1

# Defaults
VERSION="$(date +%Y.%m.%d)"                              # map version, yyyy.MM.dd (default: today)
HGT_DIR="${HGT_DIR:-/osm_tools/hgt/vectorMaps/hgt/}"     # HGT elevation data (override via env)

usage() { ...; }   # -h/--help prints usage

# Parse arguments: [VERSION] | -v/--version VER | -h/--help
while [[ $# -gt 0 ]]; do
    case "$1" in
        -h|--help)    usage; exit 0 ;;
        -v|--version) VERSION="$2"; shift 2 ;;
        -*)           echo "Unknown option: $1" >&2; usage >&2; exit 1 ;;
        *)            VERSION="$1"; shift ;;
    esac
done

# 1) refresh the OSM planet file
java -jar "$SCRIPT_DIR/OsmToolsBasic.jar" update_planet
# 2) generate online planet tiles and publish to S3
java -jar "$SCRIPT_DIR/OsmToolsBasic.jar" lomaps --mode online --version "$VERSION" \
    --hgt_dir "$HGT_DIR" \
    --release
```

Deploy the jar and the generated script into the `osmtools` install directory
(e.g. `/home/osmtools/lomaps-generator/`). It resolves the jar relative to its
own location, so it can live anywhere as long as `OsmToolsBasic.jar` sits beside it.

**Manual runs** — the script takes an optional version (default: today's date):

```bash
./generate_online_release.sh                  # version = today
./generate_online_release.sh 2026.07.23       # explicit version (positional)
./generate_online_release.sh -v 2026.07.23    # explicit version (flag)
./generate_online_release.sh --help           # usage
```

> **S3 credentials** — `--release` uploads to S3, which needs `S3_ACCESS_KEY` /
> `S3_SECRET_KEY`. Provide them via the service unit's `EnvironmentFile`
> (see section 3).

> The related task `createPlanetUpdateShScript` / `buildWithPlanetUpdateShScript`
> generates only the `update_planet` wrapper — use it if you refresh the planet
> on a separate schedule.

---

## 3. systemd service unit (user)

All units live under the `osmtools` home — no `sudo`, no `User=` line. The service
runs **two actions in order**:

1. `clean_old.sh` — purge the previous generation's intermediate folders.
2. `generate_online_release.sh` — refresh planet, generate online tiles, publish to S3.

First put the S3 credentials in `~/.config/lomaps-generator.env` and lock it down:

```bash
mkdir -p ~/.config/systemd/user
cat > ~/.config/lomaps-generator.env <<'EOF'
S3_ACCESS_KEY=your-access-key
S3_SECRET_KEY=your-secret-key
EOF
chmod 600 ~/.config/lomaps-generator.env
```

Create `~/.config/systemd/user/lomaps-online.service`:

```ini
[Unit]
Description=LoMaps online generation + release
Wants=network-online.target
After=network-online.target

[Service]
Type=oneshot
WorkingDirectory=/osm_tools/vectorMaps
EnvironmentFile=/home/osmtools/.config/lomaps-generator.env
# Action 1 — clean the previous generation
ExecStart=/osm_tools/vectorMaps/clean_old.sh
# Action 2 — generate online tiles and release to S3
ExecStart=/osm_tools/vectorMaps/generate_online_release.sh
# generation is long-running; no runtime limit
TimeoutStartSec=0
```

> With `Type=oneshot`, multiple `ExecStart=` lines run **sequentially**; if the
> clean step fails, the service fails and generation does **not** run.

> No `User=` — a user unit already runs as `osmtools`, with that account's PATH
> and tools (osmium, java, pyhgtmap, …).

---

## 4. systemd timer unit (user)

Create `~/.config/systemd/user/lomaps-online.timer`:

```ini
[Unit]
Description=Run LoMaps online generation weekly (Tuesday 03:00)

[Timer]
# Tue 03:00 local time, every week
OnCalendar=Tue *-*-* 03:00:00
Persistent=true

[Install]
WantedBy=default.target
```

- `OnCalendar=Tue *-*-* 03:00:00` → every Tuesday at 03:00.
- `Persistent=true` → if the machine was off at the scheduled time, the run starts
  once it boots again.
- `WantedBy=default.target` — the user-unit equivalent of `timers.target`.

---

## 5. Enable and verify

Everything below runs as `osmtools` — **no `sudo`**.

```bash
# reload unit definitions after creating/editing files
systemctl --user daemon-reload

# enable + start the timer (service is triggered by the timer, not enabled directly)
systemctl --user enable --now lomaps-online.timer

# check the next scheduled run
systemctl --user list-timers lomaps-online.timer

# trigger a manual run right now (does not affect the schedule)
systemctl --user start lomaps-online.service

# follow logs of the last/current run
journalctl --user -u lomaps-online.service -f
```

### One-time: keep the timer alive without a login

A user's systemd instance normally stops when the user logs out, so the timer
would not fire at 03:00 if nobody is logged in. Enable **lingering** once so the
`osmtools` instance runs at boot:

```bash
loginctl enable-linger osmtools
loginctl show-user osmtools --property=Linger   # want: Linger=yes
```

> This is the **only** step that may need elevated rights — polkit will prompt,
> or an admin runs `sudo loginctl enable-linger osmtools` once. After that, all
> service/timer management is fully in the hands of `osmtools`.

---

## Notes

- The timer uses **local system time**. Verify with `timedatectl` that the timezone
  is what you expect (e.g. `Europe/Prague`).
- For a dry run without publishing, remove `--release` from the generated
  `generate_online_release.sh` (or run the `lomaps --mode online` command by hand).
- `--release` for online mode only uploads to S3; it does **not** require
  `--store_uploader` (that is offline-mode only).
