# Changelog

## 1.0.0 — 2026-11-11

Player-facing notes for Village Heart on Minecraft 1.21.11. Grouped by what you can do in a village. Numbers match the design: claim radius, wages, dinner, building sizes, and the rest.

Sieges, random raids, wall damage, and the smith are still being built. They are listed at the end and are not playable in this version yet.

### Claim a village

Craft a Village Heart from gold ingots, emeralds, and a diamond, and place it within 16 blocks of a bell. That village is yours. One village per world. The claim is an 80-block sphere around the Heart. A Mineshaft or a Hunting Ground may sit up to 128 blocks out.

You are the Owner. Only you can break the Heart. Breaking it clears every job, turns villagers back into ordinary villagers, and drops the treasury as emeralds. The claim, ranks, treasury, tier, buildings, and workers are saved with the world.

### Ranks

Owner, Steward, Resident, and Visitor. The Owner sets ranks and can hand the village to a Steward or a Resident. Stewards hire, fire, start blueprints, and set guard shifts. Residents open the village chests and deposit emeralds. Visitors see the Overview only.

Visitors cannot open or break containers inside claimed rooms, and they cannot break markers. Ranks are not grief protection: walls can still be broken, and hoppers, pistons, explosions, and golems are not stopped. An operator can assign a new owner of an abandoned village with `/villageheart owner`.

### Markers and buildings

Craft a Blank Marker from 8 planks around an item frame, then a building marker with that blank in the centre and the building's ingredients around it. Place the plaque on the outside wall by a door.

Grey means not checked yet, red means invalid (right-click for the reason), green means valid. A room has to be enclosed, under 4096 air blocks, big enough for its type, and stocked with the blocks that type needs. One room cannot belong to two markers.

Hamlet rooms: House (floor 12, a bed and a light), Storage (floor 9, two chests or barrels), Farmhouse, Lumber Camp, and Kitchen (floor 9). Village rooms: Builder's Hut, Mine, Hunting Lodge, Barracks (floor 16, a bed per guard), and Market. The Smithy room (Town, floor 12, blast furnace, anvil, chest) can be marked. The blacksmith's work is coming in v1.

Outdoor sites: a Mineshaft at least 12 deep with 8 rails and a light, a Hunting Ground with 40 grass, leaf, or log blocks, and a Training Yard with 2 armor stands or targets.

### Work

Hire an adult on the Villagers tab. A hired villager stops trading. The work day runs from day time 1000 to 11000, dinner is at 11000, and they sleep from 12000. They carry one stack and deliver it to the nearest Storage.

- **Lumberjack** (Chopping Block, needs an axe). Fells a whole natural tree near the camp and replants it. A stone axe takes about 2 seconds a log. Placed logs and logs inside buildings are left alone.
- **Farmer** (composter). Harvests and replants wheat, carrots, potatoes, and beetroots within 24 blocks. A hoe is a ×1.25 bonus. They do not till new fields.
- **Miner** (stonecutter, needs a pickaxe). Walks into a Mineshaft and returns with a haul every 2 in-game hours. The ore depends on how deep the shaft goes, from cobblestone and coal near the surface to iron, gold, and a rare diamond below Y 0.
- **Hunter** (fletching table, needs a bow, crossbow, or sword). Hunts for 3 in-game hours and brings back meat, leather, feathers, and sometimes a rabbit hide. A bow with an arrow adds one meat. Two trips a day at level 1, three after that.
- **Cook** (smoker and crafting table). Cooks meat and potatoes, bakes bread from 3 wheat, and makes beetroot soup. Needs coal, charcoal, or logs.
- **Builder.** Puts up blueprints. See below.
- **Guard.** See below.
- **Merchant.** Sells surplus for emeralds. See below.

Tools in the job chest set the pace: wood ×1, stone ×2, copper ×2.5, iron ×3, diamond ×4, netherite ×4.5, gold ×6. Workers gain XP and reach level 2 at 100 XP and level 3 at 300. Speed is ×1, ×1.25, ×1.5. Hamlet caps them at level 1, Village at 2, Town at 3.

With no smith yet, a lumberjack, miner, hunter, or farmer makes their own wooden or stone tool when they have none, or when the one they have is nearly broken. Stone if Storage can afford it, otherwise wood. At level 1 that takes 2400 ticks for wood and 4800 for stone.

