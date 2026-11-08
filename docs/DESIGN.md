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

As built in M24 (`docs/plans/M24.md`): each permission has one minimum rank (`Permission.minimum()`, cumulative as the table). The Owner gives a rank in the
Ranks tab (online or offline players the village has seen; at most 63 ranked players, the name book keeps 64) and can **transfer ownership** to a
Steward or Resident after a second click; the old Owner becomes a Steward and the village, treasury, buildings and workers stay as they are.
An operator can take over an abandoned village with `/villageheart owner <player>` (and `rank`, `members`); ops get no other bypass.
**Chest rule:** a Visitor cannot open or break a block that holds items (chests, barrels, furnaces, hoppers, shulker boxes, brewing stands,
jukeboxes, decorated pots, ... but not the personal ender chest) inside a room building (the last enclosed room scan; a 16-block box around the marker
until the first scan), and cannot break a registered building marker. Sneak-placing a block against a chest still works. **This is not grief
protection:** a Visitor can still break a wall or floor, put a hopper under a Storage chest, and hoppers, pistons, explosions, copper golems, chest
minecarts and creative pick-block are not stopped. Also not stopped, because none of them goes through a player open or break: a Visitor's projectile
(arrow, snowball, trident, wind charge) breaking a protected decorated pot (vanilla `onProjectileHit` destroys it without the player-break event);
a button sneak-placed on a protected dispenser, dropper or crafter (sneak-placing against a container is allowed) that fires items out; and lectern
books, item frames and armor stands (not `Container` block entities, or entities). Outdoor sites have no protection. Servers that need more run a claim mod.

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
  The Farmer is the exception to both: its hoe is only a bonus (×1.25), and without one it works at ×1.0 (§5.2).
- **Levels 1–3**: workers gain XP per unit of work. Level 2 at 100 XP and level 3 at 300 XP *(tunable)*. Speed is
  ×1.0 / ×1.25 / ×1.5. Tier caps: Hamlet caps at level 1, Village at 2, Town at 3.

  Rules as built (M15): XP is cumulative and **banked up to 300** even when the tier holds the level back; a worker with 300 XP at Hamlet
  stays level 1 and becomes level 2 within a second of the Village tier-up and level 3 at Town. Levels are **never lowered** (a level
  written above the cap, which only tests and admin tools do, is kept and used). XP per unit *(tunable)*: log felled 1, crop harvested 1,
  cooked item 1, mined item 1, hunting trip 5, kill 5, sparring unit 1, smithed item 2, block placed 0.2, trade 2 (fractions carry per worker;
  walking, replanting and deposit trips earn nothing). **Firing a worker drops its level and XP**; a rehire starts at level 1 with 0 XP;
  unloaded workers earn nothing. A level-up shows happy-villager particles and the celebrate sound, tells every online Resident or above
  in chat (at most 3 lines per tick, then one "and N more" line), and moves the vanilla level badge on the villager's belt (stone, iron, gold).
  The first time a worker has the XP for a level its tier forbids, the same members hear "needs a Village / a Town"; the Villagers tab shows
  that worker's XP bar full and gold, with a tooltip.
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

  Rules as built (M9): a **natural tree** is a 26-connected set of natural logs (`#overworld_natural_logs`; no stripped logs, wood blocks or
  stems) that touches at least 3 non-persistent leaf blocks face to face, whose logs resting on soil (`#dirt`, farmland) all lie inside one
  2x2 footprint at its lowest y, with no log inside a valid building's room (interior or walls, any building type), at most 192 logs, 17
  wide and 48 tall. The base rule is what protects log walls and cabins; a lone log post on soil under 3 or more natural leaves is the one
  documented false positive, and mangroves, trees on sand and trees merged with player logs are skipped. It fells top-down (base last)
  inside the work area (32 blocks horizontally, 16 vertically of the Chopping Block, nearest first) with a work clock: stone 1.25, copper
  1.375, iron 1.5, gold 1.0, diamond 1.75, netherite 2.0, times level, times needs (`WorkSpeed`). **Tools stay in the camp chest** and wear
  out there, 1 durability per log (Unbreaking applies); several lumberjacks share the camp's axes best-first in hiring order. Without an
  axe the status is "Needs axe" (an unreadable chest is unknown, not missing); replanting and collecting still run. No new tree starts after
  day time 5000 (drops despawn after 6000 ticks); a fell in progress is saved with the village (`tree_tasks`) and any lumberjack of the camp
  finishes it. Drops (logs, saplings, sticks, apples) in the work area are collected. The base is replanted with the log's species (one sapling per
  base cell, four for 2x2 trunks) from the worker or the camp chest, after 6000 ticks with any one-cell sapling, and the spot expires after 3 days.
  A worker that cannot hand in its load (Storage full or unreachable) stops cutting. Saplings the open spots need, plus 4, are never handed in.

