# P4 plan: Blueprints 2

Planner: Cursor CLI (grok-4.7), 2026-11-11. **Approved by Kees 2026-11-11 and implemented on `blueprints-2`.**

## Decisions recorded (Kees, 2026-11-11)

1. Author a Storage blueprint and a Builder's Hut blueprint as well as the other job buildings.
2. Offer only `/houses/` templates that pass the house check. Not zombie copies, streets, or other pieces. The scan still lists every template.
3. Player blueprints (quill and design world) are capped at 16×16×16 and 1024 blocks. A vanilla house that passes and looks right may be larger.
4. No cap on saved blueprints per village. The Builder's Table catalogue paginates.
5. The market will not sell items an active build still needs. That is a sale hold, not a stock reservation: cooks and the player can still take them.
6. Swap-hands mirrors a held blueprint. Sneak-click rotates it.
7. Quill recipe: feather + ink sac + paper.
8. Mine and Hunting Lodge blueprints ship. Their markers stay red until the outdoor site exists.
9. If foundation dirt runs out, the build pauses with "needs dirt".
10. The design place is a separate flat dimension. Nothing item-shaped comes back. If that dimension cannot be created, the rest still ships and the PR says so. It is not hidden in the overworld.

**Added requirement:** a builder crafts a building block it needs (planks, slabs, stairs, walls, fences, doors, trapdoors, glass, panes, bricks, wool, beds, and the same kind of block) from raw items in Storage, using vanilla crafting and smelting recipes, when those ingredients are there. Tools, food, and redstone are not crafted. Fuel for smelting comes from Storage.

This is post-v1. It is not part of the v1.0 gate. Decisions below marked **Proposal** are the planner's default. Kees can change them; the open list is section 9.

Code was read on `dev`. One headless client launch and one dedicated-server boot were run on this machine so section 7 is a real result, not a guess. Vanilla template **names and counts** were read from the 1.21.11 client jar (`data/minecraft/structure/village/`, 483 files). Which of those pass the room check was **not** run: that is the test in phase 4. APIs that were not opened in the Mojang sources are marked **[not verified]**.

## 1. Goal

A Steward builds from three kinds of blueprint: vanilla village houses that pass the room check, authored job buildings and a few defence pieces, and buildings the player saved with a quill or in a design world. Builders work slowly, several to a site, from whatever is in Storage, and the Builder's Table shows what is still missing. Half-finished buildings are normal. Tall sites get temporary scaffolding. The ground under a house is filled or shaved so a hill does not leave the floor floating. The player sees a translucent ghost of the unbuilt blocks.

Builds stay small. There is no modular castle kit. Sharing blueprint files with other people is written down here and not built.

## 2. Audit (what exists on `dev`)

### 2.1 Blueprint catalogue

| What | Where | State |
|---|---|---|
| Text format | `core.blueprint.BlueprintParser`, `Blueprint`, `PaletteEntry`, `Material` | Works. Palette character, blockstate, material item or tag, pass (`MAIN` / `ATTACHED` / `LAST`), `part=` for the second half of a door or bed. |
| Registry | `Blueprints.all()` | Seven entries, menu order: Small House, Storage Shed, Farm Plot, Wall Segment, Gate, Lamp Post, Watchtower. |
| The two boxes | `Blueprints.SMALL_HOUSE`, `STORAGE_SHED` | These are the placeholder boxes. Same 7×5×8 shell: cobblestone floor, plank walls, flat plank roof, one door. House adds a bed, a torch, a House marker (172 placements). Shed adds two chests, a torch, a Storage marker. |
| Farm Plot | `FARM_PLOT` | Not a box. 9×9 moist farmland, one water, fence and gate. Keep it. |
| Defence | `DefenceBlueprints` | Wall (generated, length 1–32, 3-high cobblestone), gate (opening of 3), lamp post, watchtower (Town). `PlacementMode.LINE` for wall and gate. |
| Job buildings with no blueprint | `BuildingType` | Kitchen, Barracks, Lumber Camp, Farmhouse, Mine, Hunting Lodge, Market, Training Yard, Smithy. Also **Builder's Hut** and, once the shed is removed, **Storage**. Outdoor Mineshaft and Hunting Ground are dug or counted, not built from a blueprint. |
| Room proof | `BlueprintView` + `BlueprintRoomTest` | House and Storage Shed pass `RoomScanner` and `BuildingRules` in all four rotations. |

`TableSnapshot.MAX_BLUEPRINTS` is **16** and `MAX_MATERIALS` is **8**. The table screen draws every blueprint in a fixed list (`ROW_H` 16, no scroll on that list; only the build rows scroll). Forty houses will not fit, and a vanilla house has more than eight block types. Both caps and the screen have to grow in the phase that adds the catalogue.

### 2.2 Rotate, mirror, preview

