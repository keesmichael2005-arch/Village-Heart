# Village Heart: design (v1)

Agreed with the user on 2026-11-07. This is the source of truth for *what* v1 is. `docs/ROADMAP.md`
says *in what order* it gets built and how each piece is proven. Numbers marked *(tunable)* are
starting values. They live in one balance class in `core/` and get tuned from layer-3 simulation data.

## 1. Pitch and core loop

Craft a **Village Heart** and place it near the bell of a naturally generated village. That village
becomes yours: **one village per world**. From then on:

1. **Build**: put up rooms (or have a Builder construct them from blueprints) and mark each one with a
   crafted **building marker** above its door. A building counts once it is enclosed and furnished
   correctly.
2. **Hire**: assign villagers to jobs. A job needs a valid building with a free **job block** inside it.
3. **Gather**: workers chop, farm, mine and hunt, and carry the results to the shared **stockpile**: the
   chests inside your Storage buildings.
4. **Sustain**: every day the village eats from the stockpile and you pay wages from the Heart's
   emerald **treasury**. Fed, housed and paid villagers work at full speed.
5. **Grow**: meet goals to raise the village tier (Hamlet → Village → Town). Each tier unlocks new
   buildings, jobs, blueprints and higher job levels. Free beds plus spare food mean babies.
6. **Defend**: guards, golems, walls and lamps keep night mobs and pillager raids away.

The player is the village's leader, not a god. They can do any job by hand, and the mod rewards them
for organising instead.

## 2. Claiming

- The Village Heart recipe is unchanged from M0: gold, emeralds and a diamond.
- It must be placed within **16 blocks of a bell** (`ClaimRules.MAX_BELL_DISTANCE`).
- **One claimed village per world.** A second Heart placed anywhere is refused with a message.
- **Claim area**: a sphere of radius **80** around the Heart *(tunable)*. Buildings and work areas must be
  inside it. Two outdoor sites, the **Mineshaft** and the **Hunting Ground**, may be up to **128** from the Heart.
- The placer becomes the **Owner**. Only the Owner can break the Heart. Breaking it releases the village:
  every job is cleared, villagers become ordinary vanilla villagers again, and the treasury drops as items.
  The Heart can then be placed again, at the same village or another one.
- Village state is world saved data: claim position, owner, ranks, treasury, tier, buildings and workers.
  It survives a save and reload.

### Ranks (multiplayer)
Still one village per world, but several players can run it together.

| Rank | Can |
|---|---|
| **Owner** (one) | Everything: set ranks, transfer ownership, break the Heart, withdraw from the treasury |
| **Steward** | Hire and fire, start blueprints, change guard shifts, deposit to the treasury |
| **Resident** | Open Storage and building chests in the claim, deposit to the treasury, view every tab |
| **Visitor** (default) | View the Overview tab only. Cannot open chests inside claimed buildings |

## 3. Buildings

### 3.1 Markers and recipes
- **Blank Marker**: 8 planks (any wood) around an item frame → 1 Blank Marker *(tunable)*.
- **Building markers**: a Blank Marker in the **centre** of the crafting grid, with that building's
  ingredients in the 8 surrounding slots (*corners* + *edges*). Every building has its own marker item
  and texture: a small sign-like plaque that shows the building's icon.
- Place the marker **on the outside wall, above (or beside) a door**. The room behind that door is the
  building. A building may have several doors, but it needs only one marker.
- The marker has three visual states:
  - **grey**: not checked yet
  - **red**: invalid. Right-click it to see the reasons
  - **green**: valid
- Markers for buildings above the current tier can be crafted and placed, but they show red with the
  reason *"Requires Village tier"*.

### 3.2 What makes a room valid (common rules)
Checked by a flood fill of air from the block just inside the marked door:
- **Enclosed**: the fill does not escape. Walls, floor and roof are solid blocks; doors, glass, panes,
  fences, trapdoors and slabs count as wall. A fill that reaches the sky or more than 4096 air blocks fails.
- **Has a door** at the marked doorway.
- **Minimum floor area** per type (table below).
- **Required contents** per type, found anywhere inside the filled volume (blocks or block entities).
- **Not shared**: a room can belong to only one building. Two markers that resolve to the same room make
  both of them invalid.
