# Roadmap

Each milestone lists acceptance tests. A milestone is done only when its tests exist and pass in `scripts/verify.sh`.

## M0: scaffold ✅
- [x] Village Heart block, recipe, texture, lang
- [x] Claim works only within 16 blocks of a bell (unit + GameTest)
- [x] Client test places the block and takes a screenshot
- [x] CI runs every layer

Milestones below are in dependency order, and each is about one agent work session. Design reference: `docs/DESIGN.md`
(§ numbers below). Test layer tags:
**L1** unit (`core/`, JUnit) · **L2** GameTest (headless server) · **L3** long simulation (seeded
world, metrics JSON) · **L4** client test with a screenshot that the agent has looked at.
Times are in-game: 1 day = 24000 ticks. *(tunable)* numbers come from the core balance class, so tests read them
from there instead of hard-coding them.

## M1: Village state, persistence, one per world ✅
- [x] `VillageState` saved data: Heart position, owner UUID, ranks map, treasury, tier, building/worker registries (empty for now)
- [x] The second Heart in a world is refused (the M0 TODO). The Owner breaking the Heart releases the claim. Others cannot break it
- **L1**: `ClaimRules` refuses when a claim exists. `VillageState` round-trips through its serialisable form with no data loss
- **L2**: place Heart → claimed; place a second Heart by another bell → `ALREADY_HAVE_VILLAGE`; break the first → a new claim succeeds
- **L2**: claim, save and reload the level (or re-read the saved data) → the claim, owner and treasury are intact
- **L2**: a non-owner player breaking the Heart → the block is still there and the claim is intact

## M2: Layer 3: long-simulation harness ✅
- [x] `scripts/simulate.sh <scenario> <days>`: boots a headless server on a **fixed seed** with a known plains village,
      locates the village and its bell, places the Heart, applies a scenario setup, fast-forwards N days, and writes
      `reports/sim/<scenario>.json` (one record per day) plus a pass/fail summary for the scenario's assertions
- [x] Per-day metrics: day, population, adults, employed, buildings valid/invalid, stockpile totals (top items),
      food nutrition, food days left, treasury, deaths (with cause), mean/p95 **ms per tick of village logic**, server TPS
- [x] Scenario `bare`: claim only, 5 days. The harness is not part of `verify.sh` by default. Document it in `docs/TESTING.md`
- **L1**: metrics JSON writer and assertion evaluator (e.g. `population >= 4 on day 5`) are unit-tested
- **L3**: `bare` runs for 5 days, exits 0, the JSON has 5 records, the claim exists on every day, and population on the last day ≥ population on day 1 − 1
- **L3**: a deliberately failing assertion makes the script exit non-zero (prove that red is red)

