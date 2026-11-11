# Review: flakes, round 1 (verifier, 2026-11-11)

verdict: PASS

Reviewed `git diff origin/dev...origin/flakes` (head 642b8ff), merged with origin/dev (merge 7eb54df; one doc
conflict in `docs/BACKLOG.md`, both sides kept; no code conflicts). Goals: BACKLOG "Flakes on dev".

## Fix list (none blocking)
1. `docs/TESTING.md` "Flake root causes": "every caller ... so that test covers them by construction" overstates it.
   `walkToNeverArrivesBeyondItsReach` proves the `WalkTo` rule, not each caller's own distance check. It is true today
   because the only callers that re-check the distance (`SelfCraftJob`, the job-block walk-backs) pass `closest == reach`, so the
   `settled` branch (ARRIVED while within 1.5 of the settle point, at any distance) can never fire for them: at the tick it would
   settle, `d > reach == closest`. Callers with `closest > reach` (lumber, shaft, hunt, cook) act on ARRIVED and do not re-check.
   Optional: say this in TESTING so a future caller that re-checks `d <= reach` with `closest > reach` knows it reopens the band.
2. The L4 world-load bound (1200 -> 6000) is not proven by a reproduction (the builder says so). Acceptable: see evidence 5.
   If "Timeout loading world" recurs with the mixin installed, the cause is not the bound and needs a new BACKLOG item.
3. `two_hunters_one_ground` stays open in BACKLOG (not reproduced); `hungry_lumberjack_slower` showed no failure in 8 runs here
   (see 6). Nothing to change; record if either recurs.

## Evidence
1. **Full `scripts/verify.sh`: exit 0** at 7eb54df. L1 green, L2 "All 343 required tests passed", L4 green (39 screenshots),
   all three gametest mixin markers present (`WORLD_LOAD_TIMEOUT_FIX installed` once), model and 2-player gates green, perf green.
   Second full L2 run (`runGameTest` alone): 343/343 again. Looked at `0005_workers_at_job_blocks.png`: all ten workers stand at
   their job blocks (the screenshot most likely to move under strict arrival). No visual change in this branch otherwise.
2. **Strict arrival is red without the fix.** Restored `d <= reach + 0.1` in `WalkTo.tick` temporarily:
   - `worker_game_tests_walk_to_never_arrives_beyond_its_reach` FAILED: "not arrived at 1.5499999998137355 with reach 1.5: ARRIVED on tick 2".
   - `self_craft_lifecycle_game_tests_craft_works_from_just_outside_the_reach` FAILED: "progress 4003.0 on tick 602" (the original flake's number).
   Restored the file (`git checkout`); both pass alone again. The `-0.2` workaround in `SelfCraftJob` is gone (diff vs dev empty for that file).
3. **No assertion weakened.** Every changed `assert*` in the diff only gains diagnostic text (trace, day time, per-tick wear mismatches,
   light levels). One assertion added (`random_tick_speed == 0` in the side-by-side test). The only bound changed is the Fabric world-load
   bound, with a written reason in TESTING.md and the commit message. `pinClockEarlyInDay` keeps the same day index, so `seedProgress(today)`
   and `toDinner` (expects day+1) are unchanged; the test still requires exactly one dinner, six diners, six fed.
4. **Other fixes (read, not re-broken):** side-by-side env sets `random_tick_speed` 0 and the test asserts it (cause reproduced by the builder
   at 300); forced-chunk test pre-generates the 5x5 ring with `getChunk(FULL, true)`, whose temporary ticket is FULL level (33), not
   block/entity ticking, so the "grows without a player" claim still rests on `setChunkForced`.
5. **L4 mixin, gametest-only.** `WorldLoadTimeoutFix` lives in `src/gametest`, listed only in `villageheart-gametest.mixins.json` (client,
   `required: true`, `defaultRequire: 1`, so a Fabric update that drops the 1200 constant fails loudly). `build/libs/village-heart-0.1.0.jar`
   contains 0 `dev/villageheart/gametest` entries; its `villageheart.mixins.json` lists only `VillagerBrainMixin` and
   `AbstractFurnaceBlockEntityAccessor`. Judgement: acceptable. The bound counts client ticks for wall-clock chunk work on worker threads; the
   readiness signal is unchanged and a world that never loads still fails (after 6000 ticks); `verify.sh` fails if the mixin is not installed.
6. **`hungry_lumberjack_slower` vs strict arrival:** 6/6 alone (load 3.5-5.1) plus 2/2 in full L2 runs, all with identical numbers:
   hungry mean gap 80.0 (bound 80 +- 1), unhoused 53.2 (53.3 +- 1). Mechanism check: the lumberjack's `fell` acts on ARRIVED with
   CHOP_REACH 3.0 / CHOP_CLOSEST 8.0; strict arrival only matters if it is pushed into 3.0-3.1 mid-tree, and the test sweeps other villagers.
   Item pickups (reach 0.8) take at `COLLECT_TAKE` 2.2 before arrival matters. No evidence the earlier failure came from this branch.
7. **Regression sim:** `scripts/simulate.sh starter 10`: SIMULATION PASS, 14/14 assertions, `sum(work.workers.not_ticking) == 0`,
   population 8, no deaths, a dinner every day, logs grow every day, farmer harvests from day 3.
