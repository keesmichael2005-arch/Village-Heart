verdict: FAIL

# backlog-small review, round 1 (verifier)

Reviewed: `backlog-small` at f291ba6. `origin/dev` (c7f109b) was already merged, so `git merge origin/dev` reported "Already up to date".
Diff reviewed: `git diff origin/dev...HEAD`. Task: TASK 6 in `docs/HANDOFF_GROK.md`.

The full `scripts/verify.sh` exits 0. All four new tests go red without their fix, and no bound was loosened. Three things block a PASS:
- the hunter fix hides a genuinely unreachable ground;
- emerald counts are still wrong outside the market;
- the sim collector's new tier source has no test.

## Fix list
1. **Hunter: a truly unreachable ground is no longer reported when any villager is close by** (`worker/hunt/HunterJob.java` `goOut`, `core/hunt/HuntApproach.choose`).
   - `blockingVillager` now matches **any** living villager within 1 block of the hunter's box. Before, it matched only an away hunter within 0.4.
   - With a neighbour present, `choose(..., walkFailed=true)` returns YIELD, never UNREACHABLE. It returns ARRIVE when the hunter is within 6 blocks of the marker.
   - So a hunter whose ground is cut off goes back to walking, without the SITE_UNREACHABLE status and without the 600-tick back-off, for as
     long as someone stands next to it. A busy Lodge or a crowded village path makes that common.
   - The new GameTest locks this behaviour in. `jamIsNotAnUnreachableGround` builds a 3-high stone wall across the whole yard at z=12. The
     ground is at (15, 0, 17), so it is cut off. The test asserts the hunter is NOT SITE_UNREACHABLE; my run logged `status OK decision GO_OUT`.
   - That test proves "a bystander suppresses the unreachable report". It does not prove "a jam is resolved".
   - Expected:
     - (a) A YIELD that repeats (for example, a second give-up in a row, or one after the step-aside, still touching nobody who is walking) falls through to UNREACHABLE.
     - (b) Restrict ARRIVE-by-touch to the case the flake needed: an **away** or walking hunter near the marker. Do not count a random bystander.
     - (c) Replace or extend the jam test so the ground is reachable and only a neighbour is in the way, and assert the hunter gets out.
     - (d) Add a test that a walled-off ground with a NoAI villager beside the hunter still ends in SITE_UNREACHABLE.
   - Keep `twoHuntersOneGround` at 450.
2. **Singular/plural is only fixed for the market line.** TASK 6.1 says "wherever emerald counts are shown". These still print "1 emeralds":
   - `overview.treasury` "Treasury: %s emeralds", `overview.wages` "Wages tomorrow: %s emeralds", and `alert.treasury_short`. One level-1
     worker makes the Overview read "Wages tomorrow: 1 emeralds".
   - `message.villageheart.treasury.deposited`, `deposited_settled`, `withdrawn`, `get`, `set` and `add` (`en_us.json:364-375`). The Heart's
     "Withdraw 1" button produces "Withdrew 1 emeralds".
   - The new market status line has the same bug for merchants. `MarketTextsTest` even pins it:
     `"Market: sold 5 emeralds today, 1 merchants, ..."`.
   - Expected: one helper (`MarketTexts` or a small `core` `Plural`/`EmeraldTexts`) used by every emerald count, plus "1 merchant". Give each
     a unit test that is red on the old string.
3. **The sim tier source is untested.** `ScenarioFilesTest.reportCarriesVillageTierAndFedStreak` builds a `TierDay` by hand. If
   `SimMetricsCollector.tierDay` returned `TierDay.NONE`, every test would stay green: nothing in `src/gametest` or `sim/scenarios` reads
   `village.tier` or `tier.fed_streak`.
   - Expected: an L2 check, or a cheap L3 assertion in an existing scenario.
     - Option one: a GameTest that sets a village to VILLAGE with a fed streak and reads `SimMetricsCollector`'s day.
     - Option two: an assertion such as `village.tier == 0 on last day` in `starter`, which per BACKLOG stays Hamlet.
   - Either must go red on that mutation.

Advisory:
- `HuntApproach.YIELD_AFTER` (30) and the 1.0 / 0.25 inflate in `blockingVillager` are tunables that live outside `Balance`.
- The step-aside ignores NoAI neighbours in `yieldNow`, but the give-up branch calls `stepAside` for them anyway. That is fine, but say so in
  the comment.

## Evidence
**Full `scripts/verify.sh`: exit 0** (f291ba6, this machine, 4 cores):
- Layer 1 green. Layer 2: "All 386 required tests passed". The L3 self-test and the log gates are green.
- Layer 4 green. The new `SCREENSHOT_TOAST_CLEAR installed` gate passes. The model log and the 2-player session are clean.
- Perf is within the caps (worst case 3554 us, 16x16x8 3351 us, idle ticker 9.5e-4 ms).
- In that run, `twoHuntersOneGround` had both hunters out after 197 ticks.

**Mutation checks** (each mutation was restored with `git checkout`; the tree is clean):

| Fix | Mutation | Result |
|---|---|---|
| 1 market text | `MarketTexts`: `== 1` branches disabled | `MarketTextsTest.oneEmeraldIsSingularAndTheRestArePlural` **red** |
| 3 sim tier | `MetricsJson.writeDay` drops `tier` | `ScenarioFilesTest.reportCarriesVillageTierAndFedStreak` and `MetricsJsonTest.pinnedKeys` **red** |
| 3 sim tier | `SimMetricsCollector.tierDay` → NONE | no test reads it (by inspection, grep of `src/gametest` and `sim/scenarios`): **would stay green**, fix 3 above |
| 4 hunter | `HunterJob.java` reverted to `origin/dev` | `jamIsNotAnUnreachableGround` **red**: "reported SITE_UNREACHABLE ... on tick 287" |
| 2 toasts | `ScreenshotToastClear` removed from the mixins json, `-Dvillageheart.l4.only=toasts` | **red**: "a vanilla toast was still up when the screenshot was taken". The mutant's `toast_cleared.png` shows the "New Recipe(s) Unlocked" toast top right. |

**Repeated runs of `twoHuntersOneGround`** (alone, with 3 busy-loop CPU hogs, load about 6.5):
- With the fix: 5 of 5 pass, 197 ticks each.
- With the old `HunterJob`: 3 of 3 pass, at 265, 265 and 255 ticks.
- So a lone run does not reproduce the 970-tick failure, either before or after the fix. The repeats show the fix does not slow the test.
  They do not prove the flake is gone.
- The root-cause argument stands on the logged +460 SITE_UNREACHABLE and on the arithmetic: a 200-tick stall plus the 600-tick retry
  gives about 970. The jam test proves the retry is no longer armed. That is accepted, with fix 1's caveat about what it also suppresses.

**Bound:** `twoHuntersOneGround` still asserts `<= 450`. The diff only adds a log line around it, and no other bound changed.

**Screenshots I looked at** (from my full run):
- `0000_toast_cleared.png`: the title screen, no toast in the corner.
- `0015_treasury_overview.png`: the full tab bar, with Stockpile and Ranks visible and no toast.
- `0038_market_stockpile.png`: green "Market: sold 1 emerald today", tabs clear.
