# P3 plan: Cosmetic minecart haul

Planner: Cursor CLI (grok-4.7-xhigh), 2026-11-11. Builder: implement in the step order of section 4. Every step ends green on
`scripts/verify.sh --fast`. Step 5 also needs the full `scripts/verify.sh` (it adds an L4 screenshot). No Gradle and no Minecraft were run
for this plan. Code was read in this worktree (`plans-postv1`, from `origin/dev`). APIs marked **[verified]** were read in the Loom 1.21.11
Mojang-mapped sources.

This is a **post-v1** plan. It is the line in DESIGN §5.3: the miner "reappears … with one haul, which it delivers to Storage. Later the
delivery is shown as pushing a minecart along the rails (cosmetic, post-M-mining polish)." DESIGN §11 still lists real minecart logistics
as out of v1. This plan is the picture, not the logistics.

Every **Proposal** is the planner's default.

## 1. Goal

When a miner comes up with a haul and the shaft's rails run to the Storage, the player sees a minecart move along those rails toward the
Storage. The ore is still in the miner's inventory and still arrives through the existing deposit trip. The cart holds nothing, cannot be
ridden, cannot be opened, and cannot be pulled by a hopper. If the rails do not reach Storage, there is no cart and the delivery is the one
M16 already shipped.

## 2. Current state (what P3 builds on)

| Where | What is there |
|---|---|
| `core/mine/MinerPlanner` | `Action.YIELD` when the miner is carrying a haul. `GO_DOWN` walks to the shaft and hides. |
| `worker/mine/MinerJob` | Every 20 ticks, plans. If `Workers.tripRunning`, the deposit trip owns the walk and this tick returns. `YIELD` releases the miner's own `WalkTo`. A full inventory of kept food can drop a pending haul; that path is unchanged. |
| `worker/mine/MinerKeep.carriesNonFood` | True when the inventory holds anything that is not kept food. That is the "haul in hand" signal. |
| `worker/DepositTrip` | Walks to the nearest Storage door and `Stockpiles.depositHere`. Timeout 600 ticks. Trip state is not saved. |
| `worker/job/AwayTrips.surface` | Restores the flags, stands the miner at the surface cell. The haul is already in the inventory (or in `AwayState.pending` until `MinerHauls.flushPending`). |
| `worker/mine/MinerSystem` | Steps a hidden miner. Haul tables and the seeded RNG live here and in `core/mine/HaulTable`. P3 does not call them. |
| `core/building/ShaftScanner` | BFS from the marker over open cells, going down. Counts a cell whose contents contain `#rail` (the test double is `Cell.open("minecraft:rail", "#rail")` in `ShaftScannerTest`). It does **not** follow rails across the surface: a neighbour at `y >= marker.y - 1` more than 1 block from the marker is skipped. A valid shaft needs `Balance.MINESHAFT_MIN_DEPTH` 12 and `MINESHAFT_MIN_RAILS` 8. |
| `core/room/BlockView`, `Cell`, `FakeGrid` | The pure world the scanner already uses. |
| `stock/Stockpiles.depositHere` | The only way a haul enters Storage. |
| `gametest/MineTestSupport`, `MinerGameTests` | The shaft and the "≥40 cobblestone in a day" test. P3 adds a case beside them; it does not change the 40-cobble assertion. |
| L3 | No miner scenario asserts a cart. `starter` has no mineshaft. `ScenarioFilesTest` parses every file. |

Vanilla, **[verified]**:

- `EntityType.MINECART` is rideable. `Minecart.interact` calls `startRiding`. `AbstractMinecart.move` calls `pushAndPickupEntities`. A real cart can pick up the player or the miner, bump another cart, and drop `Items.MINECART` when broken. `NewMinecartBehavior` is the 1.21 rail physics. P3 does not spawn one.
- `EntityType.CHEST_MINECART` and `HOPPER_MINECART` are containers. A hopper can pull from a chest cart. P3 does not spawn those either.
- `Display` sets `noPhysics = true` in its constructor. `Display.ItemDisplay.setItemStack` and `setItemTransform(ItemDisplayContext)` exist. `EntityType.ITEM_DISPLAY` is already rendered by `DisplayRenderer.ItemDisplayRenderer`. No new renderer is required if the entity type stays `ITEM_DISPLAY`.
- `Entity.setInvulnerable(boolean)` exists. `Entity.addTag` / tags are saved with the entity.
- `BaseRailBlock.isRail(BlockState)` exists. The pure path does not call it; the glue does, and it only feeds `#rail` cells into the `BlockView` the core already understands.
- `RailShape` includes the four ascending shapes. A slope is a rail cell one y away from its neighbour.

