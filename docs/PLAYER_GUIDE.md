# Village Heart — player guide

Village Heart is a Fabric mod for Minecraft 1.21.11. You craft a Village Heart, place it by a village bell, and that village is yours: one village in the world. You mark buildings, hire villagers, and keep them fed, housed, and paid. They chop, farm, mine, hunt, cook, build, sell, and stand guard. You lead. You can still do any of the work yourself.

A Minecraft day is 24000 ticks (20 real minutes). Day time 0 is dawn, 6000 is noon, 12000 is dusk, and 18000 is midnight. Where this guide gives a tick, that is the number you see as the day time.

This is the guide for version 1.0. Everything in it is in the game. The village has three tiers (Hamlet, Village, Town); more are planned for later, after the first version has been played.

## Craft the Heart and claim the village

The Village Heart is a shaped recipe. Gold ingots in the corners, emeralds on the edges, a diamond in the middle:

```
G E G      G = gold ingot
E D E      E = emerald
G E G      D = diamond
```

Place it within **16 blocks of a village bell**. You become the Owner. The claim is a sphere of **80 blocks** around the Heart. Buildings and work have to sit inside that sphere. Two outdoor sites, the Mineshaft and the Hunting Ground, may be up to **128 blocks** from the Heart.

One claimed village per world. A second Heart, anywhere, is refused and stays in your hand.

Only the Owner can break the Heart. Breaking it releases the village: every job is cleared, villagers go back to ordinary villagers, and the emeralds in the treasury drop as items. You can place the Heart again, at the same bell or another one. The wither and the ender dragon do not break it, and pistons do not push it.

Village data is saved with the world: the claim, owner, ranks, treasury, tier, buildings, and workers. It is still there after you quit and rejoin.

If the chunks around the Heart are unloaded, the village pauses, the same way Minecraft pauses empty chunks. Dinner and wages are not made up for time nobody was nearby.

Right-click the Heart to open its screen. Sneaking with an item in your hand does not open it. Right-click with emeralds in your main hand to deposit that stack, if your rank allows it.

## Ranks

Several players can run the one village. A new player is a Visitor.

| Rank | What you can do |
|---|---|
| **Owner** (one) | Everything: set ranks, hand the village over, break the Heart, withdraw emeralds |
| **Steward** | Hire and fire, start blueprints, set guard shifts, deposit emeralds |
| **Resident** | Open Storage and the chests inside claimed buildings, deposit emeralds, view every tab except Ranks |
| **Visitor** | View the Overview tab. Chests inside claimed rooms stay shut |

The Owner sets ranks on the Ranks tab. The list is players who are online and players the village has already seen, up to 63 people with a rank. The Owner can give the village to a Steward or a Resident. That takes a second click to confirm. The old Owner becomes a Steward. The treasury, buildings, and workers stay as they are.

A Visitor cannot open or break a block that holds items inside a room building: chests, barrels, furnaces, hoppers, shulker boxes, brewing stands, and the like. The personal ender chest is left alone. A Visitor also cannot break a building marker. Sneak-placing a block against a chest still works.

Ranks are not grief protection. A Visitor can still break a wall or a floor. Hoppers, pistons, explosions, copper golems, and chest minecarts are not stopped. A Visitor's arrow, snowball, trident, or wind charge can still break a decorated pot. Outdoor sites have no chest protection. A server that needs a full claim should add a claim mod.

An operator can take over an abandoned village with `/villageheart owner <player>`. Being an operator does not bypass the other rank rules.

## Markers and valid buildings

A building counts when it is enclosed and holds what that type asks for, and a marker names it.

### Craft a marker

A **Blank Marker** is 8 planks of any wood around an item frame:

```
P P P      P = any planks
P F P      F = item frame
P P P
```

Each building marker is a Blank Marker in the **centre**, with that building's ingredients in the eight slots around it. Corners are one ingredient. The four edges are another. Two markers put a tool on the top edge and a different item on the other three edges: the Lumber Camp (stone axe on top, sticks on the sides and bottom) and the Mine (stone pickaxe on top, rails on the sides and bottom).

The marker is a small plaque. Place it on the **outside** wall, above or beside a door, so it faces out. The room behind that door is the building. A building may have several doors. It needs one marker. Outdoor sites are placed on a post or a fence at the site, not on a room.

Three colours:

- **Grey** — not checked yet
- **Red** — invalid. Right-click the plaque. The reasons are printed in chat, with a position when a block is missing
- **Green** — valid

You can craft and place a marker from a higher tier. It stays red with the reason "Requires Village tier" or "Requires Town tier" until the village reaches that tier.

Buildings are re-checked about once a second, one after another, and straight away when you use the marker or the Heart screen. A change shows up within about one second per building.

### What every room must be

The check starts in the air just inside the marked door and fills outward.

- **Enclosed.** Walls, floor, and roof are solid. Doors, glass, panes, fences, trapdoors, and slabs count as wall. If the fill reaches the sky, or more than **4096** air blocks, the room fails.
- **A door** in the marked doorway.
- **Minimum floor area** for that type, in the table below.
- **Required blocks** anywhere inside the filled room.
- **One building per room.** Two markers on the same room make both of them invalid.

