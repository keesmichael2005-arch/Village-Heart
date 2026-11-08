verdict: FAIL

# backlog-2 review, round 1 (verifier)

Reviewed: the builder commits f3c1a4b, 888a90e, 174d7c7 and 01add29 on `backlog-2`, merged with origin/dev (da7e6d1). The merge had
one conflict, in `docs/PROGRESS.md`; I kept both sides.

## Runs
| Run | Result |
|---|---|
| L2 full #1 | 230/230 green |
| L2 full #2 | 230/230 green |
| L2 full #3 | **1 failed: `lumberjack_game_tests_idler_in_the_doorway_steps_aside`** (the new test, see fix 1) |
| L2 full #4 | 230/230 green |
| L2 full #5 (lumber trace on) | 230/230 green |
| `scripts/verify.sh` (L1, L2 #6, L3 self-test, L4, gates, perf) | **exit 0** (230 L2 tests, L4 green, 1 lost wakeup rescued) |
| L2 full #7 (lumber trace on) | 230/230 green |

All runs were in the foreground, except verify.sh: it takes longer than one 10-minute tool call, so I ran it as one background job
and ran nothing else while it ran. I deleted `build/run/gameTest/world` before every run. Load average was 4.5 to 11.
`workerOpensDoors` took 64 ticks in all 7 runs. `farHunterWalksViaLodge` took 350 ticks in every run. `sixOaksTwoDays` and
`fellResumesAfterInterrupt` were green in all 7 runs.

## Mutation checks (each restored with `git checkout`; the tree was clean before the commit)
| Rule broken | Test | Result |
|---|---|---|
| `LumberJob.giveCuttings` returns at once | `stumpsReplantedWhenLeavesDropNoSapling` (alone) | **red**: "oak 0 is replanted (last log at 360) ... on tick 3002" |
| `WalkTo`: `idle = null` (no nudge) | `idlerInTheDoorwayStepsAside` (alone) | **red** with the natural signature: "no movement for 201 ticks, 21.9 blocks from ..." |
| `ReplantRules.cuttings` without the `min(stumpCells, ...)` cap | `ReplantRulesTest.aFelledTreeYieldsWhatItsStumpStillLacks` | **red**: "never more than the stump's own cells ... expected 1 but was 6" |

## Are the causes proven?
- **M17 `farHunterWalksViaLodge`: proven.** I redid the arithmetic for the failing origin (-11343766, z 4474019), assuming no rotation:
  - The far spot (6, -10) is in chunk (-708985, 279625).
  - The corner (5, -10) is in chunk (-708986, 279625), and the corner (7, -1) is in (-708985, 279626).
  - So the far spot's chunk was neither forced chunk, as claimed. A chunk next to a forced chunk only ticks blocks, so the hunter there
    could not tick. That geometry happens at 9 of 256 placements.
  - The fix forces every chunk the 3 x 10 strip touches and adds a premise assert. No bound changed.
- **M9 `sixOaksTwoDays`: proven well enough.** Oak leaves really do draw from the per-world sequence `minecraft:blocks/oak_leaves`, and the
  forced repro (salt 553 with the rule off) gives the exact natural message. The new test checks its own premise: no sapling item may
  appear before both stumps are replanted.
- **M9 `fellResumesAfterInterrupt`: plausible, not fully proven, and the fix is incomplete.** The forced repro matches the signature.
  But the new deterministic test failed in 1 of my 7 full runs with that same signature, and the nudge never fired ("idler asked false").
  So the nudge does not cover every way the doorway jam happens (fix 1).
- **M7 `workerOpensDoors`:** only logging was added, as the builder said. The BACKLOG item stays open, which is correct.

## Gameplay judgement
**Cuttings (`ReplantRules.cuttings`): sane, not a meaningful exploit.**
- It gives at most one sapling per stump cell, and only to the lumberjack.
- It counts the worker's inventory and the camp chests against the camp's open REPLANT spots of that species. It gives nothing when
  `block_drops` is off.
- When the player has fully stocked the camp, `fellsOneOakWhole` proves no cutting is given.

The cases you asked about:
- **Leaf saplings already dropped, picked up later:** they are not counted when the tree finishes, so the cutting is extra. The later
  pickups become surplus (the keep rule, then Storage). This is the intended trade, and DESIGN says "leaf saplings are surplus".
- **Saplings in Storage:** not counted. A village with 64 oak saplings in Storage still gets a cutting. That is a small buff, not
  duplication.
- **Trees the player fells:** a task tree the player cut down is still "finished" by the lumberjack, which gets the cutting. That is one
  sapling per grown tree, which vanilla gives on average anyway.
- **Bonemealed trees:** they only make trees faster, and the yield per grown tree stays the same.
- **Mega trees (2x2):** up to 4 cuttings, matching the 4 replant cells, and the cap is tested.
- **Other species:** the sapling id comes from the task's species.
- Net effect: at most "one guaranteed replant per felled tree". Nothing creates saplings without a grown tree being felled.

**Step-aside nudge (`WalkTo.idleBlocker` / `stepAside`): too broad (fix 2).**
- It correctly skips sleeping villagers and NoAI villagers (hidden away workers).
- A villager trading with a player keeps a WALK_TARGET to the player in vanilla (`LookAndFollowTradingPlayerSink`), so it is mostly
  skipped, but only by accident.
- Babies and plain idlers are fair targets.
- It **does** catch our own workers, though. `WalkTo` never sets WALK_TARGET, and between path attempts a walking worker's navigation
  is done, so it counts as "idle". The lumber trace shows this twice in two-lumberjack jams:
  - `4508 @305914 ... asks the idle villager 4509 ... to step aside`, but 4509 was walking with `WalkTo` at that moment
    (`4509 @305831 walking ... done=true path=null`, `4509 @305942 jam ... steps back`).
  - The same happened with 9854/9855 at @534534.
- In both cases a vanilla WALK_TARGET was pushed onto a mod worker in the middle of its walk, and the step-back rule for two walkers was
  skipped. A worker standing still at its work (a lumberjack chopping, a farmer at a crop, a builder placing, a guard at its post later,
  or an M11 eater) has the same shape and can be pushed 4 blocks off by any walker that stalls within a block of it.
- The M11 dinner code (origin/m11 `food/Dinner*`) does not hold villagers in place, so it is not hit today.

## Changed existing tests (rule 4)
- No assertion was removed or loosened.
- `fellsOneOakWhole` got one more assertion (no cutting when a sapling is on hand).
- `farHunterWalksViaLodge` forces more chunks and adds a premise. Its bounds (60, 700) are unchanged.
- `workerOpensDoors` only gets a longer message and a print. Bound 400 is unchanged.
- `VillageTestSupport` resets the new nudge listener between tests.

## WorkerGameTests ~line 1185 (`heartReleaseWithUnloadedWorkstation`)
- **Not the corner-chunk bug.** It forces exactly the chunk that contains `far[0]` (`fx >> 4, fz >> 4`, and `far[0]` is `(fx, y, fz)`),
  so the forced chunk is the one the release needs.
- The 80-tick `awaitWithin` after re-forcing covers a load of an already saved chunk from disk plus one `processPending` pass. The
  builder measured a 2 s stalled read turning entity-ticking in 5 ticks, and the test was green in all 7 runs.
- Low risk. Optional hardening: wait, as a premise with no bound, until `getChunkNow` is non-null, then give the release a short bound.
  A load stall would then fail as a premise and not as a lost release.

## Fix items
1. **`LumberjackGameTests.idlerInTheDoorwayStepsAside` flakes, and the `fellResumesAfterInterrupt` mechanism is not fully fixed**
   (`WalkTo.java` jam branch).
   - Failure, L2 full run #3: `the first log falls; idler asked false; ... giveUps=[...: no movement for 201 ticks, 21.9 blocks from
     ..., at (rel 11, ., 5)]` on tick 2002.
   - The walker stalled on the first cell inside the camp (rel z 5). The idler was on the door-front cell (rel z 3), 2 blocks away, so
     `idleBlocker`'s 1-block box (`inflate(1.0)`) never found it.
   - In the green runs the walker reaches the door cell (rel z 4), usually after one "steps back" first. The check races the idler's
     vanilla brain, which sets a stroll WALK_TARGET in the same tick, before the test erases it.
   - Expected: the nudge must find the villager that actually blocks the next nodes of the walker's path (for example, any villager on
     the next 1 to 2 path nodes, or a box extended along the path direction). It must also still apply when the navigation has given up
     (`nav.isDone()`) next to a standing villager.
   - The test must then be green in at least 8 full runs. Diagnose with `-Dvillageheart.lumber.trace=true` (the "jam at" lines).
2. **The nudge disturbs mod workers** (`WalkTo.idleBlocker`).
   - Exclude hired workers (anything with `WorkerData` / a job of ours) and any villager whose own `WalkTo` is active. Exclude
     `isTrading()` explicitly as well, and away/hidden workers (NoAI is already excluded).
   - Of two mod walkers, the existing id rule (step back) must decide, not the vanilla WALK_TARGET.
   - Add a GameTest: a working lumberjack (chopping, navigation idle) next to a stalled walker is **not** given a WALK_TARGET and keeps
     chopping. The existing two-lumberjack doorway test(s) must still pass.
3. (doc, minor) DESIGN: say that saplings in Storage and saplings lying on the ground are not counted by the cuttings rule (the rule is
   "one guaranteed replant per felled stump", including trees the player cut down while a task was open). That way a later balance pass
   sees the intended small sapling surplus.

Items 1 and 2 are needed for a PASS. Item 3 is a doc note.
