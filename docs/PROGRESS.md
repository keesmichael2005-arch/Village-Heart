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
