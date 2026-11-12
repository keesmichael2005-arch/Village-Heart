# M26 results (balance pass)

Builder: Sonnet cloud session, branch `m26`. Seed 1, normal difficulty. Villager AI is not seeded, so every number is one run, not a distribution.
Raw reports: the builder's scratchpad (`m26-builder/`), not committed.

## Baseline (before any tuning), code at `95e8557` (m25-full + dev + the `full` scenario and its harness additions)

The `starter`, `defended` and `defended_walled` runs shared the machine with a second short smoke run (timing numbers there are informational).

| scenario | days | result | key numbers | deaths |
|---|---|---|---|---|
| starter | 10 | PASS | village_ms mean 0.06 | 0 |
| defended | 10 | FAIL | deaths.mobs 4 (bar <= 1), kills 23 (>= 15 ok), guard.duty_samples day 2 = 49 (< 100) | d2 guard zombified, d2 guard (zombie villager), d2 farmer, d2 unhired |
| defended_walled | 10 | FAIL | deaths.mobs 4, kills 12 (< 15), duty_samples day 5 = 96 | d2, d5 x2, d8: all guards |
| siege | 4 | FAIL | siege.villager_deaths 3 (bar <= 2) | 3 unhired, night of day 2 |
| raid | 6 | FAIL | villager_deaths 4 (bar <= 3.25), at_end 9 (bar 9.75); victory, 0 losses | 3 by vindicators (unhired), 1 guard by a ravager |

## `full` (30 days) tuning rounds (interim; one run each, nothing in the world is seeded but the map)

Baseline `full` at `95e8557` only looked like a PASS: its food assertion read `food.days_left`, a decaying ratio that is never exactly 0. The assertion is now `zerorun(1 - food.out) <= 1` (`food.out` = 1 when under one day of food is left). Re-read, the baseline run collapsed: treasury 0 from day 8, every worker on strike from day 13, no food from day 16 (all 19 villagers hungry), Village on day 3.

| round | change (before -> after) | Village / Town day | treasury | strikes | food out (days with < 1 day left) | result |
|---|---|---|---|---|---|---|
| B0 | none | 3 / - | 0 from day 8 | all 10 from day 13 | 15 (days 16-30) | FAIL (food) |
| H1 | MERCHANT_DAILY_CAP {8,12,16} -> {12,16,20} | 3 / - | 0 from day 13 | all 10 from day 14 | 15 | FAIL (food, and day-1 loss) |
| H2 | PriceTable lots: logs 16 -> 10, cobblestone and deepslate 32 -> 20 | 3 / 13 | peak 44, 0 from day 20 | all 11 from day 21 | 8 | FAIL (food) |
| H3 | lots: logs 10 -> 8, cobble/deepslate 20 -> 16, coal 15 -> 10, raw iron 8 -> 6, raw copper 20 -> 14, leather 6 -> 4 | 3 / 11 | peak 45, 0 from day 21 | 11 from day 22 | 8 | FAIL (food) |
| H4 | MERCHANT_DAILY_CAP -> {16,20,24}; miner cobble per haul x1.5 (HIGH 16 -> 24, MID 12 -> 18, DEEP 12 -> 18) | 3 / 8 | 20 at the low point, 56-64 from day 8 | none | 0 | **PASS (all 15 assertions)**; sold 14-24 a day against a bill of about 20; mean village_ms 0.154 |

Why it collapses: the wage bill rises from 10 to about 20 emeralds a day when the staff reach level 3 (wage 2) and the blacksmith is hired, while sales stay at 12-17 a day. A treasury at 0 at dawn strikes the merchant too, so income stops and it cannot recover. Re-pointed tests (each now reads the tuned value, rules unchanged): `MarketRulesTest`, `MarketPlannerTest`, `PriceTableTest`, `HaulTableTest`, and `MarketGameTests` (cap, reload, roadmap, reserved stock, two merchants, short withdraw, strike, full treasury, reserve screen).

Harness fixes found on the way (no game logic): the `site` action buried villagers standing in its ramp ring (now relocated); `top_up_villagers` could spawn inside raised ground (now falls back to the surface); a standing order whose building is still re-checked on the tier-up tick is retried every 100 ticks instead of failing.

### `full` after H4: result against each criterion (one 30-day run, alone on the machine, code `08c5526`, report `full0..h4` in the builder scratchpad)

| criterion | result |
|---|---|
| reaches Village tier | yes, day 3 (Hamlet 1-2); Town on day 8 (stretch goal, not required) |
| food days never 0 for more than 1 day in a row | `zerorun(1 - food.out)` = 0: food days left stayed between 2.4 and 5.8 from day 4 on; 0 starvation deaths |
| treasury never negative | min 20 (L1 PayRunTest proves the property: the record rejects a negative purse); no strikes in 30 days |
| village logic < 1 ms per tick | mean 0.154 ms over the run, every day's mean below 1 (max day 0.19); p95 about 0.5 ms |
| deaths | 5 villagers, all `in_wall` (suffocation), none to mobs or hunger: BACKLOG, see below |

Population grew from 12 to 20 (births), 11 workers employed, 11 jobs worked.

## Builder pace (owner decision, binding)

`BUILD_TICKS_PER_BLOCK` 20 -> 70: one block per 3.5 s at level 1 (2.8 s at level 2, 2.3 s at level 3). This is outside the 10..30 range of `docs/M26-bounds.md` on purpose: the owner decision (2026-11-12 handoff: "about 1 block per 3-4 seconds at level 1") overrides that audit's draft range. Tests that carried the old pace: `BuilderPlannerTest` (now 2000 / the Balance value, plus an owner-range check of 60..80) and four `BuildGameTests` time budgets (`farmPlotBuilt` two days instead of one, `playerBlockAppearsMidBuild` and `twoBuildersTwoBuilds` 70 000 ticks, the claim-order check stops at the end of the work day). DESIGN section 5.x and the player guide say 3.5 s now. The sim `build` action advances a job in one go, so no scenario's timing changed.

## Threat scenarios after the economy rounds (M25 merged, code `9c2d444` + builder pace)

| scenario | result | notes |
|---|---|---|
| defended 10 | PASS | 1 guard lost (scripted zombie, day 5), 0 other failures |
| defended_walled 10 | FAIL | deaths 1 (ok), kills 9 (needs 15): the threat spawns outside the ring and the guards stay inside |
| siege 4 | FAIL | 3 unhired villagers lost (bar 2): the siege is a fixed 8 zombies in the scenario |
| raid 6 | FAIL | only on `village_ms.mean` day 3 = 1.59 ms (bar 1): victory, 3 deaths of 12, 11 left, 33 raiders killed |

## Guard pace round (G1): GUARD_ATTACK_COOLDOWN 20 -> 14, GUARD_CHASE_SPEED 0.6 -> 0.75

Both are unpinned (no test reads them). One run each, so the spread matters more than any single number.

| scenario | before (re-measure above) | G1 run 1 |
|---|---|---|
| defended | PASS (1 death) | FAIL: 3 deaths (2 guards at night 14402 and 14297, one converted), duty_samples day 6 = 47 |
| defended_walled | FAIL kills 9, 1 death | FAIL kills 4, 0 deaths |
| siege | FAIL 3 deaths | PASS 0 deaths |
| raid | FAIL timing only, 3 deaths | FAIL: lost the raid, 8 villager deaths, 5 left, day-3 village_ms 1.21 |