| What | State |
|---|---|
| Rotate | **Exists.** `Rotation4` matches vanilla's 90° rule: clockwise 90 sends `(dx, dz)` to `(-dz, dx)`. `Placement` rotates about the anchor. Sneak-click on the Blueprint item cycles it. The first click turns `front` toward the player. Saved on `BuildJob.rotation`. |
| Mirror | **Missing.** No field on `Placement`, `BlueprintSite`, or `BuildJob`. |
| Preview | **Box outline only.** `client.build.BlueprintPreview` spawns green or red dust on the bottom rectangle and four vertical edges, at most 120 particles, while the item shows a pending site. No per-block ghost, nothing after confirm, no highlight of missing materials. |

### 2.3 Queue, priority, who works a site

`VillageState.builds` is an ordered list. `BuilderJob.currentJob` claims the **oldest build that nobody has claimed**. One claim per build (`BuildRuntime.claims`: build id → one villager). A second builder skips a build someone else holds, and also skips a build whose saved `carrier` is a different living worker (that worker is holding the fetched materials).

There is **no priority field** and no "build this next" control. `MAX_ACTIVE_BUILDS` is 8.

`twoBuildersTwoBuilds` locks the current rule: two builders, two sites, each gets one.

### 2.4 Materials and Storage

`BuildSystem.refresh` (every 100 ticks) reserves the **whole remaining need** of every build, oldest first, through `MaterialPlanner`. Reservations are transient (not saved). The builder fetches about one stack (`BUILDER_FETCH_LOAD` 1.0, 4 slots) of what is reserved, walks to the nearest Storage that holds it, and withdraws with the build id as the holder. `missing = need − carried − reserved`. The pause text is "Needs 12 more planks".

The Builder's Table "have" column is "what a new build could still reserve" (stock minus all reservations), not a live still-needed list for a half-built site.

Dinner and other reservations never eat reserved items. The merchant sells surplus above a reserve. If builds stop reserving, those systems can take the planks a house was about to use. See section 9.

### 2.5 Pace, swing, sound, order

| What | State |
|---|---|
| Pace | `Balance.BUILD_TICKS_PER_BLOCK = 20`. `WorkClock` ticked with `WorkSpeed.untooled` (`LEVEL_SPEED` 1.0 / 1.25 / 1.5). Level 1, fed, paid: **1 block per second**. Measured on the standard site: Small House about 5 200 ticks, Farm Plot about 5 900. |
| Arm swing | **Exists.** `BuilderJob.act` calls `v.swing(MAIN_HAND)` before a clear or a place. No test asserts it. |
| Place sound | **Exists.** `BuildExecutor.place` plays the block's place sound at 0.8 volume. No test asserts it. |
| Order | **Bottom-up already.** `BuildPlan`: clear top-down, then place layer by layer from the bottom, perimeter order, then attached, then last, then a check pass, then the marker. Cells that are already right are free. |
| Stand | The builder never stands inside the box. Ring one column outside, reach 7.0 from feet to cell centre. Confirm refuses a site with no stand point. |

### 2.6 Clearing, slopes, scaffolding

Clearable blocks are the tag `#villageheart:builder_clearable` (dirt, stone, sand, snow, replaceable plants, flowers, saplings, leaves, a few ores, wild crops), leaves only when not persistent, no fluid, no block entity. Logs are refused ("fell it first"). Player blocks are never broken. Rule 11b refuses a site where a torch, ladder, door, sign, rail, lantern, or hanging entity outside the site would fall.

A house may bridge a dip (`base` 1, the floor sits above the ground). Nothing fills that dip. Defence pieces have rule 11c FOOTING: every ground cell must already be solid, and the wall is level at the start's y. There is **no terraforming**.

**No scaffolding.** The watchtower was shortened in M21 because one cell was 7.16 blocks from the ring, past reach 7.

### 2.7 Saved build

`BuildJob(id, blueprintId, params, ground, rotation, createdAt, startedBy, cursor, carried, carrier)`. Steps are recomputed; only the cursor is stored. One carrier. Codec drops a build whose blueprint id is unknown. Removing `small_house` will drop any in-progress small-house build on load. Fine before release; say so in the phase that removes it.

`VillageCodecs` splits the village compound into groups of at most 16 (`RAW_A` has 10, `RAW_B` has 7). A new list fits on `RAW_B` as an optional field with a default. Do not reorder keys. `VillageState` grows by one component and a back-compat constructor, the same pattern as `builds` and `defences`.

### 2.8 Room check the vanilla import has to pass

`RoomScanner.scanFromDoor` flood-fills air from inside a door. Solid means "has a collision box" (`RoomCells`), so glass, panes, fences, slabs, stairs, and closed doors count as wall. Open fence gates, carpets, flower pots, and torches are open. A leak to the sky, more than 4096 air, or no door fails. `BuildingRules.checkRoom(HOUSE)` then wants floor area ≥ 12, one bed, one light. The scan does not need a marker block; `scanFromDoor` starts at the door. A built copy still has to **add** a House marker, or the room never registers.

