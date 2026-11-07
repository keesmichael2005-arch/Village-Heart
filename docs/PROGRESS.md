# Progress log

## 2026-11-07: M0 scaffold
Fabric 1.21.11 project, test layers 1/2/4 working in cloud + CI. Next: write docs/DESIGN.md and the M1+ milestones.

## 2026-11-07: design agreed (branch `design`)
Planned v1 with the user: docs/DESIGN.md (buildings via crafted markers + room validation, vanilla-first job blocks,
hybrid worker style, Storage-chest stockpile, emerald wages, tiers Hamlet→Village→Town, guards with shifts/training,
Blacksmith golems, Builder blueprints incl. defences, ranks for multiplayer) and ROADMAP M1–M26 with L1–L4 acceptance tests.
Next: user review of the `design` branch, then M1. Risk to watch: villager Brain control + workstation reservation (M7 spike).

## 2026-11-07: M1 plan written (docs/plans/M1.md)
10-step plan with APIs checked by javap against the 1.21.11 jars; revised after a fresh critique round. Next: BUILD M1.
Top risk: GameTests run in parallel per environment, but the claim is global. The plan uses one test environment per claim test, with probe tests and a single-sequence fallback.

## 2026-11-07: M1 built (builder)
Village state, persistence, one village per world. Core: `HeartLocation`, `Rank`, `Tier`, `BuildingRecord`, `WorkerRecord`,
`VillageState`, `WorldClaim` (all pure, unit-tested). Glue (`dev.villageheart.world`): `VillageCodecs` (DFU only, invalid values become
DataResult errors), `VillageSavedData` (overworld `data/villageheart_village.dat`, every mutator sets dirty), `VillageClaims` service.
Block: `getStateForPlacement` refuses (no block, item kept, action-bar message, container resync), `setPlacedBy` commits, only
`affectNeighborsAfterRemoval` releases (treasury dropped as emeralds via `Containers.dropItemStack`), Fabric `PlayerBlockBreakEvents.BEFORE`
makes the claimed Heart owner-only, `PushReaction.BLOCK`, wither/dragon immune tags, stale-claim self-heal.
Tests: L1 `ClaimRulesTest`, `VillageStateTest`, `WorldClaimTest`, `VillageCodecsTest`; L2 14 GameTests (placement, second heart refused, owner-only break,
treasury drop incl. block-drops gamerule off, save/reload from disk after each mutator, piston, stale heal, inert heart, isolation probes);
L4 `refusedPlacementKeepsClientItem` (real right-click, screenshot `heart_refused_no_bell`).
Per-test GameTest environments worked first time (see docs/TESTING.md). Next: M2. Blockers: none.

## 2026-11-07: M1 review round 1 fixes (builder)
T-L4-1 now teleports to a block centre and asserts the client can predict the placement; verified red without `sendAllDataToRemote()`.
`purgePoi` lets vanilla remove bell POIs (set to air) and only calls `poi.remove` for non-bell POIs: no more "POI data mismatch" errors.

## 2026-11-07: M1 review round 2 fixes (builder)
`purgePoi` now touches only MEETING POIs (bell block to air; orphan POIs removed), plus regression test `resetLeavesOtherPoisAlone` (composter POI survives reset).
T-L2-6 now re-claims, reloads, clears and reloads again, so a missing `setDirty()` in `clear()` goes red (mutation-checked).

## 2026-11-07: M1 review round 3 (verifier): PASS
Both round-2 items re-checked with mutations (red without `setDirty()` in `clear()`; red when `purgePoi` touches all POIs).
Full `scripts/verify.sh` exit 0, 15/15 GameTests in 3 runs, 0 POI mismatches. M1 ticked. Non-blocking notes in docs/reviews/M1-r3.md. Next: M2.

## 2026-11-07: M2 plan written (docs/plans/M2.md)
10-step plan for the Layer 3 harness, de-risked with a throwaway dedicated-server spike. Findings:
- A Loom run config `runSimulation` (`server()`, gametest source set, request file) boots a seed-1 world in ~4 s.
- The seed-1 plains village is at (640, ~, 816), with its bell at (639, 66, 821) and 8 villagers. The Heart claims through a real `useItemOn`.
- Sprinting with a fake creative owner takes ~50 s per in-game day, so a 5-day run is ~6 min and a 30-day run ~25–30 min.
- A server crash still exits Gradle 0, so `simulate.sh` trusts only the report status plus `clean_shutdown`.

