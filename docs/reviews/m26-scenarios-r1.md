verdict: FAIL

# m26-scenarios review, round 1 (verifier)

Reviewed: `m26-scenarios` at bdcdd1f. `origin/dev` (c7f109b) is already an ancestor, so `git merge origin/dev` reported "Already up to date".
Diff reviewed: `git diff origin/dev...HEAD`. Task: TASK 8 in `docs/HANDOFF_GROK.md`.

The scenario work (items 1 and 3) is right, the full verify is green, and every new harness test goes red on its mutation. One required
fix is in the bounds audit (item 2): a `Balance` value marked tunable is missing. Everything else below is advisory.

## Fix list
1. **`docs/M26-bounds.md` misses `Balance.ROOM_MAX_AIR`** (4096). Its Javadoc reads "Largest room (air cells) a building may enclose
   (DESIGN section 3.2, tunable)" (`core/Balance.java:13-14`).
   - The task asks for every `Balance` value marked tunable. I matched every "tunable" comment in `Balance.java` and every *(tunable)* in
     DESIGN against the file, and this is the only one absent.
   - It is pinned by `RoomProblemTest` (`assertEquals(4096, Balance.ROOM_MAX_AIR)`) and by `RoomScannerTest` (`volume() == 4096`, and the
     text "Room too big (max 4096 air)").
   - The player guide and lang strings print it too.
   - Expected: a row with current value 4096, those pins, safe range "exactly 4096", and a design range. I suggest 2048..8192, bounded by
     the flood-fill cost that `perfTest` caps.

Advisory (no change required for PASS):
- **`CLAIM_RADIUS` is called "untunable"**, while DESIGN §1 and `Balance` both mark it *(tunable)*. "Safe range now: exactly 80" is right,
  because the pins hold it. Calling it untunable is an owner decision, so word it as a recommendation and put it to the owner.
- **The dangling Javadoc at `Balance.java:297`** ("A tool is worn when less than this share ... (tunable)") documents no field: the wear
  rule lives in `ToolWear`. Not this task's code, but the audit could say it found no field there.
- **`defended_walled` behaviour for M26.** In my run, 2 scripted zombies got inside the ring on day 5, as in the builder's runs 1 and 5.
  Guards still walk out of the gate to fight. The death log also lists "Gilda" twice: day 5 zombified inside, day 6 killed outside. That is
  either a re-hire with the same name or a double count. M26 should check which before trusting `deaths.mobs`.
- **The `villageSiegeSuppressedInsideClaim` failure message is inverted.** Under my mutation it read "player inside: the real tick did not
  run", when the real tick did run. Reword it to "the real tick ran while the player was inside".

## Evidence
**Full `scripts/verify.sh`: exit 0** (bdcdd1f, this machine):
- Layer 1 green. Layer 2: "All 388 required tests passed". The L3 self-test and the log gates are green.
- Layer 4 green, with all three NetworkSynchronizer/world-load fixes installed. The model log and the 2-player session are clean.
- Perf is within the caps (worst case 3781 us).

**Item 1, `defended_walled` tests the wall:**
- The assertions are byte-for-byte those of `origin/dev`. Comparing the parsed `assertions` arrays of `defended_walled.json` before and after gives no diff.
- `DefendedScenarioTest` also proves the action list is `defended`'s plus `wall_ring`, `lamps` and `move_in`.
- `move_in`, after the wall and the lamps:
  - releases every villager's home, job and meeting POIs;
  - beds every villager in the claim inside the ring, adding beds on open grass when the houses are short;
  - puts a bell inside;
  - removes beds, job blocks and bells outside the ring within the claim.
- `night_spawn` with `outside` now draws from `OutsideBand` (3-8 blocks Chebyshev outside the ring rectangle, all four sides). The wall is
  on the rectangle's edge (`WallRing.side`), so that is 3-8 blocks outside the wall.
- **My single unseeded run** (`scripts/simulate.sh defended_walled`, 10 days) exits 1, which is an assertion FAIL and not an error.
  - Log: `move_in: 8 villagers inside, 8 beds (6 already, 2 added), 13 outside poi blocks removed, bell 660, 70, 864`, then every night
    `night_spawn: 3 x zombie just outside the wall`.
  - Passing: `claimed`, `employed == 4`, `strikers`, `not_ticking`, `heart_distance`, `starve` and `village_ms`.
  - Failing: `sum(deaths.mobs)` is 3 (bar 1), `sum(work.guard.kills)` is 4 (bar 15), and `duty_samples` was 59 on day 5 (bar 100).
  - All three deaths were guards (Wilmund and Gilda on day 5, "Gilda" on day 6). No unhired villager died, and none died in the old village.
  - That matches the builder's five runs (0 of 5, kills 9/6/8/2/3, deaths only among hired workers who were outside). The failing bars are
    balance work for M26, as the task expected; the assertions were rightly left alone.

**Item 3, `defended_nosiege`:**
- It equals `defended` with `"village_siege": false`: the same actions and assertions, which `defendedNoSiegeIsDefendedWithTheSiegeOff` asserts.
- The flag is harness-only: `SiegeGate` wraps `VillageSiege` in the level's spawner list via reflection, in `src/gametest`. The main mod is untouched.
- The gate skips the siege's tick only while a living, non-spectator player is within `CLAIM_RADIUS` of the claim centre (the bell, then
  the Heart). Natural spawning is unchanged.
- The builder reports 5 runs at 2 of 5 PASS (kills 21/17/10/23/21). I did not re-run it.

**Item 2, the bounds audit:**
- The ranges are sensible. "Safe range now" is "exactly the current value" wherever a unit test pins the literal. I spot-checked pins:
  - `ReasonTest` max 80; `ToolRulesTest.stonePaceIsUnchanged`; `FoodMathTest` `daysLeft(400,4,0)==10`; `TierGoalsTest`;
    `GuardGameTests` `GUARD_BASE_DAMAGE + 1.0`.
  - `BuilderPlannerTest` follows `BUILD_TICKS_PER_BLOCK`.
  - `COOK_BATCH` and `MERCHANT_SALE_UNITS` have no literal test.
- The "design range after pins are re-pointed" columns are about ±50%, keep the orderings (level 2 below level 3, merchant caps rising and
  below the village cap, and so on), and agree with the plan's 3.8.2.
- The stale plan numbers (stone ×1.25, base 50, haul 2500) are correctly called out against the current `Balance`.
- No `Balance` value changed: the diff has no `Balance.java`.

**Mutation checks** (each restored with `git checkout`):

| Test | Mutation | Result |
|---|---|---|
| `nightSpawnOutsideStandsOutsideTheRing` | `nightSpawn` always uses the old 12..radius disc | **red**: "in the 3..8 band, at (-15, -2, -4)" |
| `villageSiegeSuppressedInsideClaim` | `Gated.tick` always calls the real siege | **red**: state SIEGE_DONE with the player inside |
| `OutsideBandTest`, `ScenarioParserTest.villageSiegeFlag`, `DefendedScenarioTest` | (by inspection) each asserts the new field or placement directly | — |
