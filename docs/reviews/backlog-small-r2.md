verdict: FAIL

# backlog-small review, round 2 (verifier)

Reviewed: `backlog-small` at 53cff1e (round-1 fixes in 520fe4c), with `origin/dev` merged in as 36cbdf5.
- The merge had two conflicts, both resolved by keeping both sides:
  - `VillageHeartClientTests`: this branch's `toasts` filter and `toastsClearedBeforeScreenshot` are kept beside dev's `art` filter and `placedHeart`.
  - `docs/PROGRESS.md`: dev's art-pass entries come first, then this branch's round-1 entry.
- Diff reviewed: `git diff 1c48bdb..520fe4c`.

All three round-1 items are fixed and each is backed by a test that goes red on its mutation. The verdict is still FAIL, because the
full `scripts/verify.sh` does not exit 0.
- Layer 1's GameTest run failed `twoHuntersOneGround`: "both hunters are out took 590 ticks, bound 450".
- That 450-tick flake is the one TASK 6 set out to remove. The log points to one cause, still in the code; see fix 1.

## Fix list
1. **`twoHuntersOneGround` still misses its bound when a hunter is stuck behind a hidden hunter just beyond 6 blocks**
   (`worker/hunt/HunterJob.java:269-271`, `core/hunt/HuntApproach.yieldNow`).
   - Evidence from `reports/build.log`, Layer 1:
     - At +460 the first hunter is away. The second has the decision GO_OUT and status OK.
     - It stands about 0.6 blocks from the hidden hunter, about 6.6 blocks from the marker (centre to centre).
     - It got out at +590.
   - Why it waits:
     - A hidden hunter is NoAI (`AwayTrips.java:108`, `setNoAi(true)`).
     - `yieldNow` gets `lowerIdNeighbour = !blocking.isNoAi() && ...`, so the walker never steps aside from a hidden neighbour.
     - The distance is above `HUNT_ENTRY_CLOSEST` (6.0), so `choose(..., walkFailed=false)` does not ARRIVE either.
     - So the walker waits out the 200-tick `WALK_STALL_TICKS`. Only then does the give-up path (mover, first give-up) take the
       YIELD and step-aside route.
     - Total: about 200 for the first hunter to arrive, plus the 200-tick stall, plus the step-aside walk, which matches 590.
   - This path is the same in round 1 (`f291ba6` has the identical `!blocking.isNoAi()` guard). It is not new, but it is still there.
   - Run alone, the test passes 3 of 3 at 197 ticks (load about 8). It also passed in this run's Layer 2 (198 ticks). It fails when the
     first hunter happens to hide at the 6-block edge.
   - Expected:
     - A walker jammed against an **away** hunter must not wait for the 200-tick stall. Two possible changes:
       - let `yieldNow` treat an away neighbour as one to step around (it is never the one to give way);
       - or count ARRIVE when the touched away hunter is itself within `HUNT_ENTRY_CLOSEST` of the marker.
     - Add a deterministic GameTest: a hunter sent away at a cell about 6.5 from the marker, and a second hunter that walks in behind it.
       Assert it goes out well inside the 200-tick stall. A variant of `hunterBlockedByHiddenHunterGoesOutBesideIt`, which today puts
       the hidden hunter inside 6 blocks, would do.
     - Keep the 450 bound.
2. **Fix 1(b) of round 1 is only proven in `core`, not in `HunterJob`.** Mutation: `blockerMoves = blocking != null` (any bystander
   counts as a mover again). Both new GameTests stay green: the wall test still reported at tick 430, and the gap test went out at +300.
   - The reason: `wallWithABystanderIsStillUnreachable` puts the NoAI bystander more than 6 blocks from the marker, where ARRIVE is never on the table.
   - `HuntApproachTest.aBystanderNearTheMarkerDoesNotCountAsArrived` pins `choose`, but nothing pins that `HunterJob` passes `mover(...)` and not "anyone".
   - Expected: a GameTest with a walled-off ground and a NoAI villager beside the hunter **within 6 blocks of the marker**. Assert the
     hunter is reported SITE_UNREACHABLE and never goes away.

Advisory (no change required for PASS):
- `mover()` counts any walking villager, not only a hunter (round 1 asked for "an away or walking hunter"). Near a hunting ground that
  is rare. If you keep it, say so in the comment.
- `HeartActions.deposit` (`heart/HeartActions.java:173-174`) passes **two** args to `deposited_settled`, which has three `%s`
  ("... Treasury: %s"). Read from the code: `showResult` does not pad args, so the Heart's "Deposit all" with strikers would show a broken last part. The screenshot's line comes from `Treasury.java`, which passes three args. This predates the branch (M12/M18) and should be ticketed.
- "Paid %s striking workers" and `treasury.strike_notice` ("%s workers are on strike") still read "1 workers". Round 1 scoped plurals to
  emerald and merchant counts. The builder lists this as open in PROGRESS.
- `HuntApproach.MAX_JAM_GIVE_UPS` joins `YIELD_AFTER` as a tunable outside `Balance`.

