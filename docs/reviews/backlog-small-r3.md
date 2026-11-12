verdict: PASS

# backlog-small review, round 3 (verifier)

Reviewed: `backlog-small` at 05b0d8b (round-2 fixes), with `origin/dev` merged in as ed1db90.
- The merge conflicted only in `docs/PROGRESS.md` (two hunks). I resolved both by keeping both sides; nothing from either side was dropped.
- Diff reviewed: `git diff 36cbdf5 05b0d8b`.
  - The code change is `HuntApproach.yieldNow`'s new `awayNeighbour` flag, which `HunterJob` passes as `AwayTrips.isAway(blocking)`.
  - The diff also adds two GameTests, two test environments and a `HuntApproachTest` case. The existing `yieldNow` assertions were only updated for the new argument.
  - No test was removed or loosened. `twoHuntersOneGround` keeps its 450-tick bound (`HunterGameTests.java:630`).
  - The `fabric.mod.json` change in that range comes from dev's release-prep merge, not from the fix.

Both round-2 items are fixed. Each new test goes red on its mutation, and the full `scripts/verify.sh` exits 0.

## Fix list
None.

## Round-2 items
| R2 item | Status | Proof |
|---|---|---|
| 1 A walker jammed against an away (NoAI) hunter beyond 6 blocks waits out the 200-tick stall | **Fixed** | `hunterJammedAgainstAwayHunterBeyondSixStepsAroundAtOnce` gets out after 104 ticks (bound 150). Mutation: pass `false` for `awayNeighbour` (the `!blocking.isNoAi()` guard alone, as in round 2). The test goes **red**: "took 273 ticks, bound 150". Unit test: `HuntApproachTest.aWalkerAlwaysStepsAroundAnAwayNeighbourAfterTheShortJam`. |
| 2 Mover-only arrival was proven only in `core` | **Fixed** | `wallWithABystanderNearTheMarkerIsStillUnreachable` puts the NoAI bystander within 6 blocks of the marker. The hunter is reported SITE_UNREACHABLE at tick 458, 4.3 blocks from the marker, and never goes away. Mutation: `blockerMoves = blocking != null`. The test goes **red**: "the hunter went away against a wall beside a bystander". |

I restored `HunterJob.java` after each mutation (`git checkout`), and the full run below was on the clean tree.

Note on the first item: the fix leaves the `!blocking.isNoAi()` guard in place and adds a separate away-hunter flag. Restoring the guard literally changes nothing. The equivalent mutation turns the new flag off.

## Round-1 items (spot check)
- **Emerald plurals:** every emerald or merchant count still goes through `core/Plural`. The call sites are:
  - `HeartScreen` overview: treasury and wages;
  - `WageAlerts`;
  - `Treasury`: deposited, deposited_settled and withdrawn;
  - `HeartActions`: deposit, settle and withdraw;
  - `WorkerCommands`: treasury get, set and add;
  - `MarketTexts`: sold, plus "merchant".

  `PluralTest.noTextPutsAFixedEmeraldOrMerchantNounAfterACount` still scans the lang file and `HeartTexts.EN`. The only lang line with a count and a fixed plural noun is "%s workers are on strike", a worker count already listed as advisory.
- **Sim tier:** `TierGameTests.simCollectorReadsTheVillageTier` is still in place and calls the real `SimMetricsCollector.collect`. It passed in Layer 2.

## Evidence
**Full `scripts/verify.sh` at ed1db90: exit 0.** The machine has 4 cores; Java 25 was installed with `scripts/install-java25.py`, and xvfb was present.
- ✅ Layer 1: build and unit tests. The build's own GameTest run gave these hunter results:
  - `twoHuntersOneGround`: 197 ticks;
  - the jam test: out after 104;
  - the wall test: reported at 458.
- ✅ Layer 2: "All 414 required tests passed". In this run `twoHuntersOneGround` took 197 ticks, the jam test got out after 104, and the wall test reported at 458.
- ✅ GameTest-ran gate and its self-test, the L3 script self-test, the model-log gate and the 2-player log gate self-tests.
- ✅ Layer 4: client GameTests and screenshots.
  - The lost-wakeup, disconnect-leak, world-load-timeout and toast-clear fixes are installed.
  - There are no model warnings, and the 2-player session has no server errors or disconnects.
- ✅ Perf limits.

**`two_hunters_one_ground` alone, 5 runs:** 5 of 5 passed, "both out after 197 ticks" each time.

**Mutation runs** (one test alone with `-Dfabric-api.gametest.filter`): see the table above.
- The exact id of the wall test is `hunter_game_tests_wall_with_abystander_near_the_marker_is_still_unreachable` ("abystander", not "a_bystander").
- A wrong id runs 0 tests and exits 0, so check the "required tests" count.

No visual change in this round, so there are no new screenshots.

Advisory, carried over from round 2 and still open (not required for PASS):
- `mover()` counts any walking villager, not only a hunter.
- `HeartActions.deposit` passes two args to the three-`%s` `deposited_settled` key.
- The "1 workers" strike texts.
- `MAX_JAM_GIVE_UPS` and `YIELD_AFTER` are tunables outside `Balance`.

verdict: PASS (0 fix items)
