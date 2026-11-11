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
  vanilla mining speed / 2, anchored on stone (wood 2, stone 4, copper 5, iron 6, diamond 8, netherite 9, gold 12 in vanilla): wood ×1.0, stone ×2.0, copper ×2.5, iron ×3.0, diamond ×4.0, netherite ×4.5, gold ×6.0 *(`Balance.VANILLA_TOOL_SPEED`; the base work constants are scaled so a stone tool is as fast as before)*. Tools lose durability as they are used.
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

### 4.1 Where tools come from: self-made wooden and stone tools (M19a)
**Decision (owner, 2026-11-09):** "I like that smithy unlocks at tier three but before then villagers can make wooden and stone tools
themselves, just slowly, and then the smithy unlocks better tools." Clarified the same day: "They start with wood and as the miners bring
in stone they can start making stone etc." Plan: `docs/plans/M19a.md`.
- **Who**: a hired lumberjack (axe), miner (pickaxe), hunter (sword) or farmer (hoe) whose job chest holds no usable tool for it, or whose
  tool is about to wear out (below 10 % or two work units left), makes its **own** replacement, at every tier from Hamlet up. Demand is
  counted per camp: the worker without a tool makes the missing one; a worker with a working tool never crafts.
- **What**: **stone** (cobblestone, blackstone or cobbled deepslate, and sticks or planks) when the stockpile can afford it, otherwise
  **wooden** (planks, sticks, or logs turned into planks); never copper or better: those are the Smithy's (§5.7, Town). Vanilla recipe
  counts (axe and pickaxe 3 + 2 sticks, hoe 2 + 2, sword 2 + 1; 2 planks make 4 sticks; 1 log makes 4 planks). A usable tool already in
  Storage is fetched instead.