## 3. Design

### 3.1 Assumptions

| # | Assumption | Step | If wrong |
|---|---|---|---|
| A1 | `DepositTrip` is still the delivery. `MinerJob` returns early while `tripRunning`. | 3 | Hook the show on `carriesNonFood` plus "not hidden", not by replacing the trip. |
| A2 | `ShaftScanner` must keep its surface-spread rule and its rail count. The cosmetic path is a separate BFS. | 1 | Do not add surface rails to the shaft's rail count. A line of rails to Storage must not turn an invalid shaft valid, or a valid one invalid. |
| A3 | No save field is needed. The show is transient, like `DepositTrip`. | all | Do not add a `VillageState` component or a `RAW_B` key. |

### 3.2 Proposals

1. **The cart is an `ItemDisplay` of `Items.MINECART`, not a minecart.** It is created with `EntityType.ITEM_DISPLAY`, `setItemStack(new ItemStack(Items.MINECART))`, `setItemTransform(ItemDisplayContext.FIXED)`, `setInvulnerable(true)`, `setSilent(true)`. `Display` is already `noPhysics`, so it does not push the miner or the player and it does not sit on a rail physically. The tag `villageheart.haul_cart` is added so a crash leftover can be recognised. Nothing is ever inserted into the display. The haul never leaves the villager until `DepositTrip` deposits it.
2. **The miner still walks the deposit trip.** The cart is a second actor on the rails. The player sees the cart run to Storage while the miner walks to the Storage door as today. Coupling them (miner pushed along the rail, trip retargeted) would change arrival, timeouts and the 40-cobble test. This plan does not.
3. **When the show starts.** Once per surfacing, the first `MinerJob` tick where the miner is not hidden, not away, and `MinerKeep.carriesNonFood`. If a path exists, spawn the display at the first rail cell and set a transient "shown" flag on `MinerJob.State` so a second tick does not spawn another. The flag clears when the miner goes down (`GO_DOWN`) or is fired (`MinerJob.forget`).
4. **The path.** `core.mine.RailPath.find(BlockView, Pos mouth, Pos storageDoor, int max)`:
   - BFS over cells whose contents contain `#rail`, 4 horizontal neighbours and the four slope steps (the cell one up or one down in the same horizontal direction).
   - Start at `mouth`: the rail cell of the shaft with the greatest y. The glue collects shaft rails with the existing scanner rules (so the mouth is a rail the shaft already counted) and also the surface rails beyond that, which the shaft scanner ignores.
   - Goal: a rail cell within `Balance.MINECART_STORAGE_RANGE` (8) Chebyshev of the Storage door. Among those, the one closest to the door. The door is the same idea as `PatrolPoints.doorPoint` for the nearest valid Storage that has room; if none has room, there is no show (the trip will already fail or wait).
   - Cap `Balance.MINECART_MAX_RAILS` 64. An unloaded cell (`CellKind.UNLOADED`) aborts the path: no show, no partial line into a chunk that is not there.
   - No path → no display, `MinerJob` unchanged.
5. **Motion.** One rail cell every `Balance.MINECART_CELL_TICKS` (4). 64 rails is 256 ticks, inside the deposit timeout of 600. The display is moved with `setPos` to the centre of the cell, `y + 0.2`. At the last cell it stays until the miner no longer `carriesNonFood`, or until `Balance.MINECART_HOLD_TICKS` (400) more ticks, then `discard()`. Also discard when the miner is fired, the village is released, the server stops, or the cap throws this show away.
6. **Cap.** At most `Balance.MINECART_MAX_SHOWS` (4) displays. A fifth delivery skips the picture and still deposits. Two miners do not share a display.
7. **Crash and reload.** `DepositTrip` is not saved, and neither is the show. On `ENTITY_LOAD`, an entity tagged `villageheart.haul_cart` is discarded. A cart mid-run does not survive a reload, and it does not come back as a minecart item. The ore is still on the miner or already in Storage, because it was never in the cart. `SERVER_STOPPING` discards every live show so a clean stop does not write the display.
8. **What "pushing" means.** The display moves along the rail centres in order. There is no pusher entity and no rail velocity. That is the whole animation. Powered rails, detector rails and activator rails are just `#rail` cells for the path. An `ItemDisplay` does not press them.

### 3.3 Classes

