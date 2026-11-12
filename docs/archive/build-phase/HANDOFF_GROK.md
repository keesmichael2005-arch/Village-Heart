# Handoff: ALL remaining v1 work to owner-run Cursor agents (Grok), 2026-11-11

The Claude orchestrator is PAUSED. ALL remaining work is split into 12 tasks in 4 waves. The owner starts every task of a wave in parallel
(one Cursor agent each) and starts the next wave when the previous wave's tasks have pushed their final work (or opened their PR).

| Wave | Tasks | Starts when |
|---|---|---|
| 1 | TASK 1-8 (all independent) | now |
| 2 | TASK 9 (M25 integration: merge tracks A+B, plan steps 16-19) | TASK 3 and TASK 4 are done |
| 3 | TASK 10 (v1 integration branch: merge everything) | TASK 1, 2, 5, 6, 8, 9 are done |
| 4 | TASK 11 (M26 balance) then TASK 12 (v1.0 release gate) | TASK 10 is done; 12 after 11 |

TASK 7 (Blueprints 2) is post-v1 and is NOT merged into the v1 integration branch.
They run until finished or the Cursor usage runs out. Then the owner tells the Claude orchestrator "resume": it fetches every branch, runs a Claude Opus review per task
(`docs/agents/verifier.md`), fixes what fails, and merges into `dev`.

## How to start each agent (the "mega prompt")
Start 8 Cursor agents on https://github.com/keesmichael2005-arch/Village-Heart (model Grok 4.7, highest effort). Give each one this text,
changing only the task number:

> You are Cursor agent **TASK N** of 12 (see the waves table) on the Village Heart Minecraft mod. Check out the repo, run
> `git fetch origin && git checkout origin/dev -- docs/HANDOFF_GROK.md` (or read it on GitHub, branch `dev`), read the section
> "Rules for every agent" and the section "TASK N" in `docs/HANDOFF_GROK.md`, and do that task completely, start to finish, without
> asking questions: decide sensibly and write each decision in the plan's Deviations section or in PROGRESS.md. Work only on your task's
> branch. When you are done, open a PR into `dev` titled "TASK N: <name>" with the final `reports/verify-summary.md` in the description.

## Rules for every agent
- Work ONLY on your task's branch; push often (`git push origin HEAD:<branch>`). NEVER push or merge to `dev`. Never edit
  `docs/LOOP_STATE.md`. Other tasks run at the same time: stay inside your task's area (listed per task).
- First read `CLAUDE.md`, `docs/agents/builder.md`, `docs/TESTING.md`, then your task's plan/brief.
- Java: `python3 scripts/install-java25.py`, then `export JAVA_HOME=~/.jdk25`; always `./gradlew --no-daemon`.
  After every step: `scripts/verify.sh --fast` must be green (10-20 min), then commit + push. Finish with a full `scripts/verify.sh`
  exit 0 (20-30 min; L4 needs `xvfb-run`, install `xvfb` if missing). If one known-flaky test fails alone, rerun once.
- Never weaken, skip or delete a test to make it pass. Every new behaviour gets a test at the lowest layer that proves it
  (L1 JUnit in `core`, L2 GameTest, L4 client screenshot for visuals). Mutation-check key rules: break the code, see the test go red, restore.
- Enums encoded by ordinal (`ActivityKind`, `WorkerStatus`, `AlertCode`, ...) are append-only. `VillageState` saves through
  `Codec.mapPair(RAW_A, RAW_B)`: add keys to RAW_B, keep keys flat and old saves loading (lenient, tested).
- Look at every new or changed screenshot (open the PNG) and describe it in PROGRESS.md.
- Merge `origin/dev` into your branch at the start and before your final verify (keep both sides of doc conflicts).
- Commit as kees <keesmichael2005-arch@users.noreply.github.com>; end every message with `Agent: Cursor (grok-4.7), owner-run, TASK N`.
- Finish by appending to `docs/PROGRESS.md`: what you did, the verify summary, screenshots looked at, doubts, anything unfinished.
- Owner decisions are binding: read `docs/LOOP_STATE.md` section `decisions_by_human` (read only).

