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

## 2026-11-07: M7 plan written (docs/plans/M7.md), villager-control spike decided
11-step plan for hiring and the worker framework, written ahead of M4-M6. It talks to them only through a narrow `JobSiteSource`
(`sites(job)`, `isRegistered(building)`, `reservable()`), plus M6's `Stockpiles.depositTarget/deposit`, which is needed for step 9 only.
**Spike decision: (a), a brain mixin** (recorded in DESIGN §12). A throwaway GameTest spike (8 tests, deleted) gave this evidence:
- With a mixin at the head of `Villager.registerBrainGoals`, a mod-built brain walked through a closed door to its job block in 60 ticks,
  slept in a bed at night, worked after dawn, and survived an NBT reload. A mod profession with no trades gives 0 offers.
- (b), the runner with the brain suspended, got stuck at the closed door (no `InteractWithDoor`).
- With the vanilla brain kept, `ResetProfession` cleared the mod profession within 200 ticks.
- The mod holding a composter's POI ticket blocked vanilla for 1200 ticks; a free composter was taken by tick 200.
Findings for later milestones:
- 1.21.11's villager schedule is a timeline-driven `EnvironmentAttribute`.
- POI add/remove is deferred through `server.execute`, so reservations retry and evict.
- Calling `runAfterDelay` inside a `runAfterDelay` callback crashes the GameTest server.
Revised after a fresh critique (26 findings): a stable slot cut, reserving every mod-job workstation, no chunk loading, real zombie
conversion in tests, stronger L2 assertions and isolation, breeding kept.
Next: BUILD M7 steps 1-4 (no dependencies); steps 5-8 after M5, step 9 after M6. Top risk: brain ownership (one mixin into a private
vanilla method; vanilla behaviours reused by hand).

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

## M7 (builder, branch m7): hiring and the worker framework
Steps 1-11 done. Villager control = brain mixin (DESIGN section 12): `VillagerBrainMixin` at the head of `Villager.registerBrainGoals` installs `WorkerBrain` for hired adults.
- core: `JobType`, `DaySchedule`, `JobSlots`/`HireDecision`, `WorkerData`/`Needs`, `WorkerRecord(villager, job, building, jobBlock)`, `ClaimGeometry`, `WorkerStatus`; codecs (`WorkerCodecs`, `reserved_job_blocks` in `WorldClaim`). 
- glue: nine `villageheart:<job>` professions (no POI predicate, no trades) with generated 64x64 overlays, `WorkerAttachments`, `WorkerBrain` (worker packages built from vanilla behaviours, `go_home` activity, `UpdateWorkerActivity`, `GoToJobBlock`, `DepositTrip`), `JobSiteSource`/`BuildingJobSites` (M5), `JobSiteReservations` (the mod holds the POI ticket of every job block in a valid building; evicts vanilla claimants; never loads chunks; never releases while contents are unknown), `Workers.hire/fire`, `/villageheart hire|fire|workers`, `WorkerSystem` (lifecycle, orphans, death/conversion, Heart release).
- tests: 268 L1 tests in all + 26 worker GameTests (101 GameTests in all), L4 `workers_at_job_blocks`. Mutations checked: skipping the conversion reset (T-L2-8 red). Flakes found and removed: WAKE strolls reaching the block (iron-door pen), unemployed villagers wandering out of rooms (control instead of "inside"), breeding (seeded, three windows).
- Known L4 NetworkSynchronizer flake: the fix on dev rescued 1 lost wakeup in the final full run.
- Next: M8 (Heart screen: hire/fire from the UI, worker status display), M9+ real jobs through `JobBehaviours`. Blockers: none.

### M7 review r1 fixes (builder)
- depositTripWhenFull flake: cause was a stolen walk target (GoToJobBlock took the target once the sink dropped it) and two DepositTrip instances (WORK and go_home) running at once. Workers.tripRunning now gates both; the trip re-sets its target whenever it is missing (unless the pathfinder said unreachable in the last 20 ticks). Bound stays 600. Evidence: 11 consecutive full runGameTest runs, all "All 102 required tests passed".
- Heart release with an unloaded workstation: releaseAll records those blocks in pending_ticket_releases (saved, survives the village); processPending (END_SERVER_TICK, every 20 ticks, with or without a claim) frees them once the chunk is loaded. GameTest heartReleaseWithUnloadedWorkstation (mutation: recording removed -> red) asserts the freed ticket with the villager own POI query (ALL_ACQUIRABLE_JOBS) instead of waiting for a villager to walk in a far forced chunk. VillageTestSupport.reset clears stale pending entries of the reused test world.
- Also: L4 comment fixed, camera pulled back so the vanilla villager is in the frame.