Job buildings have their own minimum floor and contents (`BuildingRequirements`): composter, chopping block, smoker, crafting table, stonecutter, fletching table, grindstone, bed per grindstone, blast furnace, anvil, Builder's Table, Market Stall, and a chest on each. Mine and Hunting Lodge also want a linked outdoor site, which a blueprint cannot place. Training Yard is outdoor (≥ 2 targets or armor stands within 6).

### 2.9 Vanilla files on disk (counted, not room-checked)

The 1.21.11 client jar contains **483** `village/**/*.nbt` templates.

| Folder | Files |
|---|---|
| `plains/houses` | 36 |
| `desert/houses` | 28 |
| `savanna/houses` | 31 |
| `snowy/houses` | 30 |
| `taiga/houses` | 27 |
| zombie copies | plains 40, savanna 39, taiga 37, snowy 33, desert 27 |
| streets, terminators, town centers, villagers, animals, decays, lamps, decorations | the rest |

Plains houses include `plains_small_house_1` … `_8`, medium and big houses, profession shops, stables, pens, farms, libraries, temples, and two meeting points that live under `houses/`. Streets, pens, farms, and meeting points are unlikely to be enclosed rooms. Zombie copies often have no bed. **The pass/fail list is the output of the phase 4 test, not a guess in this plan.**

Templates contain `jigsaw` and `structure_void` blocks, and entity lists (villagers, animals). Those are not part of a building the mod should copy.

### 2.10 What the village actually puts in Storage

Jobs deposit what their tables say, and nothing else:

- Lumberjack: logs.
- Farmer: wheat, carrots, potatoes, beetroot, seeds, poisonous potato.
- Miner (`HaulTable`): cobblestone, cobbled deepslate, coal, raw copper, raw iron, redstone, lapis, raw gold, diamond.
- Hunter (`HuntTable`): the seven ids — meats, leather, feather, rabbit hide.
- Cook: the outputs of the cooking recipes (bread, cooked food).
- Self-craft can leave planks and sticks as leftovers. That is not a supply line.
- The smith is **not on `dev`** (M19 is in progress elsewhere). Do not add smith outputs here.

Nobody produces planks, stairs, doors, glass, terracotta, wool, or beds as a job output. A vanilla house is mostly player-supplied. The still-needed list will say so. There is no new gatherer, and this plan adds no crafting step that turns logs into stairs.

Dirt from clearing a site already goes to Storage. Filling a hole uses that dirt. If it runs out, dirt is just another missing block.

### 2.11 Off limits, and why the pace constant must not move

M25 (raids, wall damage, builder repairs) is in progress on `origin/m25-walls` and is **not** on `dev`. Its plan puts `RepairPlanner` inside `BuilderWork` and times a repair as one builder action at the current 1 second (`BUILD_TICKS_PER_BLOCK`). On `dev` today `BuilderWork` only forwards to `BuilderJob`.

Hard rule for every phase:

- Do not edit `BuilderWork.java`.
- Do not change `Balance.BUILD_TICKS_PER_BLOCK` (stays 20). Construction pace is a **new** constant.
- Do not add wall hit points, breaches, or damage repair.
- Do not edit guard, raid, or smith code.
- The construction check pass may change so a missing flower pot does not block the marker. That is not wall repair. `checkPassRepairs` (a structural block comes back) stays green.

`DefenceSpecs.of` records only the lamp post and the watchtower. New visual wall pieces must not be added there, or they become patrol points. M25's damage list is separate and must not be edited.

## 3. Design

### 3.1 Catalogue

**Built-in, code, not saved:**

1. Vanilla houses that pass the gate in 3.2. Each becomes a normal `Blueprint` (`kind BUILDING`, `builds house`, `tier VILLAGE`) plus one House marker cell outside the door, facing out. If there is no free cell there for the plaque, the template fails "no marker spot".
2. Authored job buildings, one each, listed in phase 5. Pitched stair roof, windows, log frame. Each passes `BuildingRules` for its type, marker and required furniture included.
3. A few visual defence pieces: a posted wall, a battlement, a walkway, a tower piece, a gate piece. `kind DEFENCE`, `builds none`, not added to `DefenceSpecs`. They use the existing footing and "defences may touch" rules. They are not raid walls.
4. Keep Farm Plot, Wall Segment, Gate, Lamp Post, Watchtower.

**Remove** Small House and Storage Shed from `Blueprints.all()` once a real house source and a real Storage blueprint exist (phase 5). Move the old small-house text into a **test fixture** that is not in the menu, so the builder tests still have a known shape. Update counts in `MarkerGameTests.registrySmoke`, lang coverage, and the creative tab. Do not delete the assertions; point them at the new list.

**Player-made, saved on the village:** quill and design world write the same record (3.6). Cap **32** per village (**Proposal**).

**Size cap (Proposal):** for a footprint blueprint, width, height, and depth each ≤ 16, and touched cells ≤ 1024. Line walls keep `MAX_WALL_LENGTH` 32 and are exempt. Quill, design world, vanilla import, and authored buildings all use this cap. Over the cap, the vanilla test's reason is "over the size cap" and the template is not offered.

No modular composition. No castle tool. A wall is still one line of at most 32.

