# M26 prep: why the `defended` sims lose villagers

Builder, 2026-11-11, branch `m26-prep`. Scope: BACKLOG "From M18 close-out" (the death log, why `defended_walled` loses villagers,
why a `defended` run hired no replacement guards). No balance number and no scenario assertion was changed.

## What the report shows now
Each simulation summary (`reports/sim/<scenario>.summary.md`) has three new tables; the JSON carries the same data (docs/TESTING.md, "Death log and hire counts"):
- **Deaths:** one row per villager lost within the claim radius: day, day time, name, job, guard, cause, damage type, attacker type, attacker
  origin (`scripted` = a scenario `night_spawn` zombie, `converted` = a villager turned zombie villager, `natural` = anything else), position, the
  villager's and the attacker's side of the wall ring, and whether it was zombified.
- **Hires:** hires, re-hires, and for every 100-tick sample with an empty standing-hire slot, why it stayed empty (`night`, `no_free_villager`, ...).
- **Hostiles:** natural spawns within the claim radius by side of the wall, hostiles first seen inside the wall by origin (with the gate state in
  the log), hostiles present by origin.

## Runs
Round 1: 5 x `defended_walled` then 5 x `defended`, 10 days, unseeded AI, one at a time, at `9d1fbac` + the death log (walled runs 1-3 were before
the attacker-origin column; walled runs 4-5 and all `defended` runs have every column). Round 2: the same after the two `wall_ring` fixes (below).

### Round 1 (at 9d1fbac + the death log, before the wall fixes)

| run | result | deaths.mobs | deaths (who: attacker origin, wall side) | guard kills | failed assertions | hostiles inside the ring | natural spawns (day: count) | empty slots (100-tick samples) |
|---|---|---|---|---|---|---|---|---|
| defended_walled-1 | FAIL | 2 | d4 unhired zomb.: n/a, outside; d7 unhired: n/a, outside | 11 | deaths.mobs, guard.kills | 0 | 0 | - |
| defended_walled-2 | FAIL | 5 | d2 unhired zomb.: n/a, outside; d2 unhired: n/a, outside; d10 unhired zomb.: n/a, outside; d10 guard zomb.: n/a, inside; d10 guard zomb.: n/a, inside | 2 | deaths.mobs, guard.kills | 0 | 0 | guard.night 48 |
| defended_walled-3 | FAIL | 1 | d2 unhired: n/a, outside | 9 | guard.kills | 0 | 0 | - |
| defended_walled-4 | FAIL | 2 | d4 unhired zomb.: scripted, outside; d5 unhired: scripted, outside | 6 | deaths.mobs, guard.kills | scripted 8 | 0 | - |
| defended_walled-5 | FAIL | 1 | d7 unhired zomb.: scripted, outside | 5 | guard.kills | scripted 9 | 0 | - |
| defended-1 | FAIL | 3 | d2 guard: natural; d2 guard zomb.: natural; d6 guard: scripted | 19 | deaths.mobs | 0 | d2: 19 | guard.night 151 |
| defended-2 | FAIL | 8 | d5 guard: natural; d5 guard: natural; d5 lumberjack: natural; d5 unhired: natural; d6 guard zomb.: natural; d6 guard: converted; d6 lumberjack: natural; d6 farmer zomb.: natural | 11 | deaths.mobs, guard.kills, guard.duty_samples | 0 | d5: 16, d6: 13 | farmer.no_free_villager 997, guard.night 595, guard.no_free_villager 480, lumberjack.no_free_villager 1013 |
| defended-3 | FAIL | 6 | d4 guard: scripted; d4 guard: natural; d4 unhired zomb.: natural; d8 guard: natural; d8 guard zomb.: natural; d8 farmer: converted | 21 | deaths.mobs, guard.duty_samples | 0 | d4: 16, d8: 12 | farmer.no_free_villager 524, guard.night 386, guard.no_free_villager 240 |
| defended-4 | FAIL | 7 | d3 guard zomb.: natural; d3 guard: natural; d3 farmer zomb.: natural; d3 farmer: natural; d4 farmer zomb.: converted; d4 guard: scripted; d8 farmer: scripted | 12 | deaths.mobs, guard.kills, guard.duty_samples | 0 | d3: 15, d4: 16 | farmer.no_free_villager 718, guard.night 891, guard.no_free_villager 839, lumberjack.no_free_villager 689 |
| defended-5 | FAIL | 3 | d6 guard zomb.: scripted; d6 guard zomb.: scripted; d6 unhired: converted | 23 | deaths.mobs, guard.duty_samples | 0 | d6: 8 | guard.night 98 |

