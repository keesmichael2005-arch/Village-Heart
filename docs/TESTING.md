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

## Layer 4 and Fabric's NetworkSynchronizer (lost-wakeup fix)
Fabric's client-gametest framework (fabric-client-gametest-api-v1 4.3.5, the newest for 1.21.11) counts in-flight packets so that every
tick ends with all packets handled. Its `NetworkSynchronizer.waitForPacketHandlers` checks "a packet is in flight and nothing is queued"
without the lock, then parks on `await(10 s)` without checking again. If the last packet is handled in between, the signal is lost, the
thread sleeps 10 s, the synchronizer marks itself invalid and the run dies with `AssertionError: Network synchronizer in invalid state`
plus a server crash. It hit on the server thread's first tick of a new world (the client's login packet races it, and only that thread could
make progress), more often under CPU load. Fix: the gametest-only, client-only mixin `dev.villageheart.gametest.mixin.NetworkSynchronizerLostWakeupFix`
(`villageheart-gametest.mixins.json`) wraps that `await`: it re-checks the predicate before parking and parks in 20 ms slices, keeping
Fabric's 10 s failure for a packet that really never arrives. It logs `LOST_WAKEUP_FIX installed` once and `LOST_WAKEUP_RESCUED` per rescue;
`verify.sh` fails if the first is missing and reports the rescue count. Do **not** set `fabric.client.gametest.disableNetworkSynchronizer`:
that would drop the "packets handled by the end of the tick" guarantee the client tests rely on. Remove the mixin when moving to a Fabric
version without `NetworkSynchronizer` (the mixin then no longer compiles; if the class stays but the `await` call changes, the
`required` injector stops the client at start-up, so it cannot silently stop working).

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

## Building GameTests (M5+)
- **Rules** (`BuildingGameTests`, helpers in `BuildingTestSupport`): every test touches the claim, so each declares its own environment `villageheart-gametest:buildings_<name>`
  (JSON `{"type": "minecraft:all_of", "definitions": []}`), uses the structure `villageheart-gametest:yard` (32x28x32, `gametest/structure/yard.snbt`, one air block, **`palette` required**:
  without it the template silently fails to load and the test dies with "Failed to place test structure") and `skyAccess = true` (rooms are scanned; see the room-test notes above).
  Start with `BuildingTestSupport.begin(h)` (reset, fresh runtime, **sweep stray markers**, bell, claim after 3 ticks) and end with `VillageTestSupport.done(h)`.
- **Stray markers.** Test structures sit only a few dozen blocks apart, inside each other's claim radius, so markers left by other tests would be adopted by this test's claim and change the
  number of registered buildings (and the round-robin timing). `begin` removes every marker in the loaded chunks within 160 blocks before the test builds anything; tests that claim by hand call `sweepStrayMarkers`.
- **Measured waits.** `awaitWithin(h, seq, bound, what, check)` re-runs `check` every tick and then asserts it took at most `bound` ticks. `bound(n, hops) = n*20 + hops + 4`: a change that fires no hook is
  seen within `n x 20` ticks (`n` registered buildings), one more tick per dependent hop (site to parent), plus slack. The roadmap's "within 2 s" test (Storage green / red) uses 40 with `n = 1`. State `n` in the test.
- **Chat.** `recordingPlayer` is a `ServerPlayer` subclass that collects `sendSystemMessage`; read keys and fallbacks through `TranslatableContents` (`translatable`, `lineBody`).
  Right-click with `rightClick(...)` (`player.gameMode.useItemOn`, the main hand holds the stack so the sneak/hand rule works).
- **Far markers** (outside the claim, unloaded): 100 000 blocks along +z of the Heart; load with `level.getChunk` and keep with `setChunkForced`, clean up in the last step. Never load chunks from the code under test.
- **Running one GameTest:** `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:<class_snake>_<method_snake>" ./gradlew --no-daemon runGameTest`
  (exact id, e.g. `building_game_tests_shared_room_both_red`; a regex or prefix selects nothing and the run "passes" with 0 tests).
- L4 `MarkerClientTests.validationReport` (`marker_validation.png`): claims by `VillageClaims.claim` (a `/setblock` Heart never runs `setPlacedBy`), builds two rooms with commands, waits until the
  server says green and red, aims at the red plaque (asserts `mc.hitResult` is on it, so it cannot click air), presses the right mouse button and shoots the chat.
  The player must be within the block reach (about 5), so FOV is 100 to get both plaques into the frame.

