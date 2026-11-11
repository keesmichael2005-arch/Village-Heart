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

## GameTest position helpers
In 1.21.11 `GameTestHelper.absolutePos(rel)` adds the offset to the structure origin and then runs `StructureTemplate.transform` with the test's rotation (`Mirror.NONE`). `relativePos(abs)` is not the inverse: it transforms the world position with `testRotation.getRotated(Rotation.CLOCKWISE_180)` and only then subtracts the origin. `transform` of `Rotation.NONE` is the identity, and of `CLOCKWISE_180` is `(2*pivotX - x, y, 2*pivotZ - z)`, so on an unrotated test (every test here) `relativePos(absolutePos(rel))` equals `-rel` and `absolutePos(relativePos(abs))` is the point rotated 180° around the origin, not `abs`. Feeding that result to `spawn`, `setBlock` or `getBlockState` (each calls `absolutePos` or `absoluteVec` again) therefore checks the mirrored cell, which is often air, so the assertion passes without looking at the real one. `relativeVec` is a different function: it subtracts the origin first and rotates by the test rotation with no extra 180°, so on an unrotated test it does undo `absoluteVec`; nothing in this suite calls it. Map a world `BlockPos` back with `VillageTestSupport.rel` (subtract `absolutePos(BlockPos.ZERO)` and assert the round trip) or compare world positions produced by `absolutePos`.

## Layer 4 and Fabric's NetworkSynchronizer (two stalls, two gametest mixins)
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

**Second stall: a leaked clientbound count when a singleplayer world closes.** Fabric counts a clientbound packet at the HEAD of the
server-side `Connection.sendPacket` and uncounts it when the client's `channelRead0` returns. `TestSingleplayerContext.close()` closes
the client's end of the in-memory channel (`ClientLevel.disconnect`), then `Minecraft.disconnect` calls `CLIENTBOUND.reset()` (at
`dropAllTasks`), its only reset. But Netty closes the server's end of a `LocalChannel` later, on that channel's own event loop, and the
integrated server keeps ticking meanwhile (the deferred disconnect runs in the client's tick phase, in parallel with the server's tick).
Until that close runs the server still sees `isConnected()`, so every packet it sends in that window is counted after the reset and then
fails to write to the closed client end: it is never uncounted. `disconnect` returns with `inFlightPackets > 0`, and the first client tick
after "Stopping server … All dimensions are saved" waits for packets that cannot come, for 10 s, then fails the run with the same
`AssertionError: Network synchronizer in invalid state` (stack: `postRunTasksHook` from `Minecraft.run`). CPU load delays the event loop and
widens the window; a busy server tick fills it. Seen naturally at the end of M8's `heartTabs` (it killed 20 villagers just before closing:
instrumentation counted 20 `set_entity_data` + 20 `set_entity_motion` + 1 `disconnect` sent after the reset, 41 in flight at the end of
`disconnect`); under the forced delay below even idle worlds leak (a `set_time`). Fix: the gametest-only, client-only mixin
`dev.villageheart.gametest.mixin.DisconnectInFlightLeakFix` re-applies Fabric's reset in `Minecraft.disconnect` after the client has waited
for the integrated server to stop (`isShutdown()`), before the next tick. There the client's connection is closed (nothing can be received)
and the server thread has finished (nothing can be sent), so any count left is a leak by construction. It changes nothing during gameplay
(a packet that really never arrives still fails after 10 s) and does not cover a disconnect from a dedicated server (not seen; Fabric's 10 s
would still report it). It logs `DISCONNECT_LEAK_FIX installed` once and `DISCONNECT_LEAK_CLEARED: n` per world close it repairs; `verify.sh`
fails if the first is missing and reports the count. Client tests should also quiesce before closing a world: no mass entity change in the
last ticks (wait until the effect has reached the client, as `heartTabs` now does after its kill), screens closed.
Forced reproduction (temporary probe, not committed): blocking the server channel's event loop for 500 ms when the client closes makes every
world close leak; without the fix the run dies at the first close, with it the run is green and logs `DISCONNECT_LEAK_CLEARED`.

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

## Needs GameTests (M11)
`NeedsGameTests`, `NeedsHousingGameTests`, `NeedsWorkGameTests`, `SimActionGameTests` (helpers in `NeedsTestSupport`). Rules:
- **Hooks.** `VillageTestSupport.reset` calls `NeedsTestHooks.disable()` (default needs for every worker, no dinner), so the worker tests of M7-M10 keep their
  pace. A `needs_<name>` test calls `NeedsTestHooks.enable()` after its scene. `existingWorkerTestsUnaffected` proves the switch.
- **Dinner** is triggered by `NeedsTestSupport.nextDinner(h)`: today's dinner counts as served, the clock goes to the NEXT day's 10980. Never a fixed day time.
  `storageScene` sweeps every villager within 100 blocks first (leftovers would eat the bread) and the test counts the census before the first dinner.
- Speed tests start at a day's 1000 with today's dinner marked as served and assert that `last_dinner_day` is unchanged at the end.
  Farmer gaps: 20 work units at x0.5 are 40 ticks, plus up to 2 for the planner (measured 40-42).
- `forcedChunkGrowsWithoutPlayer` pins the day time to noon with time frozen: saplings only grow in daylight (without it the test flaked at night).
- `SimActionGameTests` runs the scenario's own prefab/field/grove/hire actions in the yard through the `Host` interface (the simulation implements it too).

## Wage GameTests (M12+)
`WageGameTests` (helpers in `WageTestSupport`, environments `villageheart-gametest:wages_<name>`, structure `yard`, `skyAccess = true`). Rules:
- **Hooks.** `VillageTestSupport.reset` calls `NeedsTestHooks.disable()`, which also switches wages OFF (`NeedsOverride.wagesEnabled = false`): the wage
  system only consumes each day change, hires are not charged, nobody ever strikes. A wage test hires with wages off (so no hire is charged), then calls
  `NeedsTestHooks.enableWages()` (`WageTestSupport.wagesOn()`); fed/housed stay forced, `paid` always comes from the payroll. The old L4 tests run with wages off too
  (`VillageHeartClientTests` sets it); `TreasuryClientTests` turns them on.
- **`WorkerTestSupport.begin` first**: it turns `advance_time` on (the GameTest level has it off) and the day clock must run.
- **Crossing a dawn**: `WageTestSupport.toDawn(h, seq, out)` sets the day time to `index * 24000 + 23980` and waits (bound 60 ticks) until the payroll's
  `lastWageDay` is the next index; `out[0]` is the `PayResult` of the run (null when wages are off: the day was consumed). Never jump backwards except to
  test exactly that. `setPhase(h, 900)` moves inside the day without a new run.