Walled runs 1-3 of round 1 predate the attacker-origin, breach and spawn counters (origin "n/a"; their 0 inside the ring and 0 spawns are "not measured").

### Round 2 (at 076aa10, after both wall_ring fixes)

| run | result | deaths.mobs | deaths (who: attacker origin, wall side) | guard kills | failed assertions | hostiles inside the ring | natural spawns (day: count) | empty slots (100-tick samples) |
|---|---|---|---|---|---|---|---|---|
| defended_walled-1 | FAIL | 2 | d8 unhired zomb.: natural, outside; d9 unhired zomb.: scripted, outside | 1 | deaths.mobs, guard.kills | 0 | d8: 16 | - |
| defended_walled-2 | FAIL | 2 | d2 unhired zomb.: scripted, outside; d3 unhired zomb.: scripted, outside | 0 | deaths.mobs, guard.kills | 0 | d1: 17, d7: 10 | - |
| defended_walled-3 | FAIL | 1 | d7 unhired zomb.: scripted, outside | 0 | guard.kills | 0 | d5: 5, d9: 20 | - |
| defended_walled-4 | FAIL | 2 | d4 unhired zomb.: scripted, outside; d4 unhired zomb.: converted, outside | 0 | deaths.mobs, guard.kills | 0 | d9: 7 | - |
| defended_walled-5 | FAIL | 2 | d4 unhired: scripted, outside; d4 unhired zomb.: scripted, outside | 0 | deaths.mobs, guard.kills | 0 | 0 | - |
| defended-1 | FAIL | 6 | d1 guard zomb.: natural; d1 guard zomb.: natural; d3 guard: natural; d3 guard: natural; d4 guard: scripted; d4 guard: scripted | 12 | employed, deaths.mobs, guard.kills, guard.duty_samples | 0 | d1: 17, d3: 9, d6: 9, d7: 15, d9: 13 | guard.night 931, guard.no_free_villager 720 |
| defended-2 | PASS | 0 | - | 21 | - | 0 | 0 | - |
| defended-3 | FAIL | 5 | d4 guard zomb.: natural; d4 guard: converted; d6 guard: scripted; d9 guard: scripted; d9 guard: scripted | 24 | deaths.mobs, guard.duty_samples | 0 | d4: 14, d5: 6, d9: 16 | guard.night 371, guard.no_free_villager 119 |
| defended-4 | FAIL | 7 | d1 guard: natural; d1 guard zomb.: natural; d1 farmer zomb.: natural; d1 farmer zomb.: natural; d2 guard: scripted; d2 farmer: scripted; d4 lumberjack zomb.: scripted | 5 | employed, deaths.mobs, guard.kills, guard.duty_samples | 0 | d1: 16 | farmer.no_free_villager 1997, guard.night 1137, guard.no_free_villager 1079, lumberjack.no_free_villager 1642 |
| defended-5 | FAIL | 4 | d1 guard: natural; d1 guard: natural; d4 guard: scripted; d6 guard: scripted | 21 | employed, deaths.mobs | 0 | d1: 17 | guard.night 250 |

In the tables, "zomb." = zombified; "natural" spawns in bursts at day time 18000 are the vanilla siege (Q2).

## Q1: why does `defended_walled` lose villagers when the scripted zombies spawn outside the wall?
Two causes, one of them a bug.

1. **The wall does not ring the villagers who die (design of the scenario, not a bug).** Every death outside the wall in round 1 (9 of 11) was an
   **unhired** villager of the vanilla village, 20-60 blocks west and north of the Heart (e.g. 580 67 834, 604 65 814, 625 64 777). The `wall_ring` rings
   only the new 44 x 32 site, which starts 27 blocks south of the Heart (site 638..681 x 848..879, Heart 641 64 821). `night_spawn` places the scripted
   zombies 12-20 blocks from the **Heart**, outside the ring: that is right in the old village, where the four unhired villagers sleep. Their attackers
   were `scripted` and `outside` in every row that has the column. No hired worker died outside the wall in 10 walled runs (the Lumber Camp grove is
   inside the ring), so "workers going outside" is not a cause. The guards stay inside the ring at night, which is also why kills are low (2-11 a run
   against the bar of 15): most scripted zombies never come near a guard.
