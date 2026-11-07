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
