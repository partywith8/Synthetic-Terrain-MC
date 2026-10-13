# STM internals

## Data flow

MC stores real blocks. Up to eight analytic events describe a column's vertical
motion; the server sends field changes rather than per-frame block updates.
Java collision and GLSL displacement use matching equations. Each cube moves
rigidly, so raycasts, mining and rider support share its translated position.

`epd:lab` scopes all hooks. Other dimensions retain native behavior. UUID-keyed
return positions are stored inside the current world. No telemetry, external
services, hard-coded hosts, account credentials or machine paths are used by the mod.

## Modules

| Module | Owns |
| --- | --- |
| `DemoCommands` / `DemoController` | Commands, lifecycle and event scheduling. |
| `MotionField` / `MotionEvent` | Immutable field and displacement functions. |
| `MovingTerrain` / `TerrainRaycast` | Collision, floor probes and DDA raycasts. |
| `TerrainMeshes` | Snapshots, worker, GPU buffers and replacement coverage. |
| `TerrainRenderer` / `MotionUniforms` | Culling, draw calls and UBO data. |
| `TerrainRegeneration` | Bounded native block restoration. |

One mesh job runs at a time. Native meshes stay visible until replacements are
ready. Vertex-buffer residency is capped at 1 GiB. Candidate scans back off while
idle; dirty chunks wake work immediately. Frustum culling skips off-screen meshes.
Snapshot capture and GPU upload still cost client-thread time during warm-up.

## Checks

- `./gradlew build`: 2,331,225 deterministic checks, including reset continuity,
  overlap budgets and stress coverage. Sampled stress speed: 0.347 blocks/tick.
- `python tools/validate_gpu.py` (Windows/OpenGL): 6,144 vertex comparisons;
  observed max Java/GLSL error <0.000036 blocks. Requires the Gradle test output.
- `python tools/check_client_mixins.py --help`: dependency paths supplied by the
  caller; checks class transformations without launching a game window.
- `/stm selftest`: native mining, placement, raycasts, packet round trips,
  1,035 rider samples and 10,240 support/overlap comparisons.
- `/stm benchmark`: bounded floor-query CPU/allocation comparison against the
  shape-based reference. Results go to the game log.

The reviewed direct floor probe measured 167 ns/query versus 733 ns/query, with
0 versus 2,124 allocated B/query after JVM warm-up. This is a microbenchmark,
not an FPS or whole-server MSPT claim. Client visual checks, controlled frame-time
captures and SP runtime validation remain separate checks before a stable release.

For frame-time comparisons, wait for ready/loaded counts to match, keep camera and
settings fixed, then compare idle vs `/stm stress`. F3+L records a 10 s capture.
Debug CPU-stage timings are not total frame or GPU time.