2. **BUG (fixed): zombies walked over the wall.** Runs 4 and 5 logged 8 and 9 scripted zombies inside the ring (`[sim-breach]`), all at the west wall
   (x 639-645, z 856-868), every time with the only gate **closed**. The `wall_ring` action built every wall cell two blocks above the flattened site,
   but on seed 1 the natural ground just outside is one block higher along 80 of the 148 wall cells (round 2's log: "80 cells raised ... up to +1"),
   so from outside the wall was a one-block step. A GameTest reproduces it: a zombie on ground one block up plans a path straight over a two-high wall
   and kills a villager inside. The ground in front of the **gate** was also one block up, so a zombie could step over the closed gate (1.5 high).
   That was the second hole, found in round 2's first log. In round 1 run 2 it is the likely way the two guards were zombified **inside** the wall
   at night on day 10 (that run predates the breach log). Fix in `GuardActions.wallRing` (sim harness): each wall column is `WALL_CLEARANCE` = 2 above
   the highest natural ground just outside it, and the 3 x 2 cells in front of the gate are dug down to the site's level. L2
   `SimActionGameTests.wallRingRisesOverHigherGround`: red without the raise (height check, and the zombie path check alone) and red without the
   dig (the zombie in front of the gate plans a 9-node path inside).
3. **After the fix (round 2, 5 runs): no hostile got inside the ring in any run** (`hostile_inside` 0; scripted zombies were present outside
   on every night). All 9 deaths were unhired villagers outside the ring (7 by scripted zombies, 1 by a zombie villager converted from a victim,
   1 by a siege zombie), so `deaths.mobs <= 1` still fails in 4 of 5. Guard kills fell to 1/0/0/0/0: with the wall holding, the guards inside never meet the
   threat. The scenario now measures exactly cause 1.
4. **Not causes:** natural spawns inside the lit ring (0 in every run that counted them: no hostile spawned inside or on the wall), the gate left open (it was
   closed at every breach), fire (no fire or lava damage in any row).

## Q2: why did a `defended` run hire no replacement guards from day 7 to day 10?
**The village was wiped out; there was nobody left to hire.** Reproduced in round 1 `defended` run 2: population 8 -> 4 on day 5 -> 0 on day 6.
From day 7 every empty slot reads `no_free_villager` (guard, farmer and lumberjack), employed 0, duty_samples 0. Run 3 (population 1 from day 8)
and run 4 (population 0 from day 8) show the same thing. The re-hire works whenever someone is free: every `defended` run re-hires guards the
morning after a night death (`guard.night` while it waits, then a re-hire). The night rule (`GuardTraining.mayHire`) is behaving as designed.

**What wipes the village is vanilla's zombie siege.** All natural hostile spawns in the round-1 `defended` runs came in bursts of 8-19 at day time
**18000**, on 1-2 nights per run (run 1 day 2; run 2 days 5 and 6; run 3 days 4 and 8; run 4 days 3 and 4; run 5 day 6), and no natural hostile
spawned on any other night. That is `net.minecraft.world.entity.ai.village.VillageSiege` (checked in the 1.21.11 jar): at day time 18000, with a
1-in-10 roll, it spawns up to 20 zombies about 32 blocks from a player standing in a village. The sim's owner player stands at the Heart. Observed:
a burst on 18 of 100 `defended` nights (9 of 10 runs) and 6 of 50 `defended_walled` nights in round 2. That is more often than the nominal 1 in 10;
I did not find why.
Of the 27 deaths in the five `defended` runs, 17 were by siege zombies directly (`natural` in the Deaths table, day time 18241-20700) and 3 more by
villagers converted on the same siege night (`converted`); 7 were the scripted chain. The earlier note "the deaths come in pairs on one night"
(M18 r3) is this siege: it takes both guards within a few hundred ticks. The scripted threat alone (3 zombies a night) killed 0-3 villagers a run,
mostly guards just after the 14000 spawn (14199-15205), when one meets three fresh zombies with a wooden sword.

**Round 2 confirms it.** The only run without a siege (run 2) **passed every assertion**: 0 deaths, 21 kills. The other four had a siege, three of
them on night 1, so they also fail `employed == 4 on day 1` (both guards died before the day-1 record). Over all 10 `defended` runs: 49 deaths,
32 from siege nights (28 by siege zombies, 4 by villagers they converted) and 17 from the scripted threat (16 by scripted zombies, 1 converted).

Two more observations:
- Sieges hit `defended_walled` too (round 2: 4 of 5 runs, 7-27 natural spawns at 18000, all outside the ring); round 1's walled runs 4-5 had none
  by chance. Behind the fixed wall they killed nobody inside.
- **The harness re-hires in scenario order** (farmer, lumberjack, guard), so after a siege night the first free villager becomes the farmer and the
  guard slot waits (`defended` run 4: three farmers re-hired, one guard).

## Bugs fixed
1. `wall_ring` (sim harness): the wall was a one-block step wherever the outside ground is one block higher (80 of 148 cells on seed 1).
2. `wall_ring` (sim harness): the closed gate was a one-block step for a zombie standing on the higher ground in front of it.

Both are harness bugs: the scenario promised a wall that "mobs cannot" pass. No game code changed.

## Not bugs, left as they are (for the orchestrator)
- Vanilla `VillageSiege` runs inside a claimed village. It belongs to M25 (sieges and raids), so I did not touch it.
- The harness re-hire order, and the night rule leaving a dusk death uncovered until the next morning, follow the scenario and the owner's design.
- Natural spawns other than sieges were 0 within the claim radius on every night of all runs. The cause is likely the mob cap filled by cave mobs and
  persistent scripted zombies, but that is not checked. So `defended` tests the scripted threat plus sieges, not ordinary night spawns.

## Proposed balance and scenario changes for M26 (not applied)
1. **Decide what a siege is in a claimed village (M25 first).** Vanilla's 20-zombie siege (a burst on 18 of 100 nights here) is the largest single
   killer in `defended` (32 of 49 deaths), and it is random per run: the one siege-free run passed. Either M25 replaces vanilla's `VillageSiege` inside a claim with its own sieges (scaled to the village, with
   warning), or the L3 `defended` scenarios turn it off and test the scripted threat alone, with a separate siege scenario. Until then a pass/fail
   of `deaths.mobs <= 1` mostly measures whether a siege rolled.
