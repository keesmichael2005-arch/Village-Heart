# Testing layers

| Layer | What | Command | Time |
|---|---|---|---|
| 1 | Unit tests on `core/` (pure logic) | `./gradlew build` | seconds |
| 2 | GameTests: headless server, real blocks/entities/ticks | `./gradlew runGameTest` | ~1 min |
| 3 | Long simulation runs: seeded world, claim a real village, fast-forward days, write metrics JSON | `scripts/simulate.sh <scenario> [days]` (by hand, not in verify) | ~1 min per in-game day |
| 4 | Client GameTests: real game on Xvfb, scripted, screenshots | `xvfb-run -a ./gradlew runClientGameTest` | ~2 min |

`scripts/verify.sh` runs layers 1, 2 and 4 (plus a stubbed self-test of the layer 3 script) and writes `reports/verify-summary.md` plus `reports/screenshots/`.
GitHub Actions runs the same script on every push.

Proven at setup: a deliberately broken GameTest fails the run (exit 1), so a green run means something.

## GameTests that touch the village claim (M1+)
The claim is global (one per world) and vanilla runs GameTests of the same test environment at the same time, so
claim tests would interfere. **Rule:** every test that reads or writes the claim declares its own environment,
`@GameTest(environment = "villageheart-gametest:<name>")`, backed by a JSON file
`src/gametest/resources/data/villageheart-gametest/test_environment/<name>.json` containing
`{"type": "minecraft:all_of", "definitions": []}`. It must call `VillageTestSupport.reset(h)` first and
`VillageTestSupport.done(h)` last. Use `VillageTestSupport.player(h, name, mode)` for players (a real `ServerPlayer`; the
vanilla mock player is always creative). `reset` clears the claim, leftover players, emerald items, the block-drops gamerule, and all POIs
(bells) within 48 blocks of the test, because bells of neighbouring or failed tests otherwise leak into "no bell nearby" checks.
Approach used in M1: custom environments (the documented single-sequence fallback was not needed). Probe tests `isolationProbeA/B` guard it.
Tests that wait need `maxTicks` set on the annotation (the default is short).

## Per-tick mod work and the 1 ms/tick budget (M2+)
All mod per-tick work must run either inside a `VillageSystem` (registered with `VillageSystems.register(id, system)`; runs once per
server tick while a claim exists) or inside `try (var t = VillageTicker.section()) { ... }` (for worker behaviour inside entity ticks).
`VillageTicker.TIMER` adds the sections of one tick into one sample; layer 3 reports its mean/p95/max per day as `village_ms.*`.
A separate tick listener, or behaviour code outside a section, escapes the budget measurement. GameTests T-L2-1 / T-L2-1b prove the hook measures real work.

## Layer 3: simulations
`scripts/simulate.sh <scenario> [days] [--assert "<expr>"]... [--reload-at <day>]` (`--list` shows the scenarios). It boots a real headless
dedicated server (`./gradlew runSimulation`, a Loom run config over the `gametest` source set, run dir `build/run/sim`) on seed 1, finds the plains
village, force-loads it, joins a fixed creative fake owner (`SimOwner`), runs the scenario's setup actions (the `claim` action places the Heart
through the real `useItemOn` path), sets the time to dawn, records a baseline and sprints N days (24000 ticks each). Output:
`reports/sim/<scenario>.json` (one record per day, schema in `core.sim.MetricsJson`), `.summary.md`, `.log` (`.2.log` after a reload).
Expect about 50 s per in-game day on 4 cores; a 5-day run is 5-6 minutes. The script's timeout is `240 + 150 * days` seconds.

| Exit | Meaning |
|---|---|
| 0 | `PASS`, `clean_shutdown`, N day records, no crash report, Gradle rc 0 |
| 1 | `FAIL`: an assertion was false (red is red) and everything else was healthy |
| 2 | `ERROR`: crash, hang, compile failure, timeout, stalled or missing report, bad assertion text, lock held. Gradle's own exit code is never trusted: a server crash still ends it with 0 |
| 64 | usage error (unknown scenario, `days` not 0..365, `days 0` with `--assert`, bad `--reload-at`) |

Concurrency: every run kills only what it started (its process group, plus any process carrying its private `SIM_RUN_TOKEN`), uses its own server port, and locks `$SIM_DIR/.lock`. Different worktrees (each with its own `build/run/sim`) can simulate at the same time; a second run in the same directory is refused (exit 2). On a crash, `simulate.sh` rewrites the report itself: `status: ERROR`, `clean_shutdown: false`, `crash_reports: [...]`.

`days = 0` is a smoke run (boot, locate, claim, baseline, stop; ~35 s). `--reload-at D` stops the server cleanly after day D (tick order: record
the day, run due actions, write, halt), restarts on the same world and finishes the run; `reloads` in the report is `[D]`.
Env: `SIM_TIMEOUT_SECONDS`, `SIM_STALL_SECONDS` (report must change at least this often, default 600), `SIM_START_SECONDS` (default 180), `SIM_GRADLE_CMD`.
`scripts/test-simulate.sh` (run by `verify.sh`) checks this exit-code contract against a stub server (`scripts/testdata/fake-sim.sh`).