## 2026-11-07: M9 plan written (docs/plans/M9.md), Lumberjack
12-step plan for the first real job, written while M7 is still being built (assumptions M7-1..8 and M8-1 are in §3.1; the main ones: `JobBehaviours.work`
is the per-job plug-in point, and M7 step 9's `DepositTrip` deposits everything at GO_HOME and gets three small hooks: keep, free-slot, `wanted`).
- Core: a reusable job toolkit (`core.job`: tool tiers, `WorkSpeed` = tool × level × needs, `WorkClock`, `ToolAllocation`, `WorkCounters`), tree detection
  over a `TreeView` grid (a 26-connected natural-log component with ≥ 3 non-persistent leaf contacts, a soil base inside 2×2, no log in a valid room,
  ≤ 192 logs), a budgeted nearest-first `TreeFinder`, a `LumberjackPlanner` decision table, and `TreeTask`s saved in `VillageState`, so an interrupted fell resumes.
- Pace: stone ×1.25 → 40 ticks per log (1 log per 2 s). Copper, gold and netherite multipliers are pinned. The axe is used in place in the camp chest
  (1 durability per log), never held, so it cannot be dropped or duplicated. Drops are picked up by mod code (vanilla looting needs `mob_griefing` and
  `requestedItems`). No new fells after day time 5000, so the canopy (≈ 5 600 ticks to decay) drops before night. Never loads chunks.
- Verified for the plan: villagers have FOLLOW_RANGE 16 (partial paths, so a progress watchdog is needed); random ticks run in GameTests without a player;
  the GameTest server does not pace ticks.
- Tests: 13 L1, 14 L2 (feature-grown trees with fixed seeds, measured `fellBound`, stray-tree sweep), 1-2 L4. L3: `work.<job>.<counter>`,
  `workers.status.<status>` and the reserved `stockpile.tag.*` get filled for M11.
Revised after a fresh critique (22 findings; I refuted 3 against the sources: random ticks, StandAtJobBlock, GameTest tick pacing).
Next: BUILD M9 after M7 (including step 9) is merged. Top risk: the DepositTrip interplay (M7 step 9 does not exist yet).

## M9 (builder, branch m9): Lumberjack
Steps 1-12 done (the second L4 shot is skipped: M8 is not on dev; ROADMAP not ticked on instruction). Plan deviations: docs/plans/M9.md section 8.
- core: `core.job` (tool kinds and tiers, `WorkSpeed` = tool x level x needs, `WorkClock`, `ToolAllocation`, `WorkCounters`), `JobType.tool()`, `core.tree` (`TreeScanner`, `TreeFinder`, `FellOrder`, `Species`), `core.lumber` (`LumberjackPlanner`, `ReplantRules`), `TreeTask` saved in `VillageState.treeTasks` (`tree_tasks`), L3 families `work.<job>.<counter>`, `workers.status.<status>`, `stockpile.tag.<tag>` (`DayMetrics`, `MetricsJson`, collector).
- glue: `worker.lumber` (`LumberjackWork`, `LumberJob`, `LumberRuntime`, `LevelTreeView`, `LumberSystem`, `LumberKeep`), `worker.job` (`CampContainers`, `CampTools`, `ItemPickup`, `WalkTo`, `WorkerStatuses`, `WorkerStatusText`, `WorkStats`); `DepositTrip` gets the keep rule, the free-slot rule, `wanted()` and long-range walking.
- Main findings while building: the vanilla walk sink cannot bring a worker out of a room to a tree behind a wall (own pathing, `WalkTo`); leaves block the ground up to the trunk (chop from the nearest reachable spot); a worker idling at the camp does not see drops 20 blocks away (collect in the work area); two workers jam in a doorway (jam breaker).
- Tests: 14 L2 lumberjack tests, 13+ L1 test classes for the toolkit, trees, planner, saved tasks and metrics; L4 `lumberjack_felling` and `villagers_tab_needs_axe`. Verify summary and flake counts are in the M9 round 1 fixes entry below.

## 2026-11-07: M8 plan written (docs/plans/M8.md)
9-step plan for the Heart screen v1. APIs checked against the 1.21.11 sources and Fabric 0.141.6 sources jars (no Gradle run).
- Opening: Fabric `ExtendedScreenHandlerType<HeartMenu, HeartOpenData>` (a menu with no slots; the first snapshot travels with the open packet,
  vanilla owns close/distance/one-menu-per-player), opened from `VillageHeartBlock.useWithoutItem`; `HeartScreen extends Screen implements MenuAccess`
  modelled on vanilla `StatsScreen` (TabNavigationBar), `isPauseScreen` false (else the integrated server pauses and nothing refreshes).
- Networking: per-viewer full snapshots (pure `core.heart` records, `equals` = dedupe) at most 1 Hz from `HeartMenu.broadcastChanges`, only when
  changed; C2S `AssignJob/FireWorker/RecheckBuilding` payloads with `containerId`; `ActionGate` order rate limit, open menu, epoch, village, rank
  (re-read every time); Visitors are never sent detail data. Worst case ≈ 270 KB (3-byte chars), tested < 512 KiB.
- Layout sized for the L4 default: 854x480 at auto GUI scale 2 = 427x240 GUI px. L4 asserts `clippedThisFrame == 0` and an empty `layoutProblems()`.
- M7 assumptions A1-A12 (plan 3.1): `Workers.hire/fire/fireById`, `WorkerAttachments.get` → `WorkerData(level, xp, Needs)`, `Workers.status`,
  job sites + `JobSlots.freeSlots`, activity from `getActiveNonCoreActivity` + `WorkerBrain.GO_HOME`; M8 adds a public `Workers.findLoaded`.
- Folds in BACKLOG M6 "FULL with one unknown Storage: say so" (alert text suffix).
Revised after a fresh critique (21 findings: payload bound in bytes, cross-dimension targets, 1.21.9+ input API, disabled-tab bypass, log flood, L4 ordering).
Next: BUILD M8 steps 1-3 now; steps 4+ once M7 is on dev. Top risk: M7's API moving before merge (only `HeartFacts`/`HeartActions` import it).

## 2026-11-07: M8 steps 1-3 built on branch m8 (steps 4-9 wait for M7 on dev)
- Step 1 `core.heart`: `Permission/Permissions` (DESIGN 2 matrix), sealed `HeartAction` (Assign/Fire/Recheck), `Rejection`, `ActionGate`
  (order: rate, open, epoch, village, rank), `RateLimiter` (8 tokens, +1 per 5 ticks), `VillagerNames`, `JobLevels`, `FoodDays`, `HeartLimits`; Balance constants.
- Step 2: snapshot records (`HeartSnapshot`, `Overview`, rows, `Stock`), plain input `VillageFacts`, `HeartSnapshotBuilder` (rank filter: Visitors get no
  details; order; caps with "+N more"; alerts incl. the BACKLOG M6 "FULL with unknown Storage" suffix).
- Step 3: `HeartCodecs`, 5 payloads, `HeartOpenData`, `HeartNetwork.register()` (receivers call a stub `HeartActions.handle`), lang keys for rejections/alerts.
  Worst-case snapshot encodes to 469 KiB with 3-byte chars (< 512 KiB asserted).
- verify --fast green after each step. Deviations are in docs/plans/M8.md section 8.
- Next: when M7 is on dev, rebase, re-check A1-A12, then steps 4-9 (facts from the world, menu, actions, screen, tabs, L4).

## 2026-11-07: M8 built (Heart screen v1), steps 4-9 after M7 merged
- Step 4 `HeartFacts` (collect/cached/invalidate), `VillageCensus`, public `Workers.findLoaded`, tag `#villageheart:not_village_food`.
- Step 5 `ModMenus.HEART` (`ExtendedScreenHandlerType`), `HeartMenu` (slotless; per-viewer snapshot at most 1 Hz and only when changed; closes on distance, Heart gone, release/re-claim),
  `VillageHeartBlock.useWithoutItem`, `HeartNetwork` sender seam.
- Step 6 `HeartActions.handle` (rate limit first, `ActionGate`, assign/fire/check now, replies, throttled WARN, cleared on disconnect and server stop).
- Steps 7-8 `HeartScreen` (own tabs/lists, Overview/Villagers/Buildings/Stockpile, action bar, status line, layout self-check), client receivers.
- Step 9 L4 `HeartScreenClientTests` with 4 screenshots (looked at), UI fire, scrolling, ellipsis; docs (TESTING, DESIGN 10, ROADMAP deferrals, BACKLOG tick).
- Deviations from the plan are in docs/plans/M8.md section 8 (own widgets instead of TabNavigationBar/ObjectSelectionList; unloaded worker simulated by discard; TextLine args).
- Next: M9. Not ticked in ROADMAP (orchestrator's call). Open: M7's `workerOpensDoors` bound (400 ticks) failed once at 470 under machine load, passed on rerun.

## 2026-11-07: M9 review round 1: FAIL (verifier)
- Merged origin/dev (M8) into m9; all conflicts additive (both sides kept). Review: docs/reviews/M9-r1.md.
- Fix 1: a free oak in the open yard is sometimes judged unreachable (WalkTo gives up, 6000-tick blacklist): Layer 2 failed 3 of 9
  merged runs (two_lumberjacks x2, fell_resumes x1, odd_shapes x1). Fix 2: the Heart Villagers tab shows "Working", not "Needs axe"
  (HeartFacts ignores NEEDS_TOOL/NO_WORK; WorkerStatusText unused; two key sets for the same statuses).
- All roadmap tests mutation-checked and real; M7 worker tests 12/12 green; L1, L4, perf green.
- Next: builder round 2 on m9. Blockers: none.

## 2026-11-07: M24 plan (ranks and multiplayer), branch m24-plan
- Plan: docs/plans/M24.md, 11 steps. `Permission(minimum rank)` + new OPEN_CONTAINERS/MANAGE_RANKS/TRANSFER_OWNERSHIP/BREAK_HEART/START_BLUEPRINT;
  `VillageState.known` name book (64, ranked never evicted, save key `known_players`, lenient + repaired on load); `RankRules`; transfer to a
  Steward/Resident (old owner becomes Steward); op commands `villageheart owner|rank|members` (ticks BACKLOG M1 "absent owner").
- Chest guard (v1 scope): Visitors cannot open (Fabric `UseBlockCallback`, server FAIL, sneak-place passes) or break (`PlayerBlockBreakEvents.BEFORE`)
  any `Container` block inside a room building's last enclosed scan (16-block fail-closed box until the first scan), nor break markers.
  Hoppers, explosions, golems, wall breaking: out of scope, documented.
- Ranks tab (Owner only, 5th tab) with a two-click transfer confirm; payloads `heart_set_rank`, `heart_transfer_ownership` through M8's gate.
- Release-gate harness sketched and scheduled (step 10): Fabric `createServer()` dedicated server + real client + a server-side fake Owner,
  desync checks per step; M24 does not tick the gate (it is 1 real client + 1 server-side player).
- Assumes M12 lands first (withdraw L2 waits for it; step 6 starts with a checkpoint against M12's real code). Critique: 30 findings, 25 taken.
- Top risk: the guard depends on cached room scans (windows after a restart and before a new chest is rescanned).

## 2026-11-07: M24 built (ranks and multiplayer), branch m24 (M12 not on dev: step 9 pending)
- Step 1 `Permission(minimum rank)` (OPEN_CONTAINERS, MANAGE_RANKS, TRANSFER_OWNERSHIP, BREAK_HEART, START_BLUEPRINT added), `Rank.atLeast`, Heart break via `BREAK_HEART`; literal-table `PermissionsTest`.
- Step 2 name book (`KnownPlayer`, `VillageState.known`, `seen`, `transferOwnership`, `MemberRepair`, lenient `known_players` codec), `RankRules`, new `Rejection`s.
- Steps 3-4 `ContainerGuard` + `ContainerProtection` (UseBlockCallback open guard, break guard, double-chest resend), `BuildingRuntime.lastEnclosed`, `Buildings.guardAreas`; L2 open/break/last-enclosed-scan tests.
  Three older tests broke markers as a Visitor and now break them as the owner (docs/plans/M24.md section 8, item 4).
- Step 5 `Members` (JOIN/DISCONNECT name book, `setRank`/`transfer` shared by screen and commands, container closed on demotion), `RankCommands` (`owner`, `rank`, `members`; ticks BACKLOG M1 absent owner).
- Step 6 `HeartAction.SetRank/Transfer` + `malformed()` gate slot, two payloads. Step 7 member rows in the snapshot (Owner only), `HeartFacts.members`. Step 8 Ranks tab, L4 `RanksClientTests`.
- Step 10 `MultiplayerClientTests` (dedicated server in the client JVM, real client + fake Owner), `FakePlayer`, `scripts/check-mp-log.sh` + self-test, wired into `verify.sh`. 5 of 5 runs passed.
  Finding: a fake player on an `EmbeddedChannel` breaks Fabric's `NetworkSynchronizer` (packets never "handled"); its connection must drop packets.
- Step 11 docs. Screenshots looked at: `heart_ranks.png`, `heart_ranks_confirm.png`, `mp_ranks.png`, and the four M8 shots with the new tab bar.
- **Blocked / next:** step 9 (`onlyOwnerCanWithdraw`, plan T-L2-6) and a deposit/withdraw step in the 2-player session need M12 on dev. ROADMAP not ticked (orchestrator).

### M9 review r1 fixes (builder)
Merged origin/dev (M8 Heart screen, M24 ranks) into m9 (`VillageState` takes `treeTasks` as its last component next to M24's `known`; codec and fabric.mod.json merged).
1. **False "cannot reach the tree" (roots found, three of them, none was the bound):**
   - `WalkTo`'s give-up clocks ran while the worker stood at the tree chopping (a 40-log tree takes 1600 ticks): the first step away (a deposit trip, a push) read as an 800-tick walk. They now restart whenever the worker is at its spot, and after any gap in ticking.
   - Vanilla replaces the navigation's path when a block on it changes (a door opens), recomputing with the villager's 16-block range; the new path led to the far corner of the yard (a 28-block detour and back, 300-600 ticks). `WalkTo` notices a path that is not its own and plans the long one again at once (the trace shows it about 3 times per walk through a door).
   - The yard of a test is not cleared when the structure is placed (it is one air block): the glass box of `radiusAndUnreachable` and the rooms of other tests stayed and walled off trees of the next test that used the same place. `sweepStrayTrees` now empties the yard first.
   - The planner started no new tree while a drop was waiting; with the leaves of the day's trees dropping, `oddShapesFelledWhole` never got to its 40-log dark oak before the cutoff. Trees now come before drops while a new fell is allowed (L1 `rows5to8_replantBeforeAFellBeforeCollect`).
   - New guard: every "cannot reach" verdict is recorded (`LumberRuntime.giveUps`) and the open-yard tests fail on one (`assertNoGiveUps`; `sixOaksTwoDays` only for the six original trees, a regrown canopy can wall off its own trunk).
   - Evidence: 12 consecutive full `./gradlew --no-daemon runGameTest` runs on the merged code, all 139 tests passed. Before the last fix 1 in 4 to 1 in 12 runs still failed (pace/fell bounds, six oaks), each with a traced cause above.
2. **"Needs axe" on the Villagers tab:** `ActivityKind` has `NEEDS_TOOL` and `NO_WORK`; `HeartFacts` maps them; `HeartScreen` renders `HeartTexts.activityKey(activity, jobId)` ("Needs axe", "No trees within 32 blocks", red for a missing tool). One set of words: M9's `status.villageheart.*` keys are gone, `WorkerStatusText` now returns the `gui.villageheart.heart.activity.*` keys. The job column of the row is wider (the name column narrower): "Lumberjack L1" was cut off. L2: `noAxeNoLogs` asserts the snapshot row ("Needs axe"). L4: `villagers_tab_needs_axe` (looked at).
3. Also: `DepositTrip.start` erases a left-over walk target; the felling shot shows the stump with its sapling.

## 2026-11-07: backlog hardening batch 1, branch backlog-1
- Zero-test guard: `scripts/check-gametest-log.sh` (run by verify.sh, self-tested by `test-check-gametest-log.sh`) fails on "All 0 required tests passed"; TESTING.md says filters must be exact ids. Checked against a real partial-filter run (exit 0, 0 tests) which it rejects.
- `sweepStrayMarkers` guard: TESTING.md rule + `BuildingTestSupport.assertOwnEnvironment` (reads the test's environment by reflection; fails on the default environment or a shared one). Mutation: removing the environment from `outsideClaimAndNoClaim` goes red.
- `villageTickerTimesSystems`: wall-clock `< 0.5 ms` assert replaced by a run counter (mutation: unregister no-op goes red); the timing bound is `VillageTickerPerfTest` (perfTest).
- New tests (each mutation-checked, see BACKLOG): `claimRefusalAndReleaseMessages`, `oldMenuAfterReplaceIsStaleInTheSameTick`, `actionFromOutOfReachRejectedInTheSameTick`, `doubleChestOuterHalfIsProtectedThroughItsPartner`, `readableFullPlusUnknownStorageIsFullWithOneUnknown`; stronger: `depositMarksChestChanged` (partial shrink; the old test did NOT catch a missing `changed()` on withdraw, it survived), the sticky-piston test (wall moved), `outsideClaimAndNoClaim` (non-empty asserts), save/reload release (`destroyBlock` result).
- `RecordingPlayer` now records action-bar messages (`overlays`). DESIGN section 2 "not stopped" list extended. TESTING.md snapshotCostReal line fixed.
- marker_items: noon + clear weather set right before the shot; the shot was already noon, its dark backdrop is vanilla's screen dim (looked at).
- Not done: the M24 demotion double-chest close branch (`compoundIsProtected`) is still untested (noted in BACKLOG). The `destroyBlock` assert in the save/reload test has no mutation (it cannot fail without breaking the release path another assert covers).

## 2026-11-07: M20 plan written (docs/plans/M20.md)
12-step plan for the Builder and blueprints (buildings). APIs checked against the 1.21.11 sources and data tags (no Gradle run).
- Core `core.blueprint`: a text format (palette + layers, `base`, `anchor`, `front`, passes, two-block `part=` units, `converts`, room-cell annotations),
  `Rotation4` (vanilla position rule), `Placement`, `BuildPlan` (clear top-down, place bottom-up, attached, last, check pass, marker last),
  `SiteRules` (12 ordered refusals incl. overlap with any registered room's interior/shell), `MaterialPlanner`, `FetchBatch`, `BuilderPlanner`, saved `BuildJob`.
- The three blueprints as data: Small House and Storage Shed (5x5 floor inside a 7x7 shell, marker included: 49 cobblestone, 119 planks, ...), Farm Plot
  (80 dirt to farmland, water from a bucket, 39 fences, a gate). An L1 test proves both houses are valid rooms in all 4 rotations through the real `RoomScanner`.
- "Player-placed" = anything outside `#villageheart:builder_clearable` (plus persistent leaves, fluids, block entities, logs); every marked room is protected by the overlap rule.
- Builder reuses M9's `WorkClock`/`WalkTo`/`WorkerStatuses`/`WorkStats`/`keep`; works only from stand points outside the site; reservations keyed by build id.
- Revised after a fresh critique (24 findings: confirm click below a base-1 box, check-pass reservations, double-counted carried items, unpinned gamerules, test layouts).
Next: BUILD M20 after M9 merges (re-check assumptions A1-A10 first). Top risk: builder movement (WalkTo to stand points around real village terrain).

## 2026-11-08: M20 Builder and blueprints (buildings), branch m20
- Core `core.blueprint` (format, parser, Rotation4, BuildPlan, SiteRules with 12 refusals, MaterialPlanner, FetchBatch, BuilderPlanner, BuildJob) plus saved builds in VillageState; Small House, Storage Shed, Farm Plot as data. Glue in `dev.villageheart.build` and `worker.build` (BuilderJob, BuilderKeep), Builders Table menu and screen, blueprint item with green/red footprint particles, Buildings tab Builds section, START_BLUEPRINT enforced.
- Deviations from the plan: see docs/plans/M20.md section 8. DepositTrip/ToolRequirement changes kept minimal (M10 overlap: only the builder keep rule hook).
- Measured: House built in about 5206 ticks (172 places), Farm Plot about 5870 ticks (121 places, 81 clears); pause after 600 ticks of "Needs 12 more planks" resumes cleanly.
- Pitfall: `.gitignore` `build/` hid the new `src/**/build/` packages from git; fixed with `!src/**/build/`, checked by a fresh-clone compile.
- Roots fixed on the way: WalkTo stall at 1.2 blocks (ARRIVE 1.4, outward aim), stale pause (150 ticks), TextLine 16-char arg cap (32 for build keys), table menu not syncing the inventory (broadcastChanges), farmland drying (moisture 7).
- Verify: 8 consecutive full runGameTest runs, 164/164 each. L4 screenshots viewed: builders_table, blueprint_footprint, blueprint_footprint_red, build_mid, heart_buildings_build, build_finished.
- Next: M21 (defences reuse BuildPlan/SiteRules; add LINE mode). No blockers. M14 tiers not built: tests set the tier directly.

## 2026-11-07: M10 planned (planner): Farmer
Plan: `docs/plans/M10.md` (13 steps). The farmer reuses M9's job toolkit (`core.job`, `CampTools`, `WalkTo`, `ItemPickup`, `WorkStats`, `JobBehaviours.keep`,
`DepositTrip.wanted`) and M7's DepositTrip; it adds no second version of any of them and no saved data.
- Own `FarmerWork` behaviour instead of vanilla `HarvestFarmland`/`UseBonemeal`/`WorkAtComposter` (verified: they need the vanilla FARMER profession,
  `SECONDARY_JOB_SITE`/`JOB_SITE`, `mob_griefing`, look only at 3×3 with random picks, make bread). Vanilla static helpers are reused (`isMaxAge`,
  `Block.getDrops`, `BoneMealItem.growCrop`, composter level events).
- Crops by block id (torchflower extends `CropBlock`): wheat/carrots/potatoes max age 7, beetroots 3. Harvest + replant is one atomic tick (the seed is
  taken from the drops first). Area: within 24 blocks (xz circle) of the composter, ±8 y. Deterministic composting (21 seeds per bone meal, vanilla's mean).
  Hoe = flat ×1.25, no hoe ×1.0 (§5.2 over §4's ×0.5; M9's `ToolRequirement` gets a BONUS mode).
- Changes M7: DepositTrip kept every food item forever (carrots/potatoes/beetroots would never reach Storage); now food is kept only up to 12 points.
- L2 determinism: each farmer test has its own environment with `random_tick_speed 0` (verified: batches run sequentially, teardown resets the rule).
Revised after a fresh critique (22 findings; 2 blockers fixed: a night-time control and wrong compost arithmetic; 1 refuted against the sources).
Next: BUILD M10 after M9 is merged (steps 1-3 may start once M9's `core.job` is on dev). Top risk: dependency on unbuilt M9 code.

## 2026-11-08: M10 built (Farmer), branch m10
- Farm core (`core.farm`: crops, `FieldIndex`, compost rule, `FarmerPlanner`, keep rules), BONUS tool mode, food keep rule in `DepositTrip` (food kept only up to 12 points), `FarmJob`/`FarmRuntime`/`FarmerWork` glue (own behaviour, 20-tick re-plan, server-wide index budget, claims, hoe lease, COLLECT, FETCH, compost, bone meal), `WalkTo.release` (PATH-memory fix). 14 L2 tests (`FarmerGameTests`), L4 `FarmerClientTests.farmerField`. Deviations: `docs/plans/M10.md` section 8 (D1-D12).
- Collect-timeout bug (D10): a COLLECT interrupted by a deposit trip kept its old start time and the item was blacklisted for 6000 ticks on return. Fixed for both orders (planner YIELD first; trip behaviour first), with two L2 tests that go red when the fix is reverted. **The cause of the original `storageFullStopsHarvest` flake (1 loose wheat never collected) is still unconfirmed**: a traced run never COLLECTs before its trip in phase 2. It has not failed in about 30 runs since.
- Second flake found by the series: `invalidBuildingKeepsWorker` (M7) expected status OK for a farmer with no farmland, now NO_WORK after a sweep (1 in 9); the test places one farmland block (D12).
- Evidence: 9 consecutive full `./gradlew --no-daemon runGameTest` runs on the final code, 158/158 each (an earlier series with the flake fix but before D12: 7 of 8 green, the failure being the M7 test above).
- Screenshot looked at: `farmer_field.png` (farmer at the Farmhouse door, mature wheat field, banner on the wall).
- Next: M10 review; ROADMAP is the orchestrator's. Doubt: the collect-timeout cause is inferred, not observed.
- Verify note: one full verify run failed on M8's `fireUnloadedWorker` (population 2 instead of 1 at tick 5, a stray loaded villager counted; not farmer code, not reproduced in 9 L2 runs + 2 verify runs); the rerun was green (158/158, L4, perf).

### M10 review r1 fixes (builder)
1. Stray farmland made an M7 test flaky (`hiredWorkerReachesJobBlock`: a hired farmer chased farmland 22 blocks away left by a farm test). `clearStrayFarmland` runs in both `begin`s; rule in TESTING; D12's field is removed at the test's end.
2. D10 covered only the planner-yield order: now also the trip-first order (`FarmJob.tick` drops the collect target while a trip runs). New L2 tests `collectSurvivesTripThatStartsFirst` / `collectSurvivesTripThatPlannerYieldsTo`; both red with the fix reverted. `loose()` now counts the farmer's work area only. Original flake cause stays unconfirmed (see above).
3. `farmer_field.png` re-framed from the Farmhouse side: the farmer stands between the replanted sprouts (front) and the ripe wheat (behind). Looked at.
## 2026-11-08: second L4 NetworkSynchronizer stall (disconnect leak), branch l4-flake-2
- **Root cause (Fabric, fabric-client-gametest-api-v1 4.3.5):** `Minecraft.disconnect` resets `NetworkSynchronizer.CLIENTBOUND` once, right after the client closed its end of the in-memory channel. Netty closes the server's end later on its own event loop while the integrated server keeps ticking, so clientbound packets sent in that window are counted after the reset and fail to write: never uncounted. The first client tick after "Stopping server … All dimensions are saved" then waits 10 s and dies with `Network synchronizer in invalid state`.
- **Evidence:** temporary probes (not committed). Natural failure under load (1 of 3 loaded runs before the fix), at the end of `heartTabs`: 41 packets counted after the reset (20 `set_entity_data`, 20 `set_entity_motion` from the 20 killed villagers, 1 `disconnect`), `inFlight=41` when `disconnect` returned, invalid state 10 s later.
- **Fix:** gametest-only client mixin `DisconnectInFlightLeakFix` (+ `NetworkSynchronizerAccessor`) re-applies the reset once the integrated server has stopped; logs `DISCONNECT_LEAK_FIX installed` / `DISCONNECT_LEAK_CLEARED: n`; `verify.sh` checks the first and reports the count. `heartTabs` now waits until the 20 villager deaths are over on server and client before it closes the world (the Heart menu was already closed, so no snapshot refresh is in flight). No retry, no test weakened.
- **Forced repro** (probe blocks the server channel's event loop 500 ms at each world close): without the mixin 2/2 red at the first close (leaked `set_time`); with it 2/2 green, 6 leaks cleared per run (up to 61 packets in `heartTabs`).
- **Under load** (4 to 6 of my busy loops, load 8 to 19, other agents' Minecraft too): 10/10 consecutive green L4 runs, 0 invalid states, 0 leaks needed clearing (the quiesce removed the burst); 3 lost-wakeup rescues by the first mixin.
- **Next:** a disconnect from a dedicated server (`MultiplayerClientTests`) is not covered by the mixin; not seen, and Fabric's 10 s would still report it.

## 2026-11-08: l4-flake-2 verifier review r1: PASS (docs/reviews/l4flake2-r1.md)
- Root cause confirmed in the Fabric 4.3.5, Minecraft 1.21.11 and Netty 4.2.7 `LocalChannel` sources. The reset point (after `isShutdown()`, i.e. the server thread is dead, and the client channel already closed) is safe and only clears counts that can never arrive. The mixin is not in the shipped jar.
- Forced repro (my own probe, removed afterwards): red without the mixin (invalid state 10 s after the first close), green with it (7 of 10 closes cleared). The verify.sh gate fails when the mixin is not installed.
- Under load (load 12 to 16): 6 of 6 L4 runs green, plus the full verify.sh green. One natural leak (1 packet) was cleared after `WorkerClientTests` killed 10 villagers.
- Notes: `WorkerClientTests:122` and `LumberClientTests:109,174` kill villagers without quiescing (covered by the mixin; hygiene).

## 2026-11-08: M20 verifier review r2: FAIL (docs/reviews/M20-r2.md)
- Rule 11b refuses 25 of 28 attachment probes (doors, signs of every kind, lanterns, buttons, plates, bells, ladders, paintings, item frames...). Two still break: carpets (the reflective `LevelReader` proxy runs default methods such as `isEmptyBlock` on the real level; `InvocationHandler.invokeDefault` fixes it, tried) and ascending rails (removed in `neighborChanged`, not `canSurvive`).
- Farm Plot bug, flaky `farmPlotBuilt` (1 of 17 L2 runs): the check pass clears dried or trampled farmland next to the water in one action and places it in the next, so water flows in and the build finishes with 79 farmland. Reproduced deterministically.
- Mutations of the 3 new tests all go red; the touched-cell exemption in `SiteSupport` has no test. verify.sh green; `workerOpensDoors` 0 failures in 18 runs (one 301-tick run).
- Next: builder fixes items 1 to 3, then round 3.

## 2026-11-08: M20 review rounds 1 and 2 (branch m20)
- Round 1: clearing no longer breaks things of a player that rest on the site (rule 11b `Code.SUPPORT`, `SiteSupport`, per-step re-check in `BuildExecutor.cellState`). Round 2: carpets (default methods on the proxy), ascending rails, and the Farm Plot flood (clear and place in one action; water in a CHECK cell is wrong). Details in docs/plans/M20.md sections 8 and the review notes.
- New tests: `SiteRulesTest.ruleOrder11b`, `attachedThingsRefuseTheSite`, `attachmentAfterConfirmBlocksTheBuilder`, `checkPassDoesNotFloodAFarmland`, `ownTorchDoesNotBlockTheCheckPass`; each mutation-checked.
- Open: `workerOpensDoors` (M9) rare 300-tick stall, see BACKLOG. Lesson: the scratchpad is shared between agents, so keep helper scripts in an own subdirectory.
## 2026-11-08: M10 verifier review r2: PASS (docs/reviews/M10-r2.md)
- All 3 round-1 items fixed: stray farmland is swept in both `begin`s (every farmer-hiring L2 test is covered, and the M20 tests will be too); both collect-across-a-trip orders are tested (trip-first red with its part reverted; planner-first red with the whole fix reverted; `forcePlanForTest` only sets `needsPlan`, and the trace shows a real YIELD); `farmer_field.png` shows the sprouts.
- Full verify.sh green; L2 160/160 in 10 of 10 runs (0 failures of `hiredWorkerReachesJobBlock`, `storageFullStopsHarvest`, `workerOpensDoors`, `fireUnloadedWorker`).
- Notes: the `before != COLLECT` restart is now redundant on the trip path and not isolated by any test; the `clearStrayFarmland` doc says crop items, but the code discards all items; `workerOpensDoors` should log its walk ticks.
## 2026-11-07: M16 plan (Miner + Mineshaft), branch m16-plan
- Plan: docs/plans/M16.md, 12 steps. Reuses M9's toolkit (`CampTools` PICKAXE lease in the Mine chest, `WalkTo`, `WorkSpeed`, `WorkerStatuses`, `WorkStats`, `DepositTrip` + a `depositNow` hook).
- "Disappears inside": the villager stays a loaded entity, moved to the shaft's deepest cell and set Invisible + Invulnerable + Silent + NoAI (NoAI freezes the vanilla brain; invulnerable mobs are never targeted); prior flags saved in a new persistent `AwayTrips.AWAY` attachment (separate from `WorkerData`) and restored on surfacing; trade/attack callbacks FAIL; load repair, conversion fix, `restoreVanilla` hook. Heart screen: new `ActivityKind.AWAY_WORKING` "Away (mining)".
- Haul: 2500 units, stone L1 = 2000 underground ticks, stepped every 20 ticks by `MinerSystem`, progress saved per shaft and kept overnight; 10 durability per haul; tables by `DepthBand` (Y≥40 / 0≤Y<40 / Y<0) with diamonds only for iron+ below 0; `HaulRng` = SplitMix64 of (world seed, UUID, per-worker haul index), so L2 can predict every haul.
- Shaft depth: `ShaftSurvey.deepestCell` + runtime `Buildings.shaftDepth` (M5 threw `deepestY` away). Shaft invalid mid-trip → comes up, progress reset, Mine then WORKPLACE_INVALID with M5's reasons.
- Finding: GameTests run at y ≈ -59, so a shaft in the normal yard is in the Y<0 band (cobbled deepslate). The roadmap's "≥40 cobblestone" test builds its scene high up (relative y computed from the test origin).
- L1 note: 10k rolls cannot hold 5% for the 2%/10% entries; the plan runs the literal 10k check for fixed and ≥30% entries and a 400k check for all (orchestrator may adjust the ROADMAP wording).
- Critique: remote critic session, 34 findings, 29 taken, 3 refuted, 2 accepted with reasons. Builds after M9 is on dev. Top risk: hiding a live villager with NoAI (anything else touching those flags or assuming the brain ticks).
## 2026-11-08: M16 (Miner + Mineshaft), branch m16 (builder)
- **Done (12 steps):** core `core.mine` (DepthBand, HaulTable, HaulRng, AwayProgress, AwayCheck, MinerPlanner, ShaftChoice, AwayState; 47 L1 tests incl. the roadmap statistics: 10k rolls within 5% for fixed and >=30% entries, 400k rolls for every entry, diamonds only DEEP + iron+); status words (`SITE_UNREACHABLE`, `AWAY_WORKING`, "Away (mining)"); `ShaftSurvey.deepestCell` + runtime `Buildings.shaftDepth`; `AwayTrips` (hide/surface with prior flags, load repair, interaction blocks, conversion fix); `MinerJob`/`MinerWork`/`MinerSystem`/`MinerHauls`; `JobBehaviours.depositNow` (+ `DepositTrip.wanted`); 14 L2 tests (`MinerGameTests`) and 1 L4 shot (`villagers_tab_away_mining.png`, looked at: row "Mina / Miner L1 / Away (mining)" readable, not cut).
- **Measured:** stone pickaxe, high scene: 4 hauls and 64 cobblestone by day time 12000 (hauls delivered at ticks 2164, 4504, 6844, 9184, cycle 2340); 1981 to 2000 underground ticks per haul; iron 1669, stone 1989 in the two-miner test; first descent 183 to 250 ticks after hiring.
- **Bugs found and fixed on the way (details in plans/M16.md section 8):** `Invisible` is not saved with a villager (a reloaded hidden miner came back visible: re-applied at ENTITY_LOAD); the Heart screen snapshot threw for any registered outdoor site (`DetailKind.NONE`).
- **Test infrastructure:** low scene (tower + staircase, DEEP band) and high scene (stone platform in the air, HIGH band), see TESTING "Miner GameTests". Mutation-checked: no flag restore on conversion (red), no pickaxe wear (red), `depositNow` off (red).
- **Flake seen once:** in the first full verify `minerHaulsFortyCobblestone` failed its "no item entity lying around" check (1 item). 10 full L2 runs since were green; the message now names the item (stack, position, age) so the next occurrence identifies it. Not understood; nothing in the miner code drops items except the `dropPending` fallback (logs a warning).
- **Next:** M17 reuses `AwayTrips`/`AwayState`/`DetailKind.NONE`. Doubts: the hidden miner's hitbox (BACKLOG), the one unexplained stray item.


## 2026-11-08: M20 verifier review r3: FAIL (docs/reviews/M20-r3.md)
- All round-2 items are fixed. Carpets, moss carpets and ascending rails and powered rails in all 4 directions are refused, as are the door, lantern, wall sign and painting probes, and nothing is broken. The flood test goes red only with both mechanisms off. The planner rule is pinned at L2: a livelock when it is reverted. Short materials pause with "Needs N more dirt" in the main pass and in the check pass, with no gap left.
- New regression: the check pass's "fluid in a CHECK cell is wrong" rule matches any fluid state, so it overwrites a player's waterlogged stair or chest. The support check's fluid skip (from round 1) misses a waterlogged sign attached after the confirm, and the real builder breaks it. Both are fixed by testing for `LiquidBlock` (tried, then removed).
- M10 merged cleanly (all conflicts kept both sides). Full L2: 10 of 11 green. The one red was M9's `fellResumesAfterInterrupt` (known signature). verify.sh green.
- Next: the builder fixes items 1 and 2, then round 4.

- M20 review round 3: waterlogged player blocks are left alone by the check pass and protected by the support check (`LiquidBlock` instead of "holds a fluid"); instant builder takes the material before it clears; "Needs materials" fallback; planner L1 case. See docs/plans/M20.md.

## 2026-11-08: M20 verifier review r4: PASS (docs/reviews/M20-r4.md)
- Both round-3 items are fixed (`LiquidBlock` only, in `BuildExecutor.cellState` and `SiteSupport.dependent`). The two new GameTests and the new L1 planner case each go red on the old code. M20 is ticked in ROADMAP.
- Regression probe at confirm and after confirm (carpet, ascending rail, door, waterlogged lantern): each one refuses the site or stops the builder, and each survives. The new `BuildJobs.advance` order leaves no hole and takes one plank once.
- Full L2: 6 of 6 green in the foreground (186 tests), no M9 flakes. `scripts/verify.sh` exit 0.
- Next: M21 (Defence blueprints).

## 2026-11-08: M16 verifier review r1: PASS (docs/reviews/M16-r1.md)
- Merged origin/dev (M20) into m16. Enum conflicts resolved: dev's `NEEDS_MATERIALS`/`BLOCKED` keep their indices and M16's kinds are appended after them. `HeartTextsActivityTest` now pins all four appended kinds.
- Mutation-checked: band threshold (L1 and L2), the tier gate on diamonds, no wear, haul never delivered, invalid shaft ignored, and no Invisible re-apply on load. Each one turns its test red.
- Hidden-miner probes: release while unloaded, fire then reload, and blocked surface cells (stays hidden, then recovers when cleared) all restore the flags. Notes are in BACKLOG ("From M16-r1").
- Full L2: 8 of 8 green (199 tests). The stray-item flake and M9 flakes were not seen. `scripts/verify.sh` exit 0. I looked at `villagers_tab_away_mining.png`.
- Next: M17 (Hunter) reuses AwayTrips.

## 2026-11-07: M15 plan (job levels and tools), branch m15-plan
- Plan: docs/plans/M15.md, 9 steps. One XP API: `Workers.addXp(villager, n)` (+ `WorkXp.award(villager, WorkUnit, n)` with fractional XP per unit);
  `core.job.Leveling` applies M14's `TierRules.levelFor` (banked XP up to 300, level = max(stored, levelFor), never lowered); level changes
  written to attachment and `WorkerRecord.level` at once via M12's `Workers.setLevel`; a 20-tick pass turns banked XP into levels after a tier-up.
- Feedback: HAPPY_VILLAGER particles, VILLAGER_CELEBRATE sound, chat to online Resident+ members (max 3 lines + summary), a one-off "needs a Village"
  notice, the vanilla belt badge (VillagerData level 1/2/3), a gold "capped" XP bar on the Villagers tab. Listener seam for M18 perks.
- Tools: nothing to redo (M9 has tiers, multipliers, sharing, wear per log); M15 extends the speed table test, audits `CampTools.use` per job, and
  re-proves wear in the side-by-side test.
- **Blocked on decision D1:** the roadmap's "iron ≥1.3× stone" is impossible with DESIGN's multipliers (iron/stone = 1.5/1.25 = 1.2 is the
  ceiling, walking only lowers it). Recommended: "≥1.1× stone and ≥1.3× wood" in one three-way test (stacked identical yards at +30/+60 on glass platforms).
- Critique: a separate critic session, 19 findings (1 blocker = D1 confirmed, 9 major, 9 minor), all taken (plan section 7).
- Top risk: D1, then the stacked-platform layout of the side-by-side test (fallback: three sequential days, recorded as a deviation).

## 2026-11-08: M15 (job levels and XP; tools done in M9), branch m15
- **What changed.** Core: `WorkUnit`, `Progress`, `XpChange`, `Leveling` (gain / levelNow / capped / tierForNext / sanitize), `XpAccumulator` (integer milli-XP, 0.2 per block never drifts), `Balance.LEVEL_CAPS`, `MAX_BANKED_XP`, `milliXpPerUnit`; `core.tier.TierRules` (levelCap, levelFromXp, levelFor; M14 was absent, M14 adds the rest to that class). Glue: `Workers.addXp` / `Workers.setLevel`, `WorkXp.award` (what jobs call), `WorkerLevels` (listeners for M18 perks), the 20-tick level pass in `WorkerSystem` (banked XP becomes a level after a tier-up, never lowers a level), the vanilla belt badge follows the level (also on install), `LevelUpFeedback` (particles, celebrate sound, chat to online Resident+, max 3 lines per tick then "and N more", "needs a Village/Town" notice once per threshold, test seam). XP wired into the lumberjack (log), farmer (crop) and builder (0.2 per block placed). Heart screen: `VillagerRow.levelCapped` (+1 byte in the codec), gold full bar and tooltip. Codec clamps values above the table. Zombie conversion also resets the badge level.
- **Tests.** L1: `LevelingTest`, `XpAccumulatorTest`, `BalanceLevelsTest`, `WorkSpeedTest.fullToolLevelNeedsTable` (7 tiers x 3 levels x needs), codec clamp, snapshot `levelCappedFlag`, codec round trip with the new flag, `LangCoverageTest.levelUpMessages`. L2 (`LevelGameTests`, 10 tests): API and badge, tier-up turns banked XP into levels, reload and fire, cured zombie, felling pace after a level-up (40, 40, then 32 ticks), the Hamlet cap holds pace then lifts after `setTier`, farmer XP, feedback audience (Owner/Resident yes, Visitor no), tier-up flood summary, side-by-side; plus a builder XP assertion in `BuildGameTests.smallHouseWithinTwoDays`. L4: `LevelClientTests` (badges, Villagers tab).
- **D1 (orchestrator, option A).** Side by side over day ticks [1000, 5000) at Hamlet: wood 71, stone 89, iron 106-107 logs; iron/stone 1.191-1.202 (>= 1.1), iron/wood 1.493-1.507 (>= 1.3), stone/wood 1.254. Eight full Layer 2 runs, each with the test: iron/stone 1.191, 1.202, 1.191, 1.202, 1.191, 1.191, 1.191, 1.202; iron/wood 1.493, 1.507, 1.493, 1.507, 1.493, 1.493, 1.493, 1.507 (plus 5 runs alone: all 1.191 / 1.493). Villager walking is deterministic in the stacked setup (about 4-6 ticks per log). Mutation: iron speed set to stone's turns it red.
- **Verify.** 8 of 8 full Layer 2 runs green (196 tests). One earlier `./gradlew build` showed the known M9 flake `six_oaks_two_days` ("oak 0 went at tick 600 and was not replanted within a day"), before any glue change; not seen again in 14 runs.
- **Deviations** (docs/plans/M15.md section 8): no `WorkerRecord.level` (M12 absent: T-L2-4 not written, re-install resets to level 1; M12 note in ROADMAP), codec clamps only above the table (M7's test pins the lower bound), oak trees have only 4-6 logs (T-L2-5/6 use oak seed 3), side-by-side offsets 28 instead of 30/60.
- **Next.** M16-M19, M22 each call `WorkXp.award` (notes in ROADMAP). M12 adds the record mirror in `WorkXp`. M14 adds tier progression to `TierRules`.

## 2026-11-08: M15 verifier review r1: FAIL (docs/reviews/M15-r1.md)
- Merged origin/dev (M16, M20) into m15. The five conflicts (docs, client and reset hooks, gametest entrypoints) kept both sides. No enum conflicted. `levelCapped` is appended at the end of the row codec.
- Every criterion goes red under its mutation: thresholds, cap ignored (L1 and 6 L2 tests), level not applied to speed (L1 and the felling-pace tests), XP not awarded (lumberjack, farmer, builder), the level pass removed, wear off, and the codec lower bound.
- Fix 1: the merged miner (M16) never calls `WorkXp.award(ITEM_MINED)`, so a miner's level never rises (DESIGN §4: "mined item 1"). It needs the call in `MinerSystem`'s haul branch and an L2 assertion.
- Full L2: 6 of 6 green in the foreground (209 tests). Side by side: iron/stone 1.191 or 1.202, iron/wood 1.493 or 1.507. No M9 flakes. M16's stray-item flake was seen once, in a mutated run. `scripts/verify.sh` exit 0. I looked at both M15 screenshots.
- Next: M15 round 2 (fix 1).

## 2026-11-08: M15 review r1 fixes
- The merged miner (M16) now earns XP: `MinerSystem` awards `ITEM_MINED` (sum of the haul's item counts) next to `work.miner.hauls`; `minerHaulsFortyCobblestone` asserts XP == min(300, items hauled) and goes red without the call ("expected 77, was 0"). Builder, lumberjack and farmer were already wired and tested; no other job is merged. Stale M16 "Note (M15)" removed from ROADMAP.
- Side-by-side test: the final assertions run in try/finally so a failed assertion still clears the upper platforms (a timeout is still covered only by the clear at the next run's start). T-L2-11 keeps the reflection (a rename fails loudly).

## 2026-11-08: M25 plan (raids and alerts), branch m25-plan
- Plan: docs/plans/M25.md, 10 steps. One raid truth: `VillageRaids` (glue) over pure `core.raid.RaidTracker`. Discovery with `getRaidAt(heart)` (centre or a raider in the claim, M18's rule), then latched by vanilla raid id (`Raids.get(id)`), status checked every tick because `Raids.tick` drops a stopped raid one tick later. Feeds the RAID alert (always first, DANGER), M18's `RaidCheck` (guard call-up), worker shelter, chat to Resident+, the death log's `duringRaid` flag and `raid.*` metrics.
- Workers indoors: hired non-guard workers lose vanilla `SetRaidStatus` (its PRE_RAID sends villagers out to the bell, and it only works within 96 blocks of the centre) and get a mod `SHELTER` activity (own House bed, else nearest House bed, else a room building). Unhired villagers keep vanilla.
- Death log: `VillageState.deaths` (32 entries, monotonic saved counters), every worker anywhere and every villager in the claim, cause rule shared with the sim collector; Overview shows 3 rows. L3 cross-check `deaths.logged == deaths.total`.
- Deterministic raid start: gametest helper `RaidStart` = what the IDE-only `/raid start` does (`createOrExtendRaid` + `setRaidOmenLevel`); sim action `raid` (day 3, tick 2000); a gametest accessor mixin holds waves in L2. The real Bad Omen path is tested too (test players need `doTick()` for effects).
- Scenario `raid` (6 days, Normal): Town tier, 12 adults, 8 workers incl. 4 level-2 guards in iron, exactly 1 golem, M21 walls; survive = VICTORY by the last day, claim held, ≥75% of adults alive as deaths and as survivors; 3 of 3 runs.
- Assumptions on unmerged M11, M14, M18, M19, M21 in plan section 3.1 (M18 is a hard dependency for steps 5, 9, 10).
- Critique: a fresh critic session, 22 findings (3 BLOCKER, 11 MAJOR, 8 MINOR), all taken (plan section 7); two blockers confirmed in the sources.
- Top risk: a Normal 5-wave raid (door-breaking vindicators, vexes guards ignore) against villager guards in an unseeded village; victory also needs every straggler dead.
