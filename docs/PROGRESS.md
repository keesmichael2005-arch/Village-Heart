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
