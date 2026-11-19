# Code map

Where each feature lives. Paths are under `src/main/java/dev/villageheart/` unless noted.

- **Rules (pure Java):** `core/<feature>/`, unit-tested in `src/test/java/dev/villageheart/core/<feature>/`.
- **Minecraft glue:** the matching package outside `core/`, often `worker/<job>/` for jobs.
- **In-world tests:** `src/gametest/java/dev/villageheart/gametest/<Feature>GameTests.java`. Each feature has a `<Feature>TestSupport` helper.
- **Client tests and screenshots:** the matching `<Feature>ClientTests.java` in the same folder.

Start with the rules package. Most behaviour changes, numbers included, belong there.

## Everywhere

| What | Where |
|---|---|
| Mod entry point, registration | `VillageHeart.java` |
| **Every tunable number** (rates, wages, timings, raid sizes, tier goals) | `core/Balance.java` |
| The saved village (claim, owner, treasury, tier, buildings, workers) | `core/VillageState.java`, `core/WorkerRecord.java`, `core/BuildingRecord.java`; saved data glue in `world/` |
| Claim radius and geometry | `core/ClaimRules.java`, `core/ClaimGeometry.java`, `core/WorldClaim.java` |
| Mixins into vanilla | `mixin/` (main), `src/gametest/java/.../gametest/mixin/` (test-only) |
| Blocks and items (Heart, markers, Chopping Block, Builder's Table, Market Stall, Blueprints) | `block/`, `item/`, `build/BlueprintItem.java` |
| Recipes, loot, tags | `src/main/resources/data/villageheart/` |
| Textures, models, lang (all player-visible text) | `src/main/resources/assets/villageheart/` (`lang/en_us.json`) |
| Client: Heart screen, rendering, art | `src/client/java/dev/villageheart/client/` (`heart/`, `build/`, `art/`) |

## Features

| Feature | Rules (`core/…`) | Glue | GameTests (`…GameTests`) |
|---|---|---|---|
| Heart block and screen tabs, networking | `heart/` | `heart/`, `heart/net/`, client `heart/` | HeartAction, HeartScreen, VillageHeart |
| Ranks | `rank/`, `Rank.java` | `rank/` | Rank |
| Markers and room validation (flood fill) | `room/`, `building/` | `room/`, `building/` | Room, Building, Marker |
| Stockpile (Storage chests) | `stock/` | `stock/` | Stockpile, StockpileFacade |
| Hiring, worker status, the work day | `worker/`, `job/` | `worker/`, `worker/job/` | Worker |
| Levels and XP | `job/`, `tier/` | `worker/` | Level |
| Food, dinner, needs (fed, housed, paid) | `food/`, `needs/` | `food/`, `needs/` | Needs, NeedsHousing, NeedsWork |
| Wages, treasury, strikes | `economy/` (`StrikeRules`) | `economy/`, `needs/PaidSource.java` | Wage |
| Tiers (Hamlet, Village, Town) | `tier/`, `Tier.java` | `tier/` | Tier |
| Lumberjack, trees | `lumber/`, `tree/`, `TreeTask.java` | `worker/lumber/` | Lumberjack |
| Farmer | `farm/` | `worker/farm/` | Farmer |
| Cook (Kitchen) | `cook/` | `worker/cook/` | Cook |
| Miner (Mine and Mineshaft) | `mine/` (`HaulTable`) | `worker/mine/` | Miner |
| Hunter (Hunting Lodge and Ground) | `hunt/` | `worker/hunt/` | Hunter |
| Self-made wood and stone tools, tool wear | `gear/` | `worker/gear/` | SelfCraft* |
| Merchant (Market) | `market/` (`PriceTable`) | `market/` | Market |
| Builder and blueprints, the Blueprint Book, walls, gates | `blueprint/` (`BlueprintCatalog`), `defence/` | `build/` (`BlueprintBookItem`), `build/net/`, `worker/build/`, `defence/` | Build, Defence, Gate |
| Guards (shifts, alarm, rations, training) | `guard/` | `worker/guard/` | Guard |
| Blacksmith, guard gear, iron golems | `smith/`, `gear/` | `worker/smith/`, `golem/` | Smith |
| Raids: warnings, shelter, stuck raiders | `raid/` | `raid/`, `worker/shelter/` | Raid, RandomRaid, RaidLayout, ThreatShelter, ThreatWarning, ThreatStartMessage, StuckRaider |
| Zombie sieges, wall damage, repair | `siege/` | `siege/` | Siege, SiegeLure, SiegeRepair, ZombieSiege |
| Population growth (births, move-ins) | `growth/` | `growth/` | Growth* |
| Death log | `death/` | `death/` | DeathLog |
| Day clock helpers | `time/` | — | — |
| 2-player session | — | — | `MultiplayerClientTests` (L4) |

## Simulations (L3)

| What | Where |
|---|---|
| Scenario files | `sim/scenarios/*.json` (`full` is the 30-day economy gate) |
| Scenario format, metrics, assertions (pure) | `core/sim/` (`ScenarioParser`, `ActionSpecs`, `MetricNames`, `AssertionFunctions`) |
| Runner and scripted actions | `src/gametest/java/dev/villageheart/sim/` (`SimulationRun`, `StarterActions`, `FullActions`) |
| Run one | `scripts/simulate.sh <scenario>`; the report goes to `reports/sim/<scenario>.json` |
| Balance results so far | `docs/M26-results.md`, safe ranges in `docs/M26-bounds.md` |

## Docs

| Doc | Use it for |
|---|---|
| `docs/DESIGN.md` | How every system is meant to work, with the owner's decisions inline |
| `docs/PLAYER_GUIDE.md` | What the player sees. Keep it in sync with behaviour changes |
| `docs/TESTING.md` | Test layers, helpers, and flake rules |
| `docs/BACKLOG.md` | Known issues and ideas not yet done |
| `docs/plans/`, `docs/reviews/` | Why things are the way they are (milestone plans and their reviews) |
