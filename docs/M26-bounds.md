# M26 tunable bounds and test audit

Written by TASK 8 on `m26-scenarios`. **No `Balance` value was changed.** This is the audit the M26 plan's section 3.9.4 asks the orchestrator to approve before anyone tunes (decision D-M26-3).

The plan names the machine-readable file `sim/tunable-bounds.json`, plus `sim/tuning.lock` and `scripts/check-tuning-diff.sh`. The handoff for this task names `docs/M26-bounds.md`, and TASK 11 is told to read this file. The JSON, the lock and the diff check are TASK 11. They are not in this change.

"Safe range" is the range that keeps **today's tests green**. A pin is a test that asserts the current number (or a result that only that number produces). Changing a pinned value fails that test. Re-pointing a pin to read `Balance` is a later freeze commit, and it is not done here. "Design range" is what the plan allows once that pin is gone: about ±50% for ratios, and a small list for small integers. A design range is not a licence to edit a pin.

The plan's table was written against an older `Balance`. On `dev` now, tool speed is vanilla mining speed / 2 (wood 1, stone 2, copper 2.5, gold 6, iron 3, diamond 4, netherite 4.5), `BASE_TICKS_PER_LOG` is 80 (a stone axe still fells a log in 40 ticks, 2 seconds) and `MINER_HAUL_UNITS` is 4000 (a stone pickaxe still hauls in 2000 ticks). The rows below use these current values.

## How to read a row

- **follow** — the test reads the `Balance` field. A new value still passes.
- **pin** — the test asserts the number itself. Safe range is the current value until the pin is re-pointed.
- A test can be both: it reads `Balance` and also asserts the old literal. The literal is what blocks a change.

## DESIGN *(tunable)* marks