- Dawn counters: `VillageDawn.register("test:<name>", ...)` (ids starting with `test:` are dropped by `reset` and `WageTestSupport.done`).
- Strike proofs make the striker the only one who can do the work (two farmers and one lumberjack: any fallen log is the striker's) plus a capability check
  (one deposit and the same worker fells a log). A first version told two lumberjacks apart by distance to the trees and flaked (a striker may wander).
- The far-chunk test (`frozenWhileHeartUnloaded`) claims a village 100 000 blocks away in a force-loaded chunk, un-forces it and polls (bound 200 ticks)
  until it unloads. It has no workers: the proof is that `lastWageDay` and the dawn counter do not move while the Heart is unloaded.
- **L4** `TreasuryClientTests.overviewStrike` (`treasury_overview.png`, `treasury_villagers.png`; `-Dvillageheart.l4.only=treasury` runs just this): hires charged
  with an empty treasury, a real right-click with 2 emeralds, the Overview and Villagers tab, the Deposit all and Withdraw 1 buttons. Emeralds for the button
  are put in the hotbar BEFORE the screen opens (a creative client ignores inventory updates while a menu with no slots is open; `Treasury` re-sends the
  inventory menu after a deposit or withdraw for the same reason).
- **L3** `starter` seeds the treasury with `villageheart treasury add 32` and asserts `max(wages.strikers) == 0 from day 1`, `sum(wages.runs) >= 8 from day 1`
  and `treasury <= treasury@prev - 2 from day 3`. Metrics: `wages.runs|bill|paid|spent|strikers`.

## Cook GameTests (M13)
`CookGameTests` (helpers in `CookTestSupport`; environments `villageheart-gametest:cook_<name>`, one per test; the file `cook_prefabs` belongs to `SimActionGameTests.starterCookPrefabs`).
- **Scene:** `CookTestSupport.scene(h)`: a valid Kitchen at `WorkerTestSupport.ORIGIN_A` (smoker in the first column, crafting table and chest in the far corners, the door cell kept free: a table in front of the door shuts the cook in) and a Storage at `ORIGIN_B`, 8 blocks apart. Work tests set the day time to 1000 (or 9800 for the dinner cutoff) after the scene is valid. Needs, dinner and wages are off as in every non-`needs_` test.
- **Counts are exact** (`BatchMath.simulate` is the oracle: the roadmap case is 10 beef + 9 wheat + 4 coal = 10 cooked beef, 3 bread, 2 coal, bank 600); only walking time varies, so time limits are measured bounds, `cookBound(items, batches) = items * 125 + batches * 360 + 400`. Roadmap tests log their ticks (`[m13] ...`).
- `restartMidBatchResumes` drops the cook runtime (`CookRuntime.resetForTests`) instead of reloading the entity: the inventory is the state, and the saved heat bank must not be charged twice. `tablesMatchVanilla` compares the core tables with the game's food components, `minecraft:smoking` recipes, furnace burn times and the `#logs_that_burn` tag.
- **Layer 4** `CookClientTests` (`cook_at_smoker.png`: the smoker `LIT` on the client and the cook's profession asserted; `villagers_tab_needs_fuel.png`: the row reads "Needs fuel").
- **Layer 3 baseline.** `starter_cook` compares nutrition per eaten item with the committed run `sim/baselines/starter.json` through `x@base` (only inside a function: `sum(food.eaten_nutrition@base) / sum(food.eaten_items@base)`; no baseline or a scope the baseline does not cover is a FAIL with a reason). The report carries `scenario_sha256` (SHA-256 of the scenario's canonical JSON) and `git_commit`; `StarterCookFilesTest` fails when `sim/scenarios/starter.json` changed since the baseline was captured. **Rule:** a change to `starter.json`, `DinnerPlanner`, `FoodRules` or the farmer's harvest rules re-captures the baseline in the same commit: `scripts/simulate.sh starter`, then `cp reports/sim/starter.json sim/baselines/starter.json`.

## Layer 3: simulations
`scripts/simulate.sh <scenario> [days] [--assert "<expr>"]... [--reload-at <day>]` (`--list` shows the scenarios). It boots a real headless
dedicated server (`./gradlew runSimulation`, a Loom run config over the `gametest` source set, run dir `build/run/sim`) on seed 1, finds the plains
village, force-loads it, joins a fixed creative fake owner (`SimOwner`), runs the scenario's setup actions (the `claim` action places the Heart
through the real `useItemOn` path), sets the time to dawn, records a baseline and sprints N days (24000 ticks each). Output:
`reports/sim/<scenario>.json` (one record per day, schema in `core.sim.MetricsJson`), `.summary.md`, `.log` (`.2.log` after a reload).
Expect about 50 s per in-game day on 4 cores; a 5-day run is 5-6 minutes. The script's timeout is `240 + 150 * days` seconds.

**Ticking area (sim-area).** A villager in a chunk that is loaded but not entity-ticking freezes for good, and in a real game the player's
simulation distance is what keeps a village's surroundings ticking. The sim stands in for that presence in two parts (`core.sim.SimArea`, glue `SimWorld` + `SimTickets`):
- **Static square.** ±`SimArea.staticRadiusChunks(CLAIM_RADIUS)` = ±6 chunks, force-loaded. It is centred on the village start until the claim, then on the
  **Heart's** chunk (`SimWorld.recentre`), so it covers the whole claim plus one chunk on any seed. On seed 1 the Heart is in the start chunk, so the chunks do not change.
- **Follow tickets.** Every 20 ticks each loaded hired worker gets a non-persistent ticket (`SimTickets.WORKER`, loading + simulation) that keeps its chunk
  and 2 chunks around it entity-ticking, like a player's simulation distance that goes where the action is. One ticket per occupied chunk, shared
  and reference-counted (`SimArea.Follower`). So a worker that strolls or flees past the square keeps its brain and walks home (vanilla HOME walk limit 150 blocks).
- **Still visible.** `work.workers.max_heart_distance` is the farthest a hired worker was from the Heart that day (sampled every 20 ticks, blocks, rounded down),
  and `starter` asserts it stays `< 150`. A worker past the walk-home limit is lost for real and fails the run. `work.workers.not_ticking` (sampled every 100 ticks)
  stays asserted `== 0`: a worker that is unloaded or frozen anyway (for example after `--reload-at`, which starts with no follow tickets) is still red.
- L1 `SimAreaTest` (square coverage, the reference counts, a random walk); L2 `SimActionGameTests.followTicketKeepsFarChunkTicking` (a far chunk ticks entities
  under the ticket, 2 chunks out ticks too, 3 does not; the ticket moves; release leaves nothing). The GameTest server does not wait between ticks, so the test
  generates the ticket's square synchronously first (asynchronous generation 60 000 blocks out lasted longer than 2000 ticks).

| Exit | Meaning |
|---|---|
| 0 | `PASS`, `clean_shutdown`, N day records, no crash report, Gradle rc 0 |
| 1 | `FAIL`: an assertion was false (red is red) and everything else was healthy |
| 2 | `ERROR`: crash, hang, compile failure, timeout, stalled or missing report, bad assertion text, lock held. Gradle's own exit code is never trusted: a server crash still ends it with 0 |
| 64 | usage error (unknown scenario, `days` not 0..365, `days 0` with `--assert`, bad `--reload-at`) |

Concurrency: every run kills only what it started (its process group, plus any process carrying its private `SIM_RUN_TOKEN`), uses its own server port, and locks `$SIM_DIR/.lock`. Different worktrees (each with its own `build/run/sim`) can simulate at the same time; a second run in the same directory is refused (exit 2). On a crash, `simulate.sh` rewrites the report itself: `status: ERROR`, `clean_shutdown: false`, `crash_reports: [...]`.

`days = 0` is a smoke run (boot, locate, claim, baseline, stop; ~35 s). A scenario may still list actions on later days; they are not run.
`--reload-at D` stops the server cleanly after day D (tick order: record
the day, run due actions, write, halt), restarts on the same world and finishes the run; `reloads` in the report is `[D]`.

## Raid and siege GameTests (M25)
GameTests share one overworld clock. A test that sets the time of day stays inside the current day (`atTime` in `ZombieSiegeGameTests` and `RaidLayoutGameTests`). `setDayTime` across a 24000 boundary looks like missed dawns to `WageGameTests.frozenWhileHeartUnloaded`. A filter that matches nothing exits 0 with zero tests; `scripts/check-gametest-log.sh` fails that. Pass the filter with `JAVA_TOOL_OPTIONS=-Dfabric-api.gametest.filter=...`, not `./gradlew -D...`.

`RaidLayoutGameTests.raidLayoutValid` (`villageheart-gametest:raid_layout`, structure `raid_yard`) builds `sim/scenarios/raid.json` without the site finder or the raid: grass, the prefabs, the `build` action's walls and gates, the hires. It asserts 438 defence cells, the wall lengths, four guard slots, and a threat score in 400..800.

Layer 3: `scripts/simulate.sh raid` (6 days, seed 1, a 44 by 32 ring, one scripted raid on day 3). `scripts/simulate.sh raid 0` builds day 0 and stops. `scripts/simulate.sh raid_reload --reload-at 3` is the same town with the raid at day 3 tick 20000, and it asserts the raid is active on day 3 and starts once. The resumed process seeds its raid meter from the live raid and the saved in-claim death count, so that raid is not a second start and the deaths from before the reload are not counted again. The raid town's guards are level 3. A chase path that does not close at least 12 blocks walks to the nearest gate, and the chase reaches 64 blocks past the claim. When that path cannot reach a raider already within 32 blocks, and the guard is not still walking to a gate, the guard steps to the raider. The raider is not moved. `scripts/simulate.sh siege` (4 days, 8 zombies on day 2 at tick 18000, which is night on seed 1) is reported, not a roadmap gate. The roll switch stays off; a siege the action started still runs. `siege.started`, `siege.villager_deaths` and `lure.max_lured` reset when the day is recorded, so those assertions are scoped `from day 1`. The new `build` action takes a blueprint and a site-relative `from`/`to` and advances the line to the end. Prefab words `blast_furnace`, `anvil` and `builders_table` place the Smithy and the Builder's Hut.
Env: `SIM_TIMEOUT_SECONDS`, `SIM_STALL_SECONDS` (report must change at least this often, default 600), `SIM_START_SECONDS` (default 180), `SIM_GRADLE_CMD`.
`scripts/test-simulate.sh` (run by `verify.sh`) checks this exit-code contract against a stub server (`scripts/testdata/fake-sim.sh`).

M11 actions (`StarterActions`, field shapes checked at parse time by `core.sim.ActionSpecs`): `site` (find, flatten and light a patch of untouched
natural land near the Heart: `size`, `min_distance`, `max_distance`, optional `max_deviation` and `odd_percent` for rough plains), `prefab` (a room with its
marker: `building`, `at`, `size`, `door`, `contents`, `chest_items`), `field`, `grove`, `hire` (nearest free adult, teleported to the door; with `replace: true`
a replacement is hired whenever the job has nobody). Positions are relative to the site origin. `starter` carries the M12 hook (a `treasury add` command) in its
description. Concurrent runs in one checkout need their own `SIM_REPORT_DIR` as well as `SIM_DIR`.

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

**Death log and hire counts (M26 prep, cause from the village log once M25's claim exists).** Every villager death within the claim radius gets a row in `deaths.list` (`day_time`, `name`, `job`,
`guard`, `damage`, `attacker`, `attacker_origin` = `scripted` (a `night_spawn` mob, entity tag `villageheart_sim_scripted`) | `converted` (a villager
turned zombie villager) | `natural` (anything else, including vanilla's zombie siege), `pos`, `wall` / `attacker_wall` = `inside` | `wall` | `outside`
of the scenario's `wall_ring` or "" without one, `zombified`) and in the summary's **Deaths** table; a log line `[sim-death]` too. The summary's
**Hires** table reads `work.sim.hires`, `work.sim.rehires` and `work.sim.rehire_wait.<job>.<reason>` (100-tick samples in which a standing hire had an
empty slot and could not fill it: `night`, `no_free_villager`, `no_building`, `refused_<result>`, `error`). The **Hostiles** table reads
`work.sim.hostile_spawn.<side>` (natural hostile spawns within the claim radius; `[sim-spawn]` lines carry the day time: a burst at 18000 is vanilla's
`VillageSiege`), `work.sim.hostile_inside.<origin>` (first sighting inside the wall ring; `[sim-breach]` lines carry the gate state) and
`work.sim.hostile_samples.<origin>` (hostiles present, 100-tick samples). Rendering and JSON are L1 (`core.sim.DeathLogTest`). Once a village is claimed, the cause, job, mob flag and cut name are copied from the village death log (`core.death.DeathLog`, L1 `core.death.DeathLogTest`) and a row is kept only when that log marks the death inside the claim, so `deaths.total` matches `deaths.logged`. A name longer than 32 characters is cut. A witch is `minecraft:lightning_bolt`, not `zombified`. With no claim the collector still uses the radius and `DeathCauses` directly. Deaths outside that scope are not logged (a census drop with no row is a villager that left or died outside it).
`wall_ring` builds each wall column `WALL_CLEARANCE` (2) above the natural ground just outside it and digs the gate approach down to the site's level
(L2 `SimActionGameTests.wallRingRisesOverHigherGround`): on seed 1 the ground is a block up along 80 of 148 wall cells and in front of the gate.

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
  **Rule:** a test that calls `sweepStrayMarkers` (directly or through `begin`) must declare its own environment: same-environment tests run at the same time and would sweep each other's markers. `BuildingTestSupport.assertOwnEnvironment` enforces it at run time (fails on the default environment, or when a second test sweeps in an environment another test already swept in).
- **Measured waits.** `awaitWithin(h, seq, bound, what, check)` re-runs `check` every tick and then asserts it took at most `bound` ticks. `bound(n, hops) = n*20 + hops + 4`: a change that fires no hook is
  seen within `n x 20` ticks (`n` registered buildings), one more tick per dependent hop (site to parent), plus slack. The roadmap's "within 2 s" test (Storage green / red) uses 40 with `n = 1`. State `n` in the test.
- **Chat.** `recordingPlayer` is a `ServerPlayer` subclass that collects `sendSystemMessage`; read keys and fallbacks through `TranslatableContents` (`translatable`, `lineBody`).
  Right-click with `rightClick(...)` (`player.gameMode.useItemOn`, the main hand holds the stack so the sneak/hand rule works).
- **Far markers** (outside the claim, unloaded): 100 000 blocks along +z of the Heart; load with `level.getChunk` and keep with `setChunkForced`, clean up in the last step. Never load chunks from the code under test.
- **Running one GameTest:** `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:<class_snake>_<method_snake>" ./gradlew --no-daemon runGameTest`
  (exact id, e.g. `building_game_tests_shared_room_both_red`; a regex or prefix selects nothing and the run "passes" with 0 tests).
  **Zero-test guard:** `scripts/verify.sh` runs `scripts/check-gametest-log.sh` on `reports/gametest.log` and fails when the log says "All 0 required tests passed" (or has no result line). For a raw gradle run, check the log yourself for that line; self-tested by `scripts/test-check-gametest-log.sh`.
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
- **Budget** (docs/plans/M6.md 3.4): one snapshot (= one `StockpileSystem` refresh) of 32 full containers under 1 ms (`snapshotCostReal` only prints the measured time; the wall-clock limits are asserted in `StockpilePerfTest` via perfTest, and the call-count regression guard is `StockpileCostTest`), a deposit/withdraw under 0.3 ms.


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
- **The yard is not cleared by the framework** (the structure is one air block): rooms, torches and the glass box of an earlier test that used the same place stay. `sweepStrayTrees` empties the yard (except the bell/Heart corner) before anything is built.
- **Stray trees:** `sweepStrayTrees(h, 48)` removes natural logs, leaves and saplings and item entities around the test before it builds (saplings the lumberjack planted in an earlier test grow into trees). Safe only because every lumber test has its own environment.
- **Registering per-tick samplers (`h.onEachTick`) must happen from the test body**, never inside a callback (same hazard as `runAfterDelay`); gate them with a flag set later. `LumberTestSupport.Watcher` records the tick of every log removal.
- **Never put a torch where a test block stands:** `lightYard` puts torches on the grass every 4 blocks outside the two rooms; call it before building anything else.
- **Measured bounds**, not guesses: `fellBound(logs, dist, tier) = logs * 80/multiplier + 10 * dist + 200`. Felling itself is deterministic (the gaps between logs are exactly 40 ticks with a stone axe, asserted); walking is not. Every roadmap test logs its tick counts (`[m9] ...`). Debug a stuck worker with `-Dvillageheart.lumber.trace=true` in `JAVA_TOOL_OPTIONS`: every plan, cut, path and jam is logged.
- **Walking.** A lumberjack walks with `WalkTo` (its own long-range path, published in the PATH memory so doors open), not with a WALK_TARGET: the vanilla sink plans about 16 blocks and stops at the first dead end (a worker in a room whose tree is on the far side of a wall walks to the wall). Two workers leaving one door jam (they push each other off the doorway); `WalkTo` makes the one with the higher entity id step back and wait 30-60 ticks. `DepositTrip` uses it too. The tree search also runs in the background each tick.
- **Leaves block the ground up to the trunk**, so a tree is chopped from the nearest reachable spot within `CHOP_CLOSEST` (8 blocks); a tree inside a glass box with walls 9 blocks out is unreachable (T-L2-12).
- **Replanted saplings grow into trees** and the lumberjack fells those again: assert "felled" with a sticky flag from a per-tick sampler, never "no log stands" at the end of a long test.
- Leaf drops are random (5% saplings per oak leaf): only T-L2-6 relies on them, as the roadmap asks; the others put saplings in the chest or the worker's inventory.
- **Leaf drops are a per-world sequence.** Oak leaves draw their loot from `minecraft:blocks/oak_leaves` (a `RandomSequences` entry seeded by the world): a test run alone always sees the same drops, in a full run the earlier tests (random batch order) shift them. Never rely on leaf luck; to force a lean case reset the sequence (`level.getRandomSequences().reset(id, 0L, salt, false, true)`; salt 553 gives no oak sapling in its first 150 rolls), as `stumpsReplantedWhenLeavesDropNoSapling` does.
- **A sentinel of 0 for a start tick is wrong:** the first test tick is 0 (`t0 = {-1}` and `t0[0] < 0`, or a separate flag).
- `idlerInTheDoorwayStepsAside` holds a plain villager on a door-front cell until `WalkTo` asks it to move (`WalkTo.setNudgeListenerForTest`, reset by `VillageTestSupport.reset`).
- The roadmap tests: `sixOaksTwoDays` (at least 16 logs in Storage, every felled oak replanted, a 4x3 log wall whole for 30 000 ticks), `noAxeNoLogs` (status and text "Needs axe", a day without logs, then the same village produces with an axe).
- **Layer 4:** `LumberClientTests.lumberjackFelling` (`lumberjack_felling.png`) stages the scene (oak from the real feature, three logs gone, crack overlay on the next one, sapling on a stump, drops) and checks the client-side profession and blocks; real felling is covered by Layer 2.
## Level GameTests (M15)
`LevelGameTests` (helpers `LevelTestSupport`, `SideBySide`; listed under `fabric-gametest`). Own environment per test, `villageheart-gametest:levels_<name>`, the lumber scene (`scene`/`hire`), `begin` first and `done` + `end` last. Rules:
- **Set levels by writing the attachment** (`LevelTestSupport.put(v, level, xp)`: attachment and the vanilla badge); earn them through `Workers.addXp` or real work. The level pass runs every 20 ticks (`setTier` in a test turns banked XP into levels within 21 ticks: `awaitWithin(..., 21, ...)`).
- **Feedback seam:** `LevelUpFeedback.setSinkForTest(Consumer<FeedbackEvent>)` sees every event before particles and sound (neither is observable server-side); chat is asserted on `RecordingPlayer`s (`RankTestSupport.chatty`, and `LevelTestSupport.ownerRecorder` to make a recorder the Owner). `VillageTestSupport.reset` restores the seam and clears the XP accumulator. A test can also register its own `WorkerLevels.LevelListener` (unregister it at the end).
- **A plain oak has 4-6 logs** (probed seeds 1-60; oak seed 3 has 6), a fancy oak 4-29, a dark oak 26-60. Exact pace assertions (40 ticks per log with a stone axe, 32 after level 2) need a tree felled from one spot: oak seed 3.
- **The side-by-side test** (`ironAxeOutFellsStoneSideBySide`, environment `levels_side_by_side`, maxTicks 6500) stacks three identical yards 28 blocks apart (the lumberjack searches +-16 in y, a dark oak is at most 12 high, the claim radius is 80): level 0 is the test's yard, levels 1 and 2 are glass platforms (`SideBySide`) at +28 / +56 with a 2-high glass rim, a camp, a Storage and six dark oaks each (47-60 logs, the same seeds, asserted to be translations of each other, disjoint and inside the claim). This is the one exception to "keep trees inside the yard": `SideBySide.clearUpper` clears and discards everything above the yard **before the claim is made** (a failed earlier run may have left markers there) and `clearAll` again at the end. Iron works at level 0, stone at 1, wood at 2; each chest holds two axes of its tier, the first with 29 uses left (every worker's first axe breaks after 29 logs, which also tests the break to the next axe). Per tick a sampler counts the logs of each grove going in day ticks [1000, 5000), checks every tree's claim owner, the axe wear against the logs felled every 100 ticks (exactly equal) and that no "Needs axe" spell lasts over 40 ticks. Villager walking turned out deterministic in this setup: five runs gave identical counts.
- Run one test alone with the exact id (`-Dfabric-api.gametest.filter=villageheart-gametest:level_game_tests_<method_snake>`): the filter takes no prefix and no regex, and an id that matches nothing "passes" with 0 tests (the zero-test gate catches it in `verify.sh`).
- **Layer 4:** `LevelClientTests` (`worker_level_badges.png`: three lumberjacks at levels 1/2/3, the client-side `VillagerData.level()` is asserted; `heart_villagers_levels.png`: the Villagers tab with a gold full bar for a capped Hamlet worker and a green 40 % bar). `-Dvillageheart.l4.only=levels` runs just these two.

## Farmer GameTests (M10)
`FarmerGameTests` (helpers in `FarmTestSupport`; the class is listed under `fabric-gametest` in `src/gametest/resources/fabric.mod.json`). Rules:
- **Frozen crops.** Every farmer test has its own environment `villageheart-gametest:farm_<name>`, a `minecraft:game_rules` environment
  (`{"type": "minecraft:game_rules", "rules": {"minecraft:random_tick_speed": 0}}`): crops never grow and farmland never dries, so every age in a
  test comes from the setup, the farmer's replant (age 0) or its bone meal. `FarmTestSupport.scene` asserts the rule first (a mis-parsed
  environment fails loudly). Batches run one after another and the teardown resets the rule to its default.
- **Fields** are built after the buildings validate: moist farmland at relative y = -1 and the crop at y = 0 (`plantAt`, `field`, `emptyFarmland`; crops
  with `setBlock(..., 2)`, so nothing re-checks the cell). The Farmhouse of `WorkerTestSupport.farmhouse` has its door in the **north** wall, so fields
  lie south of it (z 13-30) and the farmer walks around the room (about 10 ticks per block). `sweepStrayCrops` empties the yard and removes farmland,
  crops and crop items within 40 blocks (a 24-block work area reaches into the neighbouring test boxes).
- **Stray farmland (rule for every test that hires a farmer).** A hired farmer works any farmland within 24 blocks of its composter, and farm tests leave their fields behind. `WorkerTestSupport.begin` and `BuildingTestSupport.begin` therefore call `FarmTestSupport.clearStrayFarmland(h, 40)` (farmland, crops, stems, crop items) before the test builds anything; a test that builds a field of its own and does not need it afterwards removes it again at its end (`invalidBuildingKeepsWorker`). Any new test helper that skips both `begin`s must call it itself. `FarmTestSupport.loose` counts items only inside the farmer's work area (24-block disc around the composter), so an item the farmer cannot collect cannot hang a wait unseen.
- **A trip during a COLLECT** (`collectSurvivesTripThatStartsFirst`, `collectSurvivesTripThatPlannerYieldsTo`): the inventory is filled on a tick chosen by the trip's `(gameTime + id) % 20` check: on a check tick the trip behaviour starts first; on any other tick the test forces a plan (`FarmRuntime.forcePlanForTest`) so the planner yields first. Both go red when the collect clock is not restarted after a trip.
- **Unit log.** `FarmRuntime.unitLog()` records every finished unit (tick, worker, HARVEST / PLANT / BONEMEAL / COMPOST / TAKE_BONE_MEAL / FETCH / COLLECT, absolute cell,
  position of the worker). `FarmTestSupport.Watcher` samples only the cells of its own field at the end of every tick: a watched cell is never air ("harvest and
  replant are one tick"), every logged HARVEST reads age 0 at the next sample. Problems are collected and asserted at the end (never thrown inside a tick callback).
  Level events (bone meal particles) reach only clients, so bone meal is asserted through the log and the age change.
- **Pace** is exact while the worker stands still: `stationaryGaps` takes the HARVEST entries of a patch and keeps the pairs whose positions differ by less than
  0.3 blocks: 20 ticks without a hoe, 16 with any hoe (asserted +-1). Everything that involves walking uses `farmBound(units, unitTicks, dist) = units * (unitTicks + 20) + 10 * dist + 200`.
- **Keep rules**: `FarmJob.testKeepAll` (reset by `VillageTestSupport.reset`) lets a test fill the inventory with items the farmer "keeps" (a full inventory without a trip).
  `SimpleContainer.addItem` puts a whole oversized stack into one empty slot: add stacks of at most 64 (a 200-seed stack lost 136 seeds in the first draft of `depositKeepsSeedsAndFood`).
  World positions map back with `VillageTestSupport.rel` (see "GameTest position helpers"); `h.relativePos` mirrors the offset.
- **Stale PATH memory.** `WalkTo` publishes its path in the PATH memory so doors open, and the vanilla `MoveToTargetSink` only starts while PATH is **absent**. A walk cut short
  (the work day ended on the way, a trip ended) left PATH behind and the worker never walked to its bed (`workerFollowsDaySchedule` failed with a farmer that idled at its
  composter). `WalkTo.release(villager)` erases it; the job `stop`, idling at the job block and `DepositTrip.stop` call it.
- Roadmap tests: `nineByNineWheatOneDay` (9x9 wheat, at least 60 wheat in Storage within a day, every cell farmland with wheat, nothing outside the field changed) and
  `immatureAndOutsideUntouched` (immature crops and cells outside the 24-block circle, the diagonal (17, 17) at 24.04 and dy +9 are never touched for 24 000 ticks; a control cell that ripens at tick 8000 is harvested).
  Every test logs its tick counts (`[m10] ...`). Debug a stuck farmer with `-Dvillageheart.farm.trace=true`.
- **Layer 4:** `FarmerClientTests.farmerField` (`farmer_field.png`): its farmer between replanted sprouts and ripe wheat. The camera stands on the Farmhouse side of the field (the farmer harvests the rows nearest its door first, so the sprouts lie in front of the ripe rows; from the far side they hide behind the tall wheat).

## Miner GameTests (M16)
`MinerGameTests` (helpers in `MineTestSupport`; the class is listed under `fabric-gametest` in `src/gametest/resources/fabric.mod.json`). Rules:
- Each test has its own environment `villageheart-gametest:mine_<name>` (a JSON file like the worker ones), structure `yard`, `skyAccess = true`, `MineTestSupport.scene(h, dy, secondStonecutter, shafts...)` first (it begins like `BuildingTestSupport.begin(h, dy)`, builds the ground, a Mine at `ORIGIN_A`, a Storage at `ORIGIN_B` and the shaft(s), places the three markers and waits until all are valid **and the shafts' depth is known**), `WorkerTestSupport.begin` before it and `VillageTestSupport.done` + `WorkerTestSupport.end` last.
- **Two scenes, and why.** The GameTest world is a flat world: the structure block is at y -59 and the test's relative y 0 is absolute -58, so a shaft built in the yard is in the **Y < 0 band** (cobbled deepslate, not cobblestone). *Low scene* (`dy = 0`): a 13-high stone tower with the 12-deep shaft (the Building tests' shaft) and a staircase of stone stairs rising towards it in line with the marker (M5's tower has no way up; a path search that must go around a tower first runs out of nodes and leaves the miner at its foot). Deepest Y = -57, DEEP band: T-L2-0, 3, 7 and the unreachable-shaft test. *High scene* (`dy = MineTestSupport.high(h)` = `64 - absolute y of the test origin`, computed, never hard-coded): the whole village stands on a 13-thick stone platform in the air (top block at absolute Y 63, shaft floor at 52, HIGH band) inside the structure's own x/z footprint; every other miner test, including the roadmap's "at least 40 cobblestone". `BuildingTestSupport.begin(h, dy)` and `claim(h, dy)` place the bell and the Heart `dy` higher. `ground` clears what a failed earlier run left above the platform (not the bell and the Heart); a high test calls `clearHigh` at its end. Leftovers of a failed run are 110 blocks above every other test, outside their claim, and their markers are swept by the next test.
- `MineTestSupport.Tracker` (register `h.onEachTick(() -> t.sample(h))` from the test body) records every descent (tick, trip `since`, surface cell, day time) and ascent (tick, what the miner carried at that very tick, position, `AwayTrips.lastSurface`, the flags, day time, saved progress).
- **Measured bounds.** Underground time is `lastSurface.gameTime - trip.since` and is 1981 to 2000 ticks for a stone pickaxe (the first 20-tick step comes 1 to 20 ticks after the descent; the step phase follows the entity id, so across a reload allow +- 45); iron 1333 and stone 2000 +- 25. The walk down takes 183 to 250 ticks, a whole cycle (down, 2000 underground, deposit) about 2340. Roadmap test: 4 hauls and 64 cobblestone by day time 12000. Every roadmap test logs `[m16] ...` lines. Debug a stuck miner with `-Dvillageheart.miner.trace=true` (and `-Dvillageheart.lumber.trace=true` for the walk).
- **Exact haul check.** The haul of a miner is a function of (world seed, UUID, its saved haul counter): the tests compute `MineTestSupport.predicted(level, uuid, band, tier, n)` and compare it with what the miner carried when it came up and with Storage's totals, so a wrong band, a lost haul or a re-roll fails.
- The roadmap tests: `minerHaulsFortyCobblestone` (high scene, stone pickaxe, one day: at least 40 cobblestone and exactly the predicted hauls in Storage, 10 durability per haul) and `minerStopsWhenShaftInvalid` (two rails removed: the miner comes up with reason SITE_INVALID at its surface spot, progress reset, the Mine and the worker go invalid with M5's reasons, nothing is mined for 3000 ticks, with the rails back it goes down again).
- **Hiding is a villager with NoAI** (see DESIGN 5.3). `awayTripsRestoreFlags` pins flag restore, name tag, orphan repair on load and the witch conversion; `awayMinerSurvivesReload` saves, discards and loads a copy (`LumberTestSupport.reloadEntity`); `fireWhileAway` also fires by id while the entity is unloaded (`reloadEntity(level, v, whileUnloaded)`); `releaseWhileAway` breaks the Heart. **`Invisible` is not saved with a villager**: the reload test would be red without the re-apply at ENTITY_LOAD.
- **Layer 4:** `MinerClientTests.villagersTabAwayMining` (`villagers_tab_away_mining.png`): a real claim, a real Mine with a stone pickaxe in its chest and a real 12-deep Mineshaft, a real hire, the miner sent down by a test call to `AwayTrips.hide`; the row reads "Away (mining)" with the miner's name (asserted in the client's snapshot before the shot).

## Hunter GameTests (M17)
`HunterGameTests` (helpers in `HuntTestSupport`; listed under `fabric-gametest` in `src/gametest/resources/fabric.mod.json`). Rules:
- Each test has its own environment `villageheart-gametest:hunt_<name>` (an empty `all_of` JSON: it only separates batches; **no time of day, no game rules**: the test owns the clock, `WorkerTestSupport.begin` turns `ADVANCE_TIME` on and `setDayTime(1000)` starts the day), structure `yard`, `skyAccess = true`, `HuntTestSupport.scene(h, secondTable[, ground])` first, `WorkerTestSupport.begin` before it, `VillageTestSupport.done` + `WorkerTestSupport.end` last. Filter ids are `villageheart-gametest:hunter_game_tests_<method_snake>` (exact; a prefix runs 0 tests; avoid a lone capital in a method name such as `ByAHidden`: the snake form is surprising).
- **The scene** (all of it asserted as premises, so a wrong world fails the first tick of every hunter test with the number it read): the flat world's real grass is 3 blocks below the walking plane (relative y -3), so `floorAndField` fills y -5..-1 of the whole 32 x 32 footprint with stone, then lays an 11 x 11 grass field (y -1) around the ground marker (on a fence post, hanging on its south face): **exactly 121 `#natural` cells** within 12 blocks (the survey is a sphere, so the footprint's edge matters: a marker in the corner reaches real grass outside it and the far ground only asserts "at least 121"). A Hunting Lodge room 5 x 4 (fletching table, chest, torch) at `ORIGIN_A`, a Storage room at `ORIGIN_B`. `HuntSites.groundFor` must return the scene's marker and `HuntSites.meatWeights` must read **(72, 90, 108, 90)** (the plains biome, 9 sample columns; the GameTest world is FLAT plains with seed 0). All weapons are unenchanted (Unbreaking would make the wear random). `lightYard` torches stay 2 blocks away from the ground markers: a torch on the marker cell silently replaced it in a first version (the Lodge went invalid).
- **Predicted yields**: a hunter's trip is a function of (world seed, UUID, trip counter, whether an arrow was taken, the weights): the tests compute `HuntTestSupport.predicted/predictedTrip` and compare with what the hunter carried when it came back (`MineTestSupport.Tracker`, reused: it records every departure and return) and with Storage's totals.
- **Measured bounds** (level 1, stone sword): the hunter is out 167 to 176 ticks after hiring (the walk is about 12 blocks round the Lodge room), a trip is 2981 to 3000 ticks away (the first 20-tick step comes 1 to 20 ticks after the hide), a whole cycle (out, away, hand in, out again) about 3220; the second trip ends about 6380 ticks after hiring, i.e. at day time 7400, so the third trip is refused by both the cap and the cut-off. Two hunters hired together: the first is out at about 250 and the second 100 to 170 ticks later (the Lodge door). The tests log `[m17] ...` lines.
- The roadmap test `hunterWithSwordBringsMeatAndLeather`: a stone sword and 8 arrows in the chest, one day: at least 4 raw meat and 2 leather, **exactly** the two predicted trips in Storage, 10 durability, arrows untouched, each trip 3000 +- 25 ticks. `bowUsesOneArrowPerTrip` (bow before sword, 1 arrow a trip, +1 meat each), `bowWithoutArrowsHuntsWithoutBonus` (a tipped arrow put in later is taken), `hunterNeedsWeapon`, `groundInvalidWhileAway`, `departureCutoffAndDailyCap` (hired at 7300 goes out, at 7800 never; two trips counted for today stop a third, the same count saved for another day does not), `heartShowsAwayHuntingAndFireWhileAway` (also the canary that `MinerSystem` does not surface a hunter: 200 ticks), `twoHuntersOneGround`, `hunterBlockedByHiddenHunterGoesOutBesideIt`, `farHunterWalksViaLodge`.
- **Two flakes found and fixed (root causes)**: (1) a hunter walking behind a hidden one: a NoAI villager is solid for a walker, and under CPU load (the `gradlew build` step runs the JUnit tests next to the GameTests) the second hunter lost 100 to 2600 ticks pushing against the first (3 of 8 runs under 3 busy loops; 0 of 8 with the rule "touching an away villager near the ground counts as arrived"); the corridor test is the deterministic check (35 ticks with the rule, 133 without). (2) `minerHaulsFortyCobblestone` looked for stray item entities 40 blocks around the test and saw the chests that a neighbouring high-scene test dropped when it finished (`clearHigh`) at that very moment (age 0): both tests now look at their own footprint.
- **The far hunter** (`farHunterWalksViaLodge`) stands at (6, 0, -10), outside the 32 x 32 structure and more than 40 blocks from the far ground at (26, 0, 26) (nothing inside the footprint can be 40 blocks from a marker with a field around it). Whether a chunk outside the structure is entity-ticking depends on where the test was placed, so the test forces **every** chunk the strip touches at its start (forcing only the chunks of its two corners missed the far spot's chunk at 9 of 256 placements: block-ticking, never entity-ticking; backlog-2) (a forced chunk needs up to about 70 ticks to tick entities, the scene takes as long) and asserts the premise. The strip is laid and restored block by block.
- **Layer 4:** `HunterClientTests.villagersTabAwayHunting` (`villagers_tab_away_hunting.png`): a real claim, a real Lodge (fletching table, chest with a stone sword) with a real Hunting Ground linked to it, a real hire, the hunter sent out by a test call to `AwayTrips.hide`; the row reads "Away (hunting)" with the hunter's name (asserted in the client's snapshot before the shot).
- **Repeating Layer 2:** a full `runGameTest` leaves about 740 MB in `build/run/gameTest/world`; on a shared disk delete it between repeats (a full disk made a repeat loop fail with "No space left on device").

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
- **No false "cannot reach":** `assertNoGiveUps(h)` (every verdict is in `LumberRuntime.giveUps()`); only `radiusAndUnreachable` may have one.

## Builder GameTests (M20)
`BuildGameTests` (helpers in `BuildTestSupport`; listed under `fabric-gametest` in `src/gametest/resources/fabric.mod.json`). Rules learned while building M20:
- **Environment per test** `villageheart-gametest:build_<name>`: JSON with `game_rules` (`minecraft:spawn_mobs` false, `minecraft:advance_weather` false, `minecraft:random_tick_speed` 3, 0 for `clearing`, 100 for `instant_farm`) and `weather` clear. Structure `yard`, `skyAccess = true`. `BuildTestSupport.layout(h, With.X)` cleans and grasses the yard (it is not cleared by the framework), claims, sets the tier to VILLAGE and builds a valid Storage (`STANDARD`: plus a Builder's Hut with a table and torch posts around the site area; `STORAGE_NO_HUT`; `GRID`: a small Storage in the east strip and four sites in a 2 x 2 grid). It resets the builder action listener. End with `VillageTestSupport.done(h)` (it resets the build runtime) and, for builder tests, `WorkerTestSupport.begin/end`.
- **`GameTestHelper.relativePos` is not the inverse of `absolutePos`** (see "GameTest position helpers"): use `VillageTestSupport.rel` or `BuildTestSupport.rel(h, Pos)`. Our tests are never rotated.
- **The instant builder:** `/villageheart build start <blueprint> <x y z> <rotation>` and `BuildJobs.advance(server, id, n)` run the same placement code as a builder villager (`BuildExecutor`), without walking; `n` counts cursor steps (free steps included): a Small House is 589 steps (246 clear, 172 place, 171 check). The instant tests (T-L2-2a/b/c, 3, 12) put every touched cell through the real site rules (`SiteValidation`).
- **The item flow** needs a SURVIVAL player (`BuildTestSupport.playerWithRank`): vanilla restores the stack count of a used stack in creative, so "the blueprint is used up" cannot be seen. Click with `BuildingTestSupport.rightClick(h, player, groundRel, Direction.UP, stack)`; the player stands at `PLAYER_SPOT` looking north, so the first click picks CLOCKWISE_180 (the door turns towards the player).
- **Builder timing** (measured, level 1, the standard site): a Small House takes about 5 200 ticks from the confirm (172 placements at 20 ticks, a fetch of about a stack at a time, the walks round the site); a Farm Plot about 5 900 (81 clears, 121 placements). The roadmap tests assert "before 2 days" (48 000) and log the real numbers (`[m20] ... after N ticks`).
- **Walking:** `WalkTo` accepts a path node within 1 block as "reached", so a villager may stop 1.2 blocks from its target: the builder's walk arrives within 1.4, aims one column further out than the stand point and acts within `BUILD_REACH` + 1.4. Debug a stuck builder with `-Dvillageheart.build.trace=true` (every plan, wait, claim and release) together with `-Dvillageheart.lumber.trace=true` (every path).
- **Per-block checks:** the builder opens doors, so "the site did not change" hashes the site box only (`BuildTestSupport.boxHash`), not the yard. A farmland cell must be placed moist: dry farmland turns to dirt on its first random tick and the water is the last thing placed.
- **Texts with positions** (`build.villageheart.*`) keep args of up to 32 characters on the wire (`HeartCodecs.BUILD_TEXT_LINE`); a test world's coordinates are 7 digits, so a 16-character cut shows at once ("Something is in the way at (7958345, -57, 1").
- The roadmap tests: `smallHouseWithinTwoDays` (a valid House, materials deducted exactly with every other id unchanged, every action from outside the box), `pausesAndResumes` ("Needs 12 more planks", nothing changes for 600 ticks, adding planks resumes), `buildSurvivesRestartAndFiring`; the edge cases `playerBlockAppearsMidBuild`, `checkPassRepairs`, `farmPlotBuilt`, `entityInTheWay`, `twoBuildersTwoBuilds`, `keptMaterialsNoTripLoop`, `buildersTablePermissions`.
- **Layer 4** `BuildClientTests.builderFlow` (`builders_table.png`, `blueprint_footprint.png`, `blueprint_footprint_red.png`, `build_mid.png`, `heart_buildings_build.png`, `build_finished.png`): the table opened by a real right-click, a blueprint taken through the screen, the footprint green and red (client-side `blueprint_site` asserted), two clicks confirm, the House staged at 50 percent by the instant builder with a frozen builder villager whose claim is pinned (`BuildSystem.pinClaimForTest`), the Buildings tab row asserted on the client snapshot ("Building: 50%", "Needs 12 more planks"), then the finished green House.

## Defence GameTests (M21)
`DefenceGameTests` (helpers in `DefenceTestSupport`, reusing `BuildTestSupport`; environments `villageheart-gametest:defence_<name>`, rules as for the builder tests).
- **Lines by command:** `DefenceTestSupport.startLine(h, id, fromRel, toRel)` runs `/villageheart build line`; `DefenceTestSupport.assertBuilt` checks every touched cell of a blueprint against the world; `advanceAll` finishes every pending build with the instant builder. Eight pending builds is the limit (rule 5): a test that starts more advances in between.
- **The line item** is tested with a SURVIVAL steward and `BuildingTestSupport.rightClick`: start, end, and a click on the shown line to confirm. A click on the shown box of a pending site confirms with a fresh check (M20): after a refusal is fixed (`setTier`) the next click builds, it does not just show.
- **Lamp spawn test (`lampPostStopsSpawnsOverOneNight`)** calls the predicate the natural spawner calls (`SpawnPlacements.isSpawnPositionOk` and `checkSpawnRules`, `EntitySpawnReason.NATURAL`, a seeded `RandomSource`) for zombie, skeleton, creeper and spider on every feet cell of the yard floor: the diamond of radius 12 around the post must give 0 passes over a night, the ring at 13 and a far control must give many (so the test could see a spawn), and the same cells before the lamp is built must give many. Block light is asserted to be exactly `15 - steps from the lantern` on every cell. The night is time-warped: the day time is set to every 100 ticks of the clock from 12 500 to 23 500, one sample per tick (the sky darkness is read one tick after `setDayTime`; 11 on the dark plateau). The Heart (light 10) and the small Storage sit in the NW and NE corners; their cells and a margin are left out of the sets. The test is deterministic and takes about 120 ticks. It proves the vanilla rule, not a live spawn (natural spawning needs a player 24 to 128 blocks away and is random).
- **Layer 4** `DefenceClientTests.walledSection` (`wall_line_preview.png`, `walled_section.png`, `walled_section_gate_closed.png`, `walled_section_gate_open.png`, `watchtower.png`): the line preview from two real clicks (client-side `blueprint_site` length 16, green dust), the section built by command at dusk and seen from a stand high over the wall (lanterns lit), the gate opened with a real right-click, the tower seen from inside the wall. Since M25 the section records 6 sites (wall, gate, wall, two lamp posts, watchtower); the pictures are unchanged. `-Dvillageheart.l4.only=defence` runs just this. `BuildClientTests.builderFlow` also selects the Gate and Watchtower rows of the table (`builders_table_gate.png`, `builders_table_watchtower.png`).
- **Siege GameTests** (M25 track B) live in `SiegeGameTests`, one environment `villageheart-gametest:siege_<name>`, walls built with `BuildJobs` and never filled. `wallIsRecorded` checks a wall of 10 and a gate of 5: lengths 10 and 5, 42 defence cells (39 cobblestone, 3 gates), no patrol point. Raider breach (`ravagerBreaksWallInTwoHits`, `vindicatorChopsGate`) uses a wide ring so the south face is the closest to the bell, and holds the raider on the attack block until the opening is air. `TrackedRaids.track` stops raids within 96 blocks and clears other occupied village points within 64, because the suite keeps earlier tests in the world and vanilla would centre the raid on a neighbour.
- **Repair GameTests** (M25 track B, step 15) live in `SiegeRepairGameTests`: `builderRepairsWithStorageCobblestone`, `breachStaysOpenWithoutMaterial`, `noRepairDuringThreat` (environments `siege_repair_order`, `siege_repair_short`, `siege_repair_raid`) and `rationDoesNotRefillBesideAnEnemy` (`guards_ration_pin`). The gate is built with `DefenceTestSupport.startLine` and cracked by `SiegeDamage.hit`, then the stock is set to exactly 10 cobblestone and 1 oak fence gate. The repaired gate must equal the rotated blueprint state, Storage must be 6 cobblestone and 0 gates, work ticks at least 120, and the saved damage must be empty (a scan does not clear a crack). The short test puts back only the fence gate, so the gate closes and a stone hole stays air. The raid test holds vanilla waves (`raidCooldownTicks`) and requires the builder's brain to be `villageheart:shelter` with the stock untouched; after `TrackedRaids.stop` the fence gate must leave Storage. `rationDoesNotRefillBesideAnEnemy` holds the guard with `setNoAi` so a patrol cannot leave the 10-block bubble, and moves the zombie to `(30, y, 30)` inside the yard. `watchtowerBreachKeepsSite` then hires a builder and requires both ladder keys back, with the saved breach cleared.
- **Lure GameTests** live in `SiegeLureGameTests`, one environment `villageheart-gametest:siege_<name>`. The ring is 15 by 15 outside, the gate on the north, so a zombie walking up from the south meets solid cobble. `zombieBehindClosedWallBreaks` allows 1200 ticks to stand beside the wall (the plan's 600 is a straight walk; the π/2 waypoint arrived in 794) and 2000 ticks for the first hit (a full suite measured 1802). A skeleton and a spider grow no mod goal. Daytime does not lure unless a siege is recorded. A lamp post is not a wall. At most 24 of 30 zombies are lured, and at most 2 paths run in one tick. A trench stops the zombie, and a creeper there does not light. A three-column gap is walked through with no damage. One short wall does not lure. `creeperBreachesWall` is the night breach and a day batch that never lights. `nobodyHuntsTheBreacher` keeps a guard and a player-built iron golem off the creeper. `breachSparesHousesBehindWall` leaves the plank house and hurts the villager. `vanillaCreeperStillVanilla` is the player-targeted crater, then a planned repair. `siegeDamageGameruleCreeper` is the creeper half of T-L2-36.
- **Text args:** `TextLine` keeps at most 2 args (`HeartLimits.MAX_TEXT_ARGS`); a lang text with three `%s` shows as the raw format string on the client (the L4 picture caught it).


## Guard tests (M18+)
- `GuardGameTests` (helpers `GuardTestSupport`: `scene(h, guards)` = claim + tier Village + a valid Barracks with that many grindstones and beds;
  `hireGuard(h, spawn, pin)`; recorders for damage and villager deaths; `zombie(h, rel)` = an adult that does not burn) and `GateGameTests`. Each test
  has its own environment `villageheart-gametest:guards_<name>` / `gates_<name>` (natural spawning off, so no mob joins a test that did not spawn it)
  and the `yard` structure. Filter ids: `villageheart-gametest:guard_game_tests_<method_snake>`, `villageheart-gametest:gate_game_tests_<method_snake>`.
- **Qualified or trainee**: `hireGuard` hires through the real path and then clears the training day (so the shift tests are about shifts); `hireTrainee`
  keeps it (the night-hire rule is switched off for the call by `Workers.nightGuardHireForTests`).
- **Level 3 guards in fights**: a test fight uses `level3` (40 health, +2 damage) so it is survivable; zombies are helmeted (no sun burn). A level 1 guard
  has 20 health, a wooden sword and no armour (`guardBeatsOneZombie` is the duel: the guard must WIN, the zombie dead within 400 ticks, with no health bound. Owner decision 2026-11-11: a wooden sword takes 5 hits at vanilla damage 4.0 and the zombie lands 5 or 6 hits of 3.0 meanwhile, so the health left varies from 2 to 20. "At least 50 % with a better sword" stays a BACKLOG idea, not a test).
- **Rules added by the M18 round-1 and -3 reviews** (each has a GameTest and, where it is pure, an L1 test): `alarmWakesSleepingGuardsAndTheyReturn`,
  `guardSwitchesToTheMobThatHurtsIt`, `barracksBedEvictsHiredWorker`, `brokenGrindstoneKeepsTheGuardOnDuty`, `guardBrainHasNoPanic` (the control villager is
  penned so it cannot stroll out of the zombie's range), `newGuardTrainsOneDayThenWorksTheNight`, `traineeSparsAtTheYardAndGainsXp`,
  `guardEatsItsRationToHeal`, `noFoodNoHealingNotEvenInBed`, `offDutyGuardRefillsFromStorageAndHeals`, `guardDeathWakesTheOffDutyGuards`,
  `noGuardIsHiredAtNight`, `seniorGuardsTakeTheNights`, `hurtGuardFallsBackEatsAndRejoins`, `alarmResetsAndKeepsItsDimension`,
  `fireAndRehireIsNoFreeHeal`. L1: `ShiftBalancerTest`, `GuardRationTest` (ration, heal, training, night hire), `GuardScheduleTest`, `GuardPerksTest`.
- **Gate tests are mutation-checked**: `mobsStillCannotPassTheGate` asks for the cell `(16,0,15)` at accuracy 0, which an unhired villager reaches when
  `GatePaths.isHired` is mutated to "any adult villager" (the earlier target `(15,0,16)` at accuracy 1 never reached, so that mutation stayed green).
- **Trace**: `-Dvillageheart.guard.trace=true` logs every target, hit and drop of a guard (`[guard] ...`).
- **Gate scene** (`GateGameTests.ring`): a 31 x 18 cobblestone ring south of the camp with a three-wide gate of closed oak fence gates; the oak is more
  than 8 blocks from any spot outside the wall (the lumberjack chops from the nearest reachable spot within 8, so a smaller ring proves nothing).
  `insideRing` compares in world coordinates (structures may be rotated).
- **Layer 4**: `GuardClientTests` (`guard_on_duty.png`: a day guard holding an iron sword and a night guard asleep, the client-side main hand and pose
  are asserted; `heart_villagers_guards.png`: the Villagers tab with a Shift button that is clicked once). `-Dvillageheart.l4.only=guards` runs just these.
- **Layer 3**: `scripts/simulate.sh defended` (the starter village + a Barracks with two guards + a Training Yard + 27 scripted zombies). New actions
  `tier`, `training_yard`, `night_spawn`; `prefab` words `grindstone` and `target`; two `hire ... replace` actions keep two guards on the roll.
  `scripts/simulate.sh defended_walled` is the realistic twin: the same village and assertions, plus `wall_ring` (a two-high cobblestone wall on the
  edge of the site with ONE oak fence gate that workers and guards open), `lamps` (fence posts with torches every 7 blocks) and the zombies placed
  `outside` the wall (`night_spawn` field). `defended` stays the harsh case (wooden kit, no walls). Both run unseeded 5 times for a verdict (the result
  varies run to run). New actions have `ActionSpecs` field checks and `DefendedScenarioTest` entries.
  The re-hire hook waits for the morning (no guard is hired at night). Per-day numbers worth reading: `deaths.mobs`, `work.guard.kills`, `duty_samples`,
  `employed`, `ration_items`/`ration_points`/`food_eaten` (food the guards took and ate), `retreats`, `guards_night`/`guards_day` (the shift counts).

## Tier tests (M14+)
`TierGameTests` (helpers in `TierTestSupport`): one yard with a Farmhouse (3 composters), a Storage with bread, a Kitchen, a Builder's Hut (a Village building, the "red until the tier-up" control) and six villagers; three of them hired as farmers. Seed the progress (`seedProgress`: a written history, the L1 tests prove the counting) and cross one real dinner (`toDinner`: forward only, waits for M11's record) and one real dawn (`toDawn` = `WageTestSupport.toDawn`). Dinner is switched on with `NeedsOverride.dinnerEnabled = true` (needs stay forced). Note that `nextDinner` jumps over a midnight, so a dawn already runs at the start of `toDinner`. `DawnProbe` is a `test:` dawn listener registered after M14's: it records the tier and `BuildingSystem.priorityQueuedForTests` inside the dawn tick, which proves `Buildings.requestAll` ran (a timing bound cannot: the round-robin might get there by luck). `WageTestSupport.done(h)` drops it.

## Market GameTests (M22+)
`MarketGameTests` (helpers in `MarketTestSupport`): own `market_<name>` environment, `yard`, `skyAccess`, `WorkerTestSupport.begin` first. The scene is a Market (1 or 2 stalls, a chest) beside a Storage (2 chests) at the village tier (`BuildingTestSupport.setTier(h, Tier.VILLAGE)` before the markers: a Market is a Village-tier building), valid before the test starts. Wages stay off unless the test turns them on (`strikeStopsSalesIncomeSettles`). Rules:
- **Literals.** Assertions use literal emerald and item counts (4 emeralds, 72 cobblestone left), never values computed from `PriceTable`, so a price change cannot weaken a test; `PriceTable.lot(...)` is asserted next to it.
- **Samplers.** `MarketTestSupport.Sampler` is registered with `h.onEachTick` from the test body (never inside a callback) and records the minimum of watched stock and a conservation law (stock + 32 x treasury + carried + dropped == the start) on **every tick**; violations are collected and asserted at the end.
- **Time.** The merchant works in the WORK phase (day time 1000-11000): tests set 1000 after the scene and use `thenWaitUntil` on counts, with `maxTicks` for the longest case (the roadmap test runs a whole working day). The daily ledger is by day index: `toNextMorning` moves to the next day's 1000.
- **Seams.** `Markets.withdrawLimitForTest` (caps what a sale's withdraw may ask for), `Markets.freshViews` and `Markets.withdrawCalls` (counters); `VillageTestSupport.reset` clears them and the runtime caches. Population is added with `MarketTestSupport.population(h, n)` (NoAI villagers).
- L4: `MarketClientTests.stockpileReserves` (`-Dvillageheart.l4.only=market` runs only it): a real merchant that has sold, the Stockpile tab shot, the reserve buttons clicked through the UI.

## Self-craft GameTests (M19a)
`SelfCraftGameTests` (miner basics, helpers in `SelfCraftTestSupport`), `SelfCraftLifecycleGameTests` (wood then stone, the lumberjack bootstrap, overnight pause and reload, refunds, sharing, Storage tools, a full chest, worn-first) and `SelfCraftJobsGameTests` (hunter, farmer, the smith seam, the recipe tables against the game). Environments `villageheart-gametest:selfcraft_<name>` (the farmer's carries `random_tick_speed 0` like the farm ones); scenes come from `MineTestSupport` (the low scene, a second stonecutter where two miners are hired), `LumberTestSupport`, `HuntTestSupport` and `FarmTestSupport`, with the Storage's first chest filled by `SelfCraftTestSupport.add`. Day time is set to 1000 after the scene; every test logs `[m19a]` tick counts.
- **The clock**: `SelfCraftTestSupport.Sampler` (register `h.onEachTick(() -> s.sample(h))` from the test body) records when a worker's `self_crafts` record appeared (the commit), when it went, and the ticks its progress really advanced (`SelfCraftRuntime.State.progress`). The timing asserts check the clock against `Balance.SELF_TOOL_TICKS` (`[tier - 20, tier + 100]`) and the wall time against `selfBound(ticks, dist)`.
- **`WorkStats` counters are global**: read the counter before the scene and assert the delta.
- **A tick hook outlives the test's reset**: use `safeStatus` (null without a village) in `h.onEachTick`, not `status`.
- **The older tool tests**: a tool worker with no or a worn tool in a world whose Storage holds wood now stops to craft. Tests whose subject is the old "Needs <tool>" row (`minerNeedsPickaxe`, `noAxeNoLogs`, `hunterNeedsWeapon`, and the L4 `villagersNeedsAxe`) call `SelfCraftSwitch.off()` after the scene (`VillageTestSupport.reset` turns it back on); the row for "no tool and nothing to make one from" is the new `TOOL_NO_MATERIALS` ("Needs axe (no wood)"), shown by `noWoodSaysSo` and the L4 shot.
- **Restart path**: `SelfCraftRuntime.resetForTests()` + `GearOrders.rebuild(server)` is what a server start does (`SelfCraftRuntime.get` rebuilds the committed orders from the saved records).
- **Layer 4** `SelfCraftClientTests.villagersTabMakingTool` (`villagers_tab_making_tool.png`): a miner with a saved stone-pickaxe craft ("Making a stone pickaxe") and a lumberjack with an empty Storage ("Needs axe (no wood)").

## Flake root causes (2026-11-09) and the rules they gave
- **Layer 4: the world-load timeout.** Fabric's `ClientGameTestImpl.waitForWorldLoad` (every `worldBuilder().create()`) polls "a level exists and the loading screen is gone" for
  1200 client ticks (`TICKS_PER_MINUTE`), then throws `Timeout loading world`. The signal is the right one, the bound is a tick count for wall-clock work (the integrated server
  prepares its spawn area on worker threads; at load 10+ it sat at "Preparing spawn area: 16%" for 2 min). No API or system property changes it, so the gametest-only client mixin
  `dev.villageheart.gametest.mixin.WorldLoadTimeoutFix` (`@ModifyConstant`, required) raises it to 6000 ticks. A world that never loads still fails. It logs
  `WORLD_LOAD_TIMEOUT_FIX installed`; `verify.sh` fails if that line is missing. Written reason for the looser bound: the bound measured time, not progress.
- **Tick budgets and async work.** A GameTest server ticks as fast as it can, so 2200 ticks are a few seconds. Anything that completes on another thread (chunk generation and
  promotion to entity-ticking, lighting) can take far more ticks than that under load. `forcedChunkGrowsWithoutPlayer` waited 454 ticks for a forced far chunk to start ticking
  in a full run (7-15 alone) and timed out at 2200 under load. It now generates the chunk and its ring synchronously (`getChunk(..., FULL, true)`) before forcing it: ticking starts in 1 tick.
- **Own the world's random state.** Random ticks (default 3) grow replanted saplings: `ironAxeOutFellsStoneSideBySide` counted felled logs only inside the original groves, a regrown tree
  put logs outside them and the axes wore for logs the sampler never saw ("axe wear 115 but 114 logs felled"; reproduced at random_tick_speed 300). Its environment now sets `random_tick_speed` 0
  and the test asserts it. A test that counts world changes must not leave world generation or growth running.
- **Own the clock before switching on a clock-driven system.** The day time is whatever earlier tests left and it advances. `villageGoalsTierUpAtDawn` enabled dinner a few dozen ticks before 11000
  and the village served a natural dinner on top of the forced one ("Expected one dinner to be 1: was 2"; reproduced by starting at 10970). `TierTestSupport.pinClockEarlyInDay` sets
  1000 and counts today's dinner as served; use it before `NeedsOverride.dinnerEnabled = true`. Not a double count in the game: the second dinner was a real, different one.
- **A walk that "arrives" must arrive where the work starts.** `WalkTo` returned ARRIVED up to 0.1 block beyond the reach it was given, while every job works only within the reach itself,
  so a worker 1.5-1.6 blocks from its job block neither walked nor worked, for the rest of the day (a push from the other miner puts it there): `craftPausesOvernightAndSurvivesReload`
  "progress after the evening: 4003.0". Root fix in `WalkTo`: ARRIVED only when `distance <= reach` (the tolerance is gone; the `-0.2` workaround in `SelfCraftJob` is gone too).
  Tests: `craftWorksFromJustOutsideTheReach` (1.55 from the job block; progress 4003.0 without the fix) and `WorkerGameTests.walkToNeverArrivesBeyondItsReach` (reach + 0.05 for the reaches 1.5, 2.0, 3.0:
  not arrived, then walks inside). Every caller (miner, hunter, lumberjack, farmer, builder, cook, deposit trip, self-craft) walks through `WalkTo`, so that test covers them by construction; the full L2 suite is green
  with the strict rule. Item pickups pass 0.8, which a villager never reaches (it stops at about 0.85): they take by their own `COLLECT_TAKE` distance and ignore ARRIVED, so they are unaffected.
- A failing `craftPausesOvernight...` prints a 25-tick trace of the worker (position, decision, progress) in its message; a failing side-by-side run lists the ticks where logs and wear disagree.
