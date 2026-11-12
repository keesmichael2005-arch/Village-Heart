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
| H4 | MERCHANT_DAILY_CAP -> {16,20,24}; miner cobble per haul x1.5 (HIGH 16 -> 24, MID 12 -> 18, DEEP 12 -> 18) | (running) | | | | |

Why it collapses: the wage bill rises from 10 to about 20 emeralds a day when the staff reach level 3 (wage 2) and the blacksmith is hired, while sales stay at 12-17 a day. A treasury at 0 at dawn strikes the merchant too, so income stops and it cannot recover. Re-pointed tests (each now reads the tuned value, rules unchanged): `MarketRulesTest`, `MarketPlannerTest`, `PriceTableTest`, `HaulTableTest`, and `MarketGameTests` (cap, reload, roadmap, reserved stock, two merchants, short withdraw, strike, full treasury, reserve screen).

Harness fixes found on the way (no game logic): the `site` action buried villagers standing in its ramp ring (now relocated); `top_up_villagers` could spawn inside raised ground (now falls back to the surface); a standing order whose building is still re-checked on the tier-up tick is retried every 100 ticks instead of failing.