| Constant | Current | Tests | Safe range now | Design range after pins are re-pointed |
|---|---|---|---|---|
| `CLAIM_RADIUS` | 80 | **pin** `ReasonTest` (the text "max 80"); **pin** `SimAreaTest` (`staticRadiusChunks` must be 6, which is true for 65..80, and the message says "80-block"); **follow** `SimAreaTest` for the edge walk | exactly 80 | untunable. It is a design constant: every scenario's layout and the force-loaded square hang off it |
| `BLANK_MARKER_YIELD` | 1 | **follow** `MarkerGameTests` and `AssetGenerator`; **pin** `GeneratedAssetsTest` (the committed `recipe/blank_marker.json` has `"count": 1`, and the generator writes `Balance`) | exactly 1 | untunable. Crafting yield; a change also needs `./gradlew genAssets` |
| `WORK_START` | 1000 | **follow** `DayScheduleTest` | 500..1500, and strictly below `WORK_END` | same. Last resort: it moves every job's day |
| `WORK_END` | 11000 | **follow** `DayScheduleTest`. `COOK_DONE_BY` and `SELF_CRAFT_LAST_FETCH` are derived from it | 10000..11500, and `WORK_START < WORK_END < SLEEP_START` | same. Last resort |
| `SLEEP_START` | 12000 | **follow** `DayScheduleTest` | 11500..13000, and above `WORK_END` | same. Last resort. Guards use 12000 as `NIGHT_DUTY_START` separately; that one is not this field |
| `DINNER_TICK` | 11000 | **follow** `DailyClockTest` | keep equal to `WORK_END` (dinner is the end of the work day). 10000..11500 if the day shape moves with it | same. Last resort |
| `TOOL_SPEED` | vanilla/2: 1, 2, 2.5, 6, 3, 4, 4.5 (wood, stone, copper, gold, iron, diamond, netherite) | **pin** `ToolRulesTest.everyMultiplierIsTheVanillaMiningSpeedHalved` (each tier is that vanilla speed / 2, and iron/stone is 1.5); **pin** `ToolRulesTest.multipliersFollowTheBalanceTable` and `BalanceLevelsTest` (wood 1, stone 2, iron 3, diamond 4); **follow** the same tests' "reads `Balance.TOOL_SPEED`" lines | exactly these seven numbers | untunable. Human decision 2026-11-09: the ratios are vanilla's. The plan's older table (stone 1.25) is stale. M15 D1's iron/stone floor of 1.2 is already met (the ratio is 1.5) |
| `LEVEL_2_XP` | 100 | **pin** `BalanceLevelsTest` (`== 100`); **pin** `TierRulesTest` and `LevelingTest` (`levelFromXp(99) == 1`, `levelFromXp(100) == 2`) | exactly 100 | 50..150, and strictly below `LEVEL_3_XP` |
| `LEVEL_3_XP` | 300 | **pin** `BalanceLevelsTest` (`== 300`); **pin** `TierRulesTest` and `LevelingTest` (`levelFromXp(300) == 3`, `levelFor(300, …)`) | exactly 300 | 200..450, and strictly above `LEVEL_2_XP`. `MAX_BANKED_XP` is this field |
| `LEVEL_SPEED` | 1, 1.25, 1.5 | **pin** `BalanceLevelsTest` (`assertArrayEquals`); **pin** `CookPaceTest` (level 2 finishes an item at tick 100, which is 125/1.25) | exactly 1, 1.25, 1.5 | each ±50% (0.5..1.5, 0.6..1.9, 0.75..2.25) and strictly rising |
| `milliXpPerUnit` | LOG 1000, CROP 1000, COOK 1000, MINED 1000, HUNT 5000, KILL 5000, SPAR 1000, SMITH 2000, BLOCK 200, TRADE 2000 | **pin** `BalanceLevelsTest` for LOG 1000, CROP 1000, BLOCK 200; **follow** the `> 0` check for every unit | LOG, CROP and BLOCK exactly those numbers. The other seven: any positive integer | each ±50% of the current number, and still a positive integer. Order is `WorkUnit.ordinal()` and must not be reordered |
| `BASE_TICKS_PER_LOG` | 80 | **follow** `StrikersProduceNothingTest`, `WorkSpeedTest` (they build a clock from it); **pin** `ToolRulesTest.stonePaceIsUnchanged` (`80 / stone == 40`) | exactly 80 while stone stays 2 | the stone-axe pace stays 40 ticks (2 seconds). If stone is ever not 2, the base moves so that base/stone stays 40. Do not "fix" this back to the plan's 50 |
| `MINER_HAUL_UNITS` | 4000 | **follow** `AwayCheckTest`, `AwayProgressTest`; **pin** `ToolRulesTest.stonePaceIsUnchanged` (`4000 / stone == 2000`) | exactly 4000 while stone stays 2 | the stone-pickaxe haul stays 2000 ticks. The plan's 2500 is the pre-vanilla-speed number. Haul **contents** are `HaulTable`, below |
| `GUARD_LEVEL_HEALTH` | 20, 30, 40 | **pin** `GuardPerksTest` (20/30/40, and level 4 clamps to 40); **follow** the same class's second test; **pin** L2 `GuardGameTests.sparringGivesXpLevelTwoHealth30` (health 20 then 30) | exactly 20, 30, 40 | 10..30, 15..45, 20..60, strictly rising. Level 1 stays 20 unless the owner drops the "vanilla 20 at hire" rule |
| `GUARD_LEVEL_DAMAGE` | 0, 1, 2 | **pin** `GuardPerksTest` (0/1/2, and level 9 clamps to 2); **pin** L2 `GuardGameTests` (`GUARD_BASE_DAMAGE + 1.0` at level 2) | exactly 0, 1, 2 | each in {0, 1, 2, 3}, strictly non-decreasing. The L2 `+ 1.0` has to be re-pointed before level 2 can leave 1 |
| `GUARD_BASE_DAMAGE` | 1.0 | **follow** L2 `GuardGameTests` (it adds the pinned +1 on top) | 0.5..2.0 | same. Not a DESIGN line of its own; it is the attack attribute the bonus sits on |
| `FOOD_RESERVE_DAYS` | 3 | **pin** `ReservesTest` (`DEFAULT.foodDays() == 3`) | exactly 3 | 1..7. The Stockpile tab already clamps with `MAX_FOOD_RESERVE_DAYS` 30 |
| `ITEM_RESERVE` | 64 | **pin** `ReservesTest` (`DEFAULT.itemReserve() == 64`) | exactly 64 | 32..128, a multiple of 16 (`ITEM_RESERVE_STEP`) |
| `WAGES` (via `wageForLevel`) | 1, 1, 2 | **pin** `WageRulesTest` (levels 1/2/3 are 1/1/2, and the bill examples 4, 3, 6, 3); **follow** the loop that also reads `wageForLevel` | exactly 1, 1, 2 | each in {1, 2, 3}. The plan called this the last economy lever |