### 3.2 Vanilla gate

Load every `minecraft:village/` template through the structure manager **[API not verified this run: `StructureTemplateManager#get` / list by id prefix; confirm against the 1.21.11 sources in phase 4]**.

When reading a template:

- Skip `structure_void`, `jigsaw`, and `structure_block`.
- Skip entities.
- Keep block states. Rotate and mirror with vanilla `BlockState.rotate` / `mirror` when the player places a copy.

Then run the house check on a `BlockView` of those blocks (`RoomCells.of` for each state): find a door, `scanFromDoor`, `BuildingRules.checkRoom(HOUSE)` at Village tier with no claim error. Also require a free cell for the House marker, and the size cap.

**Offer** a template only when that passes **and** the path contains `/houses/` **and** the path does not contain `/zombie/` (**Proposal**, section 9). The test still prints every file, including the ones we refuse to offer.

The scan is a GameTest (layer 2). It needs real block collision, so it cannot be a pure unit test. It writes one line per template: id, pass or the reason (`no door`, `roof missing at …`, `floor N < 12`, `needs a bed`, `needs a light`, `no marker spot`, `over the size cap`, …). A golden file checked into the repo makes a Minecraft bump show a diff. The first implementation generates that file; reviewers look at it.

### 3.3 Structural and decorative

Computed once when a blueprint is loaded or saved, stored on the cell, deterministic.

- The door, the marker, and the minimum required contents are structural. If there are two beds, one chosen cell (lowest position) is structural and the other is decorative.
- A shell cell is structural when setting it to air makes the scan leak or the building check fail.
- Every other placed cell is decorative (pots, extra torches, carpets, roof ornaments, outside lanterns once one light remains).

Missing **structural** blocks: the build pauses, the marker is not placed, the house does not count. Status "Needs N more &lt;name&gt;".
Missing **decorative** blocks: structural work including the marker finishes, the building works, the row stays flagged "Decoration waiting: N &lt;name&gt;". Builders place those cells later, when the item is in Storage.

Any block may appear in a design. A block is **player-supplied** when its item is not in the produced set of section 2.10. The table shows that flag. No new job gathers it. Builders place it when a player puts it in Storage.

### 3.4 Materials, queue, pace

**No up-front reservation.** `BuildSystem` stops calling `MaterialPlanner` for builds. Cook reservations, wage logic, and the stockpile itself stay as they are.

A builder withdraws a batch for the **next steps it is about to place**, at the chest, about one stack, same slot cap as now. If the items are gone when it arrives, it replans. Two builders can both walk off with planks; `Stockpiles.withdraw` is the arbiter and the loser replans.

**Spread.** Among builds of equal priority that can place something with what is in Storage right now, builders take turns by batch, so two houses rise together. A build with nothing it can place is skipped, not stuck in front of the queue.

**Priority.** `BuildJob` gains `int priority`, codec default 0. Higher first, then `createdAt`. The table has "Build this next" (Steward, same permission as start). It sets priority above the current maximum. A bumped site is served first until it cannot place (missing structural material, blocked, unloaded). Then the others resume the spread.

**Still needed.** The table and the Buildings tab list, per build, the structural gaps and the decorative gaps, with the player-supplied flag. This replaces "have / need" for an in-progress build. The blueprint detail can still show the full material list and how many of each are in Storage.

**Pace.** New `Balance.BUILD_PLACE_TICKS = 70` (3.5 seconds at level 1 before hunger). `WorkSpeed.untooled` still applies, so level 2 is 2.8 s and level 3 is about 2.3 s, and hunger still halves it. `BUILD_TICKS_PER_BLOCK` stays 20. Walking and fetching are not on this clock. The 2-day Small House bound (48 000 ticks) still holds: 172 × 70 is 12 040 ticks plus walks. The unit test that pins a clock of 20 ticks is retargeted at 70. That is a real assertion change, written in the commit, not a deleted check.

**Several builders, one site.** `BuildRuntime` holds a transient lock of (build id, step index) → villager. The next step a builder may take is the lowest unfinished layer, in the existing perimeter order, so the building still grows upward. Two builders never place the same cell. The saved `carrier` / `carried` pair becomes a map of villager → items, and the old codec shape reads as a one-entry map. On restart, in-progress steps are simply not done yet; the cell check skips anything already placed.

**Swing and sound.** Already implemented. Phase 1 adds a test that a place swings the arm and plays the place sound, so it cannot quietly disappear. No new behaviour.

### 3.5 Scaffolding and the ground

**Scaffold.** New block `villageheart:builder_scaffold`. It looks like bamboo scaffolding. It is not the vanilla block (that one drops bamboo and needs bamboo). Builders have an unlimited supply. It does not come from Storage and it is not a blueprint cell. Placed only on the stand ring, outside the box, as high as the next cell needs, up to the size cap. The player can break it; it drops nothing. The builder replaces it if the build still needs that height. A cleanup pass removes it when the build finishes, and also when the build is cancelled.