---

## TASK 1: finish M23 (Population growth)
- Branch `m23`. Brief: `docs/agents/briefs/M23-finish-grok.md` (on that branch). Plan `docs/plans/M23.md`.
- State: built; the outside-pair flake has a root-cause fix. Needed: full verify exit 0, `scripts/simulate.sh starter 10` passes, every
  M23 test non-vacuous (the `relativePos` trap is explained in TESTING.md "GameTest position helpers"), ROADMAP note, PROGRESS.
- Area: `core.growth`, growth mixins, growth tests. Do not touch guards, smith, raids, walls.

## TASK 2: build M19 (Smith: on-demand tools, guard gear, iron golems)
- Branch `m19` (last commit may be a WIP that does not compile: fix it first). Brief: `docs/agents/briefs/M19-build-grok.md`.
  Plan `docs/plans/M19.md` (r3). Build every remaining plan step.
- Owner decisions: tools only on demand (missing or worn out), never spares, never upgrade a working tool, best tier the village can
  afford, slow crafting (L1 ticks: wood 600, stone 1200, copper 1800, iron 2400, diamond 4800); Smithy is Town tier; guards start with a
  wooden sword and get better swords/armour only when the village has them (supply them on demand like tools); iron golems from 36 iron
  ingots + 1 carved pumpkin in 1 day, cap 1 per 5 villagers, vanilla golem spawning off in the claim, golems below 50% repaired at the
  Smithy with ingots; nobody attacks creepers (golems ignore them).
- Area: `core.gear`, smith/golem glue, Smithy. Do not touch growth, raids, walls/sieges.

## TASK 3: M25 track A (raids side, plan steps 1-8)
- Branch `m25`. Brief: `docs/agents/briefs/M25-build-grok.md` (track A). Plan `docs/plans/M25.md` r3.1 (read "Critique r3").
- Build plan steps 1-8 ONLY (death log, raid core, shelter core, raid glue + test helpers, raid watch and guard call-up, workers shelter,
  Heart screen raid line, raid harness), then stop with a green fast verify.
- IMPORTANT: `dev` now has a sim death log (`core.sim.DeathLog`, `DeathRecord`, `SimMetricsCollector` death rows, from m26-prep).
  Merge origin/dev and reconcile into ONE death log (the village log of M25 feeds the sim rows); keep all of m26-prep's tests.
  Findings that matter: `docs/reviews/m26-prep-findings.md` (vanilla VillageSiege caused 32 of 49 deaths in `defended`).
- Area: `core.raid`, `core.death`, raid glue, Heart screen raid line. TASK 4 builds the walls side at the same time: do not build steps 9-15.

## TASK 4: M25 track B (walls side, plan steps 9-15)
- Branch `m25-walls`. Brief: `docs/agents/briefs/M25-walls-grok.md` (track B). Plan `docs/plans/M25.md` r3.1.
- Build plan steps 9-15 ONLY (gamerules, threat state, recording walls/gates, siege rules core, wall HP and crack stages, siege damage,
  breach goals for raiders, zombie lure and breach, creepers seek and blow walls, builder repairs with cobblestone + DEFENCE_DAMAGED alert),
  then stop with a green fast verify. Where you need something from steps 1-8, add the smallest stub and note it in Deviations.
- Owner decisions: walls have HP and crack stages, gates are the weak point; only zombies are lured (at night and in sieges), they path
  toward villagers without line of sight and attack defence blocks in the way; creepers breach walls and are NOT attacked by anyone and are
  not a threat to villagers; skeletons and other hostiles stay vanilla; ravagers do heavy wall damage; no per-tier wall cap; moats protect walls.
- Area: `core.defence`/siege classes, mob goals, `BuilderWork` repair. Do not touch raid tracking (TASK 3) or growth/smith.