Smith spare targets and the golem cost are marked tunable in DESIGN and are **not fields of `Balance` on `dev`** (M19 is not what this tree has). There is nothing to bound until those fields exist. Do not invent them here.

## Not marked in DESIGN, but the plan treats them as tunable (3.8.2)

| Constant | Current | Tests | Safe range now | Design range after pins are re-pointed |
|---|---|---|---|---|
| `VILLAGE_POPULATION`, `VILLAGE_WORKERS`, `VILLAGE_FED_DAYS` | 6, 3, 3 | **pin** `TierGoalsTest` (a village input of 6/3 and a fed streak of 3 is exactly "all met"; 5, 2 or a streak of 2 blocks that one goal) | exactly 6, 3, 3 | population 4..10, workers 2..6, fed days 2..5. These decide which day a run reaches Village |
| `TOWN_POPULATION`, `TOWN_WORKERS`, `TOWN_GUARDS`, `TOWN_FED_DAYS`, `TOWN_DAWN_TREASURY` | 12, 8, 2, 7, 32 | **pin** `TierGoalsTest` (12/8/2, streak 7, treasury 32 is exactly "all met"; 11, 7, 1, streak 6 or treasury 31 blocks that one goal) | exactly 12, 8, 2, 7, 32 | population 8..18, workers 5..12, guards 1..4, fed days 5..10, treasury 16..64 |
| `ADULT_NUTRITION`, `CHILD_NUTRITION` | 10, 5 | **pin** `FoodMathTest` (`daysLeft(400, 4, 0) == 10` and `daysLeft(400, 4, 2) == 8`, which is 4×10 and 4×10+2×5) | exactly 10 and 5 | adult 6..16, child half of adult |
| `HUNGER_SPEED` | 1, 0.5, 0 | **pin** `NeedsRulesTest` (1, 0.5, 0, and 0.375 = 0.5×0.75); **follow** `HUNGER_SPEED[1]` | exactly 1, 0.5, 0 | index 0 stays 1. Index 1 in 0.25..0.75. Index 2 stays 0 (two missed dinners stop work; that is the rule, not a pace) |
| `UNHOUSED_SPEED` | 0.75 | **pin** `NeedsRulesTest` (0.75, and 0.375 with one hungry day); **follow** the `Balance.UNHOUSED_SPEED` line | exactly 0.75 | 0.5..1.0 |
| `HUNTER_TRIPS_PER_DAY` | 2, 3, 3 | **pin** `HunterPlannerTest` (level 1 and the clamp below 1 are 2; level 2 and 9 are 3) | exactly 2, 3, 3 | each in {1, 2, 3, 4}, non-decreasing |
| `HUNT_MEAT_MIN` / `MAX` | 2 / 4 | **pin** `HuntTableTest` (every roll is in 2..4, the mean is 3, an arrow adds exactly 1) | exactly 2 and 4 | min 1..3, max min+1..min+3, and the arrow bonus stays 1 unless `HuntTableTest` is re-pointed |
| `HUNT_LEATHER_MIN` / `MAX`, `HUNT_FEATHER_MIN` / `MAX` | 1 / 2, 1 / 2 | **pin** `HuntTableTest` (the roll histogram) | exactly these | each min in {0, 1, 2}, max in min..min+2 |
| `HUNT_HIDE_CHANCE` | 0.5 | **pin** `HuntTableTest` (the 0.5 draw) | exactly 0.5 | 0.25..0.75 |
| `HUNT_ARROW_BONUS_MEAT` | 1 | **pin** `HuntTableTest` (the arrow adds one meat and nothing else) | exactly 1 | {0, 1, 2} |
| `COOK_TICKS_PER_ITEM` | 125 | **pin** `CookPaceTest` (8 items in 1000 ticks, the first three at 125/250/375; hungry doubles that) | exactly 125 | 60..190, so a level-1 cook stays near 8 items an hour (1000/value items per 1000 ticks) |
| `COOK_BATCH` | 8 | no test asserts the literal | 4..16 | same |
| `MERCHANT_SALE_UNITS` | 600 | no test asserts the literal | 300..900 | same. 600 ticks at speed 1 is the current sale |
| `MERCHANT_DAILY_CAP` | 8, 12, 16 | **pin** `MarketRulesTest` (8/12/16, the clamps, and "stop at 8, sell at 8 when level 2, stop at 12, stop at 32 for the village") | exactly 8, 12, 16 | each in 6..24, strictly rising, and each below `MARKET_VILLAGE_DAILY_CAP` |
| `MARKET_VILLAGE_DAILY_CAP` | 32 | **pin** `MarketRulesTest.villageCap` (sell at 31, stop at 32) | exactly 32 | 16..48, and at least the level-3 merchant cap |
| `BUILD_TICKS_PER_BLOCK` | 20 | **follow** `BuilderPlannerTest` (a clock built from it; no literal 20) | 10..30 | same. 20 is one block a second at speed 1 |
| `FARM_TICKS_HARVEST`, `FARM_TICKS_BONEMEAL` | 20, 20 | **pin** `WorkSpeedTest` (a harvest takes 20 ticks with no hoe and 16 with the ×1.25 hoe) | harvest exactly 20. Bonemeal has no literal of its own and can follow harvest | harvest 10..30. The 16-tick line also pins `HOE_BONUS` at 1.25 |
| `FARM_TICKS_PLANT`, `FARM_TICKS_COMPOST` | 10, 10 | no literal found | 5..20 | same |
| `HOE_BONUS` | 1.25 | **pin** `WorkSpeedTest` (16 ticks = 20/1.25) | exactly 1.25 | 1.0..1.5. DESIGN states it; it is not in the *(tunable)* sentence, so leave it alone unless the owner asks |
| `SELF_WOODEN_TICKS`, `SELF_STONE_TICKS` | 2400, 4800 | **pin** `ToolRecipesTest` (`== 2400`, `== 4800`) | exactly 2400 and 4800 | 1200..3600 and 2400..7200, stone greater than wood. DESIGN calls the self-craft time tunable |