- `core.mine.RailPath` and `record RailPath.Result(List<Pos> cells)`. Pure. Uses `BlockView`.
- `worker.mine.HaulCart` (glue): `start(ServerLevel, Villager, List<Pos>)`, `tick`, `discardAll`, `onLoad(Entity)`. Called from `MinerJob` after a plan that is `YIELD` and `carriesNonFood`, and from `ServerLifecycleEvents.SERVER_STOPPING`. The load hook is `ServerEntityEvents.ENTITY_LOAD`, the same event `AwayTrips` already registers (it fires for any entity, not only living ones). A loaded entity whose tags contain `villageheart.haul_cart` is discarded.
- `WorkStats.add("miner", "cart_shows", 1)` when a show starts. No scenario asserts it. `starter` will keep reporting 0.
- No new item, block, packet, screen, or codec.

### 3.4 Numbers

| Name | Value |
|---|---|
| `MINECART_MAX_RAILS` | 64 |
| `MINECART_STORAGE_RANGE` | 8 (Chebyshev, rail cell to Storage door) |
| `MINECART_CELL_TICKS` | 4 |
| `MINECART_HOLD_TICKS` | 400 |
| `MINECART_MAX_SHOWS` | 4 |

### 3.5 Not changed

`HaulTable`, `HaulRng`, depth bands, pickaxe wear, `ShaftScanner` counts, `DepositTrip` timeouts, `MinerKeep`, the 40-cobblestone GameTest, and every L3 file.

## 4. Step-by-step build order

Each step ends green on `scripts/verify.sh --fast`.

1. **Path.** `RailPath`, `Balance` constants. L1: T-L1-1, T-L1-2, T-L1-3. `ShaftScannerTest` stays green, including a grid where surface rails exist and are not counted.
2. **Show, no world hook yet.** `HaulCart` spawn, move, discard, tag, cap. L2: T-L2-1 driven by a direct `HaulCart.start` call so a failure is the cart, not the miner.
3. **Hook.** `MinerJob` starts one show per surfacing. Load and stop hooks. L2: T-L2-2, T-L2-3, T-L2-4.
4. **Safety.** Hopper, attack, no real minecart, reload. L2: T-L2-5, T-L2-6.
5. **Screenshot and docs.** L4 T-L4-1, look at it. DESIGN §5.3 one paragraph "Rules as built (P3)". ROADMAP line left unticked. PROGRESS. Do not edit `docs/LOOP_STATE.md`. Full `verify.sh`.

## 5. Test plan

### 5.1 Layer 1 (`RailPathTest`, `FakeGrid`)

| Id | What | Fails when |
|---|---|---|
| T-L1-1 | A straight line of 6 `#rail` cells from the mouth to a cell 2 away from a door pos returns those 6 in order. A gap of one non-rail cell returns empty. | The cart would jump a hole, or a connected line shows nothing. |
| T-L1-2 | A slope: rail at y, next rail at y+1 in front, is connected. A rail two up is not. An `UNLOADED` cell on the only route returns empty. A route of 65 rails returns empty (over the cap), not a truncated line. | A slope breaks the show, or a path runs into an unloaded chunk and drops the ore (it must not drop the ore either way; empty means no show). |
| T-L1-3 | Two Storage doors: the path ends at the rail closest to the nearer door, and only if that rail is within 8. A rail 9 away returns empty. The shaft scanner on a grid with 8 shaft rails plus 10 surface rails still reports 8 rails. | Surface rails change the shaft verdict, or the cart aims at the far Storage. |

### 5.2 Layer 2 (`HaulCartGameTests`, reuse `MineTestSupport`)

Place Storage next to the mine. The shaft is the usual valid shaft (12 deep, 8 rails). Add a surface line of 6 rails from the mouth to within 3 blocks of the Storage door. Give the miner 16 cobblestone and 2 coal in its inventory (a known stand-in for one haul; do not wait out a real descent). Day time in the work window.