## Round-1 items
| R1 item | Status | Proof |
|---|---|---|
| 1 Hunter hides an unreachable ground | **Fixed** in logic | (a) `choose` returns UNREACHABLE once `recentGiveUps >= MAX_JAM_GIVE_UPS`; the counter resets on arrival and after `HUNTER_RETRY_TICKS`. (b) ARRIVE needs `touchingMover`. (c) `jamInTheGapIsResolvedWhenTheNeighbourMovesOn`: a reachable ground, the neighbour in the gap, out at +374, never reported. (d) `wallWithABystanderIsStillUnreachable`: SITE_UNREACHABLE at tick 430. `twoHuntersOneGround` bound still 450. See fix 2 for (b) in-world. |
| 2 Emerald singular/plural everywhere | **Fixed** | See the grep below. |
| 3 Sim tier source untested | **Fixed** | `TierGameTests.simCollectorReadsTheVillageTier` calls the real `SimMetricsCollector.collect`: hamlet/0, then village/1/4. |

**Emerald grep** (`grep -n -i 'emerald\|merchant' en_us.json`, then every `%s` treasury, wage and market key traced to its caller):
- Every count goes through `core/Plural`:
  - Overview treasury and wages (`HeartScreen.java:790,792`);
  - the `treasury_short` alert (`WageAlerts.java:25`);
  - deposited, deposited_settled and withdrawn, from both the right-click (`Treasury.java:196,211-212`) and the Heart buttons (`HeartActions.java:174,177,211`);
  - `treasury get/set/add` (`WorkerCommands.java:69-79`);
  - market sold and status, including "1 merchant" (`MarketTexts.java:17,25`).
- No lang value or `HeartTexts.EN` value has a count followed by a fixed noun. `PluralTest.noTextPutsAFixedEmeraldOrMerchantNounAfterACount` scans both.
- The remaining "emeralds" in Java are log lines, or nouns with no count ("You carry no emeralds", the tooltips).
- `goal.dawn_treasury` "%s / %s" is a ratio and is fine.

## Evidence
**Full `scripts/verify.sh` at 36cbdf5: exit 1** (this machine, 4 cores):
- Layer 1 ❌: unit tests green; `build`'s own GameTest run failed `hunter_game_tests_two_hunters_one_ground` at 590 ticks (fix 1).
- Layer 2 ✅ "All 391 required tests passed" (`twoHuntersOneGround` 198 ticks, the wall test at 430, the gap test out at +374). L3 self-test and log-gate self-tests ✅.
- Layer 4 ✅: 42 screenshots, 1 lost wakeup rescued.
  - The lost-wakeup, disconnect-leak, world-load and toast-clear gates are installed.
  - No villageheart model warnings, and the 2-player session is clean. Perf is within the caps (worst case 3062 us, idle ticker 1.0e-3 ms).

**Screenshots I looked at:**
- `0000_toast_cleared.png`: the title screen, no toast.
- `0015_treasury_overview.png`: "Treasury: 0 emeralds", "Wages tomorrow: 3 emeralds", "Treasury short: 0 of 3 emeralds for tomorrow's wages", and
  "1 worker is on strike". The tab bar is clear.
- `0038_market_stockpile.png`: "Market: sold 1 emerald today".

**Mutation checks** (in a separate worktree; each mutation was restored with `git checkout`):

| Fix | Mutation | Result |
|---|---|---|
| 2 plural | `Plural.count` always returns the plural | **red**: `PluralTest.oneIsSingularEverythingElseIsPlural`, `WageAlertsTest.aBillOfOneEmeraldIsSingular`, `MarketTextsTest.oneEmeraldIsSingularAndTheRestArePlural` |
| 1a repeat → unreachable | `HuntApproach.choose`: drop `recentGiveUps < MAX_JAM_GIVE_UPS` | **red**: `HuntApproachTest` (2 tests); `wallWithABystanderIsStillUnreachable` "status OK decision GO_OUT ... on tick 1102" |
| 1a counter | `HunterJob`: `ws.jamGiveUps++` removed | **red**: `wallWithABystanderIsStillUnreachable` (status OK on tick 1102) |
| 1b bystander is not an arrival | `HunterJob`: `blockerMoves = blocking != null` | **green**, unit `HuntApproachTest` only covers `choose`; wall test still reported at tick 430, gap test out at +300 (fix 2) |
| 3 sim tier | `SimMetricsCollector.tierDay` always `TierDay.NONE` | **red**: `simCollectorReadsTheVillageTier` "Expected ... village: was hamlet" |

**`twoHuntersOneGround` alone**, three runs on 36cbdf5 with `-Dvillageheart.hunter.trace=true` (load about 8): 197, 197, 197 ticks.

Filter id note for the next agent: the wall test's id is `hunter_game_tests_wall_with_abystander_is_still_unreachable` (the lone capital
`A` is folded in). `..._with_a_bystander_...` runs 0 tests.