`GROWTH_MIN_FOOD_DAYS` is not in `Balance` on `dev` (M23 is not merged). No bound until the field exists.

## Tables that are tunable and are not `Balance` fields

`HaulTable` (DESIGN 5.3) and `PriceTable` (DESIGN 5.9) hold the numbers as literals. The plan's step 8 moves them into `Balance`. That move is not this task.

- **HaulTable**, pinned by `HaulTableTest` to the DESIGN rows. HIGH: 16 cobble, 2 coal, 1 raw copper, 30% 1 raw iron. MID: 12 cobble, 2 coal, 2 raw copper, 2 raw iron, 20% 4 redstone, 10% 4 lapis. DEEP: 12 cobbled deepslate, 1 coal, 3 raw iron, 30% 4 redstone, 20% 4 lapis, 20% 1 raw gold, 2% 1 diamond from an iron pickaxe up. Safe range: these exact rows. `HaulRollStatsTest` checks the means, so a count change fails it too.
- **PriceTable**, pinned by `PriceTableTest`. Logs 16, wheat 20, carrot 22, potato 26, beetroot 15, cobble and deepslate 32, coal 15, raw iron 8, raw copper 20, iron ingot 4, leather 6, each cooked meat 10. Safe range: these exact lots. Design range after the pin is re-pointed: each lot ±50%, still a positive integer.

## What this file does not do

It does not change a value, re-point a test, add `sim/tunable-bounds.json`, or add the freeze tag. A tuner who needs a pinned value to move stops and asks, which is the plan's stop rule.