The plan was revised after a fresh critique (16 findings): process-group kill and lock, stall/start checks, `ActionSchedule`/`SimClock` with `>=`,
a section-based tick timer for entity-tick work, a stubbed exit-code self-test in `verify.sh --fast`, and death-cause dedupe.
Next: BUILD M2. Top risk: run time and run-to-run variance of long scenarios (villager AI is not seeded).

## 2026-11-07: M2 built (builder): Layer 3 long-simulation harness
`scripts/simulate.sh <scenario> [days] [--assert ...] [--reload-at D]` boots a real dedicated server (Loom run `runSimulation`, gametest source set,
harness `dev.villageheart.sim.*`, off unless `-Dvillageheart.sim.request` is set) on seed 1, claims the plains village at bell (639,66,821) through a real
`useItemOn`, sprints N days and writes `reports/sim/<name>.json` (+ `.summary.md`, logs). Exit 0 PASS / 1 FAIL / 2 ERROR / 64 usage; only the report and
`clean_shutdown` decide, never Gradle's rc. Core (pure, L1-tested): `Json`, `TickTimer`, `DayMetrics`/`MetricsJson`, assertion language, `ScenarioParser`,
`ActionSchedule`, `SimClock`, `SimRequest`. Main: `VillageSystems`/`VillageTicker` (the 1 ms/tick measurement hook). L2: T-L2-1/1b (timer), T-L2-2..4 (collector), action-id sync.
`verify.sh` gained the stubbed "Layer 3 script self-test". Real runs: `bare 5` exit 0 in 237 s wall (5 records, village_ms.mean ~0.0001-0.0002 ms/tick, 24000 samples/day,
~450-840 server tps, 8 villagers throughout); red-is-red `--assert "population >= 1000 on day 1"` exit 1; bad assertion exit 2 in 21 s; `selftest_error|crash|hang` exit 2;
JVM killed mid-run exit 2; `bare 4 --reload-at 2` exit 0 (`reloads [2]`, day 3 has 23999 samples).
Note: one earlier `bare 2` lost 3 villagers to zombies on night one (unseeded AI); the collector recorded them correctly. Next: M3. Blockers: none.

## 2026-11-07: M3 plan written (docs/plans/M3.md)
7-step plan for markers, recipes and the 3 new job blocks, with APIs checked against the 1.21.11 decompiled sources (`genSources`).
- One core enum `core.building.BuildingType` (id, name, tier, kind, ring, parent site link) + `JobBlockType` + `ShapedSpec` drives
  registration, assets, recipes and later M4/M5/M7. `MarkerBlock` is wall-sign style (`isSolid()` support, 8-arg `updateShape` returns air →
  drops via loot table) with `facing` (out of the wall) and a `status` property (unchecked/invalid/valid) added now for M5.
- Assets (blockstates, models, item defs, loot, recipes, recipe-unlock advancements, axe tag, 16×16 PNG textures) come from a pure-Java
  generator in the test source set (`./gradlew genAssets`), committed, and pinned by an L1 golden test; lang stays hand-written with an L1 check.
- Verified gotchas: block models cannot use item textures in 1.21.11 (separate `items` atlas); `GameTestHelper.placeAt` clicks the air in front
  of the target, so marker tests use their own `clickFace` helper; creative tab contents are only built on the client.
- L4: a wall with all 14 markers + 3 job blocks (+ one fence-post marker) under noon + night vision, plus an inventory screenshot, and a
  `check-model-log.sh` gate on missing-model/texture warnings.
Revised after a fresh critique (20 findings, 0 blockers). Next: BUILD M3. Top risk: readability of 14 distinct 16×16 icons (judged by eye).

