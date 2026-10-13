# Synthetic Terrain MC

**STM** is a Fabric tech demo: mineable MC blocks with continuous terrain motion,
shared collision and GPU rendering. Walk across waves, raise mountains or move
an entire view of terrain.

## Showcase

**Stress test** · 15 FPS

![Coordinated terrain movement with F3](docs/showcase/stress-test.gif)

**Tiles + mining** · 20 FPS

![Moving tiles and block mining](docs/showcase/mining.gif)

Full clips, each <25 MB. GIF FPS is separate from the in-game FPS shown.

## Install

[Download STM](dist/SyntheticTerrainMC.zip) · MC **26.2** · Java **25** ·
Fabric Loader **0.19+** · Fabric API **26.2**

Put the mod JAR in `mods/` on client and server. Install Fabric API separately.
Do not install the `-sources.jar`.

## Commands

| Command | Action |
| --- | --- |
| `/stm enter` / `leave` | Enter STM / return to your entry point. |
| `/trigger <type> [radius]` | Start motion; radius 8–128, default 48. |
| `/stm auto on\|off` | Toggle ambient motion. |
| `/stm hold on\|off` | Keep new formations raised. |
| `/stm reset` | Settle motion over 12 s; keep block edits. |
| `/stm regenerate [radius]` | **Erase edits** in loaded chunks; radius 0–8, default 4. |
| `/stm stress [seconds]` / `stress stop` | Full-view swells, 15–120 s. |
| `/stm debug on\|off` / `status` | Private CPU/cache/network stats. |
| `/stm selftest` / `benchmark` | Native checks / CPU probe; requires an empty STM dimension. |

Types: `wave`, `ripple`, `mirage`, `mountain`, `ridge`, `ravine`, `tiles`, `terraces`.
Changing shared terrain requires OP. `/epd` remains an alias; the internal `epd`
ID stays stable for save compatibility. Vanilla scoreboard `/trigger` still works.

## Build

JDK 25: `./gradlew build` or `gradlew.bat build`. JARs: `build/libs/`.
`python tools/package_release.py` creates the current download and SHA-256 sums.
Java: clang-format 22. Python: Black.

## Scope

White concrete and calcite move as vertical columns, within loaded chunks.
Mining/build edits persist; motion resets on server restart. No fluids, horizontal
building motion or shader-pack integration. SP runtime validation is pending.

[Architecture, tests and perf](docs/TECHNICAL.md) ·
[Issues](https://github.com/partywith8/SyntheticTerrain-MC/issues)

All rights reserved. See [LICENSE](LICENSE).