`StandPoints` accepts a scaffolded column: try the ground ring first; if the cell is too high, a column on the ring within the height cap counts. Confirm refuses a site that even scaffolding cannot reach. Existing short buildings still need no scaffold, and a test says so.

**Ground, buildings only.** Defence footing and level walls stay as M21 wrote them.

- Under each floor cell, fill dirt down through air, at most **4** blocks.
- One block outside the footprint: shave one clearable block that sticks up, or add a dirt step where the ground falls away, again at most 4 blocks.
- Steeper than 4: build anyway and leave the cliff. Do not refuse the site.
- Use dirt the clear step just dropped, then dirt in Storage. If it runs out, pause "Needs N more dirt". That pause is structural: the floor is not done.
- Never break a player block. Never fill or shave inside a protected room.

### 3.6 Quill and the design world

Both paths save the same record, and both use the same gate: the captured blocks must pass `BuildingRules` for the building type of the marker inside them. A house with no marker is checked as a house (door, floor, bed, light) and the save adds the marker if it was missing and there is a spot. A failure chats the reasons and saves nothing. Defence pieces are not saved this way; the builtin line tools stay the way to build walls.

**Quill.** Item `villageheart:blueprint_quill`, stack 1, not consumed. Two corner clicks, inclusive box, must be inside the claim, loaded, and inside the size cap. **Proposal** recipe: ink sac, feather, paper. Default name "Saved building N", renamed at the table.

**Design world.** A Steward (same permission as starting a blueprint) presses Design on the Builder's Table.

- A dimension `villageheart:design`. `fabric-dimensions-v1` is already on the classpath (it loaded in the client smoke). The registration calls are **[not verified]**; phase 7 reads the Fabric 1.21.11 sources before writing them.
- Flat grass, bedrock, no mobs, daylight. One plot per player, spaced so players do not share a plot. The plot is the size cap plus a margin of 4. Entering clears that plot.
- On entry the server stores gamemode, position, dimension, and the full inventory (armor and offhand included), then sets creative and an empty inventory.
- Ender chests are not reachable in the plot. On the way out, any item that is not part of the stored snapshot is destroyed, not dropped into the survival world.
- Save runs the gate, stores the blueprint on the village the player came from, clears the plot, restores the snapshot, and teleports back.
- Discard does the same return without storing.
- Nothing item-shaped comes back. The blueprint record does.

### 3.7 Ghost

While the Blueprint item shows a site, the client draws each non-air cell as a translucent block, plus the existing green or red dust so legal and illegal sites stay obvious.

After confirm, a nearby in-progress build draws the cells that are not placed yet. Cells whose material is in the structural still-needed set are tinted red. Decorative waiting is dimmer. The picture updates about once a second from data the server already sends for the table (extend that snapshot; do not stream every block every tick). Built-in and vanilla blueprints exist on the client as code. A saved blueprint's cells are included in the snapshot when that build is visible, bounded by the size cap.

### 3.8 Mirror

`boolean mirrored` on `BlueprintSite` and `BuildJob`, codec default false.

Order: mirror local x across the anchor (`dx → -dx`), then rotate. Block states go through vanilla `BlockState.mirror`, then `rotate`. **[Confirm the order against `StructureTemplate` in the phase 3 step; M20 already confirmed rotate.]**

Sneak-click still rotates. The swap-hands key, while the blueprint is held, toggles mirror and the site is checked again (**Proposal**).

Tests: 4 rotations × 2 mirrors, doors and stairs face the right way, `local(world(p))` round-trips.

### 3.9 Future only: sharing files

Not built. When it is built, a saved blueprint exports to one file (the cell list, the role flags, the name, a format version) and imports through the Builder's Table after the same room gate and the size cap. No network service, no account, no automatic download. Players trade the file themselves.

## 4. Phases

Each phase is one PR into `dev`, branched from current `dev` at the time (not stacked on an unmerged phase unless Kees asks). Each step inside a phase ends on `scripts/verify.sh --fast`. The phase ends on a full `scripts/verify.sh`, screenshots looked at, and a review (section 6).

Existing tests stay. Where this plan changes a contract, the old test is replaced by a test of the new contract in the same step. The commit says which contract changed. Do not delete a test to go green.

### Phase 1 — Pace, sharing a site, priority, no reservation

Player-visible: builders are slower, two can work one house, the table can bump a site, and it lists what is still missing. Nothing is reserved up front.

1. `BUILD_PLACE_TICKS` and the clock in `BuilderJob`. Retarget the unit clock test. Leave `BUILD_TICKS_PER_BLOCK` alone. Do not edit `BuilderWork.java`.
2. Step locks and per-builder carried map. Codec reads the old single carrier. Two builders on one site place different cells, bottom-up.
3. Stop reserving build materials. Replace `reservationsHoldMaterials` with: another withdrawal can take the planks; the build pauses; the still-needed line names the count; depositing them resumes.
4. Priority field, table button, spread rule. Heart row and table show structural and decorative gaps (decorative can be empty in this phase).
5. Test that a place swings the arm and plays the place sound.
6. Docs: DESIGN §5.8 short "as built" note, TESTING.md builder section, PROGRESS.