## M3: Marker items and recipes ✅
- [x] Blank Marker plus one marker item/block per building and outdoor site (§3.1, §3.3), with textures and lang
- [x] Recipes: Blank Marker in the centre and the ring ingredients from §3.3. The marker places on a wall (wall-mounted, faces outwards)
- [x] New job blocks (Chopping Block, Builder's Table, Market Stall), with recipes and textures
- **L2**: for every building type, the recipe manager crafts the marker from the expected 3×3 grid, and the Blank Marker recipe works
- **L2**: a marker placed on a wall side is attached and facing out. If the wall is removed, the marker drops
- **L4**: screenshot of a wall showing every marker and the 3 new job blocks, in good light (checked by looking at it)

## M4: Room detection (flood fill) ✅
- [x] `core.room.RoomScanner` over a `BlockView` interface: start from the inside of the marked door and report
      enclosed, volume, floor area, contents and reasons (§3.2). Real-world adapter in glue code
- **L1**: a fake-grid closed 5×5×3 room is enclosed with the right floor area. A missing roof block fails with the reason and position.
      A missing wall fails. Fences, glass and doors count as wall. A room over 4096 air fails. Two markers into one room fail with "shared"
- **L1**: the scanner on a 16×16×8 room finishes in under 5 ms
- **L2**: build a real room in a GameTest structure. The scan finds enclosed, the door, and the bed/chest contents. Break one roof block → invalid with that position

## M5: Building registry and validation ✅
- [x] Building types with their requirements (§3.3), registered when a marker is placed and removed when it is broken
- [x] Round-robin re-validation once per second, plus on use. Marker state grey, red or green. Right-click a marker → reasons in chat
- [x] Outdoor site validators: Mineshaft, Hunting Ground, Training Yard, plus linking to the nearest parent building
- [x] Tier gate: markers above the current tier show red with "Requires <tier>"
- **L1**: each building type's requirement check against a fake scan result (all pass/fail cases in the §3.3 table)
- **L2**: valid Storage (2 chests) → green within 2 s. Remove a chest → red with "Needs 1 more chest" within 2 s
- **L2**: Mineshaft 12 deep with 8 rails and a torch → valid and linked to a Mine 20 blocks away. With 6 rails → invalid
- **L4**: screenshot of one red marker and one green marker side by side, plus the chat validation message

## M6: Stockpile backed by Storage chests ✅
- [x] `Stockpile` view aggregated over chests in valid Storage buildings. Deposit goes to the nearest Storage with space.
      Withdraw takes from any. A "Storage full" alert
- **L1**: aggregation and deposit routing over fake inventories (fill order, overflow returns the remainder)
- **L2**: two Storage buildings with items → the stockpile totals are their sum. Chests in a House are **not** counted.
      Deposit 100 cobblestone into an almost-full Storage → it spills into the second one

## M7: Hiring and the worker framework (includes the villager-control spike) ✅
- [x] Spike: choose brain mixin or a mod task runner (DESIGN §12) and record the decision in DESIGN.md
- [x] Assign or fire a job (server-side API + command for tests). Job blocks reserved via POI. Mod professions with no trades
- [x] Worker data attachment: job, building, level, XP, needs. Daily schedule (§4), going home to a bed, deposit trips
- [x] A dummy "Idle Worker" behaviour that walks to its job block during work hours (real jobs come next)
- **L1**: slot accounting: job blocks in valid buildings minus assigned = free. Hire rejected with no free slot. Firing frees the slot
- **L2**: a hired villager has 0 trade offers and the mod profession, and is at its job block by tick 3000
- **L2**: an unemployed villager next to a reserved composter does **not** take the farmer profession within 1 day.
      The same composter outside any building → it does (vanilla untouched)
- **L2**: worker data survives a save/reload. Firing restores an unemployed vanilla villager

## M8: Heart screen v1 (Overview, Villagers, Buildings, Stockpile) ✅
- [x] Networked tabbed screen (§10), with assign/fire actions through server-validated packets
- **L2**: an assign-job packet from a Visitor is rejected. From a Steward it is accepted
- **L4**: screenshot of each of the 4 tabs with a seeded village (3 villagers, 2 buildings, stockpile items), with text readable
- Deferred out of M8 (on purpose): set guard shift (M18), active blueprint on the Buildings tab (M20), surplus reserve settings (M22), tier goal checklist (M14), wage bill (M12), Ranks tab (M24). "Check now" on a building is an extra beyond this entry (Steward).

## M9: Lumberjack ✅
- [x] §5.1: tree finding (natural trees only), whole-tree felling, drop pickup, replanting, deposit
- **L1**: tree detection over a fake grid: a log column touching leaves is a tree. Placed logs with no leaves, and logs inside a building room, are not
- **L2**: **a lumberjack hired next to 6 oak trees, with a stone axe in the camp chest → the stockpile has ≥16 logs within 2 days**
- **L2**: every felled tree spot has a sapling within 1 day. A log wall 10 blocks away is untouched
- **L2**: with no axe → no logs after 1 day, and the Villagers tab reason is "Needs axe"

## M10: Farmer ✅
- [x] §5.2: harvest mature crops, replant, compost seeds into bone meal, deposit
- **L2**: a farmer with a 9×9 field of mature wheat → ≥60 wheat in the stockpile within 1 day, and every farmland block replanted
- **L2**: immature crops are not harvested. Farmland outside 24 blocks is untouched

## M11: Food, dinner and needs
- [x] §4 needs, §6 food rules: dinner at tick 11000, best-food-first, children at half, fed/housed/paid multipliers (paid is stubbed until M12)
- **L1**: dinner allocation (best food first, cooked before raw, 10 nutrition each, children 5). Food-days-left calculation.
      Hungry for 1 day → ×0.5, 2 days → 0. No bed → ×0.75
- **L2**: 4 villagers and 20 bread in Storage → after dinner, 12 bread are left (8 eaten: 2 × 5 nutrition each) and all are fed. With 0 food → all hungry, and still alive after 3 days
- **L3**: scenario `starter` (prebuilt House×2, Storage, Farmhouse + field, Lumber Camp, Kitchen; 1 farmer + 1 lumberjack), 10 days:
      0 starvation deaths, ≥90% fed villager-days from day 3, logs increase every day
- Note (sim-area): the L3 harness now keeps a moving ticking ticket around every hired worker (a player's simulation distance) on top of the Heart-centred ±6-chunk square; `starter` also asserts `max(work.workers.max_heart_distance) < 150`. 8/8 `starter 10` runs green (the M11-r5 flake was ~1 in 4).

## M12: Treasury and wages
- Note (M15): `WorkerRecord` has no `level` on dev. Add it (codec key `level`, default 1) and write it in `WorkXp.setLevel`, `WorkXp.addXp` and `WorkXp.pass` (the three places that change a level), and use it in the re-install branch of `WorkerSystem.tick` instead of `WorkerData.hired` (level 1). Then add the planned T-L2-4 (a worker that lost its attachment comes back at its level).
- [x] Deposit emeralds at the Heart (Resident+), Owner withdraw, dawn wages (§6), strikes (verified: docs/reviews/M12-r2.md)
- **L1**: wage bill by level. Paid in hiring order when the treasury is short. Strikers produce nothing
- **L2**: 3 workers and 2 emeralds → after dawn 2 are paid and 1 is on strike. The striking lumberjack chops 0 logs that day
- **L4**: Overview tab screenshot showing the treasury, wage bill and a strike alert
- Note (M24 review): M12 also owes M24's "only the Owner can withdraw" GameTest (docs/plans/M24.md T-L2-6) and the two treasury rows in `PermissionsTest`.
- Built (M12 builder, branch `m12`; boxes left for the verifier): `WageGameTests` (12 tests incl. the roadmap case `threeWorkersTwoEmeraldsOneStrikes`, M24's `onlyOwnerCanWithdraw`, M15's `workerComesBackAtItsLevel`), L1 `PayRunTest`/`PayrollTest`/`WageRulesTest`/`StrikersProduceNothingTest`/`DailyClockTest`/..., L4 `TreasuryClientTests` (`treasury_overview.png`, `treasury_villagers.png`), L3 `starter` seeded and asserted. See PROGRESS for deviations.

## M13: Cook / Kitchen ✅
- Built (M13 builder, branch `m13`; boxes left for the verifier): `CookGameTests` (cooksBeefAndBread, noFuelNothingCooked and 12 more), L1 `core/cook/*`, `@base` baseline assertions, `starter_cook` (baseline `sim/baselines/starter.json`), L4 `cook_at_smoker.png` and `villagers_tab_needs_fuel.png`. See PROGRESS and the plan's section 8 for deviations.
- Note (M15): award XP with `WorkXp.award(villager, WorkUnit.ITEM_COOKED, n)` next to the cook counter (one per output item).
- [x] §5.5 recipes, fuel use, rates (verified: docs/reviews/M13-r2.md)
- Status: verified PASS round 2 (2026-11-09, `docs/reviews/M13-r2.md`). r1's four test gaps fixed and mutation-checked; L3 `starter_cook 10` 4.60 vs B 3.14.
- **L2**: 10 raw beef, 9 wheat and coal in Storage + cook → within 1 day, 10 cooked beef and 3 bread, and the raw beef is gone
- **L2**: with no fuel → nothing is cooked and the reason is "Needs fuel"
- **L3**: `starter` + cook, 10 days: average nutrition per eaten item rises compared with the M11 baseline (reported in the JSON)

## M14: Tiers and unlocks ✅
- Note (M15): `core.tier.TierRules` already exists with `levelCap(Tier)`, `levelFromXp(int)` and `levelFor(xp, tier)` (and `Balance.LEVEL_CAPS`); add the tier-goal rules to that class. The level pass (`WorkXp.pass`, every 20 ticks) turns banked XP into levels after a tier-up, so M14 needs no level code.
- Status: verified PASS round 1 (2026-11-09, `docs/reviews/M14-r1.md`). L2 control is a Builder's Hut (plan §9); the verifier also checked a Hamlet Mine + Mineshaft turns green after setTier + requestAll.
- [x] §9 goals tracked daily, tier-ups, level caps, marker tier gating, Overview goal checklist
- **L1**: tier goal evaluation for every Hamlet→Village and Village→Town condition, including "fed N days in a row" resetting on a hungry day
- **L2**: a village set up to meet the Village goals → tier becomes Village at the next dawn, and a Mine marker turns from red ("Requires Village") to green
- **L4**: Overview screenshot with a partially met goal checklist

## M15: Job levels and tools ✅
- [x] XP per work unit, levels 1–3 within the tier cap, tool tier multipliers, tool durability use
- **L1**: speed = tool × level × needs. XP thresholds. The level cap holds at the Hamlet tier
- **L2**: side by side over one day's felling window, an iron-axe lumberjack fells ≥1.3× the logs of a stone-axe one and ≥1.3× a wooden-axe one (the original criterion, restored by the human 2026-11-09 with the vanilla-speed tool table: iron 3.0 / stone 2.0 = 1.5; it had been relaxed to ≥1.1× by orchestrator decision D1, 2026-11-08, when iron/stone was only 1.2)
- **L2**: the tool in the camp chest loses durability while it is used

## M16: Miner + Mineshaft ✅
- [x] §5.3: walk to the shaft, vanish while inside, haul every 2 hours, depth-band tables. Cosmetic minecart is optional and can be deferred
- **L1**: haul tables by depth band and pickaxe tier (seeded RNG: fixed counts and entries with chance ≥30% within 5% of expected over 10k rolls; every entry incl. rare ones within 5% over 400k rolls; clarified by the orchestrator 2026-11-08, a 2% entry over 10k rolls is seed-dependent). Diamonds only with an iron pickaxe or better and Y < 0
- **L2**: a miner with a stone pickaxe and a valid shaft → ≥40 cobblestone within 1 day. With the shaft invalidated (rails removed) → stops, with the reason

## M17: Hunter + Hunting Ground ✅
- Note (M15): award XP with `WorkXp.award(villager, WorkUnit.GAME_HUNTED, 1)` per trip.
- [x] §5.4: trips, yields, arrow use
- **L1**: trip yield table with and without a bow
- **L2**: a hunter with a sword and a valid Hunting Ground → ≥4 raw meat and ≥2 leather within 1 day

## M19a: Self-made basic tools ✅
- Status (verifier, 2026-11-09): PASS in round 2 (`docs/reviews/M19a-r2.md`). Built on branch `m19a`. Unit tests, 23 GameTests (`SelfCraft*GameTests`) and the L4 shot `villagers_tab_making_tool.png` are in; deviations in the plan's section 8.
- Depends on M15 (tools) and the tool jobs M9, M10, M16, M17. Not on M11, M18 or M19 (M19 later extends the same core). Plan: `docs/plans/M19a.md`.
- [x] (owner 2026-11-09) §4.1: a hired lumberjack, miner, hunter or farmer with no usable tool in its job chest, or one about to wear out
      (the M19 worn line), makes its **own** wooden or stone tool from Storage materials at its job block, at any tier, slowly and in its own
      work time (wooden 2400, stone 4800 ticks at level 1, *tunable*; the smith's are 600 / 1200). Stone (`#stone_tool_materials`) when
      affordable, else wooden (planks, sticks or logs); never copper or better (Smithy, M19). Vanilla recipe counts. Demand only: no upgrade
      of a working tool; a tool the player puts in the chest always counts and cancels a craft with a refund; a usable tool in Storage is
      fetched instead of crafting. The farmer makes its hoe only in idle time. The craft is saved and pauses overnight. Status "Making a
      stone pickaxe". When a hired smith exists (M19) it takes over new demands (the hand-off rule is pure and tested now)
- **L1**: recipe counts (axe and pickaxe 3 + 2 sticks, hoe 2 + 2, sword 2 + 1; 2 planks → 4 sticks; 1 log → 4 planks). Tier choice: stone
      over wooden, nothing above stone even with iron in Storage, others' reservations respected. Worn line. Demand per camp: none with a
      working tool, two workers and one tool → one craft by the toolless one. Cancel only on two checks in a row, never while the chest is
      unreadable. Hand-off: no new self-craft while a smith serves, except a required tool missing for 3000 work ticks
- **L2**: a miner with no pickaxe and planks + cobblestone in Storage → makes a stone pickaxe in about the stone self-craft time, puts it in
      the Mine chest and then mines. With only planks → a wooden pickaxe in about the wooden time
- **L2**: a miner with a working stone pickaxe, and planks, cobblestone and iron in Storage → nothing is crafted over a work day
- **L2**: a player's pickaxe put in the Mine chest mid-craft → the craft is cancelled and Storage gets exactly the materials back
- **L2**: progression: planks only → a wooden pickaxe; once the miner's cobblestone is in Storage, its next replacement is stone
- **L4**: Villagers tab row "Making a stone pickaxe" (screenshot looked at)

## M18: Guards: Barracks, shifts, sleep, training, combat
- Note (M15): levels and XP exist. Use `Workers.addXp(villager, n)` for kills (`WorkXp.award(v, WorkUnit.HOSTILE_KILLED, 1)`) and `WorkXp.award(v, WorkUnit.SPAR, units)` for sparring, and register a `WorkerLevels.LevelListener` for the level perks (max health 20/30/40). The `Leveling`/`addXp` fallback in the M18 plan is not needed.
- Note (M24): gate the set-guard-shift action with the existing `Permission.SET_GUARD_SHIFT` (Steward and up).
- [ ] §5.6: shifts with auto-balance, off-shift sleep in Barracks beds (night shift sleeps by day), patrol, combat,
      Training Yard XP, level perks for health and damage, best gear from the stockpile, raid call-up
- **L1**: shift balancing. Gear ranking. Max health by level (20/30/40)
- **L2**: a night-shift guard is asleep in a Barracks bed at tick 6000 and patrolling at tick 18000
- **L2**: a zombie spawned in the claim at night is killed by a guard within 60 s, and no villager dies
- **L2**: an iron chestplate in Storage → the guard wears it at shift start
- **L2**: a guard sparring at the Training Yard gains XP. At level 2 the max health is 30
- **L3**: scenario `defended` (starter + Barracks with 2 guards), 10 days: villager deaths from mobs ≤ 1

## M19: Blacksmith / Smithy
- Note (M15): award XP with `WorkXp.award(villager, WorkUnit.ITEM_SMITHED, n)` per ingot, item or repair.
- [ ] §5.7: smelting, gear targets, repairs, golem build and repair. Vanilla golem spawning is disabled in the claim
- **L1**: gear target planner (what to craft next given the stock and the number of guards)
- **L2**: 36 iron ingots + a carved pumpkin + a blacksmith → a new iron golem within 1 day, and the stockpile minus 36 ingots
- **L2**: a golem at 40% health → it walks to the Smithy and is above 90% after repair, with ingots consumed
- **L2**: no vanilla golem spawns in the claim over 2 days with 10 gossiping villagers (vanilla would)

## M20: Builder and blueprints (buildings) ✅
- Note (M24): gate starting a blueprint with the existing `Permission.START_BLUEPRINT` (Steward and up).
- [x] Blueprint data format in core. Builder's Table GUI, Blueprint item placement with particles and rotation, material reservation,
      clearing natural blocks, layer-by-layer build. Small House, Storage Shed and Farm Plot
- **L1**: blueprint parse, rotation (4 rotations), material list. A site overlap with a player room is refused
- **L2**: Small House materials in Storage + builder → a **valid green House** within 2 days, with materials deducted exactly
- **L2**: missing materials → the build pauses with "Needs 12 more planks". Adding them → it resumes and completes
- **L4**: screenshot mid-build and one of the finished Small House
- Built in M20 (branch m20, see docs/plans/M20.md section 8 for the deviations; the boxes above are ticked by the orchestrator, not the builder). Ready for M21: the blueprint
  format has `kind` (BUILDING/DEFENCE), `mode` (`PlacementMode.FOOTPRINT`; M21 adds `LINE` and its two-click flow in `BlueprintUse`) and `BlueprintParams.length`
  (`Blueprints.resolve(id, params)` is where a generated Wall Segment goes); `BuildPlan`, `SiteRules`, `BuildExecutor` and the builder need no change for new blueprints.

## M21: Defence blueprints ✅
- [x] Wall Segment (two-click line), Gate, Lamp Post, Watchtower (Town). Watchtowers and lamp posts become guard patrol points
- **L1**: wall line generation for axis-aligned start/end. Lengths over 32 are rejected
- **L2**: a 20-long wall segment is built complete. A gate opens. A lamp post stops hostile spawns in its light radius over 1 night
- **L4**: screenshot of a walled section with a gate, lamp posts and a watchtower
- Built in M21 (branch m21, deviations in docs/plans/M21.md section 8; the boxes are ticked by the orchestrator, not the builder). Guard patrol points: the source is done and tested (`dev.villageheart.defence.Defences.patrolPoints(server)`); **wiring it into M18's `PatrolPoints` is M18 step 6** (M18 was not merged when M21 was built).

## M22: Merchant / Market
- Note (M15): award XP with `WorkXp.award(villager, WorkUnit.TRADE_MADE, 1)` per sale.
- [x] §5.9: surplus selling, price table, daily cap, reserve settings on the Stockpile tab
- **L1**: surplus calculation respects the food and item reserves. Price table. Daily caps by level
- **L2**: 200 cobblestone in Storage + merchant → treasury +≥4 emeralds within 1 day, and cobblestone never drops below 64
- Built in M22 (branch m22, deviations in docs/plans/M22.md section 8; the boxes are ticked by the orchestrator, not the builder). Adds the village-wide cap of 32 emeralds a day (orchestrator decision) to the per-merchant 8 / 12 / 16. L1 `core/market/*Test`, `MarketHeartTest`, `MarketCodecsTest`; L2 `MarketGameTests` (15 tests, `twoHundredCobblestoneFourEmeralds` is the roadmap case); L4 `MarketClientTests.stockpileReserves` (`market_stockpile.png`).

## M23: Population growth
- [ ] §7: dawn breeding trigger, one birth per day max
- **L1**: breeding conditions (free bed, food days ≥3, ≥2 fed and housed adults)
- **L2**: a free bed and plenty of food → a baby within 2 days. With no free bed → no baby in 3 days
- Built in M23 (branch m23, deviations in docs/plans/M23.md section 8; the box above is ticked by the orchestrator). **Orchestrator decision: all vanilla villager breeding inside the claim is blocked**, growth happens only through the dawn licence, so feeding villagers bread does nothing there. L1 `core/growth/*Test`, `GrowthCodecsTest`; L2 `GrowthGameTests` (T-L2-1 is the roadmap "baby within 2 days", `GrowthCourtshipGameTests.noFreeBedNoBabyInThreeDays` the "no baby in 3 days"), `workersCanBreed` rewritten; no L3 assertion and no L4 (no visual change).

## M24: Ranks and multiplayer ✅
- [x] §2 ranks: Ranks tab, chest protection in claimed buildings for Visitors, ownership transfer
- **L1**: permission matrix for every rank × action
- **L2**: a Visitor fake player cannot open a Storage chest, and a Resident can. A Steward can hire. Only the Owner can withdraw from the treasury
- **L4**: Ranks tab screenshot with 3 players
- [x] Done in M12: "Only the Owner can withdraw from the treasury" (plan T-L2-6) is `WageGameTests.onlyOwnerCanWithdraw`, and `PermissionsTest` has the
  `DEPOSIT_TREASURY`/`WITHDRAW_TREASURY` rows (verified: docs/reviews/M12-r1.md and M12-r2.md). Everything else above passed review (docs/reviews/M24-r1.md).

## M25: Raids and alerts
- [ ] Raid detection → alert, all guards on duty, workers indoors. Death log
- **L2**: starting a raid on the claim → the alert appears in the Heart state and all guards are on shift within 10 s
- **L3**: scenario `raid` (Town-like setup, 4 guards, 1 golem, walls), a raid on day 3: the village survives, ≥75% of villagers are alive, and metrics are logged

## M26: Balance pass
- [ ] Run the L3 scenarios and tune the *(tunable)* values. Record the before/after metrics in PROGRESS.md
- **L3**: scenario `full` (all building types, 12 villagers, starts at Hamlet with a starter kit of resources), 30 days, no player input:
      reaches the Village tier, food days left never 0 for more than 1 day in a row, treasury never negative, village logic mean < 1 ms/tick

## v1.0 release gate
- [ ] `scripts/verify.sh` (L1, L2, L4) green on CI. Every milestone above checked
- [ ] All L3 scenarios (`bare`, `starter`, `defended`, `raid`, `full`) pass. The `full` 30-day run is under the 1 ms/tick budget
- [ ] A 60-day `full` soak: no crash, no error logs from `villageheart`, and the save size grows by less than 1 MB
- [ ] Save/reload in the middle of each L3 scenario gives the same metrics trend (no lost state)
- [ ] A dedicated server with 2 players (Owner + Resident) runs a scripted session with no desync errors
  - Harness (M24): `MultiplayerClientTests` = 1 real networked client + 1 server-side player (the Owner), `scripts/check-mp-log.sh`; a second real client would need a protocol bot. Whether that satisfies this item is decided at release.
- [ ] L4 screenshot set refreshed and reviewed: every marker state, all Heart tabs, a Builder mid-build, the defence set
- [ ] README updated (recipes, how to start, the building table). DESIGN.md matches what shipped