## Stockpile tests (M6+)
- **Core** (`src/test/java/dev/villageheart/core/stock/`): `FakeInventory` (strings as items, per-key limits, refuse sets, call counters) feeds `StockpileTest`
  (routing, fill order, spill, limits, reservations), `StorageSetTest`, `StorageAlertTest`. `StockpileCostTest` pins the work deterministically by counting slot reads
  (a spill across 64 sites reads each slot at most twice); the wall-clock budgets are in `StockpilePerfTest` (tag `perf`, run by `perfTest`):
  snapshot of 13 824 slots under 3 ms, full-storage deposit under 1 ms, a deposit spilling across 64 sites under 3 ms.
- **Glue GameTests** (`StockpileGameTests`, environment `villageheart-gametest:stockpile_glue`, no claim, no buildings): build a stockpile from hand-placed chests with
  `StockpileTestSupport.siteOf` (it goes through `ContainerBinder`, like the real code). Cover double chests, components, comparator update, unloaded/replaced positions.
  A comparator's `FACING` points at its input, so a comparator reading the chest to its west faces WEST.
- **Facade GameTests** (`StockpileFacadeGameTests`): follow the Building GameTests rules (own environment `stockpile_<name>`, `yard`, `skyAccess`, `begin`/`done`, `awaitWithin`).
- **Rule: stockpile code never calls `level.getBlockEntity`** (it loads the chunk synchronously on the server thread); it uses `StorageContainers.at` (`getChunkNow` + `LevelChunk.getBlockEntity`).
- **Budget** (docs/plans/M6.md 3.4): one snapshot (= one `StockpileSystem` refresh) of 32 full containers under 1 ms (asserted at 3 ms in `snapshotCostReal` because it shares a machine with other work), a deposit/withdraw under 0.3 ms.


## Worker GameTests (M7+)
`WorkerGameTests` (helpers in `WorkerTestSupport`, building helpers from `BuildingTestSupport`). Rules learned the hard way while building M7:
- **Environment per test** `villageheart-gametest:workers_<name>` (JSON `{"type": "minecraft:all_of", "definitions": []}`): the day time and the claim are global. Tests that need rooms use the `yard` structure, `skyAccess = true`, `BuildingTestSupport.begin(h)`, and `VillageTestSupport.done(h)` last (its `reset` also releases every POI ticket the mod holds, restores `JobSiteSource.DEFAULT` and discards the villagers the test spawned). Call `WorkerTestSupport.begin(h)` first (turns time on, remembers the day time) and `end(h)` last (restores it).
- **Time:** set the day time with `level.setDayTime(...)` and let it advance; assert it advanced (a check at tick 0 sees the old time). Tests that depend on vanilla job acquisition set 2000 first.
- **POIs are added and removed one `server.execute` later** than the block change: wait for `poiPresent` before reserving or asserting on a POI. A POI ticket leaked by a crashed run stays in the POI file.
- **Never call `runAfterDelay` / `thenExecuteAfter` from inside a callback** (it modifies the map `GameTestInfo.tickInternal` iterates: duplicated callbacks, then an NPE that crashes the server). Schedule every step up front from the test body or use `startSequence()` (loops that add `thenExecuteAfter` steps from the body are fine).
- **Villagers walk, open wooden doors and wander.** Position assertions of the kind "it has not reached X before time T" need a pen with an **iron door** (villagers cannot open it; the test opens it with `openDoor`). "It stays in the room" cannot be asserted; use controls instead (the same layout with the marker removed must let vanilla take the composter).
- **Framework tests without a claim** make workers with `WorkerTestSupport.hireDirect`, which exempts the villager from the orphan sweep (a worker with no registry record is restored to vanilla when it loads). Everything else uses `Workers.hire` into a real valid building.
- **Night tests** light the area (`light(...)`, torches every 4 blocks): the test level is NORMAL difficulty.
- **Breeding** (`workersCanBreed`) seeds `BREED_TARGET` every 50 ticks in the wake-up window and gets three windows: vanilla's `InteractWith` is random and its gate sometimes aborts.
- Run a flaky-suspect test alone and repeatedly: `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:<class_snake>_<method_snake>" ./gradlew --no-daemon runGameTest` (exact id).
- Mutation-checked: skipping the `MOB_CONVERSION` profession reset turns `workerDeathAndConversion` red.
- **Layer 4** `WorkerClientTests.workersAtJobBlocks` (`workers_at_job_blocks.png`): nine workers at their job blocks and a vanilla villager; asserts each client-side `VillagerData` profession before the shot. The profession textures are generated (`ProfessionArt`, `./gradlew genAssets`, preview in `build/asset-preview/professions.png`); the glyph sits on the lower robe because the crossed arms hide the chest. `scripts/check-model-log.sh` also fails on `Missing resource villageheart:...` (what a profession without its PNG logs).