Every failure has a reason, such as "Roof missing at (12, 70, -4)", "Needs 1 more chest", or "Room too big (max 4096 air)". Storage counts chests and barrels, and the reason still says "chest". A marker outside the claim turns red and is not registered: "Outside the village: 93 blocks from the Heart (max 80)" is the shape of that message. A marker placed before you claim is adopted when the Heart is placed, if its chunk is loaded, or when you right-click it.

Each **job block** inside a valid building is one worker slot.

### Room buildings

| Building | Tier | Min floor | Required blocks | Marker corners / edges |
|---|---|---|---|---|
| House | Hamlet | 12 | at least 1 bed, and a light | planks / white wool |
| Storage | Hamlet | 9 | at least 2 chests or barrels | planks / chest |
| Farmhouse | Hamlet | 9 | composter, chest | dirt / wheat |
| Lumber Camp | Hamlet | 9 | Chopping Block, chest | logs / stone axe on top, sticks on the other edges |
| Kitchen | Hamlet | 9 | smoker, chest, crafting table | cobblestone / coal |
| Builder's Hut | Village | 9 | Builder's Table, chest | brick / ladder |
| Mine | Village | 9 | stonecutter, chest, a linked Mineshaft | cobblestone / stone pickaxe on top, rails on the other edges |
| Hunting Lodge | Village | 9 | fletching table, chest, a linked Hunting Ground | leather / arrow |
| Barracks | Village | 16 | grindstone, 1 bed per guard slot, chest | cobblestone / iron ingot |
| Market | Village | 9 | Market Stall, chest | white wool / emerald |
| Smithy | Town | 12 | blast furnace, anvil, chest | iron ingot / smooth stone |

The Smithy is the blacksmith's workshop. What the blacksmith does is in [Blacksmith](#blacksmith).

A House's beds are homes: one bed, one villager. A Barracks needs a bed for every grindstone, because each grindstone is a guard slot. Where the table says a workstation, more than one means more than one worker.

Three job blocks are crafted. The others are vanilla blocks.

| Job block | Recipe |
|---|---|
| Chopping Block | any axe on top of any log |
| Builder's Table | paper, centred, over a row of brick, crafting table, brick |
| Market Stall | three wool over plank, emerald, plank |

```
Chopping Block     Builder's Table     Market Stall

A                  . P .               W W W
L                  B C B               P E P
```