### Phase 2 — Scaffold and ground

1. The scaffold block, no drop, not from Storage. Cleanup on finish and on cancel.
2. `StandPoints` scaffold rule. Short buildings still need none. A tall fixture needs a column and gets one.
3. Fill and skirt, cap 4, buildings only. A 2-block dip under a floor becomes dirt. A defence line over a dip is still refused (FOOTING unchanged). A player block in the skirt is not broken.
4. One client screenshot of a scaffolded tall build, and one of a house on a slope after the skirt. Look at both.

### Phase 3 — Mirror and ghost

1. Mirror field, transform, tests for stairs and doors in 8 orientations.
2. Ghost renderer for the held blueprint and for an in-progress build. Red tint on a missing structural cell.
3. Screenshots: mirrored house footprint, ghost at roughly half built, ghost with a red missing cell.

### Phase 4 — Vanilla scan and the houses that pass

1. Template loader (skip void, jigsaw, structure block, entities).
2. The scan test and the golden file. The PR description includes the pass/fail list.
3. Register the offered houses. Each gets a marker cell. `StandPoints`, rotation, lang, and the creative-tab tests are updated to the new `all()`, not narrowed.
4. Scrollable table, categories (Houses, Jobs, Defence, Saved), raised caps. Worst-case packet test updated and still under its limit.
5. Screenshots of at least two different passing vanilla houses, finished, from the front (door and green marker visible).

### Phase 5 — Authored buildings, then remove the boxes

One blueprint per row, each with an L1 test that `BuildingRules` passes in four rotations, an L2 test that `advance` places it and the marker goes green, and a screenshot.

| Blueprint | Check |
|---|---|
| Kitchen | floor ≥ 9, smoker, chest, crafting table, marker |
| Barracks | floor ≥ 16, grindstone, one bed, chest, marker |
| Lumber Camp | floor ≥ 9, chopping block, chest, marker |
| Farmhouse | floor ≥ 9, composter, chest, marker |
| Mine | floor ≥ 9, stonecutter, chest, marker. The linked Mineshaft is not part of the blueprint; the marker stays red until a Mineshaft exists. The test builds a shaft or asserts that single reason. |
| Hunting Lodge | same shape: fletching table and chest; red until a Hunting Ground exists |
| Market | floor ≥ 9, market stall, chest, marker |
| Training Yard | outdoor: marker post and ≥ 2 targets. Not a room. |
| Smithy | floor ≥ 12, blast furnace, anvil, chest, marker. Town tier. |
| Storage | floor ≥ 9, ≥ 2 chests, marker. Replaces the shed. |
| Builder's Hut | floor ≥ 9, Builder's Table, chest, marker. **Proposal:** include it; it is a job building with no blueprint. |

Plus the visual pieces: posted wall, battlement, walkway, tower piece, gate piece. Tests: they place, they are not in `DefenceSpecs`, footing still applies.

Then remove Small House and Storage Shed from the menu. Point the 2-day builder test at the test fixture or at the new Storage, with the same "materials deducted exactly" and "finished and valid" assertions. In-progress saves of the old ids drop on load; one codec test covers that.

Look at every finished screenshot. Fix roofs that read as a flat box, missing windows, or a marker buried in a wall.

### Phase 6 — Quill

1. Item, recipe, two-click box, size cap, claim check.
2. Save gate. A valid house is stored and appears under Saved. A leaking box or a missing bed saves nothing and chats the reason.
3. A builder copies it from Storage materials, including a player-supplied block that arrives late (structural pause, then decorative flag).
4. Screenshot of a quill save and of the copy.

### Phase 7 — Design world

1. Read the dimension API. If it cannot do a flat, isolated plot, stop and tell Kees; do not invent an overworld pocket in the same PR.
2. Enter, empty creative inventory, plot reset.
3. Save and return. The survival inventory is unchanged (a test puts a unique named item in the survival inventory and in the design inventory; only the survival one remains).
4. Discard returns without a blueprint.
5. Screenshot of the plot and of the table after save.

### Not a phase

Community file sharing (section 3.9). Do not add an export command.

## 5. Tests that must stay honest

| Contract today | What phase 1 does |
|---|---|
| `reservationsHoldMaterials` | Replaced by "no hold; a withdrawal takes the planks; pause text; resume". |
| `MaterialPlannerTest` for builds | Planner stays for any non-build caller. Builds stop using it. A new test locks spread and priority. |
| `twoBuildersTwoBuilds` | Kept (two sites). Added: two builders, one site, both place, no double place, bottom-up. |
| `BuilderPlannerTest` clock of 20 | Retargeted at `BUILD_PLACE_TICKS`. The "within 2 days" bound stays. |
| `checkPassRepairs` | Stays for a structural block. A new test: a missing pot does not block the marker. |
| `smallHouseWithinTwoDays` exact item map | Stays, aimed at the fixture or the new Storage after phase 5. |
| Loops over `Blueprints.all()` (rotation, stand, palette vs `RoomCells`, lang, creative tab) | Extended to the new entries. A new building that fails reach or lang fails these. Do not exclude it from the loop. |