## TASK 5: release prep (docs and metadata only)
- New branch `release-prep` from `dev`. NO Java code or test changes.
- Write `docs/PLAYER_GUIDE.md` for players (claiming, ranks, markers and valid buildings with a table per building: floor, required
  blocks, tier; every job and what it needs; food/needs/dinner; wages/treasury; tiers; market; builder and blueprints; guards: wooden-sword
  start, night-heavy shifts, training day, rations, alarm; gates, lamps, walls). Mark sieges, random raids, wall damage, smith and population
  growth as "in v1, being finished". Use real numbers from `docs/DESIGN.md`.
- `CHANGELOG.md` for v1.0.0 (player-facing). Rewrite `README.md` (what it is, requirements: Minecraft 1.21.11, Fabric Loader, Fabric API,
  Java 21; install; link to the guide; build from source). `fabric.mod.json`: description, authors "kees", sources URL, and an original
  128x128 pixel-art icon (heart over a bell) at `src/main/resources/assets/villageheart/icon.png`.
- Check: `./gradlew --no-daemon build -x test` succeeds.

## TASK 6: small fixes in finished areas
- New branch `backlog-small` from `dev`. Each fix: a test that fails before and passes after.
  1. Market text "1 emeralds": correct singular/plural wherever emerald counts are shown.
  2. Client-test screenshots: clear vanilla toasts ("New Recipe(s) Unlocked" etc.) before every screenshot.
  3. L3 sim report: add the village tier and fed-days streak per day (metrics + report + test).
  4. `hunter_game_tests_two_hunters_one_ground` sometimes takes ~970 ticks vs a 450 bound under load: find the root cause (likely a walk
     jam between the hunters plus a long retry) and fix the behaviour; do not loosen the bound; prove with repeated runs.
- Area: market texts, client test helpers, sim report, hunter. Do not touch guards, gates, raids, walls, smith, growth, builder.