A is any axe, L any log, P paper (Builder's Table) or any planks (Market Stall), B a brick, C a crafting table, W any wool, E an emerald. The paper sits in the middle of the top row.

### Outdoor sites

These have no room and no floor size. The marker goes on a post or a fence. The Mineshaft and the Hunting Ground may be up to 128 blocks from the Heart. The Training Yard uses the normal 80-block claim.

| Site | Tier | Marker corners / edges | Valid when |
|---|---|---|---|
| Mineshaft | Village | rail / torch | The entrance is a player-dug hole. Connected air drops **at least 12 blocks** below the marker, with **at least 8 rails** and a light. The hole has to be under the post or next to it: near the surface the check only spreads one block around the marker. It links to the nearest Mine within **48 blocks**. |
| Hunting Ground | Village | stick / feather | **At least 40** grass, leaf, or log blocks within 12 blocks, and the site is outside any House or Farmhouse. It links to a Hunting Lodge within **40 blocks**. |
| Training Yard | Village | hay bale / wooden sword | **At least 2** armor stands or target blocks within 6 blocks. It links to the nearest Barracks. Optional. Guards gain sparring experience only here. |

The Mineshaft's deepest point picks the ore table in the miner section. A light set into the shaft wall counts. The fill stops at 4096 cells and at 16 blocks sideways.

## Jobs

Hire on the Heart screen's Villagers tab. Pick an adult villager inside the claim, then a job with a free slot. Steward and Owner can hire and fire. A baby cannot be hired.

A hired villager drops its old profession and **does not trade** while it works for you. Firing makes it unemployed. It can take a vanilla profession again. Its old trades are not given back.

Workers are ordinary villagers. The job shows on the profession clothing, on what they hold, and with particles.

### The day

For every job except the guard:

| Day time | What they do |
|---|---|
| 0–1000 | Wake |
| 1000–11000 | Work |
| 11000 | Dinner, taken from Storage wherever they stand |
| 11000–12000 | Walk home |
| 12000 onward | Sleep in a bed |

A worker carries up to one stack. When that is full, or at the end of the work day, they walk to the nearest Storage with room, deposit, and go back. Tools, seeds, raw food, and ore are fetched the same way.

### Tools and levels

Right-click a hired villager to see what they are carrying. The card shows their name, job, level, the same XP bar as the Heart, what they are doing, and whether they are fed, housed, and paid. The eight slots are look-only. A guard's card also shows the sword and armour they are wearing. An unhired villager still opens the trade window.

Each worker has their own chest in the workplace, the nearest one nobody else owns. A lumberjack, miner, hunter, or farmer keeps the first slot for their tool. During the work day the tool is in that slot. At the end of work they put it back in their chest, take gathered goods to Storage, then go home. In the morning they take the tool out again. If the chest is full, the tool stays in their pocket overnight. If they die, the tool goes back into the chest and the rest drops. A room with two workplaces and one chest hires one worker. The other, in an old village that already had two, waits until you add a chest and shows "Needs a chest".

A tool in **that worker's chest** is the one they carry. It loses durability while they work. A better tier is faster. The multiplier is the vanilla tool speed divided by 2, so a stone tool is the baseline:

| Tool | Speed |
|---|---|
| Wood | ×1.0 |
| Stone | ×2.0 |
| Copper | ×2.5 |
| Iron | ×3.0 |
| Diamond | ×4.0 |
| Netherite | ×4.5 |
| Gold | ×6.0 |

The lumberjack, miner, and hunter do nothing without their tool. The farmer works at full speed without a hoe; a hoe is ×1.25.

Workers are level 1, 2, or 3. Level 2 is **100 XP**. Level 3 is **300 XP**. Speed is ×1.0, ×1.25, and ×1.5. Hamlet caps them at level 1, Village at 2, Town at 3. XP still banks up to 300 while the tier holds the level back, and the level arrives within a second of the tier-up. Firing wipes level and XP. A rehire starts at level 1 with 0 XP.

XP per bit of work: a log, a harvested crop, a cooked item, or a mined item is 1. A hunting trip is 5. A kill is 5. A sparring unit is 1. A block the builder places is 0.2. A sale is 2. Walking, replanting, and the trip to Storage earn nothing. A level-up plays the celebrate sound, shows happy particles, and tells Residents and above in chat.

Work speed is tool × level × the needs multipliers below.

### Wooden and stone tools, made on the job

Until the smith arrives, a hired lumberjack, miner, hunter, or farmer makes a missing tool, or replaces one that is about to wear out (under 10% durability, or under two uses left). They do this at every tier.

They make **stone** when Storage can afford cobblestone, blackstone, or cobbled deepslate, plus sticks or planks. Otherwise they make **wood** from planks, sticks, or logs. They never make copper or better. Those tiers are the smith's (see [Blacksmith](#blacksmith)). The counts are the vanilla ones: an axe or a pickaxe is 3 material and 2 sticks, a hoe is 2 and 2, a sword is 2 and 1. Two planks make 4 sticks. One log makes 4 planks. A usable tool already in Storage is fetched instead of crafted.

It is slow, and it uses that worker's own work time. At level 1, wood takes **2400 ticks** and stone takes **4800 ticks** at the job block. The craft is saved. It pauses overnight and continues. The farmer, whose hoe is only a bonus, makes one only when there is nothing else to do. A working tool is never thrown away because better material showed up. A tool you put in the chest cancels a craft, and the materials go back to Storage.

The Villagers tab says "Making a wooden axe" or "Making a stone pickaxe", "Needs axe (no wood)" when the material is missing, and "Needs axe (no room)" when the chest has no free slot.

### Lumberjack

Needs a Lumber Camp, a Chopping Block, and an **axe**. Without an axe the status is "Needs axe" and no tree is cut. Replanting still runs.

They fell the nearest **natural tree** within 32 blocks horizontally and 16 blocks vertically of the Chopping Block: a column of natural logs touching leaves. The whole connected tree comes down, top first. Leaves decay as usual. Drops (logs, saplings, sticks, apples) are picked up, and a sapling of that wood is replanted on the stump. Logs you placed are left alone. Logs inside a valid building are left alone. A bare log post standing on soil under a few natural leaves can be mistaken for a tree.

At level 1 with a stone axe, one log takes about **2 seconds** (40 ticks). A wooden axe takes about 4 seconds. An iron axe takes about 1.3 seconds. No new tree starts after day time 5000, because drops despawn. A tree already started is finished later, by any lumberjack of that camp. If Storage is full or out of reach, they stop cutting. Saplings the stumps still need, plus 4, are kept back from Storage.

### Farmer

Needs a Farmhouse and a composter. No tool is required. A hoe in that farmer's chest is ×1.25, any tier, and wears out one durability per harvest. Without a hoe nothing is reported and the work is full speed.

They work farmland whose crop is within **24 blocks** horizontally of the composter and within 8 blocks of height. They harvest ripe wheat, carrots, potatoes (age 7), and beetroots (age 3), and replant in the same moment. They do not till new ground, and they do not touch torchflowers, pitcher plants, stems, berries, or nether wart. Empty farmland inside the area is planted with whichever of those four crops is most common next to it. Ties go wheat, then carrots, potatoes, beetroots. A neighbour's field inside the 24 blocks is worked too.

Surplus wheat seeds and beetroot seeds (above 16 of a kind) are composted. Composting is steady: 21 seeds make one bone meal. Bone meal goes on the youngest crops, crops other than beetroot first. They keep one stack of each seed, 16 carrots, and 16 potatoes for replanting. The rest of the harvest goes to Storage. A full Storage stops the harvest. Planting, bone meal, and composting continue.

### Miner

Needs a Mine, a stonecutter, a linked Mineshaft, and a **pickaxe**. They walk to the shaft and work down inside it, hidden, then come back with one haul. There is no tunnelling you can watch. A haul is **2 in-game hours** of underground work: 2000 ticks with a stone pickaxe at level 1 (about 100 seconds), plus the walk. Wood takes 4000 ticks, copper 1600, iron about 1333, diamond 1000, netherite 889, gold 667. The pickaxe loses 10 durability per haul. They come up at day time 11000 without a haul, and they do not start a new descent after 10400.

The haul depends on the shaft's deepest Y, at level 1 with a stone pickaxe. A wooden pickaxe uses the same table, more slowly. Diamonds need an iron pickaxe or better, and Y below 0.

| Depth | Each haul |
|---|---|
| Y ≥ 40 | 24 cobblestone, 2 coal, 1 raw copper, 30% chance of 1 raw iron |
| 0 ≤ Y < 40 | 18 cobblestone, 2 coal, 2 raw copper, 2 raw iron, 20% redstone, 10% lapis |
| Y < 0 | 18 cobbled deepslate, 1 coal, 3 raw iron, 30% redstone, 20% lapis, 20% raw gold, 2% diamond (iron pickaxe or better) |

A redstone or lapis hit is 4 of that item. Gold and diamond hits are 1. What does not fit is delivered later, never dropped on the ground. If the rails are removed, the miner comes up and the Mine goes invalid until a Mineshaft is linked again.

### Hunter

Needs a Hunting Lodge, a fletching table, a linked Hunting Ground within 40 blocks, and a **bow, crossbow, or sword**. Spears, the mace, and the trident are not hunter weapons. A bow or crossbow is taken before any sword. The weapon is in that hunter's pocket during the day and loses 5 durability per finished trip. The tier of the weapon does not change how often they hunt.

They walk to the Hunting Ground and are gone for **3 in-game hours** (3000 ticks at level 1, 2400 at level 2, 2000 at level 3), then return. A trip brings 2–4 raw meat (beef, pork, mutton, or chicken, weighted by the animals that spawn in the biomes around the marker), 1–2 leather, 1–2 feathers, and a 50% chance of a rabbit hide. With a bow or crossbow, one arrow from the chest (plain arrows first) adds one extra meat. With no arrow they still hunt, without the bonus. Infinity is not special-cased.

Level 1 takes **2 trips a day**. Levels 2 and 3 take **3**. A trip only starts if it can finish 300 ticks before work ends at 11000. At level 1 the last start is day time 7700.

### Cook

Needs a Kitchen, a smoker, a chest, and a crafting table. No tool. They need fuel from Storage: coal and charcoal are worth 1600 heat, a log is worth 300, and one cooked item costs 200. Heat is saved on that smoker, so half a coal is not wasted. With food to cook and no fuel, the status is "Needs fuel". With nothing to cook, it is "Nothing to cook".

Recipes, best nutrition gain first:

| Result | Inputs | Nutrition gain |
|---|---|---|
| Cooked beef, cooked porkchop | the raw item, at the smoker | +5 |
| Bread | 3 wheat, at the crafting table | +5 |
| Cooked chicken, mutton, salmon | the raw item, at the smoker | +4 |
| Baked potato | potato, at the smoker | +4 |
| Cooked cod | cod, at the smoker | +3 |
| Cooked rabbit | rabbit, at the smoker | +2 |
| Beetroot soup | 6 beetroot and a bowl, at the crafting table | 0 |

The cook stands at the smoker and converts what they are carrying. One item takes **125 ticks** at level 1 before the walk, which is 8 items per in-game hour at the block. With fetching and depositing, expect about 4 or 5 items an hour. A batch is at most 8 items, and soup does not stack, so a batch of soup is smaller. No batch starts unless it can be back in Storage before dinner at 11000. At the end of the work day the cook keeps nothing.

### Builder

Needs a Builder's Hut and a Builder's Table. The blueprints, the site rules, and the pace are in [The builder and blueprints](#the-builder-and-blueprints).

### Guard

Needs a Barracks. Shifts, training, rations, and the alarm are in [Guards](#guards).

### Merchant

Needs a Market and a Market Stall. Prices and caps are in [The market](#the-market).

### Blacksmith

Town tier. Needs a valid Smithy (blast furnace, anvil, chest). The blacksmith works only in work hours (day time 1000–11000) and picks up again the next morning.

- **Tools and armour, on demand.** The smith makes a tool or a piece of armour only when a worker is missing one or has a worn-out one. A working tool is never replaced or upgraded, and there are no spares. The tier is the best the stockpile can pay for (wood, stone, copper, iron, diamond). Guards get swords and armour the same way. Diamonds are kept back: five are always left in Storage. Crafting is slow: an iron tool takes 2400 ticks of work at speed 1, a diamond tool 4800. Raw iron, copper, and gold are smelted into ingots first, using fuel from Storage.
- **Repairs.** Put worn gear in the Smithy chest. One ingot (or one leather) restores a quarter of the item's durability. Wood and stone tools are replaced, not repaired.
- **Iron golems.** 36 iron ingots and 1 carved pumpkin, one day of work at the anvil. At most one golem per 5 villagers. Vanilla villagers no longer spawn golems inside the claim, so these are the village's golems. A golem below half health walks to the Smithy, and the smith heals it, 25 health per ingot. Golems never go after creepers.
- With no smith hired, workers keep making their own wooden and stone tools.

## Food, needs, and dinner

Three needs. They show as icons on the Villagers tab. There is no mood score.

| Need | Rule | If it fails |
|---|---|---|
| Fed | Ate at least 10 nutrition at the last dinner. A child needs 5. | One missed dinner: work at ×0.5. Two or more: they stop working. They never die of hunger. |
| Housed | A bed in a valid House, or in their own building. Guards sleep in the Barracks. | Work at ×0.75. |
| Paid | The wage was paid at the last dawn. | On strike. Work speed is 0 until the treasury can pay. Unpaid wages are not saved for later. |

Dinner is at day time **11000**. The meal comes straight out of Storage chests. Nobody walks to the kitchen for it. Every living, loaded villager inside the claim eats: workers, unemployed villagers, nitwits, and children. A villager in an unloaded chunk keeps their old hunger and is not fed in arrears. Food in a villager's own inventory is not the dinner. Items a build or a cook has reserved are not eaten.

Best food first: cooked before raw, then higher nutrition, then higher saturation. Bread is 5, a baked potato is 5, cooked beef is 8, a carrot is 3, an apple is 4, and raw meat counts as its vanilla raw value. Never served: rotten flesh, spider eye, poisonous potato, pufferfish, suspicious stew, chorus fruit, golden apple, enchanted golden apple, and golden carrot. The hungriest eat first. A villager eats until they reach their 10 (a second beef can go past 10) or, if the food left cannot cover them, they get nothing. Stews put the bowl back in Storage.

**Food days left** is the nutrition in Storage divided by the daily need: adults × 10, plus children × 5. That number is on the Overview.

An unhoused worker who is awake moves into the nearest free bed in a valid House. They do not take another worker's bed.

## Wages and the treasury

The treasury is emeralds stored in the Heart, capped at **100,000**.

Deposit: right-click the Heart with emeralds in your main hand. The whole stack goes in, up to the cap. Owner, Steward, and Resident can deposit. A Visitor is told no, and the Overview opens instead. "Deposit all" on the Overview takes every emerald in your inventory. A renamed emerald still counts, and it loses its name.

Withdraw 1 or 64 from the Overview. **Owner only.** What does not fit in your inventory drops at your feet.

Wages are paid at **dawn**, once, while the Heart's chunk is loaded. There is no catch-up for days you skipped or spent elsewhere.

| Level | Emeralds per day |
|---|---|
| 1 | 1 |
| 2 | 1 |
| 3 | 2 |

If the treasury is short, workers are paid in hiring order. A worker who cannot be paid **strikes for that day**, and the pay run continues, so a later worker with a smaller wage can still be paid. A striker's status is "On strike". They do no work. Deposit trips still happen. Any new emeralds (a deposit, or a merchant sale) pay today's strikers immediately, at the wage they were owed at dawn, in hiring order.

A villager you hire is charged at once. Hiring when the treasury is empty puts them on strike. Firing at dusk and hiring again at dawn is not a free day. Hiring someone who was already paid today costs nothing more that day.

**The merchant never strikes.** Their sales pay every wage, their own first, so a village with an empty treasury can still earn its way out.

The Overview shows the treasury, "Wages tomorrow", in red when the purse is short, and a line when workers are on strike.

## Tiers and how to grow

The village starts as a **Hamlet** the moment the Heart is placed. The tier is checked once at each dawn, after wages, and it rises by at most one step. It never drops. The Overview lists each goal.

Population is the villagers loaded inside the claim, babies included, plus hired workers who are recorded but not currently loaded. Workers include people on strike. A fed day is a dinner where every villager who ate at that dinner was fed, children included. A hungry dinner resets the streak to 0. A day with no dinner, because the chunks were unloaded or the clock jumped over 11000, leaves the streak as it is. The treasury goal looks at the purse after wages, and once it has been true at a dawn it stays true.

| Tier | Reached when | What it unlocks | Job level cap |
|---|---|---|---|
| Hamlet | Heart placed | House, Storage, Farmhouse, Lumber Camp, Kitchen | 1 |
| Village | population at least 6, at least 3 workers, a valid Storage, Farmhouse, and Kitchen, and fed 3 days in a row | Builder's Hut, Mine and Mineshaft, Hunting Lodge and Hunting Ground, Barracks and Training Yard, Market, and every blueprint except the Watchtower | 2 |
| Town | population at least 12, at least 8 workers, at least 2 guards, fed 7 days in a row, and treasury at least 32 at some dawn | Watchtower. The Smithy (blacksmith, gear, golems). | 3 |

On the way up, everyone online at Resident or above gets a chat line and a sound, and every building is re-checked.

Babies count toward population and eat half a meal.

**Growth.** Inside the claim, feeding villagers does not make babies. At each dawn the village starts at most one birth, and only if there is a free bed in a valid House, at least **3 days of food**, and two adults who are fed and housed. The two adults who are closest to each other get a licence for the day, walk to each other, and the baby is born into the free bed. A parent rests for a while after a birth. Outside the claim, vanilla breeding is unchanged. Babies grow up and can be hired like anyone else.

## The market

A merchant stands at a Market Stall during work hours (day time 1000–11000) and sells **surplus** from Storage. About every **30 seconds** at level 1 (600 ticks of work; 480 at level 2, 400 at level 3) they sell one lot for **1 emerald**, straight into the treasury. The goods leave Storage in that same moment. Nothing is carried. A stall they cannot reach for 600 ticks of work shows "Can't reach its stall".

Surplus for ordinary items is the count above **64**, and above anything a build or a cook has already reserved. Food is different: the village keeps **3 days** of nutrition (the same adults × 10 + children × 5 as the Overview), and anything above that may be sold. Food is not also held to the 64 reserve. With nobody to feed, the food reserve is 0.

The Owner and Stewards change the two reserves on the Stockpile tab. Food days can be 0 to 30. The other-items reserve can be 0 to 1024, in steps of 16 on the screen. Defaults are 3 days and 64.

One lot is one emerald:

| Goods | Count in one lot |
|---|---|
| Any natural log | 8 |
| Wheat | 20 |
| Carrots | 22 |
| Potatoes | 26 |
| Beetroot | 15 |
| Cobblestone or cobbled deepslate | 16 |
| Coal | 10 |
| Raw iron | 6 |
| Raw copper | 14 |
| Iron ingot | 4 |
| Leather | 4 |
| Any cooked meat or cooked fish | 10 |

Not bought: bread, raw meat and raw fish, gold, diamonds, redstone, lapis, saplings, seeds, tools, and anything renamed, enchanted, or damaged.

They sell whichever item has the most whole lots of surplus. The Villagers tab says why they stopped: "Sold today's limit", "Treasury full", or "Nothing to sell (all stock is within the reserve)".

Caps, reset when the day index changes:

| | Emeralds per day |
|---|---|
| A merchant at level 1 / 2 / 3 | 16 / 20 / 24 |
| That same stall | the same 16 / 20 / 24, so firing and rehiring does not reset it |
| The whole village | **32** |

Each sale is 2 XP. Unemployed villagers keep their vanilla trades. The Stockpile tab shows how many emeralds were sold today, and item counts that can still be sold are marked.

## The builder and blueprints

At Village tier, a Steward or the Owner uses the Builder's Table in a valid Builder's Hut. Anyone in the claim may open the table and read the list. Take and Cancel need Steward or Owner.

Taking a blueprint gives you a **Blueprint** item. Using it on the ground shows the footprint as particles: green if that site is allowed, red if it is not. The door faces you. Sneak-use rotates the plan by a quarter turn. Use it again inside the green outline to confirm.

Wall Segment and Gate are a line, not a footprint. The first click sets the start (clicking grass or snow uses the block below). The second click sets the end. The line has to run straight along x or z, level with the start's ground, and be at most **32 blocks** including both ends. A gate is at least **5 blocks**. A diagonal, a line that is too long, or a gate that is too short keeps the start, so the next click is another try at the end. A good line is shown in green or red dust. Click the shown line to confirm. Any other click picks a new end. Sneak-click clears the start.

The site check refuses a plan that fails any of these, and the particles go red:

- You are a Visitor or a Resident, the blueprint's tier is above the village, or the spot is in the wrong dimension or out of height
- It is outside the 80-block claim
- 8 builds are already going
- The chunks are not loaded
- It overlaps a marked building's room or marker, or another build's standing room
- Water or lava is in it or next to it
- A natural tree is in the way (fell it first)
- A block that is not natural ground is in the way
- The builder would have nowhere to stand within 7 blocks of every cell
- For a wall or gate, some cell has no solid ground under it

Natural ground, which the builder may clear, is dirt, stone, sand, snow, plants, flowers, saplings, leaves, coal, iron, and copper ore, and a few wild crops. Player-placed leaves are left. Fluids and blocks with an inventory are left. A torch, ladder, door, sign, rail, lantern, item frame, or painting that would fall because the build clears its support refuses the site, or stops a build that is already going. The builder never breaks those.

Dirt, stone, or sand you placed in the open, outside any marked room, can be cleared, because it looks like terrain. The same is true of a pumpkin or melon patch. Mark a room you want kept, or do not confirm a site on top of it. Drops from what is cleared go to Storage.

The builder reserves every material for the build in Storage, fetches about a stack at a time, clears the top layer first, and places from the bottom, working from the ring around the site rather than from inside it. Pace is **one block per 3.5 seconds** at level 1, faster with level and with needs met. Torches, then water, then the marker go in last, so a finished house is complete the first time it is checked. If something is short, the build pauses and the Buildings tab names it, for example "Needs 12 more planks". It resumes on its own after the planks arrive. Cancelling keeps the blocks already placed and consumes the blueprint. Firing the builder sends what they are carrying back to Storage.

| Blueprint | Tier | What you get |
|---|---|---|
| Small House | Village | A 5×5 floor inside the walls (the shell is 7×7). 49 cobblestone, 119 planks, a door, a bed, a torch, and the House marker. 172 placements. It can turn green as a House when it finishes. |
| Storage Shed | Village | The same shell, with two chests and the Storage marker. |
| Farm Plot | Village | 11×11. 80 farmland around one water source, 39 fences, and a fence gate. The water is placed from a bucket, and the bucket comes back. The farmland is already moist. |
| Wall Segment | Village | Cobblestone, 3 high, any straight length up to 32. 3 cobblestone per block of length. A wall of 20 is 60 cobblestone. |
| Gate | Village | The same wall with a centred opening 3 wide: fence gates on the bottom, air above them, a cobblestone lintel. 3 cobblestone per block, minus 6, plus 3 fence gates. You can walk through when a gate is open. A closed gate is too tall to jump. |
| Lamp Post | Village | 2 fences and a lantern on top. Stops monster spawns nearby. Details under gates and lamps. |
| Watchtower | Town | A 3×3 cobblestone shaft, 5 high, with a ladder, a 2-high entrance, and a 5×5 plank platform with a fence rail. 38 cobblestone, 24 planks, 16 fences, 6 ladders. A guard patrol point. |

Walls and gates are level. The line sits at the start's ground height. Higher natural ground inside that box is cleared. Lower ground is refused ("No ground under …"). A slope is several segments. A dirt path or farmland counts as ground. Short grass and a snow layer do not. Defences may touch — wall to wall, a corner, a lamp beside a wall — and they may not overlap.

## Guards

A guard is hired at a grindstone in a valid Barracks, by day only. From dusk to dawn (day time 12000–24000) the hire is refused. A guard who falls at night is replaced the next morning.

### Wooden sword, then whatever the village owns

A new guard starts with a **wooden sword and no armour**, and with **20 health**, the same as any villager. They take better gear from their own Barracks chest and from Storage: leather, then copper, then chainmail and gold, then iron, then diamond, then netherite, and swords by their attack damage. Enchantments are not compared. They pick up better gear at the start of duty and again every 1200 ticks. A night guard hands the sword and armour back to their own chest at dawn, and a day guard at dusk. Armour does not show on the villager. The sword does. A guard who dies drops that gear. A converted guard drops it too.

There are no archers. Guards do not use bows.

### Shifts

Each guard is on a day shift or a night shift. **With 1, 2, or 3 guards, all of them work nights.** There is no day shift, so the alarm is the only daytime cover. From the 4th guard on, the day shift gets one guard per four: 4–7 guards means 1 on days, 8–11 means 2. Nights keep the rest, and nights always have at least 3.

**Seniors take the nights.** Seniority is level, then XP. A new guard has the least of both, so with four or more guards they take an open day slot and move to nights as they gain XP, or when a night guard falls. A Steward can pin a guard to a shift with the Shift button. A pin does not move.

| | On duty | Off duty, awake | Asleep |
|---|---|---|---|
| Night shift | 12000–24000 | 0–1000 | 1000–12000, in a Barracks bed |
| Day shift | 0–12000 | 12000–13000, handing gear in and sparring | 13000–24000 |

A night-shift guard sleeps through the day. Each guard has their own Barracks bed. Ordinary villagers are not given those beds.

### Training day

A newly hired guard spends **one day training**. The Villagers tab says "Training". They spar at the Training Yard linked to their Barracks and gain 1 XP every 40 ticks. With no valid yard they stand at the Barracks and gain nothing. They are not on duty, and a raid or the alarm does not call them up. They qualify at dusk (day time 12000) and fight that night.

After that day, awake off-shift time is spent sparring too, if a yard is valid. Kills inside the claim are 5 XP each.

### Level

| Level | Max health | Extra melee damage |
|---|---|---|
| 1 | 20 | +0 |
| 2 | 30 | +1 |
| 3 | 40 | +2 |

Hamlet caps the level at 1, so the extra health waits for a higher tier. The XP is kept. Firing and hiring again does not heal them.

### Rations

There is no free regeneration. A guard carries a ration of up to **12** nutrition, taken from cooked food in Storage, so it counts against food days. When they are below 6, and they are off duty or within 16 blocks of the Barracks, and no hostile is within 10 blocks, they withdraw a little cooked food. They do not walk to the chest for it.

A hurt guard with no hostile within 10 blocks eats one nutrition every **2 seconds** (40 ticks) and heals **1 health** per point, awake or asleep. No food in Storage means no healing. A miss is tried again after 200 ticks.

Below half health, a guard falls back to within 5 blocks of a healthy awake guard (at least 70% health) within 32 blocks, eats there, and fights again at 80%. With nobody to fall back to, they stay in the fight. Below 30% health they go to bed, and they get back up at 80%. A guard with no bed is never sent to bed this way. A bed does not heal. Food does.

### On patrol, and the alarm

On duty they patrol the Heart, a point just outside each valid building's door, and every finished Lamp Post and Watchtower.

They attack hostile mobs and raiders inside the claim, within 24 blocks. A neutral mob is attacked when it is hunting a villager. **Creepers are ignored**, because guards are melee. Flying mobs are not chosen. A sword wears down per hit. Armour wears down when they are hit.

The **alarm** is raised when a hostile hurts a villager inside the claim, or when a guard dies. It stays up while a hostile that is hunting a villager is within 24 blocks of a guard, and it ends **600 ticks** (30 seconds) after the last sign of a threat. While it is up, every guard who is not wounded and not still training comes on duty, including guards who were asleep, and they gather at that spot. A wounded guard stays in bed.

A vanilla raid whose centre, or one of whose raiders, is inside the claim does the same call-up. See [Raids and sieges](#raids-and-sieges) for what happens when a raid or a siege comes.

## Raids and sieges

Raids and sieges are what the guards, gates, walls, and golems are for. They are hard, and they are meant to be fought **with you in the village**, not left alone.

### Warning

Before a random raid or a siege, everyone within 96 blocks of the Heart gets ominous **gold** chat lines spread over the hours before it ("Scouts have been spotted among the hills..."). When a **raid** starts you get the big title "A raid is starting!" and a **bold red** chat line, and the village bell rings. When a **siege** starts you get a **bold red** chat line ("The dead are coming!") and the bell rings. There is no title for a siege. Only the online Owner, Stewards, and Residents hear these.

### Everyone shelters

When a raid or a siege starts, the bell rings and every villager in the claim, hired or not, goes indoors: into their own House bed, a free House bed, or the nearest valid room. Hired workers go back to work when it is over. **Guards stay out and fight**, unless they are still training or wounded.

### Raids

- Vanilla Bad Omen raids still work. A raid counts as a village raid when its centre or one of its raiders is inside the claim. All your qualified guards come on duty, golems fight, and the Overview shows the raid line: coming, which wave, and how many raiders are left.
- Raids also start **on their own**, rolled once a day at day time 1000 (the game rule `villageheart:random_raids`, on by default). A young village gets a short one; a grown village faces the full raid. Winning a random raid pays the treasury once.
- Guards chase raiders up to 32 blocks past the claim, using the nearest gate if the wall is in the way.
- **Stuck raiders.** A raider that makes no progress toward the village for 12000 ticks and has nobody near starts to **glow**, and you are told. A minute later it flees, so the raid can end. A raider that is moving, fighting, or has someone near it is never removed.
- **Nobody attacks creepers.** Guards and golems ignore them.

### Sieges

A Village or Town can be besieged at night (the game rule `villageheart:sieges`, on by default; a Hamlet is never besieged). Vanilla's zombie siege is replaced: zombies appear at day time 18000 and are drawn toward your villagers, breaking whatever wall stands in the way. Creepers walk to a wall and blow up beside it, and the blast hurts the wall, not the houses behind it.

### Walls take damage

Zombies, raiders, and creepers break defence blocks (game rule `villageheart:siege_damage`, on by default). Gates are the weak point. Stone cracks before it breaks. A hired builder repairs gates first, then broken stone, then cracked stone, from cobblestone in Storage (a fence gate for a gate). Nothing is repaired during a raid or a siege. The Overview lists the raid or siege, then "Walls damaged".

The honest summary: a village with no player in it can lose people to a raid, even behind walls. Stay near, bring a sword, keep the guards fed and equipped, and keep the builder hired.

## Gates, lamp posts, and walls

These are blueprints, in the builder section, with their costs. How they behave:

**Gates.** Hired workers and guards open and close the village's fence gates as they pass, the same way they open doors. Mobs cannot. A gate opens only inside the claim, never while a hostile is within **8 blocks** (a guard who is already fighting may still open it), and never when redstone is powering it. It closes once nobody needs it.

**Lamp posts.** A lantern on a fence post. Vanilla refuses a monster spawn in a cell whose block light is above 0. The lantern is light level 15, and that light falls by one per block. On level ground, every cell within **12 steps** (a diamond of blocks, not a circle) is safe from natural monster spawns. Pillager patrols, slimes, phantoms, drowned, spawners, raids, and mobs that walk in from outside are not stopped by the light. Walls and houses shade it. A finished lamp is a guard patrol point. If the lantern is broken, it stops being one.

**Walls.** A finished segment is solid cobblestone, 3 blocks high. A closed ring with gates still lets hired workers and guards through, because they open the gates. Mobs cannot open them. Walls can be broken: see [Raids and sieges](#raids-and-sieges).

**Watchtowers.** Town tier. The ground in front of the entrance is a patrol point. If a ladder is broken, it stops being one. The village remembers up to 64 finished lamps and towers.

## The Heart screen

Right-click the claimed Heart.

- **Overview.** Name, tier, the goal checklist, population and beds, workers and slots, food days left, the treasury, tomorrow's wage bill, and alerts: starving, storage full, strikes, invalid buildings, a raid or siege line, and damaged walls.
- **Villagers.** Name, job, level, XP, the fed / housed / paid icons, and what they are doing. Assign a job, fire, or set a guard's shift.
- **Buildings.** Every marker, with type, position, valid or invalid, the reasons, and slots in use. Builds in progress show the blueprint, the place, the percent, what is short, and the builder's name.
- **Stockpile.** Totals across Storage, food nutrition, what the market sold today, and the reserve settings.
- **Ranks.** Owner only. Others see the tab greyed out ("Only the Owner can manage ranks").

A Visitor receives the Overview only.

The stockpile is the chests and barrels inside valid Storage buildings: chest, trapped chest, and copper chest. Ender chests and shulker boxes are not part of it. A chest that also sits in another room, even in a shared wall, is not stockpile. Building chests that hold tools are not stockpile. Workers put goods in the nearest Storage with space. If every Storage is full, they keep the goods and stop gathering, and the Overview says so.
