verdict: PASS

# backlog-2 review, round 3 (verifier, focused)

Reviewed: e636143 (r2 must-fix: two new step-aside variants, plus the optional "cells ahead only once the navigation gave up") and
f2cd64b (jam clock with an idle navigation) on `backlog-2`, merged with origin/dev (BACKLOG/LOOP_STATE only, no conflict).

## Runs
| Run | Result |
|---|---|
| L2 full #1 to #4 (foreground) | 234/234 green in all 4 |
| `scripts/verify.sh` (L1, L2 #5, L3 self-test, L4, gates, perf) | **exit 0** (234 L2 tests green, L4 green, 0 lost wakeups rescued) |

`build/run/gameTest/world` was deleted before every run. Load average was 4 to 7.8. In every L2 run (5 in total):
- `idlerTwoCellsAheadIsAsked`, `idlerAheadIsAskedAfterTheNavigationGaveUp`, `idlerWithItsOwnWalkTargetIsAsked`: asked after 31 ticks.
  The premises held each time: 30 walk ticks with an idle navigation, and 31 idler ticks holding its own WALK_TARGET.
- `idlerInTheDoorwayStepsAside`: asked after 70 to 71 ticks, first log after 302 to 303 ticks. `chopperIsNotAskedToStepAside`: nudged 0.
- `twoMinersAndBrokenMarker`: green (iron haul 1665, stone haul 1994). `workerOpensDoors`: 64 ticks. `farHunterWalksViaLodge`: 350 ticks.
- No crashes in the logs. The only errors are the known offline Yggdrasil key fetch errors.

**Flakes seen: none** (0 red tests in 5 L2 runs, 1170 test runs).

## r2 must-fix: mutation checks (each test run alone, restored with `git checkout`, tree clean afterwards)
| Mutation in `WalkTo` | Test | Result |
|---|---|---|
| none (baseline) | `idlerAheadIsAskedAfterTheNavigationGaveUp` | green, asked after 31 ticks |
| M3: `idleBlocker(v, cur, ...)`, so there is no `lastPath` fallback | `idlerAheadIsAskedAfterTheNavigationGaveUp` | **red**: "the idler on the door front was not asked on tick 1502" |
| M4: jam branch `&& !nav.isDone()` (the pre-backlog-2 gate) | `idlerAheadIsAskedAfterTheNavigationGaveUp` | **red**, same message |
| M5: `if (o.getBrain().hasMemoryValue(WALK_TARGET)) continue;` in `idleBlocker` | `idlerWithItsOwnWalkTargetIsAsked` | **red**, same message |
| none (baseline) | `idlerWithItsOwnWalkTargetIsAsked` | green, asked after 31 ticks |

The variants assert their own premises, so they cannot pass vacuously:
- nav-gave-up: the walker's navigation is idle at every walk tick after the first plan, for at least 30 ticks;
- own-target: the idler holds its own WALK_TARGET for at least 30 ticks.

The shared helper `twoCellsAhead` keeps the original variant unchanged. The r2 fix item 1 is done.

## (a) Only the cells ahead count once the navigation gave up
- With `live == false` the 1-block box around the walker is dropped, and the cells start at the path's next node (not the one before).
- This narrows r2 BACKLOG item 2 as suggested. A villager that only stands next to an idle walker is no longer nudged.
- The nav-gave-up variant still finds the door-front idler (it is on the next nodes), so the narrowing does not lose the real case.
- No test pins the narrowing itself. That is acceptable, since it was an optional item.
- Small remaining gap (BACKLOG 1): `lastPath` is only replaced on a new plan. `retarget()` and arrival do not clear it, so after a
  retarget, a walker whose navigation is idle reads the cells ahead of the *old* path until it plans again. Its frozen
  `nextNodeIndex` can also trail the walker if it was pushed. The effect is at most a stray nudge of a plain villager every 30 ticks.

## (b) f2cd64b: is the two-walker step-back timing really back to origin/dev?
I compared the jam branch with origin/dev line by line.
- origin/dev:
  - `if (now - jamAt >= JAM_TICKS && !nav.isDone()) { jamAnchor = null; if (cur != null && next > 0 && jamNeighbour) stepBack; else jamAt = now; }`
  - With an idle navigation nothing happens, so the jam clock keeps running.
- HEAD:
  - `if (now - jamAt >= JAM_TICKS) { idle = idleBlocker(..); if (idle != null && stepAside) {reset} else if (nav.isDone()) {nothing} else if (cur != null && next > 0 && jamNeighbour) {jamAnchor = null; stepBack} else {jamAnchor = null; jamAt = now} }`
- When `idle == null`, or `stepAside` finds no spot, the result matches origin/dev in every state:
  - navigation idle: no change (the clock runs, `jamAnchor` is kept);
  - navigation busy: the same step-back or clock reset, with `jamAnchor` cleared in both branches as before.
- `idleBlocker` only reads entities. The randomness `stepAside` uses is the idler's, and only when a candidate exists. So the
  walker's own random sequence (`nextPathAt` jitter) is unchanged.
- Two mod walkers never see an `idle` candidate: `isWorker` excludes them (proven by `chopperIsNotAskedToStepAside`). So the
  two-walker path (miners at a shaft, two lumberjacks in a door) **is** the pre-backlog-2 behaviour.
- It differs only when a plain villager stands on the cells ahead, which is new behaviour by design.
- The M16 cause (r2-era code reset `jamAt` every 30 ticks with an idle navigation) matches the code. The builder says it was inferred
  from one failure, not measured. `twoMinersAndBrokenMarker` was green in all 5 runs here. Since the restored code matches origin/dev,
  no new risk is left there.

## Other checks
- No test was weakened or deleted. The new environments `lumber_door_idler_nav_gave_up` and `lumber_door_idler_own_target` exist,
  and the class is already registered.
- No client code is referenced from `main`, and nothing is visual (no new screenshots).
- `docs/PROGRESS.md` has no builder entry for e636143/f2cd64b (DoD 5). I added the review entry. The builder should log its own
  next time (item 2).

## Fix items
None must-fix.
1. (BACKLOG) `WalkTo.lastPath` is not cleared on `retarget()`/arrival: a walker with an idle navigation can read the cells ahead
   of a stale path (a stray nudge at most).
2. (process) The r2-fix commits did not append to `docs/PROGRESS.md`.
3. (BACKLOG, carried over) r2 items 3 (golems/traders/passengers/leashed) and 4 (`heartReleaseWithUnloadedWorkstation` premise
   hardening) stay open.
