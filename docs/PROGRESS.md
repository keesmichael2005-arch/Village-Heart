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

## 2026-11-07: M18 plan (guards), branch m18-plan
- Plan: docs/plans/M18.md, 13 steps. Guard brain without panic/bell/raid hiding. Combat is a mod behaviour plus vanilla `doHurtTarget` after registering `ATTACK_DAMAGE` on villagers (`FabricDefaultAttributeRegistry`), because villager brains lack the attack memories.
- Day sleep uses vanilla `SleepInBed` (no time check), with REST driven by a pure `GuardSchedule` (night shift: sleep 0-11000, duty 12000-24000).
- Barracks beds are reserved through the M7 POI pass (HOME eviction). Gear is worn (sword visible, armour invisible) and handed back to the Barracks chest off shift.
- Level perks are permanent attribute modifiers. Guards heal while asleep, and a wounded guard retreats to its bed.
- Shift pins are saved in `VillageState`; the Heart screen gets a `SetGuardShift` action gated by `SET_GUARD_SHIFT`. L3 `defended` sits on M11's `starter`.
- Assumes M9 (WalkTo, without relying on its give-up timings), M11, M15 (else a minimal Leveling) and M12 (strike = no duty).
- Critique session gave no answer in time; self-review is in plan section 7. A fresh critique is owed before the build.
- Top risk: L3 `defended` (≤ 1 mob death in 10 unseeded days) depends on the unbuilt M11 harness and on vanilla villager night behaviour.