- Buildings are re-checked in a round-robin (one per second) and immediately when their marker or Heart
  screen is used. Any change to the room is picked up within about `buildings × 1 s`.

Every failure has a clear reason, for example *"Roof missing at (12, 70, -4)"*, *"Needs 1 more chest"*
or *"Room too big (max 4096 air)"*.

Pinned details (M5): Storage counts chests **and barrels** but its reason reads *"Needs 1 more chest"*. A marker outside the claim sphere
turns red without being registered (*"Outside the village: 93 blocks from the Heart (max 80)"*); a marker placed before the village exists is
adopted when the Heart is claimed (loaded chunks) or when it is right-clicked. Releasing the village greys the plaques of loaded buildings;
plaques in unloaded chunks keep their colour until used. A room that reaches an unloaded area gets no new verdict (the old one stays).

### 3.3 Building list

Each **job block** inside a building is one worker slot.

| Building | Tier | Marker ring (corners / edges) | Min floor | Required contents | Jobs |
|---|---|---|---|---|---|
| **House** | Hamlet | planks / white wool | 12 | ≥1 bed, a light source | Each bed = 1 home |
| **Storage** | Hamlet | planks / chest | 9 | ≥2 chests or barrels | none (the stockpile) |
| **Farmhouse** | Hamlet | dirt / wheat | 9 | composter(s), chest | Farmer (per composter) |
| **Lumber Camp** | Hamlet | log / stone axe ×1 + sticks | 9 | **Chopping Block**(s), chest | Lumberjack |
| **Kitchen** | Hamlet | cobblestone / coal | 9 | smoker(s), chest, crafting table | Cook |
| **Builder's Hut** | Village | brick (the item) / ladder | 9 | **Builder's Table**(s), chest | Builder |
| **Mine** | Village | cobblestone / stone pickaxe ×1 + rail | 9 | stonecutter(s), chest, linked Mineshaft | Miner |
| **Hunting Lodge** | Village | leather / arrow | 9 | fletching table(s), chest, linked Hunting Ground | Hunter |
| **Barracks** | Village | cobblestone / iron ingot | 16 | grindstone(s), 1 bed per guard slot, chest | Guard |
| **Market** | Village | white wool / emerald | 9 | **Market Stall**(s), chest | Merchant |
| **Smithy** | Town | iron ingot / smooth stone | 12 | blast furnace(s), anvil, chest | Blacksmith |

**Outdoor sites** do not need a room. Their marker is placed on a post or fence at the site.
- **Mineshaft** (marker: rail / torch). The marker sits at a player-dug entrance. Connected air must
  descend **at least 12 blocks** below the marker, with **≥8 rails** inside it and a light source.
  The deepest point reached sets the ore table (§5.3). It links to the nearest Mine within 48 blocks.
  The fill goes down from the marker cell through open air within 16 blocks sideways (at most 4096 cells); near the surface it only
  spreads one block around the marker, so the hole has to be right under (or next to) the post. A light source also counts when it is
  embedded in the shaft wall.
- **Hunting Ground** (marker: stick / feather). Valid if there are **≥40 grass, leaf or log blocks**
  within 12 blocks and it is outside the House and Farmhouse rooms. It links to the nearest Hunting Lodge.
- **Training Yard** (marker: hay bale / wooden sword). Valid if there are **≥2 armor stands or target
  blocks** within 6 blocks. It links to the nearest Barracks. It is optional, but guards only gain XP
  by sparring here.

### 3.4 Job blocks: vanilla first
Vanilla workstations are reused where the job fits. Those inside a valid building are **reserved**: vanilla
villagers cannot claim them, and a villager assigned there takes the mod's profession for that job.

| Job | Job block | Source |
|---|---|---|
| Farmer | Composter | vanilla |
| Cook | Smoker | vanilla |
| Hunter | Fletching Table | vanilla |
| Miner | Stonecutter | vanilla |
| Guard | Grindstone | vanilla |
| Blacksmith | Blast Furnace | vanilla |
| Lumberjack | **Chopping Block** | new (log stump with an axe in it) |
| Builder | **Builder's Table** | new (opens the blueprint list) |
| Merchant | **Market Stall** | new |

