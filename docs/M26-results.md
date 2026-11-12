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

## Guard pace round G1: reverted

`GUARD_ATTACK_COOLDOWN` 20 -> 14 and `GUARD_CHASE_SPEED` 0.6 -> 0.75, three runs of `raid` and two of `defended` against the re-measure. Raid deaths 8 (lost), 5, 4 against 3 before; defended deaths 3, 2 against 1; siege 0 against 3; walled kills 4 against 9. No clear gain over the run-to-run spread, so it was reverted (no commit carries it).

## `full` with threats on: why there are two scenarios

The harness leaves random raids and sieges off unless a scenario says `"threats": true`. The first `full` runs did not, so 30 days passed without a single threat. With threats on (three runs, same village, then with a wall ring, a golem and four guards) the day-12 random raid hits a Town of 16-19 villagers defended by 2-4 guards at level 1-2 with wooden swords, and kills 15 (4 left), then 12 (3 left). The village regrows from births, but the dead merchant stops the income, the wage bill outruns the empty treasury, everyone strikes and food runs out by day 28 (`zerorun(1 - food.out)` = 3 and 18). Village logic stayed at 0.04-0.38 ms a day, raid days included.

| scenario | threats | result |
|---|---|---|
| `full` | off (the harness default) | PASS, the criteria below |
| `full_raids` | on, plus wall ring, golem, four guards | FAIL (food, after the day-12 raid) - a balance target, recorded, not a gate |

The orchestrator brief for the `full` gate defaulted to "raids and sieges on". That default was not an owner decision, and it makes the gate depend on a random raid that the hard start is meant to lose to. It is reported here for the orchestrator to decide.

## M26-R (orchestrator round) and what was kept

The orchestrator asked for a bounded extra round: scale random raids to the defence, stop the death spiral, and make `full_raids` pass. Results, in order:

- Raid size vs defence: a Town no longer got the full raid for free and `RAID_FULL_GUARDS` went 4 -> 6. In the first `full_raids` run it cut the day-12 raid from 12-15 villager deaths to 3 (pop 15 -> 12). The owner then corrected it ("the raids will kill people if there is no player ... with the player it will be balanced"), so **this was reverted**: raid code, `RaidPlanTest` and `RandomRaidGameTests` are byte-identical to before M26-R.
- Death spiral: **kept**. A merchant never strikes (`core.economy.StrikeRules`, applied in `Workers.status` and `PaidSource`, so it also keeps its work speed): its sales pay every wage, including its own first. L1 `StrikeRulesTest` (including the settlement order) and L2 `MarketGameTests.merchantNeverStrikesAndItsSalesPayTheWages` (replaces `strikeStopsSalesIncomeSettles`, which pinned the old rule: a striking merchant sold nothing and needed a player's emerald). Metric `wages.striking` (1 on a day that ends with a striker) is kept for `zerorun(1 - wages.striking)`.
- It is not a full cure: in the first M26-R run four guards plus eleven workers cost 21-22 emeralds a day against sales of 14-19, the treasury drained by day 21, every other worker struck and the merchant had nothing left to sell. `full_raids` therefore keeps `full`'s two guards.

## Final numbers (head commit below, one run each unless noted; Normal difficulty, seed 1, nothing else running)

| scenario | before (baseline `95e8557`) | final | notes |
|---|---|---|---|
| `full` 30 days (the gate) | looked like PASS, was a collapse (see above) | **PASS**, all 15 assertions | Village day 3, Town by day 10; treasury min 16, 31 on day 30; food days left 2.1-6.3 from day 4, never under 1; no strike in 30 days; 2 deaths (both `in_wall`); population 12 -> 20, 11 employed; village_ms mean 0.178 ms, worst day 0.402, worst p95 0.79 ms |
| `full_raids` 30 days (no player) | not measured | FAIL (recorded target) | day-8 siege (3 guards and 2 villagers lost), day-12 random raid (Town gets the full raid as M25 built it) kills the rest: 17 deaths on day 13, population 0, then food 0 for 18 days. Expected to be harder than real play: the player is part of the defence. village_ms mean 0.141 ms |
| `starter` 10 | PASS | not re-run after the economy rounds: Hamlet has no market, prices or caps, and none of the changed numbers is read there; full verify's L2 covers the units | |
| `defended` 10 | FAIL (4 mob deaths) | PASS (1 run, 1 mob death) in the re-measure; 3 and 2 deaths in two later runs under the reverted guard-pace test | the spread is wide (0..6 deaths); no guard number was changed for good |
| `defended_walled` 10 | FAIL (4 deaths, kills 12) | FAIL (kills 9, deaths 1) | threat spawns outside the ring, guards stay inside |
| `siege` 4 | FAIL (3 villager deaths) | FAIL (3) in the re-measure, PASS (0) in one run with the reverted guard-pace change | fixed 8 zombies in the scenario |
| `raid` 6 | FAIL (4 deaths) | FAIL on `village_ms.mean` day 3 = 1.59 ms only (re-measure): victory, 3 deaths of 12, 11 left, 33 raiders killed | later runs 1.21 and 1.13 ms on the raid day; the casualty bars pass in the re-measure and fail in other runs (4 and 5 deaths) |

Unchanged assertions: no assertion of any scenario was loosened. `full` gained one (`zerorun(1 - food.out)`, replacing a vacuous one).

Final verify: full `scripts/verify.sh` exit 0 at `772e50a` (all 519 GameTests, layer-4 client tests and screenshots, perf), 2026-11-12.

## Deviations (from the plan and `docs/M26-bounds.md`)

- No `sim/tunable-bounds.json`, `sim/tuning.lock`, `check-tuning-diff.sh`, freeze tag, JFR cross-check, `extends`, `targets` or `full_layout`/`full_short`/`full_soak`/`full_crowd` files: the handoff names `docs/M26-bounds.md`, and the time went into the tuning itself. `full` was run alone on the machine; the JFR coverage check was not done, so `village_ms` rests on the section timer only (which double counts, i.e. it over-reports).
- Values outside `docs/M26-bounds.md`'s draft safe range, each with its pinned tests re-pointed to read the value and keep the rules: merchant caps 16/20/24 (the file allowed 6..24 after re-pointing, and "exactly 8, 12, 16" before); `BUILD_TICKS_PER_BLOCK` 70 (file: 10..30; the owner decision overrides it); price lots at or inside +-50% (logs 8, cobblestone and deepslate 16, coal 10, raw iron 6, raw copper 14, leather 4); miner cobble per haul 24/18/18 (+50%).
- `--reload-at` with `full` was not built: standing orders are not restored from a report.
- Harness: standing orders (`when`), `zerorun`, `food.out`, `wages.striking`, `mineshaft`, `hunting_ground`, content words `stonecutter` `fletching_table` `market_stall`; fixes for the site action burying villagers in its ramp ring and `top_up_villagers` spawning inside raised ground. `full` needs `"threats": true` to see raids; its default is off.
- Not reached: tuning `defended_walled` kills and `siege`/`raid` casualties (a guard-pace round, attack cooldown 20 -> 14 and chase speed 0.6 -> 0.75, showed no gain over the run-to-run spread and was reverted); a JFR-checked 3-run F set for `full`; a second `full` run for variance (one clean 30-day run per configuration).