| Id | Test | Assertions (and how it fails if broken) |
|---|---|---|
| T-L2-1 | `displayFollowsTheRails` | After `HaulCart.start` with 6 cells, the display's block position is the first cell, then the third cell after 8 ticks (2 cells × 4 ticks), then the last cell after 24 ticks. The item is a minecart. Exactly one entity tagged `villageheart.haul_cart`. Fails: it teleports to the end, or it never moves. |
| T-L2-2 | `minerDeliveryStillFillsStorage` | Start the miner carrying 16 cobble and 2 coal, with the surface rails. Within 600 ticks Storage holds 16 cobble and 2 coal, the miner's inventory does not, and at some earlier tick a tagged display existed. The sum of cobble in the inventory plus Storage is 16 at every sampled tick (every 20 ticks). No `EntityType.MINECART`, `CHEST_MINECART` or `HOPPER_MINECART` exists at any sample. Fails: the ore was moved into the cart and lost, or duplicated, or a real cart was spawned. |
| T-L2-3 | `noRailsNoCart` | The same miner and haul, shaft rails only, no surface line to Storage. | Storage still receives the 16 cobble. No tagged display appears. `work.miner.cart_shows` stays 0. Fails: a missing path ate the haul, or a cart spawned in the shaft. |
| T-L2-4 | `oneShowPerTrip` | Tick `MinerJob` 100 times while the miner still carries the haul. | One display, not 100. `cart_shows` is 1. A second miner with a haul makes a second display. A fifth is skipped (seed 5 miners): 4 displays, all 5 hauls still in inventories. Fails: a display leak. |
| T-L2-5 | `cartHasNoItems` | A hopper under the display's cell, a player survival attack for 40 ticks, a player right-click. | The hopper stays empty. The player's inventory gains no minecart. The display is still there (invulnerable) until the haul is deposited. The player is not riding anything. Fails: the cosmetic cart became a container or a vehicle. |
| T-L2-6 | `reloadDropsThePictureNotTheOre` | Display live, miner still holding 16 cobble, `VillageTestSupport.reload`. | No tagged display. Cobble is 16, either on the miner or in Storage, not both and not neither. Fails: a duplicate stack, or a minecart item on the ground. |

The existing `MinerGameTests` cobble-in-a-day test is run unchanged by `--fast`. Do not add rails to its structure.

### 5.3 Layer 4

**T-L4-1 `HaulCartClientTests.cartOnTheRails`:** the T-L2-2 layout, camera at eye height beside the rail line, the miner visible and the minecart item display on the rails. Screenshot `haul_cart.png`. Look at it: the cart reads as a minecart on the rails, the miner is not inside it, and there is no chest on the cart.

### 5.4 Layer 3

No scenario file is added or edited. `defended`, `defended_walled`, `starter`, `starter_cook` and `bare` stay as they are. `work.miner.cart_shows` may show up as 0 in a run that has no delivering miner; do not assert it. The show is off the 1 ms village budget except while a miner is delivering: one BFS of at most 64 cells at the start, then one `setPos` per 4 ticks. Do not scan the world every tick.

### 5.5 Acceptance mapping

| Want | Tests |
|---|---|
| Shown along the rails | T-L1-1, T-L1-2, T-L2-1, T-L4-1 |
| Same ore, same Storage | T-L2-2, T-L2-3, the existing day-haul test |
| Safe: no ride, no hopper, no drop, no dup on reload | T-L2-5, T-L2-6 |
| No path → today's delivery | T-L1-3, T-L2-3 |
| Small | No new save key, no new item, step count 5 |

## 6. Risks, out of scope, open questions

1. **(Top risk) The display still saves if the discard hooks miss a path.** The tag plus the load hook is the backstop: a leftover is removed the next time its chunk loads, and it has no items to drop. T-L2-6 is that backstop. If `ENTITY_LOAD` does not fire for an `ItemDisplay`, discard on the first `HaulCart.tick` by scanning tagged displays whose id is not in the live map, once per 200 ticks, claim radius only.
2. **The picture and the miner disagree.** The cart can reach Storage before the miner, or the miner can arrive first if the rail line is long and the door is close by foot. The ore moves only with the miner. The hold at the last cell (400 ticks) is so the cart is still there when the chest fills. If the walk is longer than that, the cart vanishes early. That is still cosmetic.
3. **Item display orientation.** `ItemDisplayContext.FIXED` may not sit on the rail the way a minecart entity would. T-L4-1 is the check. If it looks like a floating item, try `GROUND` before writing a custom renderer. Do not switch to `EntityType.MINECART` to fix the look.
4. **Shaft mouth.** If the 8 required rails are all at the bottom, the mouth is the highest of them, which may be underground. The surface line must connect to that cell. The test lays the connection on purpose. A player rail that does not meet the shaft gets no show (T-L1-1's gap).

**Out of scope:** real minecart physics, chest minecarts, hopper minecarts, the miner riding, items inside the cart, a rail network between buildings, furnace carts, detector-rail redstone, changing haul sizes or the deposit trip, and any L3 scenario.

**Open questions for the owner:**

1. Is an item display of a minecart enough of a "minecart", or do you want a real `Minecart` even with the riding and dropping risks?
2. Should the miner keep walking the Storage door (this plan), or walk along the rails behind the cart?
3. Is "no cart unless the rails come within 8 blocks of a Storage door" the right cutoff?

## 7. Deviations (builder)