## 2026-11-07: M3 built (builder): markers, recipes, job blocks
`core.building` (`BuildingType` x14, `Ring`, `ShapedSpec`, `JobBlockType` x3, `Ingredients`; `Balance.BLANK_MARKER_YIELD`), glue `MarkerBlock` (wall-sign style, `facing` out of
the wall, `status` unchecked/invalid/valid for M5), `JobBlock`, `MarkerBlocks`/`JobBlocks`/`ModItems`/`ModCreativeTab` (tab `villageheart:main`). All 199 asset files are generated by
`./gradlew genAssets` (test source set) and pinned by `GeneratedAssetsTest`. 19 recipes (Blank Marker, 14 markers, 3 job blocks + Heart), recipe-unlock advancements.
Tests: L1 `BuildingTypeTest`, `RingTest`, `ShapedSpecTest`, `JobBlockTypeTest`, `LangCoverageTest`, `GeneratedAssetsTest`; L2 `MarkerGameTests` (registry, every recipe + tag variants + negatives, advancements through
a real player, attach/refuse/drop incl. a sticky-piston pull, fence and glass sites, drops, job-block facing); L4 `marker_wall` and `marker_items` with a client-side missing-model assertion and
the `check-model-log.sh` gate (proved red by pointing one model at a missing texture). A mutation (`canSurvive` always true) was caught by 5 GameTests.
Art: iterated three times on the x8 sheet; the axe, wheat sheaf, bow and mineshaft (now a mine cart) were redrawn after the first look; House/Mine/Barracks/Market/Hunting Ground colours were changed to meet
the 3:1 contrast rule. Next: M4. Blockers: none.