## Lumberjack GameTests (M9)
`LumberjackGameTests` (helpers in `LumberTestSupport`, building and worker helpers from `BuildingTestSupport` and `WorkerTestSupport`). The class must be listed under `fabric-gametest` in `src/gametest/resources/fabric.mod.json` (an unlisted class runs 0 tests and a filter on it still "passes"). Rules:
- Each test has its own environment `villageheart-gametest:lumber_<name>` (a JSON file like the worker ones), structure `yard`, `skyAccess = true`, `BuildingTestSupport.begin` first (through `LumberTestSupport.scene(h, twoChops, storage)`, which also sweeps, lays grass and waits for a valid camp and Storage) and `VillageTestSupport.done` + `WorkerTestSupport.end` last.
- **The ground is flat grass at relative y = -1** and a tree grown at relative y = 0 stands on it. `LumberTestSupport.grow(h, TreeFeatures.X, baseRel, seed)` places the configured feature with a fixed seed (the same tree every run; assert on the log count it prints), `logsOf` floods its exact logs. Keep trees inside the yard.
- **Stray trees:** `sweepStrayTrees(h, 48)` removes natural logs, leaves and saplings and item entities around the test before it builds (saplings the lumberjack planted in an earlier test grow into trees). Safe only because every lumber test has its own environment.
- **Registering per-tick samplers (`h.onEachTick`) must happen from the test body**, never inside a callback (same hazard as `runAfterDelay`); gate them with a flag set later. `LumberTestSupport.Watcher` records the tick of every log removal.
- **Never put a torch where a test block stands:** `lightYard` puts torches on the grass every 4 blocks outside the two rooms; call it before building anything else.
- **Measured bounds**, not guesses: `fellBound(logs, dist, tier) = logs * 50/multiplier + 10 * dist + 200`. Felling itself is deterministic (the gaps between logs are exactly 40 ticks with a stone axe, asserted); walking is not. Every roadmap test logs its tick counts (`[m9] ...`). Debug a stuck worker with `-Dvillageheart.lumber.trace=true` in `JAVA_TOOL_OPTIONS`: every plan, cut, path and jam is logged.
- **Walking.** A lumberjack walks with `WalkTo` (its own long-range path, published in the PATH memory so doors open), not with a WALK_TARGET: the vanilla sink plans about 16 blocks and stops at the first dead end (a worker in a room whose tree is on the far side of a wall walks to the wall). Two workers leaving one door jam (they push each other off the doorway); `WalkTo` makes the one with the higher entity id step back and wait 30-60 ticks. `DepositTrip` uses it too. The tree search also runs in the background each tick.
- **Leaves block the ground up to the trunk**, so a tree is chopped from the nearest reachable spot within `CHOP_CLOSEST` (8 blocks); a tree inside a glass box with walls 9 blocks out is unreachable (T-L2-12).
- **Replanted saplings grow into trees** and the lumberjack fells those again: assert "felled" with a sticky flag from a per-tick sampler, never "no log stands" at the end of a long test.
- Leaf drops are random (5% saplings per oak leaf): only T-L2-6 relies on them, as the roadmap asks; the others put saplings in the chest or the worker's inventory.
- The roadmap tests: `sixOaksTwoDays` (at least 16 logs in Storage, every felled oak replanted, a 4x3 log wall whole for 30 000 ticks), `noAxeNoLogs` (status and text "Needs axe", a day without logs, then the same village produces with an axe).
- **Layer 4:** `LumberClientTests.lumberjackFelling` (`lumberjack_felling.png`) stages the scene (oak from the real feature, three logs gone, crack overlay on the next one, sapling on a stump, drops) and checks the client-side profession and blocks; real felling is covered by Layer 2.
## Heart screen tests (M8+)
- **L1**: `core/heart/*Test` (permissions matrix, action gate order, rate limiter, snapshot builder: rank filter, order, caps, alerts) and `heart/net/HeartCodecsTest` (round trips, the worst-case snapshot size with 3-byte characters, printed and asserted under 512 KiB, bounded strings and lists). `ByteBufCodecs` initialises in plain JUnit. `LangCoverageTest` pins every Heart text (`HeartTexts.EN`, rejections, alerts) against `en_us.json`.
- **L2** `HeartScreenGameTests` (menu, opening, refresh, snapshot numbers) and `HeartActionGameTests` (ranks, gate, bad packets, rate limit). Helpers in `HeartTestSupport`: `seed(h)` (claim, Storage with 64 logs and 20 bread, Farmhouse), `open(h, player)` (the real `useWithoutItem` path), `recorder()` (replaces `HeartNetwork`'s sender, restored by `done`), `send(player, codec, payload)` (encodes and decodes with the real codec, then `player.connection.handleCustomPayload`: Fabric dispatches it to our receiver synchronously on the server thread). Test players have random UUIDs, so rate-limit buckets are never shared; `HeartActions.reset()` clears them. An unloaded worker is simulated by discarding the entity while its record stays.
- **L4** `HeartScreenClientTests.heartTabs`: precondition 427 x 240 GUI (fails loudly otherwise), a seeded village, right-click on the Heart, the four tabs by real clicks (`tabButtonCentre`), `heart_overview/villagers/stockpile/buildings.png`, Fire through the UI (`clickScreenButton`), a 20-villager list that scrolls and a long item name that is cut with an ellipsis. `HeartScreen.recordLayout = true` makes the screen record every drawn rectangle: the test asserts `layoutProblems()` is empty (nothing outside its area, no overlap inside a row, scrollbar inside the window) and `clippedThisFrame() == 0`. NoAI villagers keep their activity (WORKING at noon).
- Gotchas: `Screen.isPauseScreen()` is true by default (a singleplayer server would pause and never send a refresh); custom names in `summon` NBT are `CustomName:"Ada"` (a text component), not a quoted JSON string.


## Rank and multiplayer tests (M24+)
- **L1**: `PermissionsTest` (a literal table, not computed from `Permission.minimum()`, plus a row for every permission and the monotonic check; `Rank.values()` is pinned),
  `VillageStateMembersTest`, `RankRulesTest`, `ContainerGuardTest` (`FakeGrid` scans), `VillageCodecsMembersTest` (`known_players`, repair, the 63/64 caps), `ActionGateTest` (crafted rank values),
  `HeartSnapshotBuilderTest`/`HeartCodecsTest` (member rows only for the Owner, caps, worst case 481 KiB).
- **L2** `RankGameTests` (helpers `RankTestSupport`): own environment per test (`villageheart-gametest:ranks_<name>`), `seed`/`begin` first, `done` last. Visitors are `OverlayPlayer`s (they record the action bar), chat is
  recorded by `chatty(...)`. Open through `rightClickAbs` (real `useItemOn`, so Fabric's `UseBlockCallback` runs), break through `player.gameMode.destroyBlock` (never `level.destroyBlock`, which skips the event).
  **Raw `AssertionError`s thrown inside a `GameTestSequence` tick crash the whole GameTest server**; use `h.assertTrue` there. `joinAs(h, uuid, name)` joins a fresh player with a chosen UUID through `placeNewPlayer`:
  Fabric's JOIN event does fire for `EmbeddedChannel` players, so `Members.onJoin` is tested through the real path. Ops commands run through `Console` (a `CommandSource` that collects the replies);
  `lastKey()` looks inside the red wrapper component commands put around failures.
- **L4** `RanksClientTests.ranksTab` (`heart_ranks.png`, `heart_ranks_confirm.png`): offline members written into the saved data, the tab opened by a real click, a rank set through the buttons, the two-click transfer, and the
  Ranks tab locking again for the old Owner.
- **L4 2-player session** `MultiplayerClientTests.ownerAndResident` (`mp_ranks.png`): Fabric's `createServer()` starts an in-process **dedicated** server (random port, offline mode, EULA accepted in `build.gradle`) and `connect()` joins the one real
  client. The second player is a `FakePlayer` (the Owner "Olga"), a server-side `ServerPlayer` whose connection **drops every packet**: with a queueing `EmbeddedChannel` Fabric's `NetworkSynchronizer` would count them as
  in flight forever and the client would die after 10 s. So this is 1 real networked client + 1 server-side player, not two real clients. The session: Visitor refused on a chest, a jukebox (disc stays in hand) and the break of one half of a
  double chest (both halves' client state equals the server's); Resident opens the chest and moves a stack; the Heart screen as Resident, Steward and Owner (the Ranks tab unlocks, Olga online); Olga demoted through the UI.
  `syncCheck` after every step: still connected, Olga still listed, the client's inventory equals the server's (item, count, components), and with the Heart open the client's snapshot equals `HeartMenu.lastSent()` **and** shows the step's change.
  The test logs `MP_SESSION begin/end`; `scripts/check-mp-log.sh` (self-test `scripts/test-check-mp-log.sh`, sample logs in `scripts/testdata/mp-log-*.log`) fails when a `Server thread` or `villageheart` ERROR, or "Disconnected", "Connection lost", "Timed out" or "Failed to decode", appears between the markers.
  Authlib/Yggdrasil errors (no network in the sandbox) come from their own threads and are not matched. It took ~25 s and passed 5 of 5 runs. Whether "1 client + 1 server-side player" satisfies the v1.0 gate item is the release decision; M24 did not tick it.