These vanilla workstations have no mod job and keep only their normal trading use: lectern, cartography
table, brewing stand, loom, cauldron, barrel and smithing table. The same workstation **outside** a mod
building behaves exactly as in vanilla.

## 4. Villagers and jobs (common rules)

- **Hiring**: on the Heart screen's Villagers tab, pick an adult villager in the claim and then a job with
  a free slot. Ranks Steward and above can do this. The villager loses its vanilla profession and
  **trades while employed**. Firing them makes them unemployed. They can then pick up a vanilla
  profession again, but their old trades are not restored.
- **Workers are vanilla villager entities** with mod data attached. Vanilla models only: job
  identity comes from the profession texture or held item, plus particles.
- **Day shape** *(tunable, ticks of the 24000-tick day)*: 0–1000 wake and eat, 1000–11000 work, 11000–12000 go home
  and eat dinner, 12000+ sleep in a bed. Guards follow shifts instead (§5.6).
- **Delivery**: a worker carries up to 1 stack in its inventory. When full, or at the end of the day,
  it walks to the nearest Storage, deposits, and returns. Inputs (tools, seeds, raw food, ore) are fetched
  the same way.
- **Tools**: a tool placed in the building's chest is used by its worker. A better tier works faster:
  wood ×1.0, stone ×1.25, iron ×1.5, diamond ×1.75 *(tunable)*. Tools lose durability as they are used.
  Some jobs can work without a tool at ×0.5; others cannot work at all without one (marked *requires tool* below).
- **Levels 1–3**: workers gain XP per unit of work. Level 2 at 100 XP and level 3 at 300 XP *(tunable)*. Speed is
  ×1.0 / ×1.25 / ×1.5. Tier caps: Hamlet caps at level 1, Village at 2, Town at 3.
- **Needs** (status icons on the Heart screen, not a mood number):
  - **Fed**: ate ≥10 nutrition at the last dinner. Hungry 1 day → work at ×0.5. Hungry 2+ days → stops
    working. **Never dies of hunger.**
  - **Housed**: has a bed in a valid House or their own building. If not → work at ×0.75 and cannot have children.
  - **Paid**: wage paid at the last dawn. If not → **on strike** (no work) until the treasury can pay. Owed wages
    are not carried over.
- **Work speed** = tool × level × needs multipliers. All of this rate math is in `core/` and unit-tested.

## 5. Jobs in detail

The work style is **hybrid**. Lumberjacks, farmers, cooks, builders, guards and blacksmiths act in the
world. Miners and hunters *go away and come back*: you see them walk to the site, vanish into it for a
while, and come back carrying goods, which are produced from a table.

### 5.1 Lumberjack (Lumber Camp, Chopping Block, *requires axe*)
Finds the nearest **natural tree** in the work area (within 32 blocks of the camp): a log column touching
leaves. It walks there and fells the **whole tree**, all connected logs. Leaves decay as in vanilla.
It collects the drops (logs, saplings, sticks, apples) and **replants a sapling** on the stump spot.
Logs that the player placed are never touched: logs in valid rooms and logs without leaves contact are skipped.
Pace: about 1 log per 2 s at level 1 with a stone axe *(tunable)*.

### 5.2 Farmer (Farmhouse, Composter)
Works farmland within 24 blocks of the Farmhouse. It harvests **mature** wheat, carrots, potatoes and
beetroots and replants from the yield. It composts surplus seeds into bone meal and uses the bone meal on
young crops. It **does not make new fields** in v1: the player or a Builder's Farm Plot does that. No tool is needed (a hoe gives ×1.25).

### 5.3 Miner (Mine + Mineshaft, Stonecutter, *requires pickaxe*)
It walks from the Mine to the Mineshaft and disappears inside. It reappears every **2 in-game hours**
with one *haul*, which it delivers to Storage. Later the delivery is shown as pushing a minecart along
the rails (cosmetic, post-M-mining polish). The haul comes from a table chosen by the shaft's
**deepest point** and the pickaxe tier *(tunable)*:

| Depth band | Per haul at level 1 with a stone pickaxe |
|---|---|
| Y ≥ 40 | 16 cobblestone, 2 coal, 1 raw copper, 30% 1 raw iron |
| 0 ≤ Y < 40 | 12 cobblestone, 2 coal, 2 raw copper, 2 raw iron, 20% redstone, 10% lapis |
| Y < 0 | 12 cobbled deepslate, 1 coal, 3 raw iron, 30% redstone, 20% lapis, 20% raw gold, 2% diamond (iron pick+) |

There is no real tunnelling in v1 (post-v1 idea).

### 5.4 Hunter (Hunting Lodge + Hunting Ground, Fletching Table, *requires bow or sword*)
It walks to the Hunting Ground, is gone for **3 in-game hours** per trip, and returns with
2–4 raw meat (beef, pork, mutton or chicken, weighted by nearby biome), 1–2 leather, 1–2 feathers and 0–1 rabbit hide.
With a bow, it uses arrows from the chest (1 per trip) and gets a +1 meat bonus. There are 2 trips per day at level 1.

### 5.5 Cook (Kitchen, Smoker)
It takes raw food from the stockpile, processes it at the Kitchen's smoker and crafting table, and stores the result:
raw meat → cooked; potato → baked potato; 3 wheat → bread; 6 beetroot + bowl → beetroot soup.
Pace: 8 items per in-game hour at level 1. Cooking needs fuel (coal, charcoal or logs) from the stockpile.

### 5.6 Guard (Barracks, Grindstone)
- **Shifts**: each guard is on **day** or **night** shift. The default auto-balances 50/50, and
  Stewards can change it per guard. A guard sleeps in a Barracks bed during its off shift, so night-shift guards sleep in the day.
- **On shift**: it patrols between the Heart, Watchtowers and lamp posts. It attacks hostile mobs and
  raiders inside the claim. During a raid every guard is called on shift.
- **Training**: off-shift time while awake is spent sparring at the Training Yard (guards in pairs, or
  against armor stands), which earns XP. Guards also earn XP from kills.
- **Level perks**: max health **20 / 30 / 40** and melee damage +0 / +1 / +2 *(tunable)*.
- **Gear**: at the start of each shift it equips the best armour and weapon available in the stockpile
  and Barracks chest (leather < chainmail < iron < diamond), and returns damaged gear when off shift. There is no bow combat in v1.

### 5.7 Blacksmith (Smithy, Blast Furnace), Town tier
- **Smelts** raw iron, copper and gold from the stockpile into ingots (it uses fuel).
- **Makes gear** from stockpile ingots and leather: iron tools, iron armour and leather armour. It keeps
  a *(tunable)* target, such as "2 spare iron axes, 1 set of armour per guard", and works towards it.
- **Repairs** tools and armour returned to the Smithy chest with ingots.
- **Builds iron golems**: 36 iron ingots + 1 carved pumpkin, 1 in-game day of work. The cap is 1 golem per 5
  villagers. Vanilla villager golem spawning is **disabled inside the claim**, so golems come from the Smithy.
- **Repairs golems**: golems below 50% health walk to the Smithy, and the Blacksmith uses ingots to heal them
  (25 HP per ingot, as in vanilla).

### 5.8 Builder (Builder's Hut, Builder's Table)
- At the Builder's Table, a Steward+ picks a **blueprint** and receives a **Blueprint item**. Using it on the
  ground shows the footprint as particles. A second click (sneak to rotate) confirms the **build site**.
  Wall Segments take two clicks (start and end, axis-aligned).
- The builder **reserves materials** in the stockpile, fetches them, clears natural blocks (dirt, stone,
  plants and leaves, with drops returned to the stockpile) and places blocks layer by layer. It will
  **never break player-placed blocks**: a site that overlaps a valid room or claimed building is refused.
- Pace: 1 block per second at level 1. If materials run out, it pauses and the reason appears on the Buildings tab.
- **Blueprints in v1** are stored as data (palette + layers) in `core/`, so they can be unit-tested:

| Blueprint | Tier | Notes |
|---|---|---|
| Small House | Village | 5×5 room, 1 bed, door, torch, House marker included |
| Storage Shed | Village | 5×5 room, 2 chests, Storage marker included |
| Farm Plot | Village | 9×9 farmland around a water block, fenced, with a gate |
| Wall Segment | Village | cobblestone, 3 high, any length up to 32 |
| Gate | Village | wall section with a 3-wide fence-gate opening |
| Lamp Post | Village | fence post + lantern. Stops mob spawns nearby |
| Watchtower | Town | 3×3 base, 7 high, ladder, railed platform. A guard patrol point |

  Built rooms include their marker and furniture in the material list, so they become valid on completion.

### 5.9 Merchant (Market, Market Stall)
It sells stockpile **surplus** for emeralds that go into the treasury. Surplus means stock above a reserve:
3 days of food, and 64 of everything else *(tunable, adjustable on the Stockpile tab)*. It sells at fixed prices
(e.g. 16 logs, 20 wheat, 32 cobblestone, 4 iron ingots or 10 cooked meat = 1 emerald). The daily cap is 8 / 12 / 16
emeralds by level. Unemployed villagers keep their vanilla trades.

## 6. Economy

- **Stockpile** = the combined contents of every chest and barrel inside valid **Storage** buildings.
  The player can open these chests normally. Workers put new goods in the nearest Storage with space.
  Building chests (tools, inputs) are *not* part of the stockpile.
  If all Storage is full, workers keep the goods in their inventory and stop gathering (shown as an alert).
  Rules as built (M6): the stockpile is a *live view* over the chests and barrels (chest, trapped chest and copper chests;
  not ender chests or shulker boxes) that the latest scan of a valid Storage lists; nothing is stored in the mod, so it can
  never drift from the chests.
  - A chest that is also in the scan of any other room building (a House, a Lumber Camp's tool chest, even in a wall shared
    with a Storage) is *not* stockpile. A Storage that ends up with no readable container is "unknown", not full.
    Right after a restart a Storage is unknown while a neighbouring room building (marker within 32 blocks) has no scan yet.
  - A double chest is two containers (each half has its own 27 slots); a half outside the room is not part of the stockpile.
  - Items are the same only with equal components (a renamed paper, an enchanted book or a damaged sword is a separate item).
  - *Nearest* Storage = nearest door (straight line) among those with space for that item; a deposit spills into the next
    nearest. Fill order inside a Storage: partial stacks first, then empty slots, in container then slot order. What does not
    fit is returned to the caller. Withdraw takes from the nearest Storage that has the item.
  - **Storage full** alert = no empty slot in any readable Storage, or a deposit that found no room within the last 10 s.
    It means "no empty slot", not per item: a partial stack can still take more of its item. *No Storage* alert when the
    village has no valid Storage. Shulker boxes and bundles count as one item, their contents are not counted (v1).
  - Reservations (a worker or build claims items, M20) are claims, not locks: the items stay in the chests.
- **Food**: villagers eat at **dinner (tick 11000)**. Each villager needs **10 nutrition** per day, using vanilla
  food values (bread 5, baked potato 5, cooked beef 8, carrot 3, apple 4, raw meat counts as its vanilla raw
  value). The best foods are eaten first, cooked before raw. Children need half.
  *Food days left* = stockpile nutrition ÷ (population × 10).
- **Treasury**: emeralds deposited at the Heart (Resident+). Only the Owner can withdraw.
- **Wages**: paid at **dawn**: **1 / 1 / 2 emeralds** per day for levels 1 / 2 / 3 *(tunable)*. If the treasury is short,
  workers are paid in hiring order, and the rest strike for the day.
- **Simulation is frozen when the village's chunks are unloaded**, as in vanilla. There is no catch-up. Dinner and
  dawn events fire only while the Heart chunk is loaded.

## 7. Population growth
At each dawn, if there is **≥1 free bed** in a valid House, **food days left ≥3**, and ≥2 adult villagers who are fed
and housed, the mod makes two of them willing to breed (vanilla breeding). The cap is 1 birth per day.
Babies are normal vanilla villagers and can be hired as adults.

## 8. Threats and survival
- **Night mobs** (vanilla): villagers go indoors at night (vanilla behaviour). Guards on night shift patrol.
  Lamp posts and walls reduce spawns and access.
- **Raids** (vanilla Bad Omen raids, unchanged). The Heart screen shows a raid alert, all guards come on duty,
  golems fight, and workers go indoors.