Layer map for the new work:

- **L1:** pace constant, mirror math, structural/decorative split, priority order, spread, size cap, quill box geometry, golden reasons that do not need block collision (the classifier given a fake grid).
- **L2:** scan of all 483 templates, place each authored blueprint, two builders one site, scaffold drop, slope fill, quill save, design-world inventory restore, vanilla house marker turns green.
- **L3:** not required for this work. Do not change `starter.json` or the baselines. If a shared constant would change a metric, it must not: construction pace is the new constant.
- **L4:** every screenshot in section 4. Look at them (section 6).

## 6. How this repo writes, tests, and reviews

From `CLAUDE.md`, `docs/TESTING.md`, `docs/AGENT_LOOP.md`, and `docs/agents/`.

### Write

- Village rules live in `src/main/java/dev/villageheart/core/` with no Minecraft imports, tested by JUnit in milliseconds.
- Blocks, entities, and AI are thin glue. Client rendering stays in `src/client/`.
- A new behaviour gets a test at the lowest layer that can prove it.
- Never weaken or delete a test to go green. If a test is wrong, the commit says why.
- `docs/ROADMAP.md` and `docs/PROGRESS.md` are updated when a phase lands. This plan does not tick a v1 milestone.
- Do not edit `docs/LOOP_STATE.md`.

### The four layers

| Layer | What | Command | In `scripts/verify.sh`? |
|---|---|---|---|
| 1 | Unit tests on `core/` | `./gradlew --no-daemon build` | Yes |
| 2 | GameTests: headless server, real blocks, real ticks | `./gradlew --no-daemon runGameTest` | Yes |
| 3 | Seeded world, claim a real village, fast-forward days, metrics JSON | `scripts/simulate.sh <scenario> [days]` | Only a stubbed self-test of the script. A real sim is by hand, about a minute per in-game day. |
| 4 | Real client on Xvfb, scripted input, screenshots | `xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew --no-daemon runClientGameTest` | Yes, unless `--fast` |

`scripts/verify.sh` runs layers 1, 2, and 4, plus the layer 3 script self-test, log gates, and a perf test that runs alone at the end. Exit 0 is the definition of done. `--fast` skips the client. Java 25: `python3 scripts/install-java25.py`, then `export JAVA_HOME=~/.jdk25`. Always `./gradlew --no-daemon` in cloud sessions.

Screenshots land in `build/run/clientGameTest/screenshots/`. The script copies them to `reports/screenshots/`. The agent looks at each one. `VillageHeartClientTests` is the suite; `-Dvillageheart.l4.only=build` (or `defence`, and the other keys in that class) runs one slice while iterating.

Claim tests each use their own GameTest environment, `VillageTestSupport.reset` first and `done` last. `GameTestHelper.relativePos` is not the inverse of `absolutePos`; use `VillageTestSupport.rel`.

### Can this machine run the client and take screenshots?

Yes. Section 7. The implementation run uses the repo's Xvfb client tests, not a hand-launched window. There is no sound device here; the client turns sound off and keeps going. Screenshots do not need OpenAL. A test that required an audible device would fail; do not write one.

### Review

The repo's loop (`docs/AGENT_LOOP.md`) is plan → build → verify.

- The builder follows this plan, tests with the code, small commits, `verify.sh --fast` after each step, a full `verify.sh` at the end of the phase, and looks at every screenshot.
- A fresh verifier runs the full `scripts/verify.sh`. Any failure is a fail. For each acceptance line they find the test and, if unsure, break the code, see the test go red, and restore it. They look at every screenshot. They hunt weakened tests, test-only paths, crashes, and client classes referenced from main.
- The verdict goes in `docs/reviews/P4-rN.md` (`verdict: PASS` or `FAIL`, a numbered fix list, the evidence). FAIL comes back to the same builder. PASS, then the PR into `dev`.

This plan is the plan step. Kees accepts it before the build step starts. The implementation run does the build, all four layers (layer 3 only if a phase actually changes a scenario, which none of these should), the screenshots, and the verifier pass, then opens the PR.

## 7. Smoke test on this machine (2026-11-11)

Java 25.0.1 was installed with `python3 scripts/install-java25.py` to `~/.jdk25`. Xvfb is at `/usr/bin/xvfb-run`. Mesa's GLX is installed. No physical display and no sound card.

**Client.** `xvfb-run -a -s "-screen 0 1280x720x24" ./gradlew --no-daemon runClient` loaded Minecraft 1.21.11 with Fabric Loader 0.20.1 and Village Heart 0.1.0. LWJGL 3.3.3 came up with the usual GL extensions. OpenAL failed (`Failed to open OpenAL device`); the client logged "Turning off sounds & music" and continued. A frame grabbed from the Xvfb screen (`ffmpeg -f x11grab`) shows the Minecraft Java Edition title screen, the narrator prompt, and the Continue button. So a headless client on this VM does render, and a screenshot of that frame is a real picture, not a blank buffer.