## 2026-11-07: M3 review round 2 (verifier): PASS
On a clean checkout, `scripts/verify.sh` exited 0 twice (L2 38/38, L4 green, no synchronizer flake). The model-log fixtures are now tracked, and no other test reads an ignored file.
Two mutations went red as expected (a storage recipe ingredient, and the kitchen marker's loot item). Both screenshots were looked at: the marker_items shot is clean now.
M3 ticked in ROADMAP. 3 non-blocking notes in `docs/reviews/M3-r2.md`. Next: M4. Blockers: none.
## 2026-11-07: M4 plan written (docs/plans/M4.md, branch m4)
7-step plan for the room flood fill, with APIs checked against the 1.21.11 sources. No gradle run, because the M3 builder was using the CPU.
- Core `core.room`: `Pos`, `Dir`, `Cell` (OPEN/SOLID/UNLOADED, door half, partner, content keys), `BlockView`, `RoomScanner`
  (door search from marker position + outward direction, BFS with a 4096 cap, floor area = distinct columns with a standable cell,
  contents from interior + shell), `RoomProblem` with exact messages, `RoomScan`, `SharedRooms` (same `roomKey` → both SHARED).
- Glue `dev.villageheart.room`: `RoomCells` (wall = any collision box, plus fence gates; cached per state, cleared on datapack reload),
  `LevelBlockView` (`getChunkNow`, never loads chunks; the sky test walks down from `WORLD_SURFACE` with the same rule), `RoomScans` facade.
- No dependency on M3 code: the scanner takes the marker position and M3's `FACING` (out of the wall); the marker cell is forced OPEN.
- Verified gotcha: GameTests are encased in barriers, on top as well unless `skyAccess = true`, so every room test sets `skyAccess = true`.
Revised after a fresh critique: 17 findings, including 2 blockers (the heightmap sky test, and localising a hole above the start). Next: BUILD M4.
Top risk: leak localisation (roof hole vs wall gap, and its position) is a pinned heuristic, so irregular holes can get the wrong word.

## 2026-11-07: M4 built (builder): room detection
Core `core.room` (`Pos`, `Dir`, `Cell`, `BlockView`, `RoomScanner`, `RoomScan`, `RoomProblem`, `SharedRooms`, package-private `LongMap`; `Balance.ROOM_MAX_AIR = 4096`): flood fill from just inside the door the marker
marks, floor area = distinct standable columns, contents from interior + shell (two-block things via `partner`), and one reason with a position (`Roof missing at`, `Wall gap at`, `Doorway blocked`, `Room too big`, shared, ...).
Glue `dev.villageheart.room`: `RoomCells` (wall = any collision box, plus fence gates; cache cleared on reload), `LevelBlockView` (never loads chunks; sky test = walk down from `WORLD_SURFACE` with the same rule), `RoomScans.scan`
(server thread only). Tests: L1 `PosTest`, `DirTest`, `RoomProblemTest`, `FakeGridTest`, `RoomScannerTest` (every roof cell, every wall cell, porch, inset roof, gable roof, door search, partners, floor area...), `SharedRoomsTest`,
`RoomScannerPerfTest` (16x16x8 median ~2.7 ms vs 5 ms; 4097-cell worst case ~2.5-3 ms vs 10 ms); L2 `RoomGameTests` (real room, roof hole + repair, 16 wall materials + 7 hole blocks + water, shared room, unloaded chunk, sky test, off-thread, classifier).
Mutation checks: removing `skyAccess` from `brokenRoofBlockReported` -> red ("expected a failing scan"); `blocked(H) >= 2` -> `>= 5` made 4 L1 tests red.
Deviations (see the plan): the roof test also tries the ceilings of E's other covered neighbours (gable roofs), primitive hash tables for speed, `scripts/testdata/*.log` un-ignored and committed.
Next: M5 (markers validate rooms: call `RoomScans.scan(level, pos, state.getValue(MarkerBlock.FACING))`, wrap in a VillageSystem, map `RoomProblem` to translation keys). Blockers: none.

## 2026-11-07: M4 review round 1 fixed (builder)
Perf tests assert the best of 5 batch medians (absolute 5 ms / 10 ms kept) and T-L1-17 adds a deterministic call-count guard. The extra roof-test candidates only consider neighbours inside the room (porch slot, pergola and y=8 regression tests added; gable test still passes). Larger roof holes now pin the exact cell.

## 2026-11-07: M4 review round 2 fixed (builder)
Perf wall-clock tests moved to a `perf`-tagged `perfTest` task run alone by verify.sh (retrying batches up to 5 s); deterministic call-count guards (tightened to 2x) stay in `test`. TESTING.md updated.

## 2026-11-07: Layer-3 harness made safe to run concurrently (builder)
`simulate.sh` no longer runs a machine-wide `pkill`: it kills its own process group and anything carrying its per-run `SIM_RUN_TOKEN` (found via /proc). Each run picks a free server port (two servers on 25565 crashed the second one).
`SIM_DIR` now works for real runs (`build.gradle` reads it). A crash rewrites the report (`ERROR`, `clean_shutdown: false`, `crash_reports`). New stub cases: wrong phase-2 `reloads`, crash recorded in the report, and isolation
(a bystander run survives another finishing and another being killed). Proof: two real `simulate.sh bare 1` from two worktrees at once both exit 0. While testing, another track's old `verify.sh` self-test really did pkill my first
real sim, which is the bug reproduced live. Next: M4. Blockers: none.

## 2026-11-07: M5 plan written (docs/plans/M5.md, branch m5-plan)
8-step plan for the building registry and validation, against M3's markers (verified on dev) and M4's planned `RoomScans`/`RoomScan`
API (assumptions A0-A8 in §3.0, so the builder can adapt once M4 merges).
- Registry lives in `VillageState.buildings` (`BuildingRecord` gains `status` and `link`, saved, backward compatible). Markers register in
  `MarkerBlock.onPlace` (ignores recolours via `oldState.is(this)`), unregister in `affectNeighborsAfterRemoval`, and an orphan sweep catches
  `/setblock` removals (flags `2|256`, no removal hook). Out-of-claim markers turn red without registering; pre-claim markers are adopted on claim
  (loaded chunks) or on right-click; unloaded markers are skipped and partly unloaded scans give a "deferred" verdict that changes nothing.
- Pure core: `BuildingRules` + per-type `BuildingRequirements` over an abstract `RoomFacts`, site rules, `ShaftScanner`/`AreaCounter` over M4's
  `BlockView`, `ClaimArea`, `Links`, and a `RoundRobin` scheduler (one building per 20 ticks + ≤1 priority check per tick) run inside a
  `VillageSystem`. Right-click validates synchronously and prints the reasons with `translatableWithFallback`.
- Tests: 13 L1, 14 L2 (own environments, a 32×28×32 SNBT yard structure, a measured `bound(n, hops)` for "within 2 s"), 1 L4 (red + green
  plaques and the chat report).
Revised after a fresh critique (24 findings, 3 blockers: shared-room requeue loop, grass in the Hunting Ground test, tight wait bounds).
Next: BUILD M5 once M4 is on dev. Top risk: M4's API differing from its plan (mitigated by the two small adapters).

## 2026-11-07: M5 built (builder): building registry and validation
Core (`core.building`, pure, L1): `BuildingStatus`, `BuildingRecord(type, marker, status, link)` (saved, old saves decode unchanged, duplicate markers dedupe on load), `VillageState.withTier/building/withBuilding/withoutBuilding`
(sorted, duplicate check), `Reason` (exact texts, translation keys), `Thing`, `Verdict`, `RoomFacts.of(RoomScan)`, `BuildingRequirements` (the section 3.3 table), `BuildingRules.checkRoom/checkSite`, `ClaimArea`
(3-D sphere, 80 / 128 for Mineshaft and Hunting Ground), `Links`, `ShaftScanner`, `AreaCounter`, `RoundRobin` (1 per 20 ticks + at most 1 priority check per tick), `VerdictPolicy` (deferred verdicts change nothing).
Glue (`dev.villageheart.building`): `BuildingSystem` (a `VillageSystem`: pending placements, round-robin, `useMarker`), `BuildingValidator`, `SiteSurveys`, `MarkerAdoption`, `MarkerReport`, `Buildings` facade, `BuildingContentKeys`
(`#rail`, `#natural`, `#anvil` added to `RoomCells`); `MarkerBlock` got `onPlace`, `affectNeighborsAfterRemoval`, `useWithoutItem`; `VillageClaims.claim/releaseAt` adopt and reset markers.
Tests: L1 (new `ReasonTest`, `BuildingRequirementsTest` (every room type, every shortfall), `BuildingRulesOrderTest`, `ClaimAreaTest`, `LinksTest`, `SiteRulesTest`, `ShaftScannerTest`, `AreaCounterTest`, `RoundRobinTest`,
`VerdictPolicyTest`, extended `VillageStateTest`/`VillageCodecsTest`); L2 `BuildingGameTests` (14 tests: Storage green then red within 2 s, break/orphan sweep, shared rooms, tier gate, no claim / outside claim, release, unloaded,
persistence, Mineshaft 12 deep linked to a Mine 20 away, Hunting Ground, Training Yard, RoomCells group keys, right-click report, recolour is not a placement); L4 `marker_validation.png`.
Mutation checks: no unregister on removal -> `sharedRoomBothRed` red ("survivor green took 38 ticks, bound 5"); shared rule not applied -> same test red ("plaque ... to be INVALID: was VALID").
Screenshot looked at: green Storage plaque (left) and red one (right, crosshair on it), chat shows `Storage at (-1, -58, 11): invalid`, ` - Needs 1 more chest`, `Containers: 1`. FOV 100, 4 blocks from the red plaque (block reach).
Interface check vs merged M3/M4 and every deviation: plan section 9. Notable: the in-tick click-spam reuse was dropped (it contradicts the same-tick re-check test); `Verdict.slots` for Storage is its container count; `yard.snbt` needs a palette;
tests sweep stray markers of neighbouring test structures before claiming (they otherwise adopt them and the measured bounds fail).
Next: M6 (stockpile reads `Buildings.valid(server, STORAGE)` and `latestScan().positions("#chest")` + barrels), M7 (job slots from `verdict().slots()`), M14 calls `Buildings.requestAll` after a tier-up. Blockers: none.


## 2026-11-07: M6 plan written (docs/plans/M6.md)
7-step plan for the stockpile over Storage chests, APIs checked against the 1.21.11 sources (no gradle run).
- Pure core `core.stock`: `Inventory<K>` over fake inventories, `Stockpile<K>` as a live view (replaces the unused M0 counter; its 3 tests are ported),
  nearest-Storage-with-space by the door anchor, partials-then-empties fill order, spill, remainder returned, withdraw from any, `StorageSet.resolve`
  (dedupe by position, chests shared with any other room building excluded, Storage unknown while an unscanned neighbour building is within 32 blocks),
  `StorageAlertTracker` (OK / FULL / NO_STORAGE, 200-tick hold), `Reservations` (UUID holders, the M20 hook).
- Glue `dev.villageheart.stock`: `StackKey(item, DataComponentPatch)` equals `isSameItemSameComponents` exactly; each double-chest half read as its own
  `ChestBlockEntity` (never `ChestBlock.getContainer`); containers found via `getChunkNow` + `LevelChunk.getBlockEntity`, because `Level.getBlockEntity`
  loads chunks; `setChanged()` per touched container; server thread only; no memo. `Stockpiles` facade for M7-M22 and the M2 collector.
- Assumes M4's `#chest` per block + `minecraft:barrel` ids and M5's `Buildings.valid/all` with `latestScan` (A1-A7 in the plan); steps 1-4 do not need M5.
Revised after a fresh critique (20 findings, 3 blockers: contradictory test numbers, fill-order expectation, false FULL for unloaded Storage).
Next: BUILD M6 once M4 is merged (steps 1-4), step 5 after M5. Top risk: the stockpile sees chests only through M5's cached scans (lag and restart window).

## 2026-11-07: M6 built (builder): stockpile backed by Storage chests
Core (`core.stock`, pure, L1): `Inventory<K>`, `ContainerRef`, `StorageSite`, `Stockpile<K>` (a live view: `count`, `deposit` nearest-first with spill, `depositInto`, `withdraw`, `withdrawFirst`,
`nearestWithSpace`, `hasSpaceFor`, `snapshot`), `Reservations` (UUID holders, claims not locks), `StorageAlertTracker` (OK / FULL / NO_STORAGE, 200-tick hold), `StorageSet.resolve` (dedupe, chests of other room
buildings excluded, Storage unknown while an unscanned neighbour is within 32 blocks); the unused M0 string counter `core.Stockpile` is gone and its 3 tests are ported. Three `Balance` values.
Glue (`dev.villageheart.stock`): `StackKey` (item + component patch), `ContainerInventory` (one chest half or barrel, `setChanged` per touched container), `StorageContainers.at` (never loads a chunk),
`ContainerBinder`, `StorageSites.collect` (reads `Buildings.all`), `Stockpiles` facade (server thread only), `StockpileSystem` registered as `villageheart:stockpile`; `SimMetricsCollector` now reports `stockpile.items.*`.
Tests: L1 `StockpileTest` (17), `StorageSetTest`, `StorageAlertTest`, `StockpileCostTest` (slot-read counts), `StockpilePerfTest` (perf tag: snapshot of 13 824 slots ~0.3 ms, spill across 64 sites ~0.4-1.2 ms);
L2 `StockpileGameTests` (double chest halves, components vs `isSameItemSameComponents`, comparator, unloaded/replaced) and `StockpileFacadeGameTests` (totals of two Storages, House chest in a shared wall,
spill into a second Storage, no-storage / full alerts, off-thread refusal, real snapshot cost 32 chests ~0.26 ms, new/removed chest, invalid Storage, collector metric). Mutation check: no exclusion set -> shared-wall test red.
Interface check vs merged M4 / M5 and all deviations: plan section 9. Docs: DESIGN section 6 (rules as built), TESTING "Stockpile tests".
Next: M7 (workers deposit with `Stockpiles.deposit`/`nearestWithSpace`, stop gathering on `!hasSpaceFor`), M8 (Stockpile tab reads `Stockpiles.snapshot`, alert text), M20 (build reservations).
Blockers: none. Doubts: the stockpile sees chests only through the building scans (up to 1 s per building behind a change; unknown right after a restart).

## 2026-11-07: L4 "Network synchronizer in invalid state" flake fixed (builder, branch l4-flake)
Root cause is a lost-wakeup race in Fabric's `NetworkSynchronizer.waitForPacketHandlers` (fabric-client-gametest-api-v1 4.3.5; newer Fabric removed the class, and no newer
fabric-api exists for 1.21.11). It tests "packet in flight, nothing queued" outside the lock, then `await(10 s)` without testing again. On the server's first tick of a new
world the client's login packet is handled in that gap, the signal is lost, the server sleeps 10 s (nothing else can wake it: the client waits for the server) and the
synchronizer declares itself invalid. Evidence: every failure is on the server thread at `NetworkSynchronizer.java:138` (the await timeout), 10 s after `Time elapsed`
and before the player logs in; a counter probe showed both counters at 0 during the whole stall. Fix: gametest-only client mixin `NetworkSynchronizerLostWakeupFix`
(re-test before parking, 20 ms slices, Fabric's 10 s failure kept); no retry, no test changed. `verify.sh` fails if the fix is not installed and reports the rescue count.
Measured with 4 busy loops of mine (load 9-12): unpatched dev 1/10 failed (plus 3/6 in earlier probe runs); patched 12/12 green (2 rescues logged).
Mutation: with a 30 ms sleep before the `lock()` the unpatched code failed 2/2 with the same stack; with the fix it passed with 3 rescues.
Next: nothing on this item. Blockers: none.

## 2026-11-07: M6 review r1 fixed (builder)
- False "Storage full": `noteDepositShort(time, sitesSeen, unknownSites)` now ignores a short deposit that did not see every Storage (none, or any unknown). L1 `shortDepositThatSawNoStorageProvesNothing`;
  GameTest `depositWithoutStorageDoesNotRaiseFull` (deposit with no Storage, then an empty Storage appears: alert stays OK). Mutation (rule removed): that test red ("an empty Storage is not full: was FULL").
- `depositMarksChestChanged` now pre-fills 27 slots, tops them up (grow path, signal 1 -> 15) and partially withdraws (shrink path, -> 8). Mutation (`changed()` empty): red ("signal after the grow to be 15: was 1").
- `snapshotCostReal` no longer asserts wall-clock (prints it); `StockpilePerfTest` uses the best-batch retry window like `RoomScannerPerfTest`. Enchanted sword added to the pairwise StackKey check (55 pairs); `depositHere` Javadoc says it never raises FULL.

## 2026-11-07: M11 plan written (docs/plans/M11.md)
Planner. Food, dinner and needs, plus the first real L3 scenario `starter`. Planned on assumptions about M7 (origin/m7 at a49fa0f), M9 (origin/m9-plan)
and M10 (no plan yet); section 3.1 lists them. M11 is built only after M7, M9 and M10 are on dev.
- Dinner is abstract: at day tick 11000 a village system takes food straight from the Storage chests for every loaded villager in the claim
  (no walking). Pure `core.food.DinnerPlanner`: hungriest first, then workers in hiring order; best food first (prepared before raw, then
  nutrition); a villager eats only if it can reach its full need (10, children 5), so food is not wasted on partial meals. `DinnerClock` fires once per
  day index (`!=`, so `/time set` works), saved as `last_dinner_day`; no catch-up.
- Hunger lives in a persistent `villageheart:diet` attachment on every villager (epoch-checked); `WorkerData.needs` becomes a derived copy with
  the invariant fed == (hungryDays == 0). `Needs.speed()` = hunger {1, 0.5, 0} x unhoused 0.75 x unpaid 0; paid is stubbed true (`PaidSource`) until M12.
  Housed = HOME bed head is a `#bed` of a valid House scan (or the worker's own building). Workers are moved into free House beds (may take one
  from an awake non-worker). Speed 0 gets a planner row in M9/M10 (idle + `TOO_HUNGRY`).
- Harness: five new actions (`site` = deterministic flat-site search in core + flatten + light, `prefab` rooms, `field`, `grove` of worldgen oaks,
  `hire` at day 1 tick 200), `food.*` and `housing.*` metrics, `sim/scenarios/starter.json` with the assertions for the roadmap criteria.
- Roadmap correction to make in the build: 4 villagers + 20 bread leaves 12 bread (8 eaten), not 8.
Revised after a fresh critique (22 findings; 3 refuted: `@prev` exists, `--reload-at` halts at dawn, job/building ids are core enums). Key fixes: test hooks so older worker GameTests keep full pace, adults-before-children tiebreak, lenient needs decode, dinner clock reset after the harness sets dawn.
Next: BUILD M11 after M10 (orchestrator: approve the 12-bread roadmap correction). Top risk: `starter` relies on the unbuilt M9/M10 workers surviving and producing in an unseeded village for 10 days.
