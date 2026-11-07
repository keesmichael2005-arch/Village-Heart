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