**Server.** `./gradlew --no-daemon runServer` loaded the same Minecraft and Fabric, then stopped on the EULA because that Loom run does not accept it (`eula=false` under `run/`, which is gitignored). After `eula=true` in that ignored file, the dedicated server prepared a world and logged `Done (2.650s)! For help, type "help"` (`Starting Minecraft server on *:25565`, spawn area 100%). It was then stopped. The implementation run does not use `runServer`. It uses `runGameTest` and `runClientGameTest`, and the GameTest config already sets `eula = true` in `build.gradle`.

This smoke did not run `scripts/verify.sh` and did not play a world in the client. It only answers "can the game start here, and can we capture a picture".

## 8. Risks

1. **Most vanilla templates will fail the room check.** Streets, pens, farms, meeting points, and zombie copies are not enclosed houses with a bed and a light. The offered list may be short. That is an acceptable result. Do not relax the room rules to inflate it.
2. **Jigsaw holes.** Skipping jigsaw blocks can leave a hole in a wall and fail the scan. Do not paper over that with a fake wall. The reason string is the fix for the catalogue (the template is not offered).
3. **Player-supplied houses.** Vanilla and the nicer job buildings need glass, stairs, doors, and beds the village does not make. Players will see long still-needed lists. That matches the decision. Do not quietly add a crafter.
4. **No reservation vs the merchant.** The stall can sell planks a house is waiting on, because those planks are surplus. Section 9 asks Kees. Until answered, the merchant is unchanged.
5. **Shared cursor.** Two builders and one saved cursor will corrupt the build if the step lock is wrong. The test is two builders, one site, exact final blocks, no double withdrawal.
6. **Codec migration.** Old `carried` / `carrier` must still load. A bad custom blueprint drops that entry only, not the village.
7. **Table payload size.** More blueprints and longer material lists can blow the existing size test. Raise the caps and keep a worst-case test that fails when the packet grows past the limit.
8. **Reach.** `StandPointsTest` loops `Blueprints.all()`. A new building that does not fit the scaffold rule fails that test. Do not remove it from the loop.
9. **Design dimension.** The Fabric API is on the classpath and was not read line by line. Phase 7 starts by reading it. If a private plot cannot be done cleanly, stop.
10. **M25 merge.** Construction changes sit in `BuilderJob`, `BuildSystem`, `Balance` (new field only), and the table. `BuilderWork.java` is untouched so M25 can still insert repair there. Do not retune `BUILD_TICKS_PER_BLOCK`.
11. **Sound on this VM.** Place-sound tests should assert the server `playSound` call (or the sound event the GameTest level records), not that a speaker made noise.
12. **Removing the boxes** drops in-progress `small_house` / `storage_shed` builds on load. No public release yet. The codec test records it.

## 9. Open questions for Kees

1. **Storage and Builder's Hut.** The agreed list did not name them. Both have no real blueprint once the shed is gone. Proposal: author them in phase 5 with the others.
2. **Which vanilla paths are offered.** Proposal: pass the house check, path contains `/houses/`, path does not contain `/zombie/`, inside the size cap. The test still lists every failure. Alternative: offer every passer, including a zombie house that happens to have a bed.
3. **Size cap.** Proposal: 16×16×16 and at most 1024 touched cells. Line walls stay at 32 long. If a favourite vanilla house is bigger, raise the cap rather than special-case it.
4. **Saved blueprint cap.** Proposal: 32 per village.
5. **Merchant.** Should "still needed" structural items be subtracted from what the stall may sell, without a real reservation (players and cooks can still take them)? Proposal: yes. Unchanged until you say so.
6. **Mirror key.** Proposal: swap-hands toggles mirror; sneak-click still rotates.
7. **Quill recipe.** Proposal: ink sac + feather + paper.
8. **Mine and Hunting Lodge blueprints** cannot place the outdoor site, so they stay red until the player builds a Mineshaft or Hunting Ground. Proposal: ship them anyway and let the marker say why.
9. **Dirt for foundations** when clearing did not drop enough. Proposal: pause "Needs N more dirt", player-supplied, no dirt gatherer.
10. **Design world** if the dimension API is a poor fit. Proposal: stop and ask, rather than hide the plot in the overworld.

## 10. Out of scope

Modular or castle composition. Blueprint file sharing (section 3.9 only). A gatherer or a crafter for glass, stairs, or terracotta. Wall damage, wall repair, raid logic, guard AI, smith behaviour. Changing `BuilderWork` or `BUILD_TICKS_PER_BLOCK`. Terrain-following walls. Mirroring a line wall (walls have no facing to mirror; leave LINE as it is). Undo of a finished building.

## 11. Server smoke line

`Done (2.650s)! For help, type "help"` after the EULA file under `run/` (gitignored) was set true. First boot without that edit stopped at "You need to agree to the EULA". Client result is in section 7.