### 5.2 Farmer (Farmhouse, Composter)
Works farmland within 24 blocks of the Farmhouse. It harvests **mature** wheat, carrots, potatoes and
beetroots and replants from the yield. It composts surplus seeds into bone meal and uses the bone meal on
young crops. It **does not make new fields** in v1: the player or a Builder's Farm Plot does that. No tool is needed (a hoe gives ×1.25).

  Rules as built (M10): the work area is every farmland cell whose crop cell (the block above) lies within 24 blocks (horizontal, Euclidean,
  inclusive) of the Farmhouse **composter** and within 8 blocks of it in height (the composter anchor and the height limit are additions to the
  text above; a neighbour's field inside the area is worked too). Exactly four crops, matched by block id: wheat, carrots, potatoes (ripe at age
  7) and beetroots (ripe at 3); torchflowers, pitcher plants, stems, berries and nether wart are never touched. One unit of work is one atomic
  change of one cell: **harvest and replant happen in the same tick** (the seed comes out of the drops, so a full inventory cannot cost the
  replant; the rest of the yield goes to the inventory, what does not fit is dropped and collected later). Units cost 20 ticks (harvest, bone
  meal), 10 (plant, compost) at speed 1.0. Empty farmland is planted with the crop most common around it (ties: wheat, carrots, potatoes,
  beetroots) from the worker's seeds or the Farmhouse chest; it never tills. **Composting is deterministic**: the first seed raises the
  composter to level 1, every further seed adds 3 tenths of a level, so 21 seeds make one bone meal (the mean of vanilla's random composter); the
  farmer takes the bone meal at READY. Seeds above 16 per kind (wheat and beetroot seeds only) are surplus; a farmer composts a batch of 8, or
  anything left when there is nothing else to do. Bone meal goes to young crops, non-beetroot first, lowest age first, nearest the composter.
  The farmer keeps one stack of each seed, 16 carrots and 16 potatoes for replanting, and all its bone meal; the rest of the harvest goes to
  Storage. A farmer with a full Storage stops harvesting (planting, bone meal and composting go on). A **hoe** in the Farmhouse chest gives a flat
  ×1.25 (any tier, 1 durability per harvest, wears out in the chest); without one the farmer works at ×1.0 and nothing is reported. Every
  worker keeps up to 12 food points of bread, potatoes, carrots and beetroots (vanilla's breeding threshold, bread first) and hands in the rest.

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

  Rules as built (M16): **"disappears inside"** means the villager stays a loaded entity: at the shaft entrance it is moved to the shaft's
  deepest cell (up the column if that cell is under a fluid or a full block; never the marker cell) and made Invisible, Invulnerable, Silent
  and NoAI (NoAI freezes the vanilla brain, invulnerable mobs are never targeted). The flags it had are saved in its own persistent
  attachment and given back when it comes up at the spot it went down from (or, if that is blocked, the nearest free standable cell within
  2 blocks of the marker; with none it stays hidden and reports "Can't reach the Mineshaft"). `Invisible` is not saved by vanilla, so it is set
  again when the entity loads. Players cannot trade with or hit a hidden miner; it is re-pinned to its cell every step (pistons). Firing,
  releasing the village, a conversion or an unreadable trip all bring it up (a villager is never left hidden). **2 in-game hours** means
  *underground work time*: a haul costs 2500 work units and a stone pickaxe at level 1 earns 1.25 per tick (2000 ticks; wood 2500, iron
  1667, diamond 1429, netherite 1250), counted in 20-tick steps; the walk to the shaft and the deposit trip are on top, so a real cycle is
  about 2340 ticks. Progress is kept per shaft (reset when the miner uses another one) and overnight: it comes up at 11000 without a haul,
  and no new descent starts after 10400. The pickaxe stays in the Mine chest (M9) and loses 10 durability per haul. The band comes from the
  shaft's deepest Y at its last complete survey (unknown after a restart until the shaft is re-checked: the miner then waits). Diamonds need an iron
  pickaxe or better and Y < 0; every chance entry draws one number from a generator seeded by (world seed, villager UUID, the miner's haul
  counter), so a reload never re-rolls a haul. 4 redstone or lapis per hit, 1 gold, 1 diamond (DESIGN gave no counts). A wooden pickaxe uses the stone table,
  slower. What does not fit into the inventory stays pending and is delivered later (never dropped); the miner hands in everything but food
  as soon as it is up. A shaft that stops being valid (rails removed) brings the miner up at once with its progress reset; the Mine
  then turns invalid ("Needs a linked Mineshaft") and its miners report "Workplace invalid" until it is fixed. Statuses: "Needs pickaxe",
  "No valid Mineshaft", "Can't reach the Mineshaft", and "Away (mining)" on the Heart screen while it is down.

### 5.4 Hunter (Hunting Lodge + Hunting Ground, Fletching Table, *requires bow or sword*)
It walks to the Hunting Ground, is gone for **3 in-game hours** per trip, and returns with
2–4 raw meat (beef, pork, mutton or chicken, weighted by nearby biome), 1–2 leather, 1–2 feathers and 0–1 rabbit hide.
With a bow, it uses arrows from the chest (1 per trip) and gets a +1 meat bonus. There are 2 trips per day at level 1.

  Rules as built (M17): the hunter "goes away" with the miner's machinery (DESIGN 5.3): it stays a loaded entity, hidden (Invisible,
  Invulnerable, Silent, NoAI) and re-pinned, but where it *stands* when it arrives near the Hunting Ground marker (within 3 blocks of it;
  there is no hole to hide in), on a free standable cell if another hunter already holds that one. **3 in-game hours** is *time away*: a
  trip costs 3000 work units at 1.0 per tick at level 1 (2400 ticks at level 2, 2000 at level 3), counted in 20-tick steps; the walk and
  the deposit trip are on top. The pace does not depend on the weapon's tier (a bow and a diamond sword give the same trips); the tier
  only decides which weapon is taken. **Weapons** are `#minecraft:swords` (wood, stone, copper, gold, iron, diamond, netherite), bow and
  crossbow (spears, mace and trident are not hunter weapons). Any bow or crossbow is taken before any sword, then by tier and chest
  order; the weapon stays in the Lodge chest and loses 5 durability per completed trip. **Arrows** are taken at the return, not at the
  start: a bow or crossbow takes 1 arrow (plain first, then any other item of the arrows tag) from the Lodge chest and brings 1 more meat;
  without an arrow it still hunts, with no bonus (Infinity is not modelled). **Trips per day**: 2 at level 1, 3 at levels 2 and 3 (a
  count saved with the hunter for the day, reset on any other day); a trip only starts if it can end 300 ticks before the work day ends
  at 11000 (at level 1 the last start is at day time 7700), and the fit is checked again when it arrives. At day end it comes back without
  a yield and keeps its progress. The yield of a trip is drawn from a generator seeded by (world seed, villager UUID, trip counter), in a
  pinned order: base meat 2-4, leather 1-2, feathers 1-2, rabbit hide 0-1 (50 percent), then the kind of each piece of meat; the bow's
  extra piece is one more draw of the same stream, so with and without a bow the same trip is the same plus one meat. **Biome weighting**
  reads the vanilla creature spawn weights of the biomes at the marker and eight points 12 blocks around it (cow and mooshroom beef, pig
  porkchop, sheep mutton, chicken; where none of them spawns the vanilla farm-animal weights 8/10/12/10): a plains ground gives 8/10/12/10.
  Leather, feathers and hide do not depend on the biome. **Which ground**: the nearest VALID Hunting Ground linked to the Lodge. **A rule
  change from M5**: a Hunting Ground links only to a Hunting Lodge **within 40 blocks** (it was: anywhere in the village); one farther out
  is invalid ("No Hunting Lodge within 40 blocks"). A hunter more than 40 blocks from the marker first walks to its fletching table. The
  yield goes into the villager's inventory and the hunter hands in every hunted item at once (raw meat is not a kept food). A hunter that
  stands in a hidden hunter's way counts as arrived when it touches it near the ground. Statuses: "Needs bow or sword", "No valid Hunting
  Ground", "Can't reach the Hunting Ground", and "Away (hunting)" on the Heart screen while it is out. If the ground's chunk is not
  entity-ticking the hunter is not stepped (everything freezes, DESIGN 6). Known gap: a hunter hidden in the open has an invisible hitbox that can catch a player's
  crosshair (BACKLOG, before v1).

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

**Rules as built (M20)** (`docs/plans/M20.md`):
- "5x5" means a 5x5 floor inside the walls (a 7x7 shell): a 5x5 outside gives 9 floor cells, under the House minimum of 12. Small House: 49 cobblestone, 119 planks, a door, a bed, a torch and the House marker (172 placements, the door and the bed one step each); Storage Shed: the same shell with two chests; Farm Plot: 11x11, 80 farmland (placed moist, moisture 7) around one water source (from a bucket, placed last, the bucket comes back), 39 fences and a gate.
- A blueprint is data in `core.blueprint` (palette, layers, `base`, anchor, front), parsed once; rotation about the anchor in 90 degree steps (vanilla's position rule, `BlockState.rotate` for the states). An L1 test runs the real `RoomScanner` and `BuildingRules` over both houses in all four rotations.
- At the Builder's Table (anyone in the claim may read it; Take and Cancel need Steward, `Permission.START_BLUEPRINT`) a Steward takes a Blueprint item. The first click shows the footprint (green dust when the server's site check passes, red when not; the door turns towards the player), a sneak-click turns it, a click inside the outline confirms. The check (`SiteRules`, twelve ordered refusals): rank, tier, dimension and height, inside the claim sphere, at most 8 builds, loaded, no overlap with any registered building's room (interior or shell, valid or not) or marker, no overlap with another build's stand ring, no water or lava in or next to it, no natural tree (fell it first), no block that is not natural, a place to stand within 7 blocks of every cell.
- "Natural" = in `#villageheart:builder_clearable` (dirt, stone, sand, snow, replaceable plants, flowers, saplings, leaves, coal/iron/copper ore, a few crops of the wild) and, for leaves, not persistent (a player placed it), with no fluid and no block entity. The builder never breaks anything else; it stops on such a block that appears later ("glass in the way at ...", retried every 5 s). Known gap: dirt, stone or sand a player placed outside any marked room cannot be told from terrain and may be cleared (its drops go to Storage). The same holds for a player's pumpkin or melon patch (both are in the tag as crops of the wild).
- A natural cell is only cleared when nothing of a player rests on it: the site check (rule 11b, `SiteSupport`) refuses a site where a block outside the touched cells would lose its support (a torch, ladder, door, sign, rail or lantern on or against a cell the build clears, found by asking `canSurvive` with the cell as air) or a hanging entity (item frame, painting) is attached to one, naming the thing and its cell ("torch at (x, y, z) would fall: it rests on the site"). The builder asks the same before every clear step; a thing attached after the confirm stops the build with "torch in the way at (x, y, z)" and nothing is broken. Natural neighbours that pop or fall with the cell (flowers, sand) are not protected.
- The builder reserves the whole build's materials (keyed by the build id, oldest build first, one wood type when possible, transient like all reservations), fetches about a stack at a time, clears (top layer first, drops to Storage), places layer by layer from the bottom in perimeter order, then torches, then water, then a check pass that puts back anything a player removed, and the marker last, so the room is complete when it is first checked. It works only from the ring of columns around the site, never from inside it. 1 action per second at level 1.
- A pause names the first material in step order that is short ("Needs 12 more planks"); it resumes by itself within a refresh (100 ticks) plus the walk after the planks arrive. Cancelling keeps the placed blocks and loses the blueprint; firing a builder hands the materials it carries back to Storage; a dead builder's carried materials are lost (vanilla villagers drop nothing), the shortfall is reserved again.

**Rules as built (M21)** (`docs/plans/M21.md`):
- **Two-click lines.** Wall Segment and Gate are LINE blueprints (`mode LINE`). With one in hand: click 1 sets the start (a short grass or snow click means the block below), click 2 sets the end, straight along x or z and at most 32 blocks including both ends (a gate at least 5): a diagonal is "A wall must run straight along x or z (n off the line)", a long one "Too long: 33 blocks (max 32)", a short gate "Too short: 4 blocks (min 5)", and the start is kept so the next click is another end. A good line is checked with the same twelve rules as any site and shown green or red as dust along its outline. A click on the shown line confirms, any other click is a new end (to shorten a line, sneak-click to clear the start and begin again). `/villageheart build line <wall_segment|gate> <from> <to>` is the same for operators.
- **Level, no terrain following.** The whole line is level at the start's ground height (the ok text says "level at y 65"). Higher ground inside the box is natural and is cleared; a block of a player there refuses as usual. Lower ground is the new rule 11c FOOTING ("No ground under (x, y, z): defences need level ground"): every ground cell under a defence must be solid (a dirt path or farmland counts, short grass and snow layer do not). A sloped wall is several segments.
- **Defences may touch** each other (wall end to end, a corner, a lamp beside a wall) but never overlap; a House still keeps a block of room from a pending defence. A builder never stands in a column that belongs to another pending build.
- **Wall:** 3 high cobblestone, 3 per block (a 20-long wall is 60). **Gate:** the same wall with a centred 3-wide opening: closed fence gates at the bottom, one block of air above, a cobblestone lintel on top, so a player passes (2 high) when a gate is opened and a closed gate cannot be jumped. A gate costs 3 per block minus 6 cobblestone and 3 fence gates. Villagers cannot open fence gates (vanilla): a ring of wall with only gates cuts workers off; leave one gate open or a gap.
- **Lamp Post:** 2 fences and a lantern (it stands on the post). Vanilla refuses a natural monster spawn in a cell with block light above 0 (the overworld's limit), the lantern's 15 falls by one per block along x + z and up: on level ground every cell within 12 steps (a diamond, not a circle) is safe. Not stopped: pillager patrols, slimes, phantoms, drowned, spawners, raids, mobs walking in. Walls and houses shade the light.
- **Watchtower** (Town): a 3x3 cobblestone shaft 5 high with a ladder inside, an entrance 2 high on the front, a 5x5 plank platform with the ladder's hole and a fence rail round it: 38 cobblestone, 24 planks, 16 fences, 6 ladders. The centre cell above the ladder hole is left as it is (out of the builder's reach from the ring).
- **Patrol points.** A finished Lamp Post or Watchtower is saved with the village (`VillageState.defences`, at most 64, in finish order). Its patrol point is the first walkable cell next to it (lamp: in front of the post; tower: in front of the entrance), found again at every query. The record goes when its lantern or one of its two ladders is gone (checked every 200 ticks in loaded chunks). `dev.villageheart.defence.Defences.patrolPoints(server)` lists them for the guards (M18).

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
  Rules as built (M11):
  - **Abstract dinner.** At 11000 the village system takes every villager's meal straight out of the Storage chests (`Stockpiles.withdraw`),
    wherever they stand; nobody walks. Eaters = every living, loaded villager in the claim sphere (workers, unemployed, nitwits, children);
    unloaded ones keep their state (no catch-up). Food in a villager's own inventory is never eaten. Reserved items (M20) are never eaten.
    A day jump over the whole window (`/time add`) skips that dinner; `/time set` back re-serves it (`last_dinner_day` is compared with `!=`).
    Sleeping through a thunderstorm before 11000 also skips it. `last_dinner_day` is saved with the claim.
  - **Need and order.** Adult 10, child (baby) 5. Best food first: prepared before raw (raw = beef, porkchop, chicken, mutton, rabbit, cod,
    salmon, potato), then nutrition, saturation, item id. Never eaten (= item tag `villageheart:not_village_food`): rotten flesh, spider eye,
    poisonous potato, pufferfish, suspicious stew, chorus fruit, golden apple, enchanted golden apple, golden carrot. Eaters: the hungriest
    first, then workers in hiring order, adults before children, UUID. A villager eats until it has 10 (a second beef overshoots) or, when
    the food left cannot cover its need, nothing at all. Stews leave their bowl in the Storage.
  - **Hunger** is a per-villager attachment (`villageheart:diet`, saved, claim-scoped). A missed dinner adds a day, a meal resets it.
    1 missed dinner: work speed x0.5; 2 or more: the worker stops working (status "Too hungry to work"; deposit trips still run).
    Nobody ever dies of hunger. `WorkerData.needs` is a derived copy written only by the needs system.
  - **Housing.** A worker is housed when its bed (HOME) is in a valid House (or its own building, M18); unhoused = x0.75. Every 100 ticks an
    awake unhoused worker moves into the nearest free House bed, else the nearest awake non-worker's; workers never take each other's beds.
  - *Food days left* uses the real daily need (adults x10 + children x5).
- **Treasury**: emeralds deposited at the Heart (Resident+). Only the Owner can withdraw.
- **Wages**: paid at **dawn**: **1 / 1 / 2 emeralds** per day for levels 1 / 2 / 3 *(tunable)*. If the treasury is short,
  workers are paid in hiring order, and the rest strike for the day.
  Rules as built (M12):
  - **Deposit** = right-click the Heart with emeralds in the main hand: the whole stack goes in (as much as fits under 100 000), for Owner, Steward
    and Resident; a Visitor gets a message and the Overview opens instead. Sneaking with an item skips the block (vanilla). "Deposit all" on the
    Overview takes every emerald in the inventory. **Withdraw 1 / 64** (Overview, Owner only) puts emeralds in the inventory (what does not fit drops at
    the player). Any emerald counts (a renamed one loses its name). Every deposit and withdraw is one INFO log line with the player's name.
  - **Dawn** = the first tick of a new day index (`floorDiv(dayTime, 24000)`, also where sleeping lands). It runs once per index change and only while
    the Heart chunk is loaded: no catch-up for skipped days or unloaded time, `/time set` back pays once. A new claim or an old save starts at today
    (no instant charge). With `advance_time` off nobody is paid. `VillageDawn.register(id, listener)` lets later milestones react to the same dawn
    (after wages, with the pre- and post-wage treasury in `PayResult`).
  - **Pay rule.** Every registered worker, loaded or not (its level is mirrored in `WorkerRecord.level`), in hiring order; a worker whose wage fits what
    is left is paid, one that does not strikes **and the walk goes on** (a cheaper later worker can still be paid). Owed wages are never carried over.
  - **Strike** = on today's unpaid list. A striker's work speed is 0 (all jobs, via `needs.paid`; deposit trips still run), its status is "On strike"
    and it outranks hunger. **Every treasury increase** (deposit, the `treasury add` and `set` commands, a merchant's sale in M22) pays the strikers at
    their wage as charged at dawn, in hiring order, at once. **A hire is charged at once** (a fresh hire strikes when the treasury cannot pay), so
    firing at dusk and hiring again at dawn is no free day; a worker already paid today costs nothing to hire again the same day.
  - **Overview.** Treasury, "Wages tomorrow: N emeralds" (red when the treasury is short), a danger line "N workers are on strike" and a warning "Treasury
    short: X of Y". Alerts are ordered severity first (danger, then warning), so the five-line cap never hides a strike.
  - The treasury is capped at 100 000 emeralds (`Balance.MAX_TREASURY`). `/villageheart treasury get|add n|set n` (op) is the admin and test way in.
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
  As built in M20: a "Builds" section under the buildings lists each build in progress (blueprint, where, "Building: 50%", what it is short of, the builder's name); Residents and above see it (at most 8 rows).
  The Builder's Table has its own screen (blueprints with their material list and what Storage could still give, Take, the builds with Cancel).
- **Stockpile**: item totals across Storage, food nutrition total, and surplus reserve settings.
- **Ranks** (Owner only): players and their ranks.

As built in M24: the fifth tab lists the Owner and every player the village has seen (online ones with a green dot), with their rank; the Owner picks a row, then
`[Steward] [Resident] [Visitor]` or `[Transfer ownership]` (a second click on `[Confirm: give the village to X]` sends it; only a Steward or Resident can receive it).
Only the Owner's snapshot carries the list, so other players never receive other players' UUIDs. After a transfer the old Owner's screen drops to the Overview.

As built in M8 (`docs/plans/M8.md`): the screen is a slotless menu (`ExtendedScreenHandlerType`) opened by right-clicking the claimed Heart with any item
(not while sneaking with an item: vanilla keeps that for placing). The server sends each viewer a full snapshot at most once a second and only when it
changed; a Visitor's snapshot holds the Overview only (no villager, building or stock data is sent). Actions (assign, fire, check now) are
client-to-server payloads that the server re-checks (rate limit, open screen, current village, rank re-read every time, villager and building looked up
in the Heart's dimension only). The screen is drawn for a 427 x 240 GUI (854 x 480 at GUI scale 2); tabs and lists are our own widgets.
Population counts loaded villagers plus recorded workers that are not loaded. Food days use 10 nutrition per adult and 5 per baby a day;
foods in `#villageheart:not_village_food` do not count.

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