Scenarios are `sim/scenarios/<name>.json` (`ScenarioFilesTest` parses every file in L1). Fields: `schema` 1, `name` (= file name), `description`,
`default_days`, optional `seed`, `actions` (`action`, optional `day` (0 = setup, before the baseline) and `tick` 0..23999), `assertions`.
Assertion language (`core.sim.Assertion`): `population >= population@day1 - 1 on every day`, `sum(deaths.total) <= 1`, `mean(village_ms.mean) < 1 from day 2`.
Scopes: `on every day | on last day | on day N | on days A..B | from day N` (default: last day). Day refs: `@day1 @first @last @prev` (not inside a
function). Functions: `sum mean min max`. An empty or out-of-range scope is a FAIL, never a pass; an unknown metric is a parse error.

How to extend (each one place + a test):
- **A scenario:** drop a JSON file in `sim/scenarios/`; L1 parses it. The self-tests `selftest_error|crash|hang` must end in exit 2 (harness checks).
- **An action type:** a `SimAction` in `dev.villageheart.sim.SimActions.ALL` plus the id in `ScenarioParser.ACTION_TYPES` (a GameTest keeps the two in sync).
  M2 has `claim` (optional `offset` from the bell), `command` (a server command run at the Heart; `~` is relative to it; failure is an ERROR), `fail`, `crash`, `hang`.
- **A metric:** a field in `core.sim.DayMetrics` + its `flatten()` entry + `MetricNames.KNOWN`, the JSON read/write in `MetricsJson`, and the source in `SimMetricsCollector`.
- **An assertion function:** `core.sim.AssertionFunctions` + a test in `AssertionTest`.
Report JSON keys are snake_case and pinned by `MetricsJsonTest.pinnedKeys`.
Villager AI is not seeded, so runs differ (a night-one zombie attack can kill villagers). Write assertions with slack; never loosen one silently.

## Markers, generated assets and the model-log gate (M3+)
- **Generated assets.** Everything for markers and job blocks (blockstates, models, item definitions, loot tables, recipes, recipe-unlock
  advancements, the axe-mineable tag and all 16x16 PNGs) is produced from the `core.building` enums by `src/test/java/dev/villageheart/tools/AssetGenerator.java`
  (art in `MarkerArt` / `JobBlockArt`). After changing a `BuildingType`/`JobBlockType`, the art or `Balance.BLANK_MARKER_YIELD`, run
  `./gradlew --no-daemon genAssets` and commit the output; it also writes a x8 contact sheet to `build/asset-preview/markers.png`
  (look at it). `GeneratedAssetsTest` (L1) fails on any hand edit, stale file or orphan (JSON byte-for-byte, PNG pixel-for-pixel) and on unreadable
  art (glyph distance, contrast, status only on the frame). Lang (`en_us.json`) stays hand-written; `LangCoverageTest` checks it.
- **Marker GameTests** (`MarkerGameTests`) all use environment `villageheart-gametest:markers`, never call `VillageTestSupport.reset`, create players with
  `MarkerTestSupport.player` and remove only their own (`removePlayer`), and click through `MarkerTestSupport.clickFace`, not `placeAt` (which clicks the
  air in front of the block). Tests that check drops first assert the `block_drops` gamerule is on.
- **Model-log gate.** `scripts/check-model-log.sh <log>` fails (exit 1) when the client log has a `Missing textures in model`, `Missing block model`,
  `Missing model for variant` or `Unable to bake model` warning about `villageheart`; exit 2 if the log is missing or empty. `verify.sh` runs it on
  `reports/clienttest.log` after layer 4 and self-tests it (`scripts/test-check-model-log.sh`, sample logs in `scripts/testdata/`) in both modes.


## Room detection tests (M4+)
- **Core** (`src/test/java/dev/villageheart/core/room/`): `FakeGrid` is a sparse test world (unset = air) with `hollow`/`box`/`door` builders and a
  never-stale sky cache; the standard fixture `room5x5x3()` has its marker at `FakeGrid.MARKER`, outward NORTH. `RoomScannerCostTest` (normal `test`) pins the scan cost
  deterministically by counting `cell()` / `openToSky()` calls (16x16x8 and the 4097-cell cap case). The wall-clock limits (16x16x8 under 5 ms, cap worst case under 10 ms) are in
  `RoomScannerPerfTest`, tagged `perf`: excluded from `test`/`build`, run by `./gradlew --no-parallel perfTest`, which `verify.sh` runs as its own last step so nothing competes for the CPU.
  It times batches of 30 scans with 100 ms pauses until one batch median is under the limit (budget 50 batches / 5 s) and prints `perf ...` lines (copied into the verify summary).
- **Room GameTests** (`RoomGameTests`) **must declare `@GameTest(skyAccess = true)`**. The test framework encases the 8x8x8 structure in barrier blocks, on top
  too unless the test has sky access; with a barrier ceiling a roof hole is "enclosed" and every leak test passes (or here: fails) for the wrong reason.
  Leave a free strip at least 2 deep in front of the door wall, because the barrier wall at z = -1 would make a 1-deep strip an alley. Mutation-checked:
  removing `skyAccess` from `brokenRoofBlockReported` turns it red.
- `LevelBlockView` never loads chunks and `RoomScans.scan` throws off the server thread (`getChunkNow` returns null on other threads).
