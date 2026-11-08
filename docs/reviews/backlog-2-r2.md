verdict: FAIL

# backlog-2 review, round 2 (verifier)

Reviewed: the builder fix commit 7249be4 (and dc4935c, PROGRESS only) on `backlog-2`, merged with origin/dev (one file, `LOOP_STATE.md`,
no conflict). I checked the three fix items of `docs/reviews/backlog-2-r1.md` and looked for cases where the wider step-aside search
moves villagers it should not.

## Runs
| Run | Result |
|---|---|
| L2 full #1 to #7 (foreground) | 232/232 green in all 7 |
| `scripts/verify.sh` (L1, L2 #8, L3 self-test, L4, gates, perf) | **exit 0** (232 L2 tests green, L4 green, 1 lost wakeup rescued) |

I deleted `build/run/gameTest/world` before every run. Load average was 6.4 to 10.5. In every L2 run:
- `idlerInTheDoorwayStepsAside`: the idler was asked 70 ticks after the hire, and the first log fell after 302 ticks.
- `idlerTwoCellsAheadIsAsked`: asked after 31 ticks.
- `chopperIsNotAskedToStepAside`: nudged 0 times, with the walker stalled for 120 ticks.
- `workerOpensDoors`: 64 ticks. `farHunterWalksViaLodge`: 350 ticks.

**Flakes seen: none** (0 red tests in 8 Layer 2 runs, 1856 test runs).

With these runs, r1's "green in at least 8 full runs" condition for item 1 is met: 7 L2 runs plus the L2 run inside verify.sh.

## Mutation checks (each run alone, restored with `git checkout`)
| Rule broken in `WalkTo` | Test | Result |
|---|---|---|
| M1: path cells removed (only the 1-block box is left) | `idlerTwoCellsAheadIsAsked` | **red**: "the idler on the door front was not asked" |
| M2: `isWorker` exclusion removed | `chopperIsNotAskedToStepAside` | **red**: "the chopping lumberjack was asked to step aside" (3 times) |
| M3: no `lastPath` fallback (`idleBlocker(v, cur)`) | `idlerTwoCellsAheadIsAsked` | **green** (asked after 31 ticks): not covered |
| M4: jam branch gated on `!nav.isDone()` again (the old code) | `idlerTwoCellsAheadIsAsked` | **green** (31 ticks): not covered |
| M5: idler holding a WALK_TARGET skipped again (the r1 race) | `idlerTwoCellsAheadIsAsked` | **green** (31 ticks): not covered |
| M5 | `idlerInTheDoorwayStepsAside` | **green**, but the idler was asked after 132 ticks (70 in every unmutated run), so the change matters and the end-to-end test does not pin it |

## Is the `idlerTwoCellsAheadIsAsked` setup realistic?
- `setOnGround(true)` is fine. A villager that really stalled on a floor is on the ground. The flag only undoes what the per-tick
  `teleportTo` + zero motion leave behind, so that `GroundPathNavigation` will plan, as it does for any grounded mob.
- What the test does **not** show: because the walker is pinned, its navigation always keeps a live path. So the two other parts of the
  r1 item 1 fix are never reached:
  - the `lastPath` fallback and the jam branch running while `nav.isDone()` (M3, M4);
  - the race removal (M5). Until the idler is asked, the test erases the idler's WALK_TARGET and stops its navigation every tick, which
    is exactly the race the fix claims to remove.
- r1 asked for "It must also still apply when the navigation has given up (`nav.isDone()`) next to a standing villager". That code
  exists, but no test proves it (fix 1).

## Item 2 (never our workers): done
- `WorkerAttachments.isWorker` covers every hired villager: walking, working, or away and hidden.
- `isTrading()`, `isSleeping()` and `isNoAi()` are excluded explicitly.
- The id step-back between two mod walkers is reached again, because a worker is never returned as `idle`.
- `chopperIsNotAskedToStepAside` holds the premise (navigation idle, 120 ticks of stall, which is 4 jam periods) and goes red on M2.
- One remark: with M2 the chopper still felled the oak after 3 nudges. So "keeps chopping" is not what makes the test red, the nudge
  list is. That is fine, since the rule under test is "never nudged".

## Item 3 (DESIGN §5.1): done
- It says that saplings in Storage and on the ground are not counted ("one guaranteed replant per felled stump", including trees the
  player cut).
- It lists the step-aside exclusions.

## Adversarial: who can the wider search nudge?
**The step-aside target.** It is `LandRandomPos.getPosAway(o, 4, 2, walker)`, with `DefaultRandomPos.getPosAway` as a fallback. This is
the same family vanilla uses for a panicking villager. The point is:
- 0 to 4 blocks away horizontally and ±2 vertically, within ±90° of the direction away from the walker;
- `isStableDestination`, which needs solid ground under it;
- not water (Land variant) and `hasMalus == false` (both variants). That rejects water (malus 8), lava, fire and other danger cells,
  fences, powder snow and so on.

There is no reachability check. The vanilla `MoveToTargetSink` pathfinds to it, so the villager walks only as far as a real path goes.
The candidates are only `Villager.class` entities.

| Case | Nudged? | Judgement |
|---|---|---|
| Iron golem, wandering trader (an `AbstractVillager`, not a `Villager`), zombie villager, player | no | Safe. But a golem or trader standing in a doorway still jams a worker until the stall give-up (BACKLOG 2) |
| Baby villager | yes | Fair: a plain villager, and the walk is short |
| Unhired vanilla professional at its workstation | yes, if it is on the walker's next cells or within a block | Harmless: it keeps its POI claim and walks back (`StrollToPoi`). Only happens when it stands in the worker's way |
| Awake villager near its bed at night | yes | Harmless: it walks back to the bed. Workers sleep at night too, so this is rare |
| Sleeping, trading, NoAI, any hired worker | no | Correct |
| Leashed / led villager | yes | Harmless: the leash limits it. A led villager that is walking (motion > 0.01/tick) is skipped |
| Villager in a boat or minecart | yes | It has no effect (a passenger does not walk). The walker re-asks every 30 ticks and gives up after `WALK_STALL_TICKS` as before. No worse than before (BACKLOG 3) |
| Penned villager (fence, closed fence gate) | yes | It cannot leave: the target can lie outside the pen (no reachability check), but villagers cannot open fence gates or jump fences, so the path ends inside. A wooden **door** does not hold it, but vanilla villagers open doors every day anyway |
| Into danger (lava, water, cliff) | no | The target cell has no malus and stable ground. A drop of more than 2 is ruled out by the ±2 vertical range, and the path never takes a fall the pathfinder does not allow |
| Outside the claim | possible, by up to 4 blocks | A non-issue: plain villagers are not bound to the claim, and vanilla strolls go much further |
| Trading hall (villagers locked in 1x1 cells next to a corridor) | yes, if within a block of a jammed worker | Harmless: they cannot move. A wasted WALK_TARGET |

**A new scope, not a must-fix (BACKLOG 1).** The jam branch now also runs while the walker's navigation is done. So a walker that
stands still waiting for its 10-tick re-plan, or at the end of a partial path that is not within `closest`, nudges any standing plain
villager within a block of it, or on the last two cells of its old path, every 30 ticks. That can happen at a crowded meeting point or
at a fence in front of an unreachable target. The villagers are pushed up to 4 blocks away even though they may not be what blocks it.
It is mild (at most about 6 nudges before the 200-tick stall give-up), but it goes beyond "in its way".

## Other checks
- No test was weakened or deleted. The two new tests are listed in the class that `fabric.mod.json` already registers. The new
  environments `lumber_door_idler_ahead` and `lumber_chopper_not_nudged` exist.
- No client classes are referenced from `main`. No lang/texture entries are needed.
- No crashes in the L2 logs, apart from the known offline `api.minecraftservices.com` lookup.

## Fix items
1. **(must-fix) Test the parts of r1 item 1 that no test covers** (`LumberjackGameTests`). M3, M4 and M5 all stay green.
   - Add a variant of `idlerTwoCellsAheadIsAsked` in which the walker's navigation has given up before the jam check: the test calls
     `v[0].getNavigation().stop()` each tick after the first plan, so `nav.getPath()` is null. The idler must still be asked, through
     `lastPath`.
   - Add a variant in which the idler holds a WALK_TARGET of its own while standing: do not erase it, or give it a WALK_TARGET to its own
     cell each tick, and keep its motion at zero. The idler must still be asked.
   - Each must be red with M3+M4 and M5 respectively. Mutation-check them.
2. (BACKLOG) The nudge while `nav.isDone()` reaches any standing villager within a block, not only one in the way. Consider limiting the
   `nav.isDone()` case to the `lastPath` cells (drop the 1-block box there) or to the first two cells ahead.
3. (BACKLOG) Iron golems and wandering traders in a doorway still jam a worker. Passengers and leashed villagers are re-nudged without
   effect. Either skip `isPassenger()`/`isLeashed()` (and let the id step-back or the stall give-up decide), or note it.
4. (BACKLOG, carried over from r1) Optional premise hardening in `WorkerGameTests.heartReleaseWithUnloadedWorkstation`.