- **Hunger and homelessness**: these slow or stop work (§4). Villagers **do not die** of either.
- Deaths are tracked (cause and day) for the Overview tab and the simulation metrics.

## 9. Progression: village tiers

| Tier | Reached when | Unlocks |
|---|---|---|
| **Hamlet** | Heart placed | House, Storage, Farmhouse, Lumber Camp, Kitchen. Job level cap 1 |
| **Village** | population ≥6, ≥3 workers, valid Storage + Farmhouse + Kitchen, fed 3 days in a row | Builder's Hut, Mine + Mineshaft, Hunting Lodge + Hunting Ground, Barracks + Training Yard, Market. Blueprints except Watchtower. Level cap 2 |
| **Town** | population ≥12, ≥8 workers, ≥2 guards, fed 7 days in a row, treasury ≥32 at some dawn | Smithy (golems, gear), Watchtower. Level cap 3 |

The tier never goes down in v1. The Overview tab shows each goal's progress.

## 10. UI

### Heart screen (right-click the Heart): tabbed
- **Overview**: name and tier, next-tier goal checklist, population/beds, workers/slots, food days left,
  treasury and tomorrow's wage bill, and alerts (raid, starving, storage full, strikes, invalid buildings).
- **Villagers**: a list with name, job, level, XP bar, needs icons (fed, housed, paid) and current activity.
  Actions: assign job, fire, set guard shift.
- **Buildings**: every marker, with type, position, valid or invalid and the reasons, worker slots used,
  and an active blueprint with its missing materials.
- **Stockpile**: item totals across Storage, food nutrition total, and surplus reserve settings.
- **Ranks** (Owner only): players and their ranks.

### In the world
- Marker colours (grey, red, green). Right-clicking a marker prints its validation report.
- Particles for work-site status (chopping, hauling).
- Blueprint footprint particles while placing.

## 11. Explicitly OUT of v1
- Fire, weather events, crop blight, seasons
- Real tunnel mining and minecart logistics (cosmetic minecart push may land late in v1)
- Multiple villages per world, founding a village without a bell
- Villager relationships, marriage, families, personalities
- Custom villager models and animations (vanilla models + held items + particles only)
- Copying or recording player builds as blueprints
- Bow-wielding guards, ranged combat AI
- Catch-up simulation while chunks are unloaded
- Config UI (the balance values are in code; a config file is allowed if it is cheap)

## 12. Technical notes for implementers
- **Core first**: rules, rates, tiers, needs, wages, food, blueprint data, room flood fill (over a
  `BlockView`-style interface with a fake grid in tests) and haul tables live in `dev.villageheart.core`
  and are unit-tested.
- **Villager control is the riskiest part.** Villagers use the `Brain` system, not goal selectors. The first
  jobs milestone starts with a spike to choose the approach: (a) add mod activities and behaviours to the
  villager brain via mixin, or (b) a separate mod-driven task runner that suspends the brain while
  working. Write the decision down in this file. Either way, reserving workstations means handling the
  POI system so that vanilla villagers cannot claim job blocks inside mod buildings.
  **Decision (M7 spike, 2026-11-07): (a), a brain mixin.** One mixin at the head of `Villager.registerBrainGoals` gives
  hired villagers a mod-built brain: the vanilla door, panic, raid, bell, home and sleep behaviours, plus mod work
  activities, and none of vanilla's job-site/profession behaviours (`ResetProfession` otherwise resets a mod profession
  within 200 ticks). Unhired villagers keep the untouched vanilla brain. The day shape stays in `core` (no data timeline).
  (b) was rejected: with the brain suspended a worker cannot open doors (stuck at a closed door in the spike), and it
  would still need a mixin. Workstations in valid buildings are reserved by the mod holding their POI ticket, with a
  retrying reconcile that evicts vanilla claimants (POI updates are deferred by the server). Mod professions have
  `heldJobSite`/`acquirableJobSite` = none and no trade table. Evidence and details: `docs/plans/M7.md` §3.1.
- Worker data uses Fabric data attachments on the entity. Village data uses world saved data.
- **Performance budget**: village logic averages under **1 ms per tick** with 20 villagers, measured by layer 3.
- Every number marked *(tunable)* belongs in one `core` balance class.