### Food, needs, and dinner

At day time 11000 every loaded villager in the claim eats from Storage. An adult needs 10 nutrition, a child 5. Cooked food is served before raw. Bread is 5, a baked potato is 5, cooked beef is 8, a carrot is 3, an apple is 4.

One missed dinner slows work to half. Two or more stops work. Nobody dies of hunger. No bed in a valid House slows work to ×0.75. The Overview shows how many days of food are left.

### Wages and the treasury

Deposit emeralds at the Heart (Resident and above). Only the Owner withdraws. The cap is 100,000 emeralds.

Dawn pays 1 emerald to a level 1 or 2 worker and 2 to a level 3. If the purse is short, people are paid in hiring order and the rest strike until emeralds come in. A new hire is charged the same day. Strikers do no work.

### Tiers

The village starts as a Hamlet. At dawn it can rise one tier, and the tier never falls.

Village needs 6 population, 3 workers, a valid Storage, Farmhouse, and Kitchen, and 3 fed days in a row. It unlocks the builder, the mine, the hunt, the barracks, the market, and the blueprints other than the Watchtower, and job level 2.

Town needs 12 population, 8 workers, 2 guards, 7 fed days in a row, and a treasury that has held at least 32 emeralds after wages. It unlocks the Watchtower and the Smithy building, and job level 3.

### The market

A merchant at a Market Stall sells one lot of surplus about every 30 seconds, for 1 emerald into the treasury. The village keeps 3 days of food and 64 of anything else. Those reserves are set on the Stockpile tab.

A lot is 16 logs, 20 wheat, 22 carrots, 26 potatoes, 15 beetroot, 32 cobblestone, 15 coal, 8 raw iron, 20 raw copper, 4 iron ingots, 6 leather, or 10 cooked meat or fish. A merchant sells at most 8, 12, or 16 emeralds a day by level. The whole village sells at most 32.

### Builder and blueprints

From the Builder's Table, a Steward takes a blueprint and stamps the footprint. Green particles mean the site is allowed. Sneak rotates it. A second click confirms. Walls and gates are two clicks, a straight level line of at most 32 blocks.

The builder reserves materials in Storage, clears natural ground, and places about one block a second at level 1. They do not break a marked building. A site on a tree, in lava, or outside the claim is refused.

Blueprints: Small House and Storage Shed (a 5×5 floor inside a 7×7 shell), Farm Plot (11×11 fenced field around water), Wall Segment, Gate, Lamp Post, and, at Town, a Watchtower.

### Guards

A new guard starts with a wooden sword, no armour, and 20 health, and spends one day training. You can hire a guard only by day. They qualify at dusk and fight that night. Sparring at a Training Yard is how they gain XP off duty. Level 2 is 30 health and +1 damage. Level 3 is 40 health and +2 damage.

With 1 to 3 guards, all of them work nights. From the fourth guard, one per four works days, and the more experienced guards take the nights. A Steward can pin a shift.

They patrol the Heart, the doors, the lamp posts, and the watchtowers, and they fight hostiles in the claim. Creepers are left alone. There is no free healing: a ration of up to 12 nutrition from Storage heals 1 health a point, one point every 2 seconds. Below half health they fall back behind a healthier guard.

If a hostile hurts a villager, or a guard dies, the alarm wakes every guard who is not wounded or still training. It lasts 30 seconds after the last threat. A vanilla raid that reaches the claim does the same.

### Gates, lamps, and walls

Hired workers and guards open fence gates. Mobs do not. A gate stays shut while a hostile is within 8 blocks, and while redstone is powering it.

A lamp post stops natural monster spawns within 12 steps on level ground. A cobblestone wall is 3 high. A watchtower is a patrol point. Up to 64 finished lamps and towers are remembered.

### Still coming in 1.0.0

- **Sieges.** A siege that draws zombies toward the village is coming in v1.
- **Random raids.** Raids that start on their own, rather than only from Bad Omen, are coming in v1.
- **Wall damage.** Mobs breaking walls and gates, and builders repairing them, are coming in v1. Defence blocks do not take damage yet.
- **The smith.** Smelting, iron and leather gear, repairs, and iron golems (36 iron and a carved pumpkin) are coming in v1. Until then, workers make their own wooden and stone tools.
