verdict: PASS

# tool-speed review, round 1 (verifier)

Reviewed: 596cc01 on `tool-speed`, only its own diff (`git diff origin/backlog-2...origin/tool-speed`, without the dev merge):
`Balance` (table + two base constants), 7 test files, DESIGN/TESTING/ROADMAP/PROGRESS.

**Condition: merge tool-speed after backlog-2 (or merge origin/backlog-2 into it first).** On its own, 596cc01 sits on backlog-2 at
dc4935c, which lacks the backlog-2 r2 fix f2cd64b (WalkTo jam clock). My first L2 run on 596cc01 had exactly the red that f2cd64b fixes
(`twoMinersAndBrokenMarker`: "both miners go down took 995 ticks, bound 900", a descent, before any tool-paced work). The merge of
origin/backlog-2 into 596cc01 (only `docs/PROGRESS.md` conflicts, both sides kept) passes the full `scripts/verify.sh`.

## Runs
| Run | Tree | Result |
|---|---|---|
| L1 `./gradlew build` (runs L2 too) | 596cc01 | L1 green; L2 1 red: the inherited `twoMinersAndBrokenMarker` descent bound (see above) |
| L2 full #2 (foreground) | 596cc01 | 232/232 green |
| L2 full #3 (foreground) | 596cc01 | 232/232 green |
| `scripts/verify.sh` (L1, L2 #4, L3 self-test, L4, gates, perf) | 596cc01 + origin/backlog-2 (local merge d52585d, not pushed) | **exit 0**: 234/234 L2, L4 green, 0 lost wakeups, perf green |

`build/run/gameTest/world` was deleted before every run. Load average about 5 on 4 cores (other sessions). L3 skipped (the `starter`
scenario exists only on origin/m11).

Measured in every green run: side by side wood 48 / stone 88-89 / iron 128 logs, **iron/stone 1.438-1.455** (bound 1.3), iron/wood 2.67;
`paceByTier` gaps wood 80, iron 26-27, diamond 20 exactly; level-up gaps 40, 40, 32, 32, 32; `twoMinersAndBrokenMarker` iron haul 1325,
stone 1994 underground ticks.

L1 mutation (restored afterwards): `MINER_HAUL_UNITS` 2500 and gold vanilla speed 2 → 9 unit tests red (`stonePaceIsUnchanged`,
`everyMultiplierIsTheVanillaMiningSpeedHalved`, `fullToolLevelNeedsTable`, AwayCheckTest ×2, AwayProgressTest ×4).

## 1. Vanilla speeds, checked against the 1.21.11 jar
`javap -c net.minecraft.world.item.ToolMaterial` (loom `minecraft-common-1.21.11` jar), static initialiser, constructor
`(TagKey incorrect, int durability, float speed, float attackBonus, int enchant, TagKey repair)`:

| Material | durability | speed | `Balance.VANILLA_TOOL_SPEED` | multiplier |
|---|---|---|---|---|
| WOOD | 59 | 2.0 | 2 | 1.0 |
| STONE | 131 | 4.0 | 4 | 2.0 |
| COPPER | 190 | 5.0 | 5 | 2.5 |
| IRON | 250 | 6.0 | 6 | 3.0 |
| DIAMOND | 1561 | 8.0 | 8 | 4.0 |
| GOLD | 32 | 12.0 | 12 | 6.0 |
| NETHERITE | 2031 | 9.0 | 9 | 4.5 |

All seven match, in `ToolTier` ordinal order (wood, stone, copper, gold, iron, diamond, netherite).

## 2. Every user of the multipliers
- `ToolRules.multiplier` → `Balance.TOOL_SPEED`; only caller is `WorkSpeed.multiplier`.
- **M9 lumberjack** (`LumberJob`, `LumberRuntime` `WorkClock(BASE_TICKS_PER_LOG)`): tier-paced, rescaled 50 → 80. Correct.
- **M16 miner** (`MinerSystem` → `AwayCheck.check(in)` → `MINER_HAUL_UNITS`; `AwayProgress` 2-arg methods): rescaled 2500 → 4000. Correct.
- **M10 farmer** (`FarmJob` ×2): `Mode.BONUS` → flat `HOE_BONUS` 1.25, never reads `TOOL_SPEED`. Unchanged, correct.
- **M17 hunter**: `HuntPace` uses `WorkSpeed.untooled`, `HUNTER_TRIP_UNITS` 3000 untouched. Tier-independent, unchanged.
- **M15 levels**: `LEVEL_SPEED` untouched; XP is per log / item / harvest, not per work unit, so XP rates are unchanged.
- **M20 builder** (`BuilderJob`, also on origin/m20): `WorkSpeed.untooled`, no tool. Unaffected.
- `NO_TOOL_SPEED` 0.5: no job uses `Mode.OPTIONAL` today, so it is dead for now (a future optional-tool job would be 0.25× stone instead of 0.4×).
- **Plans**: origin/m19-plan (A9) and origin/m19a-plan (A1) refer to `Balance.TOOL_SPEED`, `ToolRules.multiplier`, `MINER_HAUL_UNITS`,
  `BASE_TICKS_PER_LOG`, `WorkSpeed.untooled`. All these names are kept (`VANILLA_TOOL_SPEED` and `TOOL_SPEED_DIVISOR` are additions). Their
  context tables still quote the old values {1.0, 1.25, ...}, but both plans say this follow-up changes them and that they do not depend on the
  numbers. M19a T-L2-5 computes its bound from `MINER_HAUL_UNITS / multiplier(WOOD)`, which gives the intended 4000.

## 3. Stone pace is exactly unchanged
`ToolRulesTest.stonePaceIsUnchanged`: 80 / 2.0 = 40 ticks per log, 4000 / 2.0 = 2000 ticks per haul. Per 20-tick miner step a stone pickaxe
earns 40 units out of 4000 (before: 25 out of 2500): the same 100 steps. In game: stone gaps of exactly 40, stone haul 1994.

## 4. CLAUDE.md rule 4: every changed test
| Test | Change | Follows from the table? |
|---|---|---|
| `MinerGameTests.minerComesUpAtDayEnd` progress | `underground*1.25 ±25` → `underground*2.0 ±40` | ±25 / 1.25 = ±20 ticks, ±40 / 2.0 = ±20 ticks: **the same tolerance in ticks, not looser** |
| same, second haul | `(2500-p)/1.25 ±20` → `(4000-p)/2.0 ±20` (ticks) | same. Measured 961 vs 980 and 981 vs 1000 (off by 19, the entity-id step phase), the same arithmetic as before (100 steps) |
| `awayMinerSurvivesReload` wait | 1000 → 1600 units | 1000 / 1.25 = 800 = 1600 / 2.0 ticks: the same 800 ticks. The 1300 "comes up on time" bound and the 2000 ±45 assertion are **unchanged**; the builder's first-run red (1495 > 1300) fits a wait left at 1000 units (500 ticks), so raising the wait restores the old timing and the bound stays honest |
| `twoMinersAndBrokenMarker` | iron 1667 → 1333 ±25 | 4000 / (3.0·20) = 66.7 → 67 steps (1321-1340); old 84 steps (1667). Tolerance unchanged. Measured 1325 |
| `LevelGameTests.ironAxeOutFellsStoneSideBySide` | iron ≥ 1.1× stone → **≥ 1.3×** | stricter (ROADMAP restored). The ≥1.3× wood bound is unchanged (now an easy 2.67), not weakened |
| `LevelGameTests.levelUpSpeedsUpFelling` | message only (32 = 80 / (2.0·1.25)) | yes |
| `LumberjackGameTests.paceByTier` | {50, 33.3, 28.6} → {80, 26.7, 20} | 80/1, 80/3, 80/4; tolerance unchanged |
| `BalanceLevelsTest`, `ToolRulesTest.multipliersFollowTheBalanceTable` | literals 2.0 / 3.0 / 4.0 | yes; plus new `everyMultiplierIsTheVanillaMiningSpeedHalved` (literal vanilla table) and `stonePaceIsUnchanged` |
| `WorkSpeedTest` | wood 80, iron 100 logs 2667, L2 2.5, L3 3.0, full table from literals, iron L3 4.5 → 1778 | 8000/3 = 2666.7, 8000/4.5 = 1777.8; tolerances unchanged |
| `AwayCheckTest` due-haul ×2 | (2475, ×1.25) → (3960, ×2.0) | 3960 + 40 = 4000: still exactly due after one step |
| `AwayCheckTest.theHunterNumbers...` (renamed) | old: at 2500 the miner is due, the hunter not. New: at 3980 the miner is due; at 2980 the miner stays **and** the hunter is due | still proves the two numbers differ in both directions (a hunter on 4000, or a miner on 3000, goes red). The trailing `at3000` block now duplicates `at2980` (harmless redundancy, not a weakening) |
| `AwayProgressTest` | wood 125 → 200 steps; iron ×3.0: 67 steps, remainder 20; 25 000 steps → 375 hauls; 2-arg probes 3990/3900 | 4000/20; 67·60 = 4020; 1.5 M / 4000; 3990+20 = 4010 (1 haul, 10 left), 3920 (0) |

No test deleted, no assertion weakened.

## 5. Gameplay sanity
- **Gold ×6** is vanilla-faithful. Gold tools have 32 durability: a gold pickaxe lasts 3 hauls (10 wear each, 667 ticks each), a gold axe
  32 logs. Fine as a "fast but fragile" option the smith will not make (M19 plan). One inconsistency to note (not blocking):
  `ToolAllocation.assign` ranks tools by `ToolTier.ordinal()`, so gold ranks between copper and iron: with an iron and a gold pickaxe the first
  hired worker gets the iron one although gold is twice as fast. Defensible (durability), but it is now a deliberate choice that nobody has made.
- **Wood 1.6× slower** (80 ticks per log, 4000-tick hauls, about 2 hauls per work day instead of 4 with stone). Nothing on this tree gives a
  starter kit; the only wooden-tool tests are `paceByTier`, the side-by-side and the miner wooden-pickaxe case, all updated or tier-agnostic.
  origin/m11's `starter` scenario was not run (not merged); m11 has no wooden-tool code beyond what is here. M19a's self-crafted wooden
  pickaxe is already planned with 4000-tick hauls.

## Fix items
None for this diff. Merge order (backlog-2 first) is the only condition.

## Notes for BACKLOG (not blocking)
1. `ToolAllocation` ranks gold below iron/diamond/netherite by enum order although its work speed is now the highest (see §5).
2. Saved progress is in units: a miner underground at the moment of the update keeps its old-scale units (e.g. 2400 of the old 2500 is now
   60 % of 4000), so that one haul takes longer. Harmless before a release; worth a line if worlds from before this change matter.
3. `minerComesUpAtDayEnd`'s second assertion is consistently 19 ticks off with a ±20 bound (pre-existing, the step phase; identical before
   this change).
4. `AwayCheckTest.theHunterNumbersMakeATripDueAt3000AndTheMinersAt4000`: the `at3000` inputs equal `at2980`; drop one or probe 3000 exactly.
5. `NO_TOOL_SPEED` 0.5 was not rescaled; unused today (no `Mode.OPTIONAL` job), but a future optional-tool job would be 0.25× a stone tool.