## TASK 7: Blueprints 2 (post-v1 feature, on its own branch)
- New branch `blueprints-2` from `dev`. Read `docs/DESIGN.md` 3.2 (room rules) and 5.8 (builder), `docs/plans/M20.md`, `docs/plans/M21.md`.
  First write `docs/plans/P4-blueprints-2.md`, then build:
  1. Load vanilla village buildings (the game's own `minecraft:village/*` structure templates) as builder blueprints; keep only those that
     pass the mod's room/house check (a test scans every one and lists which pass and why others fail).
  2. A "Blueprint Quill": mark two corners of a building the player built; if it passes the room check it is saved as a named blueprint at
     the Builder's Table and builders can build copies from Storage materials.
  3. Nicer blueprints for every job building that has none (Kitchen, Barracks, Lumber Camp, Farmhouse, Mine, Hunting Lodge, Market,
     Training Yard, Smithy) and a redesigned Small House / Storage Shed: pitched stair roofs, windows, log frames; each passes the building
     check (marker + furniture included), a test per blueprint, client screenshots of finished builds.
  4. Builder feel: slower tunable pace (about 1 block per 3-4 s at level 1, faster with level), several builders can share a site, arm swing
     and placing sounds, temporary scaffolding on tall builds.
- Area: blueprints, Builder's Table, builder pace/animation. Do NOT touch wall damage, wall repairs, `BuilderWork` repair logic (TASK 4),
  raids, guards, smith.

## TASK 8: defence scenarios and M26 groundwork
- New branch `m26-scenarios` from `dev`. Read `docs/reviews/m26-prep-findings.md`, `docs/plans/M26.md` (if present on `origin/m26-plan`),
  `sim/scenarios/defended.json` and `defended_walled.json`.
  1. Fix the scenario design flaw the findings describe: in `defended_walled` the scripted zombies spawn in the old village outside the wall
     and kill unhired villagers there. Make the scenario test the walls: put the whole village (old villagers included, or move them) inside
     the ring, and spawn the threat outside the wall. Keep the assertions unchanged; record results of 5 unseeded runs in PROGRESS.
  2. Write the M26 test audit and tunable bounds file the plan asks for (`docs/M26-bounds.md`): for every `Balance` value marked tunable,
     its current value, the tests that depend on it, and a safe min/max range. Do not change any value.
  3. Add a scenario variant `defended_nosiege` (vanilla VillageSiege off inside the claim via a gamerule/scenario flag only in the harness)
     and report 5 runs, so M26 can compare.
- Area: `sim/`, sim harness, docs. Do not touch game rules code (M25 changes sieges).

## TASK 9 (wave 2): M25 integration and plan steps 16-19
- New branch `m25-full`: start from `origin/m25` (TASK 3), merge `origin/m25-walls` (TASK 4) and `origin/dev`. Resolve conflicts so both
  tracks work together; replace every stub TASK 4 noted in Deviations with TASK 3's real code. `scripts/verify.sh --fast` green.
- Then build plan steps 16-19 of `docs/plans/M25.md` (random raids + scaling + young-village caps 3.13a + rewards, zombie sieges replacing
  vanilla VillageSiege inside the claim, the `raid` scenario, L3 runs and docs). Run the L3 scenarios the plan names and record results.
- Full `scripts/verify.sh` exit 0. Area: everything M25.

## TASK 10 (wave 3): v1 integration branch
- New branch `v1-integration` from `origin/dev`. Merge, in this order, resolving conflicts carefully (code: make both sides work; docs:
  keep both): `origin/m23`, `origin/m19`, `origin/m25-full`, `origin/backlog-small`, `origin/m26-scenarios`, `origin/release-prep`.
  After EACH merge run `scripts/verify.sh --fast` and fix breakage before the next merge (a fix commit per merge, explained).
- Typical conflicts: `Balance`, `VillageCodecs` RAW_B keys, append-only enums (keep dev's order, then each branch's new values in merge
  order), `fabric.mod.json` test lists, ROADMAP/PROGRESS/BACKLOG/TESTING.
- Finish: full `scripts/verify.sh` exit 0, `scripts/simulate.sh starter 10`, `defended 10`, `defended_walled 10`, `raid` once each; record.

## TASK 11 (wave 4): M26 balance pass
- Branch `m26` from `origin/v1-integration`. Plan `docs/plans/M26.md` (from `origin/m26-plan` if not on the branch) and `docs/M26-bounds.md`
  (TASK 8). Owner decisions: the `full` 30-day sim may act as a pretend player making choices (standing orders count as no player input);
  the start is meant to be hard; tune yields too (miner haul tables, lumberjack/farmer/hunter/cook output), builder pace (owner: slower,
  about 1 block per 3-4 s at level 1), guard numbers, siege/raid sizes. Stay inside the bounds file; every change recorded before/after in
  PROGRESS with the sim numbers. Goal: the roadmap M26 criteria (`full`, 30 days: reaches Village tier, food never 0 for more than 1 day
  in a row, treasury never negative, village logic < 1 ms/tick) and `defended`/`defended_walled` passing once tools, armour, golems,
  walls and lights exist (owner accepted a hard start: a defended Hamlet may lose some villagers, the village must not be wiped out).
- Full `scripts/verify.sh` exit 0 and all sims recorded.

## TASK 12 (wave 4, after TASK 11): v1.0 release gate
- Branch `v1-release` from `origin/m26`. Do the "v1.0 release gate" section of `docs/ROADMAP.md`: full verify, every L3 scenario, the
  2-player test (M24's real client + FakePlayer), build the release jar (`./gradlew --no-daemon build`, jar in `build/libs`), check the jar
  contains no gametest classes, update CHANGELOG/README/PLAYER_GUIDE for the final state, tick ROADMAP, write `docs/RELEASE_NOTES_v1.0.md`.
  Do not tag or publish a release: the owner and Claude do that after review.

---

## When the owner says "resume" to Claude
Claude fetches every branch above and reviews them as Opus verifiers (per milestone: M23, M19, M25, M26, the release gate, plus 5, 6, 8
and 7), fixes what fails, merges the v1 work into `dev`, and tags v1.0 with the owner. Whatever wave was not reached, Claude finishes.