- **Slowly**: at its job block, in its own work time (so it costs that worker's output), wooden **2400** and stone **4800** ticks at level 1
  *(tunable)*, four times the smith's. Saved; pauses overnight and continues. The farmer, whose hoe is only a bonus, makes it only when it
  has nothing else to do.
- **Demand only**: a working tool is never replaced because better material appeared; tools improve as they wear out. A worn tool of the
  same tier is used up before the new one. A usable tool the player puts in the chest always counts and cancels a craft in progress; the
  materials go back to Storage.
- **Progression**: a new village makes wooden tools; a miner's first haul brings enough cobblestone for several stone tools, so from about
  the second day every replacement is stone (with vanilla tool speeds a wooden tool works at half a stone tool's pace and lasts 59 uses
  against 131).
- **Hand-off to the smith (M19)**: once the village has a hired blacksmith at a valid Smithy, new tool demands go to the smith (faster,
  better tiers); a self-craft already started finishes. A worker whose required tool has been missing for 3000 work ticks with no order
  may still make its own.

#### Rules as built (M19a)
- Pure core in `core.gear` (demand per camp and need, the worn line `ToolWear.worn`, `TierChoice.selfMade`, the cancel hysteresis, `SupplyRule`, `SelfCraftPlanner`); glue in `worker.gear` (`SelfCraftJob` is called at the top of each tool job's tick and owns the tick while it fetches or crafts; `SelfCraftSystem` checks every saved craft every 100 ticks).
- **Worn line** (self-craft, lead 0): below the larger of 10 % of the durability, two work units, so a stone axe (131) is replaced at 13 left, a wooden pickaxe (59, 10 per haul) at 19. Within a tier the tool with less left is used first.
- **Fetch and commit**: the worker reserves the planned items, walks to the Storage holding them and withdraws exactly those into its saved record (never into its inventory, so a death or a deposit trip cannot touch them); a withdraw that comes up short puts everything back. Then it works at its job block. A cancel (a fresh tool now covers the slot, the worker was fired or died, the building went) needs two checks 100 ticks apart and refunds the committed items to Storage.
- **Status**: "Making a wooden/stone <tool>"; "Needs axe (no wood)" when it cannot start for lack of material, "Needs axe (no room)" when the chest has no free slot.
- **Not here**: a crafting-table station, bows, shovels and spears, XP for crafting, copper or better (M19).

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
  inside the work area (32 blocks horizontally, 16 vertically of the Chopping Block, nearest first) with a work clock: stone 2.0, copper
  2.5, iron 3.0, gold 6.0, diamond 4.0, netherite 4.5 (wood 1.0; vanilla speed / 2, anchored on stone: 80 work units per log, so stone fells a log in 40 ticks, wood in 80, iron in 27), times level, times needs (`WorkSpeed`). **Tools stay in the camp chest** and wear
  out there, 1 durability per log (Unbreaking applies); several lumberjacks share the camp's axes best-first in hiring order. Without an
  axe the status is "Needs axe" (an unreadable chest is unknown, not missing); replanting and collecting still run. No new tree starts after
  day time 5000 (drops despawn after 6000 ticks); a fell in progress is saved with the village (`tree_tasks`) and any lumberjack of the camp
  finishes it. Drops (logs, saplings, sticks, apples) in the work area are collected. The base is replanted with the log's species (one sapling per
  base cell, four for 2x2 trunks) from the worker or the camp chest, after 6000 ticks with any one-cell sapling, and the spot expires after 3 days.
  **A felled tree gives its lumberjack the saplings its own stump still lacks** (backlog-2): leaf drops are random (and oak leaves draw from
  a per-world loot sequence), so six small oaks can give fewer than six saplings in a day; the worker counts its own and the camp chests'
  saplings against the camp's open spots and gets the rest of this stump's cells (none with `block_drops` off). Saplings in Storage and
  saplings lying on the ground are **not** counted, so the rule is "one guaranteed replant per felled stump" (also for a task tree the player
  cut down while the task was open). Leaf saplings are an intended small surplus (kept up to the reserve, the rest handed in to Storage).
  A walker blocked by a plain villager that just stands in its way (on its next path cells or within a block, e.g. an idler in front of a
  door) asks it to step aside (a vanilla WALK_TARGET a few blocks away). Our own workers are never asked (two walkers settle it by the id
  step-back rule; a worker standing at its work keeps working), nor trading, sleeping or NoAI villagers.
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
  *underground work time*: a haul costs 4000 work units and a stone pickaxe at level 1 earns 2.0 per tick (2000 ticks; wood 4000, copper 1600, iron
  1333, diamond 1000, netherite 889, gold 667; vanilla speed / 2, anchored on stone), counted in 20-tick steps; the walk to the shaft and the deposit trip are on top, so a real cycle is
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

**Rules as built (M13).**
- **Mod-side conversion, not the vanilla smoker.** The cook converts the items it carries on a work clock while it stands at its smoker (125 ticks per item at speed 1.0, times level and needs; speed 0 cooks nothing). The smoker only shows it: its `LIT` state is set while the cook works, so the vanilla smoke and crackle play. Items are never parked in the smoker's slots and nothing is ever dropped into the world.
- **Recipes** (a fixed table, `CookRecipes`; component-free stacks only, a renamed beef is a player's item): beef, porkchop, chicken, mutton, rabbit, cod, salmon and potato at the smoker (the village's raw list); 3 wheat to bread and 6 beetroot + a bowl to beetroot soup after a stop at the crafting table. Every recipe costs fuel. Recipes are tried by gain (output nutrition minus input nutrition): beef, porkchop, bread (5), chicken, mutton, salmon, baked potato (4), cod, rabbit, soup.
- **Fuel is heat.** Coal and charcoal are worth 1600, a log 300 (vanilla's furnace burn times); one output item costs 200. Each smoker keeps a saved heat bank, so half a coal is never lost. Fuel is taken from the Storage only (not the Kitchen chest), charcoal and coal before logs. With raw food in stock and no fuel the Villagers tab says "Needs fuel"; with nothing to cook it says "Nothing to cook".
- **Batches.** The cook plans one recipe at a time: at most 8 items, by the inputs, the fuel, the free inventory slots (soup does not stack: at most 5 with an empty inventory) and the time. It reserves the inputs and fuel under its id first, so a second cook or the dinner cannot count on them, withdraws them at the nearest Storage that holds each, cooks, keeps the outputs until the batch is done and then hands everything in. No batch starts that cannot end by 10500 (the food must be in Storage before dinner at 11000); at the end of the work day the cook keeps nothing.
- **Overall rate.** 8 per hour is the rate at the smoker; with the fetch and deposit walks a cook makes about 4 to 5 items an hour.

### 5.6 Guard (Barracks, Grindstone)
- **Shifts**: each guard is on a **day** or a **night** shift. **Up to 3 guards all work nights** (where the mobs are) and there is no day shift; from
  the 4th guard on the day keeps `floor(n / 4)` guards (4-7 guards: 1, 8-11: 2), so nights always have at least 3. **Seniors take the nights** (level,
  then XP); a new guard has the least seniority, so it takes an open day slot and moves to the night as it gains XP or when a night guard falls.
  With no day guard the alarm (below) is the only daytime cover. A Steward can pin a guard to a shift (the Shift button).
  A guard sleeps in a Barracks bed during its off shift, so night-shift guards sleep in the day.
- **On shift**: it patrols between the Heart, Watchtowers and lamp posts. It attacks hostile mobs and raiders inside the claim. During a raid, or when a
  hostile hurts a villager, or when a guard dies, every guard that is not wounded or in training is called on duty.
- **Training**: a **newly hired guard spends one day training** (status "Training"): it spars at the Training Yard for XP (at a village without one it
  drills idle at the Barracks and gains nothing), is not on duty, and is qualified at dusk, so it fights that night. Guards are **hired by day only**: a
  guard that fell at night is replaced the next morning. After its training day, off-shift time while awake is spent sparring too. Guards also earn XP from kills.
- **Starting gear**: a **wooden sword and no armour**. A guard takes better gear only from what the village has (Barracks chest, Storage, later the Smithy).
- **Level perks**: max health **20 / 30 / 40** (a level 1 guard has the vanilla 20; more only from levelling; tunable for M26) and melee damage +0 / +1 / +2 *(tunable)*.
- **Food and healing**: there is **no free regeneration**. A guard carries a small food ration (nutrition points, up to 12) that it withdraws from the
  village's food Storage (so it counts against the food days) when it is low and the guard is off duty or at the Barracks. A hurt guard with no hostile
  near eats one point per 2 seconds and heals 1 health per point. No food in storage means no healing.
- **Retreat**: a guard below 50 % health falls back behind a healthy guard (at least 70 %), eats there and heals, and rejoins the fight at 80 %.
- **Gear**: at the start of each shift it equips the best armour and weapon available in the stockpile
  and Barracks chest (leather < chainmail < iron < diamond), and returns damaged gear when off shift. There is no bow combat in v1; **archers are not built** (a later milestone).

#### Rules as built (M18)
- **Shifts** (`core.guard.GuardSchedule`): day shift on duty `[0, 12000)`, off `[12000, 13000)` (hand in gear, spar), asleep `[13000, 24000)`; night
  shift off `[0, 1000)`, asleep `[1000, 12000)`, on duty `[12000, 24000)`. Overrides, first wins: wounded goes to bed (even in a raid), a trainee is
  off and awake (never on duty, never sent to bed by the clock), a raid or the alarm puts every guard on duty, a guard that cannot work (strike,
  starving) is off instead of on duty but still sleeps. `ShiftBalancer.assign` ranks the unpinned guards by level, then XP (ties keep the side a guard
  is on, then hiring order, so nothing flaps) and gives the best `n - floor(n / 4)` the night; pinned guards never move and count for their side.
  `GuardSystem` re-ranks every 20 ticks with the XP the village state does not hold.
- **Alarm** (`GuardAlarm`): raised when a hostile hurts a villager in the claim, or when a guard dies (so every sleeping or off-duty guard wakes and
  comes). `refresh` keeps it up every 20 ticks while a hostile that hunts a villager is within 24 blocks of a guard; it ends 600 ticks after the last
  sign of a threat (no flapping). Wounded guards stay in bed. While it is up the guards gather at its spot. The state is static, so it is cleared when
  a server stops or starts, and the spot is kept with its dimension.
- **Brain**: a guard has its own brain (`GuardBrain`): no panic trigger, no bell reaction, no raid hiding, no HOME acquisition. Activities
  `GUARD_DUTY`, `GUARD_OFF` (the default) and vanilla REST; combat runs in CORE so a guard that is attacked wakes up and fights even when it
  would go to bed (defend beats wounded and sleep). Night-shift guards sleep by day with vanilla `SleepInBed`, which has no time-of-day check.
- **Beds**: the mod holds the POI ticket of every Barracks bed, so no ordinary villager takes a guard's bed; each guard gets a bed of its own
  Barracks (`GuardBeds.assign`) and `HOME` is set by `GuardSystem` every 20 ticks. A guard below 30 % health goes to bed (hysteresis: back at 80 %),
  but a guard with no bed never counts as wounded. A bed does not heal: food does.
- **Food** (`core.guard.GuardRation`, `GuardRations`): the ration is in the guard's attachment (`ration`, nutrition points, cap 12). Below 6 points,
  off duty or within 16 blocks of the Barracks and with no hostile within 10 blocks, `GuardSystem` withdraws cooked food (one item at a time, up to
  4) from Storage (`Stockpiles.withdrawFirst`, nothing if there is none; a miss is retried after 200 ticks). A hurt guard with no hostile within 10
  blocks (and a retreating guard behind its ally) eats one point per 40 ticks for 1 health, asleep or awake. `work.guard.ration_items`,
  `ration_points` and `food_eaten` count it. The withdrawal is remote (the guard does not walk to Storage for its ration, unlike gear).
- **Combat**: the villager gets a real `ATTACK_DAMAGE` attribute (a mod registration for every villager), so vanilla `doHurtTarget` gives the
  sword's damage, enchantments and kill credit. Targets: hostiles and raiders inside the claim (24 blocks of sight), neutral mobs only when they
  hunt a villager; **creepers are ignored** (guards are melee) and flying mobs are never chosen. A kill in the claim is 5 XP; a sword wears per
  hit, worn armour per hit taken (vanilla's rule). A guard that a different hostile hurts switches to it. A guard below 50 % health with a healthy
  awake guard (at least 70 %) within 32 blocks falls back to within 5 blocks of it, eats and holds the line there, defending itself only against what
  reaches it, and fights again at 80 % (`work.guard.retreats`); with nobody to fall back to it fights on.
  A duel of one level-1 guard (wooden sword, no armour) against one zombie is a test (`guardBeatsOneZombie`): the guard must win (no health bound: a wooden sword takes 5 hits at vanilla damage and the health left varies from 2 to 20; owner decision 2026-11-11).
- **Gear**: worn on the villager with a guaranteed drop (a dead guard drops it intact; a converted guard's gear drops and the zombie villager
  wears nothing). A hired guard gets a wooden sword (a trainee keeps it; a fired guard hands it in with the rest). At the start of duty and every 1200
  ticks a guard plans one pick per slot, strictly better than what it wears, from the Barracks
  chest and the stockpile (leather < copper < chainmail = gold < iron < diamond < netherite by armour value; swords by attack damage), reserves
  it and fetches it; off shift it hands everything back to the Barracks chest (else Storage, else the ground). **Enchantments are not ranked**
  (they are no attribute modifiers) and **armour is invisible** on the villager model (v1: only the held sword shows).
- **Patrol**: the Heart, then a **door point** three cells outside every valid room's door (a guard is a hired worker, and workers are never
  asked to step aside, so a guard must not dwell in a doorway), then the finished Lamp Posts and Watchtowers. A closed fence gate on the way is
  opened by the guard (see below).
- **Training** (`core.guard.GuardTraining`, `GuardData.trainingUntil`): `Workers.hire` refuses a guard from day time 12000 to 24000
  (`HireRefusal.GUARD_AT_NIGHT`); a hire sets `trainingUntil` to dusk (12000) of that day. Until then `GuardSchedule` gives the trainee OFF, it
  spars at the valid Training Yard linked to its Barracks (one XP per 40 ticks) or stands at the Barracks, and the Villagers tab shows "Training".
  After it is qualified, off shift with a valid Training Yard a guard spars at its work pace the same way. Level perks are permanent attribute
  modifiers (`villageheart:guard_health`, `villageheart:guard_damage`), saved with the entity. Hiring and firing keep the health the guard has
  (clamped to the new max): a fire and a hire is no free heal.
- **Raid call-up**: `RaidCheck` (a raid that is not over or stopped, with its centre or a raider in the claim; `getRaidAt` alone matches any
  raid within 96 blocks). M25 replaces the answer through `RaidCheck.setSource`.

### 5.7 Blacksmith (Smithy, Blast Furnace), Town tier
- **Smelts** raw iron, copper and gold from the stockpile into ingots (it uses fuel).
- **Makes gear** from stockpile ingots and leather: iron tools, iron armour and leather armour. It keeps
  a *(tunable)* target, such as "2 spare iron axes, 1 set of armour per guard", and works towards it.
- **Repairs** tools and armour returned to the Smithy chest with ingots.
- **Builds iron golems**: 36 iron ingots + 1 carved pumpkin, 1 in-game day of work. The cap is 1 golem per 5
  villagers. Vanilla villager golem spawning is **disabled inside the claim**, so golems come from the Smithy.
- **Repairs golems**: golems below 50% health walk to the Smithy, and the Blacksmith uses ingots to heal them
  (25 HP per ingot, as in vanilla).
- Before the Smithy (and whenever no smith is hired), workers make their own wooden and stone tools (§4.1, M19a); a hired smith takes
  over new tool demands.

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
- **Wall:** 3 high cobblestone, 3 per block (a 20-long wall is 60). **Gate:** the same wall with a centred 3-wide opening: closed fence gates at the bottom, one block of air above, a cobblestone lintel on top, so a player passes (2 high) when a gate is opened and a closed gate cannot be jumped. A gate costs 3 per block minus 6 cobblestone and 3 fence gates. **Hired workers and guards open and close the village's fence gates as they pass, like doors; mobs still cannot** (M18, human decision 2026-11-09): a closed ring of wall with only gates no longer cuts workers off. A gate opens only inside the claim, never while a hostile mob is within 8 blocks of it (a guard that is fighting may), never when redstone powers it, and is closed again as soon as nobody needs it (see §12).
- **Lamp Post:** 2 fences and a lantern (it stands on the post). Vanilla refuses a natural monster spawn in a cell with block light above 0 (the overworld's limit), the lantern's 15 falls by one per block along x + z and up: on level ground every cell within 12 steps (a diamond, not a circle) is safe. Not stopped: pillager patrols, slimes, phantoms, drowned, spawners, raids, mobs walking in. Walls and houses shade the light.
- **Watchtower** (Town): a 3x3 cobblestone shaft 5 high with a ladder inside, an entrance 2 high on the front, a 5x5 plank platform with the ladder's hole and a fence rail round it: 38 cobblestone, 24 planks, 16 fences, 6 ladders. The centre cell above the ladder hole is left as it is (out of the builder's reach from the ring).
- **Patrol points.** A finished defence is saved with the village (`VillageState.defences`, at most 128, in finish order). A Lamp Post or Watchtower has a patrol point: the first walkable cell next to it (lamp: in front of the post; tower: in front of the entrance), found again at every query. A Wall or a Gate is recorded too, with its length and the cells the build placed, but it has no patrol point and no key block, so a missing block does not drop it (M25). A ravager or a vindicator that belongs to the village's tracked raid breaks the defence cells between it and where it is going (stone in two hits of 6, a gate in two hits of 4) and walks through the opening. At night, or during a siege, a zombie with no target is walked toward the nearest villager with no line-of-sight test and breaks the wall in its way (stone 1 a hit, 12 hits; a gate 2 a hit, 4 hits). A creeper with no target walks to the nearest wall or gate and lights beside it. The blast is spent on the defence cells (the target and the one above or below, neighbours cracked) and still hurts entities; houses and other blocks stay. A creeper chasing a player keeps the vanilla crater. Guards and iron golems do not hunt it. At most 24 zombies and creepers are lured, and at most 2 siege paths run in one tick. A hired builder repairs a breached gate, then breached stone, then a crack at half the block's health or more, then a lighter crack, before starting a new build. The material comes from Storage (cobblestone, or any fence gate). A heavy crack costs one cobblestone and the block stays; a light crack is free. A missing watchtower ladder is repaired the same way. Nothing is repaired during a raid or a siege: a hired worker other than a guard stands in the shelter activity until that threat record is cleared. The Heart lists a raid, then a siege, then damaged walls. The first breach says "The wall of %s was breached" at most once a minute, and the last repair says "The walls of %s are repaired" (M25, track B; beds, doors and the death log are track A). A lamp or tower record goes when its lantern or one of its two ladders is gone (checked every 200 ticks in loaded chunks). `dev.villageheart.defence.Defences.patrolPoints(server)` lists the lamp and tower points for the guards (M18).

### 5.9 Merchant (Market, Market Stall)
It sells stockpile **surplus** for emeralds that go into the treasury. Surplus means stock above a reserve:
3 days of food, and 64 of everything else *(tunable, adjustable on the Stockpile tab)*. It sells at fixed prices
(e.g. 16 logs, 20 wheat, 32 cobblestone, 4 iron ingots or 10 cooked meat = 1 emerald). The daily cap is 8 / 12 / 16
emeralds by level. Unemployed villagers keep their vanilla trades.

Rules as built (M22, `docs/plans/M22.md`):
- **At the stall, in work hours.** The merchant walks to its Market Stall like any worker and sells while it stands within 1.5 blocks of it in the WORK phase
  (day time 1000-11000). About every 30 seconds (600 work units at level 1; 480 at level 2, 400 at level 3) it sells **one lot** for 1 emerald, which goes
  straight into the treasury (`Treasury.deposit`: it pays strikers at once). The goods are taken from the stockpile in the same tick as the sale (nearest
  Storage first); nothing is carried. A stall that is not reached for 600 WORK ticks shows "Can't reach its stall". An invalid Market, a strike or
  hunger stops it (the work clock is `needs.speed()`, and `WorkGate.mayWork` is checked too).
- **Surplus.** Only plain stacks of priced items are ever sold (a renamed, enchanted or damaged item is the player's). Non-food: the count above the
  claims of other systems (builds, cooks) and the **item reserve** (64). Food (any item with village nutrition; crops and cooked meat are priced,
  bread and raw meat are not): the whole village keeps **3 days** of nutrition (`days x (adults x 10 + babies x 5)`, the Overview's numbers), and what is
  above that is for sale, whichever food it is; food is not held to the item reserve. With nobody to feed the food reserve is 0.
- **Prices** (one lot = 1 emerald; `core.market.PriceTable`): 16 of any natural log, 20 wheat, 22 carrots, 26 potatoes, 15 beetroot, 32 cobblestone or
  cobbled deepslate, 15 coal, 8 raw iron, 20 raw copper, 4 iron ingots, 6 leather, 10 of any cooked meat or fish. Not bought: bread, raw meat and fish,
  gold, diamonds, redstone, lapis, saplings, seeds, tools, anything with components.
- **What is sold next**: the item with the most whole lots of surplus (ties: price-table order). Stop reasons, in this order: daily limit, treasury full,
  nothing above the reserves; they show as "Sold today's limit", "Treasury full", "Nothing to sell (all stock is within the reserve)".
- **Caps per day** (day index of the overworld clock, `!=` so `/time set` back also starts a new day; with `advance_time` off the cap never resets):
  a merchant sells at most 8 / 12 / 16 emeralds at level 1 / 2 / 3 (the current level, so a level-up raises the cap at once), a stall at most the same
  (so firing and re-hiring, or swapping villagers, does not reset it), and the **whole village at most 32 emeralds** (`Balance.MARKET_VILLAGE_DAILY_CAP`),
  so many stalls and a cobblestone generator are not an unbounded emerald farm. Each sale gives 2 XP (`WorkUnit.TRADE_MADE`) and counts in
  `work.merchant.sales` / `work.merchant.emeralds`.
- **Settings and saved state**: the two reserves (food days 0..30, items 0..1024) and today's ledger are saved on the claim (`WorldClaim.market`, key `market`,
  clamped on read, a broken entry drops that entry only) and reset when the village is released. The Owner and Stewards change the reserves on the Stockpile
  tab (`SetReserves`, `Permission.SET_RESERVES`); `/villageheart market reserves <days> <items>` and `market status` (op) do the same.

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
  - The L3 harness models the player who keeps a village alive: a force-loaded square over the claim, centred on the Heart, plus a moving
    ticket around every hired worker, like a player's simulation distance. Without it, a worker that wanders past a fixed square freezes. That
    is a harness artefact that a nearby player never sees. Such a worker is still reported: its farthest distance from the Heart is a metric and
    is asserted under the 150-block walk-home limit (docs/TESTING.md, "Ticking area").

## 7. Population growth
At each dawn, if there is **≥1 free bed** in a valid House, **food days left ≥3**, and ≥2 adult villagers who are fed
and housed, the mod makes two of them willing to breed (vanilla breeding). The cap is 1 birth per day.
Babies are normal vanilla villagers and can be hired as adults.

## 8. Threats and survival
- **Night mobs** (vanilla): villagers go indoors at night (vanilla behaviour). Guards on night shift patrol.
  Lamp posts and walls reduce spawns and access.
- **Raids** (vanilla Bad Omen raids). The Heart screen shows a raid alert, all guards come on duty,
  golems fight, and workers go indoors.
  As built (M25 track A, step 2): a raid counts when it is still going and its centre or a raider is inside the claim
  (the 80-block sphere, the edge included). The tracker latches that raid by its vanilla id; a different id does not
  replace it. Victory beats a loss, and a loss beats a stop or an over raid; a raid that vanishes is gone. The alert
  line is danger: coming, the wave and how many raiders are left, or between waves. The screen and the call-up
  are later steps.
  As built (step 3): a worker shelters in its own House bed, else the nearest free House bed, else the nearest
  valid room's inside door. A worker who is already asleep at home stays there. Someone away does not turn back.
  The online owner, stewards and residents are the ones who hear a raid.
- **Hunger and homelessness**: these slow or stop work (§4). Villagers **do not die** of either.
- Deaths are tracked (cause and day) for the Overview tab and the simulation metrics.
  As built (M25 steps 1 and 4): the village keeps the last 32 (cause, day, whether it was in the claim, and whether
  a raid was on). A hired worker is logged wherever they die. Any other villager is logged only inside the claim.
  A conversion within one tick is the same loss as the death, whichever event arrives first. A witch conversion is
  lightning. A cure is not a death. The Overview rows are a later step.

## 9. Progression: village tiers

| Tier | Reached when | Unlocks |
|---|---|---|
| **Hamlet** | Heart placed | House, Storage, Farmhouse, Lumber Camp, Kitchen. Job level cap 1 |
| **Village** | population ≥6, ≥3 workers, valid Storage + Farmhouse + Kitchen, fed 3 days in a row | Builder's Hut, Mine + Mineshaft, Hunting Lodge + Hunting Ground, Barracks + Training Yard, Market. Blueprints except Watchtower. Level cap 2 |
| **Town** | population ≥12, ≥8 workers, ≥2 guards, fed 7 days in a row, treasury ≥32 at some dawn | Smithy (golems, gear), Watchtower. Level cap 3 |

The tier never goes down in v1. The Overview tab shows each goal's progress.

As built (M14): the check runs once at every dawn, after wages, and the tier goes up at most one step per dawn. Population is the Overview's number (loaded villagers in the claim, babies included, plus registered workers that are not loaded there). Workers are all registered workers, strikers included. A fed day is a dinner where every diner was fed (children included); a hungry dinner resets the streak to 0, a day without a dinner leaves it alone. The treasury goal counts the purse after wages at a dawn and is sticky ("at some dawn"). On a tier-up members get a chat line and a sound, and every building is re-checked at once.
At every tier, tool workers make their own wooden and stone tools (§4.1); the Smithy (Town) adds better tools.

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
  As built in M22: a third header line shows "Market: sold N emeralds today" (or "No merchant: nothing is sold" while the storage alert is not showing); an item
  count is green when the market may sell some of it (tooltip "Up to N for sale above the reserve"; for food the numbers share one spare, so it is not a
  sum); the bar at the bottom reads `Keep: food [-] 3 days [+]   other items [-16] 64 each [+16]` (buttons for a Steward or Owner, the values for
  Residents). The buttons send the absolute new pair, so a double click cannot drift.
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
- **Gate opening (M18).** Vanilla types a closed fence gate `PathType.FENCE` (a wall for every mob), while a closed wooden door is
  `DOOR_WOOD_CLOSED`, which `WalkNodeEvaluator.getPathTypeWithinMobBB` turns into `WALKABLE_DOOR` for a mob that can open doors (villagers
  can), and vanilla's `InteractWithDoor` opens a door when the path reaches it. Two more mixins give gates the same treatment for hired
  workers and guards only: `WalkNodeEvaluatorMixin` redirects the per-cell path type so an eligible walker sees a gate as `DOOR_WOOD_CLOSED`
  (`GatePaths.mayPass`: a hired villager, a gate inside the claim, not powered, no hostile within 8 blocks unless an engaged guard), and
  `NodeEvaluatorAccess` reads the mob being planned for. `GateTraffic` (CORE of the worker and the guard brain) opens a gate that is among the
  next nodes of the navigation path; `GateKeeper` (a village system, every 5 ticks) closes each gate it opened when no worker is at it or about
  to pass, no one stands in it, and at the latest after 400 ticks. The gates it opened are a saved ledger (`GateLedger`), so a gate open at
  shutdown, or left by a fired or removed walker, is closed after the reload.
- Worker data uses Fabric data attachments on the entity. Village data uses world saved data.
- **Performance budget**: village logic averages under **1 ms per tick** with 20 villagers, measured by layer 3.
- Every number marked *(tunable)* belongs in one `core` balance class.