2. **`defended_walled`: put the threat where the wall is.** Place `night_spawn ... outside` around the **site**, not 12-20 blocks from the Heart, or
   build the ring around the old village and the Heart, or move the four unhired villagers into the walled Houses (the site has 4 house beds).
   Otherwise the wall protects only the hired workers and the scenario measures the old village's losses. Re-measured after the wall fix
   (round 2): 1-2 unhired deaths a run outside, 0 inside, guard kills 0-1, so `kills >= 15` cannot pass while the threat stays outside the ring.
3. **Guard strength against three fresh zombies at dusk** (the remaining scripted deaths): levers in order of how little they change the
   owner's design: the first guard of a pair waits for the second before engaging (fight together, already a lever b in M18 r1); a stone sword
   from the Smithy (M19) or a Balance `GUARD_BASE_DAMAGE` above 1.0; three guards at the Village tier.
4. **Harness re-hire priority:** fill an empty guard slot before the farmer and lumberjack (a player under attack would), so the defence
   recovers after a bad night.
5. **Population is the real limit after a bad night:** 8 villagers with 4 hired leaves no reserve. M23's growth (dawn births) is what refills it.
   `defended` could start with 10-12 villagers once M23 lands.

## Files
- Code: `core/sim/DeathRecord`, `DeathLog`, `WallRing`, `MetricsJson` (pure); sim glue `SimMetricsCollector`, `StarterActions` (hire counts),
  `GuardActions` (`wallRing` fixes, scripted tag), `SimulationRun`.
- Tests: L1 `DeathLogTest`; L2 `SimActionGameTests.wallRingRisesOverHigherGround`.
- Raw reports of every run: the builder's scratchpad (`m26-prep/round1`, `round2`), not committed.
