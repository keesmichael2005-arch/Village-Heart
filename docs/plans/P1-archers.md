# P1 plan: Archer guards

Planner: Cursor CLI (grok-4.7-xhigh), 2026-11-11. Builder: implement in the step order of section 4. Every step ends green on
`scripts/verify.sh --fast`. Steps 8 and 11 also need the full `scripts/verify.sh` (they add or change an L4 screenshot). No Gradle and no
Minecraft were run for this plan. Code was read in this worktree (`plans-postv1`, from `origin/dev`). An API marked **[verified]** was read
in the Loom 1.21.11 Mojang-mapped sources jar `minecraft-common-…-sources.jar` (and the client jar for `TippableArrowRenderer`).

This is a **post-v1** plan. It is not part of the v1.0 gate. The owner called archers a "maybe" (2026-11-09) after accepting M18 as the hard
start. Every decision below marked **Proposal** is the planner's default. The owner can change it; the open list is section 6.

## 1. Goal

A Steward can hire an **archer** from the Barracks, on the same slot, bed, shift, training day and ration as a sword guard. On duty the archer
stands a post: a cell on a Watchtower platform, or a cell on a new Wall Walk the builder can place at Village tier. It shoots hostiles and
raiders it can see from that post. The bow and the arrows come from the Barracks chest and the stockpile. With no bow it holds the post and
does not swing a sword. It never shoots a creeper, a villager, a player, a golem, or any mob it was not aiming at.

A village that never hires an archer is unchanged, including the L3 `defended` and `defended_walled` bars.

## 2. Current state (what P1 builds on)

Read in this worktree.

| Where | What is there |
|---|---|
| `core/worker/JobType` | Nine jobs. `GUARD("guard", …, BuildingType.BARRACKS, BuildingRequirements.GRINDSTONE)`. `tool()` is empty for guards. |
| `core/worker/JobSlots` | A slot is a job block. `heldBlocks` is every worker, so two jobs that share a grindstone cannot both take it. `decide` is the hire refusal order. |
| `core/building/BuildingRequirements` | Barracks: floor 16, one grindstone, **one bed per grindstone**, one chest. `SlotRule.MIN_JOB_BLOCKS_AND_BEDS`. |
| `core/guard/GuardData` | `(wounded, trainingUntil, ration)`. `FRESH` is all empty. `training(now)` is the one training day. |
| `world/GuardCodecs.GUARD_DATA` | Three optional fields: `wounded`, `training_until`, `ration`. A missing field is the default. |
| `worker/guard/Guards` | `isGuard` is `jobId == "guard"`. `phaseOf` calls `GuardSchedule.phase(…, raid, GuardAlarm.active, wounded, canWork, training)`. `onHired` sets `trainingUntil` to dusk and, if the hand is empty, equips `Items.WOODEN_SWORD`. |
| `worker/Workers.hire` | Refuses `JobType.GUARD` from day time 12000 (`HireRefusal.GUARD_AT_NIGHT`) unless the test flag is set. Then `Guards.onHired`. |
| `worker/WorkerBrain.install` | `JobType.GUARD` gets `GuardBrain`. Everyone else gets the work brain. |
| `core/VillageState` | `guardRank` / `guardIds` count `jobId == "guard"` only. 17 components; the last is `guardShifts`. |
| `world/VillageCodecs` | `RAW_A` (10 keys) and `RAW_B` (7 keys, last `guard_shifts`) joined with `Codec.mapPair`. `RecordCodecBuilder.group` caps one group at 16. |
| `tier/TierFacts` | Town's "≥2 guards" counts `JobType.GUARD` only. |
| `needs/NeedsSystem` | Skips `GUARD` in `rehome`. Beds are `GuardBeds`. |
| `core/guard/ShiftBalancer` | `dayCount(n) = n/4`. 1–3 guards: all night. Seniors (level, then XP) take nights. Pins stick. |
| `core/guard/GuardSchedule` | 7-arg `phase`: a trainee who is not wounded is `OFF` (no raid, no alarm). Wounded sleeps. Else raid or alarm forces `DUTY`. |
| `core/guard/GuardTargets` | `Kind`: `HOSTILE, RAIDER, CREEPER, NEUTRAL, UNREACHABLE`. `choose` drops `CREEPER` and `UNREACHABLE`. Neutrals only when they target a villager or hurt this guard. Duty: in the claim, or it hurt me. |
| `worker/guard/GuardCombat` | Melee. `kind`: fliers → `UNREACHABLE` (phantom, vex, blaze, ghast, breeze, or a flying navigator); `Creeper` → `CREEPER` before `Enemy`. Sight `Balance.GUARD_SIGHT` 24. Hit via `doHurtTarget` every `GUARD_ATTACK_COOLDOWN` 20 ticks. A kill in the claim awards `WorkUnit.HOSTILE_KILLED` and `work.guard.kills`. |
| `core/guard/GearPlanner`, `GearRanking`, `GearItem` | One pick per `GearSlot` (`HEAD, CHEST, LEGS, FEET, WEAPON`). A weapon is usable only when `attackDamage > 0`. Ranking ignores enchantments. |
| `worker/guard/GuardGear.read` | Swords (`ItemTags.SWORDS`) and armour with an equippable slot. A bow is not gear. `onHired`'s wooden sword is how a new guard starts. |
| `core/guard/GuardPlanner` | Duty: `FIGHT > EQUIP > PATROL`. Off: `DEFEND > RETURN_GEAR > SPAR > IDLE`. |
| `worker/guard/GuardRuntime.patrol` | Alarm spot first (gather within 6 blocks). Else `PatrolPoints.of`: Heart, then a door point 3 cells outside each valid room, then `Defences.patrolPoints`. `PatrolRoute.start` staggers guard `i` of `n`. |
| `core/defence/DefenceSpecs` | Lamp and watchtower only. Tower patrol candidates are ground cells `(2,0), (2,4), (0,2), (4,2)` at layer 0. Keys: ladder at `(2,0,2)` and `(2,5,2)`. Walls and gates have no spec. |
| `core/blueprint/DefenceBlueprints.WATCHTOWER` | Town, footprint `5 7 5`, anchor `(2,2)`. Layers 0–4 shaft, layer 5 plank platform, layer 6 fence rail. The eight `_` cells of layer 6 are the platform air; `(2,6,2)` is `any` (the ladder hole). |
| `core/defence/DefenceSite` | `(id, blueprintId, ground, rotation, builtAt)`. No length. `BuildJobs.finish` records a site only when `Defences.recorded` (lamp and tower). |
| `defence/Defences.walkable` | Feet and head free of collision and fluid, cell below solid. An unloaded chunk counts as walkable. |
| `stock/Stockpiles` | `withdrawFirst(level, from, match, max, holder)` and `withdraw`. Guard rations already use `withdrawFirst` (remote, no walk). |
| `worker/hunt/HuntGear.takeArrow` | Lodge chest only: plain `Items.ARROW` first, then `ItemTags.ARROWS`. Hunters do not make arrows. |
| `core/heart/HeartAction.Assign` | `(villager, jobId, building)`. Payload `AssignJobPayload` / `heart_assign_job`. Do not add a field: old tests round-trip this shape. |
| `heart/HeartFacts.activityOf` | Guards: fighting, training, equipping, patrolling, idle, wounded. |
| `core/heart/ActivityKind` | Append-only (the screen codec uses the ordinal). Last values are `DAILY_LIMIT, TREASURY_FULL`. |
| `client/.../HeartScreen` | Five tabs. A selected unemployed villager gets one button per `JobChoice`. The guard row already has the Shift button (`Permission.SET_GUARD_SHIFT`). |
| L3 | `sim/scenarios/defended.json` and `defended_walled.json`. Owner 2026-11-11: their bars stay (`deaths.mobs <= 1`, `work.guard.kills >= 15`). They are M26 targets. `ScenarioFilesTest` parses every file in `sim/scenarios`. |
| Smith | No `worker/smith` and no `core` smith package on this branch. `JobType.BLACKSMITH` exists. M19 is not merged. DESIGN §5.7 does not list bows. |
| Tests that must stay green | `GuardGameTests` (including `guardBeatsOneZombie`, `zombieInClaimKilledAtNight`, `raidCallsUpAllGuards`, `alarmWakesSleepingGuardsAndTheyReturn`, `guardPatrolsLampPost`, `noGuardIsHiredAtNight`, `threeGuardsAllWorkNights`), `DefenceGameTests.watchtowerTownAndPatrolPoints`. |

Vanilla, **[verified]** in the 1.21.11 sources:

- `BowItem.MAX_DRAW_DURATION` is 20. `Items.BOW` has durability 384. A bow has no attack-damage attribute the gear ranker would see.
- `CrossbowItem.MOB_ARROW_POWER` is `1.6F`.
- `Projectile.shoot(double, double, double, float power, float inaccuracy)` and `AbstractArrow.setBaseDamage(double)`. Default `baseDamage` is 2. Hit points are `ceil(speed * baseDamage)` in `AbstractArrow.onHitEntity`.
- `AbstractArrow.setBaseDamageFromMob` adds a difficulty roll. Do not use it.
- `AbstractArrow.findHitEntity` keeps an entity only when `canHitEntity` says so. Returning false lets the arrow fly on.
- `AbstractArrow.Pickup.DISALLOWED` does not drop an arrow item. `setOwner` on a non-player leaves pickup alone.
- `ProjectileUtil.getMobArrow` exists. P1 does not use it, because of the difficulty roll inside `setBaseDamageFromMob`.
- `Arrow` is `EntityType.ARROW`, size `0.5 × 0.5`. The client renderer is `TippableArrowRenderer` (abstract `ArrowRenderer` is not the one registered).
- `Entity.teleportTo(double, double, double)` exists.
- `EntityType.Builder.build(ResourceKey)` is the 1.21 register shape (same idea as `ModItems`).

## 3. Design

### 3.1 Assumptions (check before the step named; write a difference in section 8)

| # | Assumption | Step | If wrong |
|---|---|---|---|
| A1 | M19 is not on this branch. P1 does not craft bows. It only publishes a demand the smith can fill later. | 10 | If a smith gear planner exists, call `ArcherDemand.missing` from it and do not invent a second planner. |
| A2 | M25 is not on this branch. Walls and gates are still not `DefenceSite`s. Creepers are already ignored by `GuardTargets`. | all | If M25 records gates, do not record them again. Archers still ignore creepers. |
| A3 | `DefenceSite` is still the 5-arg record. P2 may add the same `length` field. | 6 | One optional key `length`, default 0. A 5-arg constructor delegates to length 0 so `VillageStateDefencesTest` and `DefencesTest` keep compiling. |
| A4 | `ActivityKind` is still appended at `TREASURY_FULL`. | 9 | Append after whatever is last. Never insert. |
| A5 | One claim per world. Posts are for that claim. | all | A later multi-village change keys posts by the claim. |

### 3.2 Proposals (owner can change)

1. **Archers are guards, not a new job and not a new building.** The job id stays `"guard"`, so `ShiftBalancer`, `GuardBeds`, `TierFacts`, the night-hire refusal, `WorkerBrain` and `ModProfessions` keep counting them. A new `JobType.ARCHER` would need a profession, a brain branch, and a second pass over every `GUARD.id()` check, and the Heart would show two slot counts for one grindstone. A new Archery building would split beds from the Barracks. The Barracks button row gains a second label, "Archer", on the same free-slot count.
2. **No free bow, no wooden sword.** `Guards.onHired` skips the wooden sword when the new guard is an archer. An archer with an empty hand stands its post. The Villagers tab says "Needs bow". This keeps the hard start: hiring an archer spends a guard slot and does nothing until the village has a bow.
3. **Bows and arrows from the village.** Same places as armour: the Barracks chests first (`CampContainers`), then the stockpile (`Stockpiles.withdraw` / `withdrawFirst`). Plain `Items.ARROW` only. Tipped and spectral arrows stay in the chest. One arrow is taken when the shot is released. A miss still costs the arrow. No Infinity. The bow wears 1 durability per shot and is handed back off shift with the armour (`GuardGear.returnAll`, once a bow counts as gear for an archer).
4. **The smith comes later.** `core.guard.ArcherDemand.missing(archers, usableBows, plainArrows)` is the contract: one usable bow per archer, and `Balance.ARCHER_ARROW_RESERVE` (16) plain arrows per archer, above what is already in the Barracks and the stockpile. P1 does not smelt or craft. When M19's planner exists it should craft a vanilla bow (3 sticks + 3 string) and arrows (1 flint + 1 stick + 1 feather = 4) for that missing pair, at the smith's own pace. Until then the player puts bows and arrows in Storage or the Barracks chest.
5. **Posts.** An on-duty archer with a post does not walk `PatrolPoints`. Melee guards still do, and the tower's ground patrol candidates stay as they are (`DefenceGameTests.watchtowerTownAndPatrolPoints`, `guardPatrolsLampPost`).
   - **Watchtower (Town):** one of the eight platform cells, local `(x, 6, z)` where layer 6 is `_`: `(1,6,1) (2,6,1) (3,6,1) (1,6,2) (3,6,2) (1,6,3) (2,6,3) (3,6,3)`. Not the ladder hole `(2,6,2)`. Feet stand on the layer-5 planks. The archer walks to the existing ground patrol point, then climbs the ladder column `(2, y, 2)` one block every `Balance.ARCHER_CLIMB_TICKS` (8) with `teleportTo`. `WalkTo` is not asked to path the shaft. A tower whose ladder key is gone is already dropped by `DefenceSystem`.
   - **Wall walk (Village):** a new LINE blueprint `wall_walk`, length 4..32 (max `Balance.MAX_WALL_LENGTH`). The M21 wall is 3×1 cobblestone with no headroom and no saved site, so an archer cannot stand on it. This blueprint is the walk. Cross-section, x along the line, z depth, y layer, anchor `(0,0)`, size `n 4 2`:
     - z = 0 (rail): layers 0–1 cobblestone, layer 2 oak planks, layer 3 oak fence.
     - z = 1 (stand): layers 0–1 cobblestone, layer 2 oak planks, layer 3 air.
     - Feet cell: `(x, 3, 1)`. One post every 4 blocks, x = `1 + 4i`, while `x <= n - 2`. Length 8 has posts at x = 1 and x = 5 (2 posts). Materials: `4n` cobblestone, `2n` planks, `n` fences. Length 8 is 32 / 16 / 8.
   - **No post:** the archer patrols the melee loop and shoots from wherever it stands. A Village with no wall walk and no watchtower still has a working archer; it just has no elevation, so a 3-high wall blocks its shot. That is the backup, not a siege engine.
6. **Who stands where.** `core.guard.ArcherPosts.assign(archersInHiringOrder, posts)` gives each archer one post, stable by hiring order. Towers first (finish order, then the eight cells in the list above), then wall walks (finish order, then x). Extra archers get no post and patrol. A post is not shared. Recomputed every 200 ticks (`PatrolPoints.REFRESH_TICKS`). A pin is out of scope (P2 owns pins).
7. **What they shoot.** The same `GuardTargets.choose` as melee, including "creepers are never chosen" and fliers are `UNREACHABLE`. The owner (2026-11-11): nobody attacks creepers; they only breach walls. An archer is not anti-air and not a creeper hunter. Range is `Balance.ARCHER_RANGE` 24, the same as `GUARD_SIGHT`. They do not chase. A target closer than `Balance.ARCHER_MIN_RANGE` (4) makes them step back along the post if the cell behind is walkable; they still shoot if it is not. They never call `doHurtTarget`.
8. **The arrow.** A new entity `GuardArrow extends Arrow`, registered like `EntityType.ARROW` (`MobCategory.MISC`, `0.5×0.5`, tracking 4, update 20, `noLootTable`). `canHitEntity` is true only for the UUID aimed at. A creeper, villager, player, iron golem, armour stand or a second zombie in the way is passed through, not hit and not ignited. Pickup is `DISALLOWED` before `setOwner`, so a stuck arrow drops nothing. `setBaseDamage(Balance.ARCHER_BASE_DAMAGE + GuardPerks.bonusDamage(level))` with `ARCHER_BASE_DAMAGE` 2.0, `shoot(..., power = 1.6F, inaccuracy = 0)`. No crit, no Power enchantment bonus (enchantments are already unranked on swords). One shot per `Balance.ARCHER_SHOT_TICKS` (30). Aim from `getEyeY()` at the target's eye, only with line of sight that ignores friends and creepers and stops on a solid block. If the block is closer than the target, do not shoot. A kill still flows through `GuardCombat`'s `AFTER_DEATH` listener (the owner is the guard), so XP and `work.guard.kills` stay one path. Also count `work.guard.arrows`.
9. **Shifts, alarm, training, rations — no new rules.**
   - Shifts: an archer is a guard in `guardIds()`, so 1–3 guards of either style are all on nights, and seniors still take nights. The Shift button pins an archer the same way.
   - Training: `GuardTraining` and `HireRefusal.GUARD_AT_NIGHT` apply. Until dusk the phase is `OFF`. An archer does not shoot while training. It spars at the Training Yard if one is linked, exactly as a sword guard, and the yard's armour stands are not targets.
   - Alarm and raid: `GuardSchedule` already forces `DUTY`. On duty an archer with the alarm up leaves the post, walks to `ARCHER_STANDOFF` (8) blocks short of `GuardAlarm.spot` (or the raid centre, the same gather `GuardRuntime.patrol` uses today), and shoots from there. It does not walk into melee range. Wounded archers sleep. A trainee is not called. When the alarm ends, the archer goes back to its post (climb again if the post is up a tower).
   - Rations: `GuardRation` and `GuardSystem.feed` are unchanged. A night archer on a far tower refills during the off window, the same as a night guard who is far from the Barracks. Food still heals only with no hostile within `GUARD_EAT_SAFE_DISTANCE` (10), retreat bite included.
   - Gear hand-in: off shift, the bow goes back to the Barracks chest, else Storage, else the ground, by the existing `returnAll` once the bow is in the worn list.
10. **Level perks.** Health stays `GuardPerks` (20 / 30 / 40). The damage perk is added to the arrow's base damage, not by giving the villager a melee swing. `villageheart:guard_damage` can stay on the attribute; the archer simply never swings.

### 3.3 Core (`dev.villageheart.core.guard` unless noted)

- `GuardData` gains `boolean archer`, default false. `FRESH` stays non-archer. `withArcher(boolean)`. `GuardCodecs`: optional `archer`, default false. An old save loads as a sword guard.
- `ArcherGear` (pure): a bow id `minecraft:bow` is the only ranged weapon. Usable when the durability fraction is at least `GEAR_MIN_DURABILITY` (0.1). Better bow = higher fraction, then the same source tie-break as `GearRanking` (worn, then barracks, then stockpile). Swords are not candidates. Armour planning stays `GearPlanner` for the four armour slots.
- `ArcherDemand.missing(int archers, int usableBows, int plainArrows) -> (bows, arrows)`, bows = `max(0, archers - usableBows)`, arrows = `max(0, archers * ARCHER_ARROW_RESERVE - plainArrows)`.
- `ArcherPosts`: the assigner in 3.2 #6. Posts are `record Post(Pos stand, Pos approach, boolean climb)` where `approach` is the ground cell (tower patrol point, or the stand cell itself for a wall walk) and `climb` is true for a tower.
- `core.defence.DefenceSpecs` gains `standCells(DefenceSite)` returning world `Pos` with a real y (not the y = 0 patrol list). Tower: the eight cells. Wall walk: the feet cells from the generated blueprint and `site.length`. Lamp: none. `patrolPoint` is not changed.
- `DefenceSite` gains `int length` (≥ 0). Canonical 6-arg record. Extra constructor of the old 5 args calls `this(..., 0)`. Codec key `length`, optional, default 0. `BuildJobs.finish` passes `job.params().length()`.
- `DefenceBlueprints`: `WALL_WALK_ID = "wall_walk"`, `wallWalk(n)`, `minLength` and `line` learn the id. `Blueprints.ALL` includes `wallWalk(4)` (the shortest), so the Builder's Table lists it. Tier `VILLAGE`, kind `DEFENCE`, mode `LINE`.
- `Balance`: `ARCHER_RANGE` 24, `ARCHER_MIN_RANGE` 4, `ARCHER_STANDOFF` 8, `ARCHER_SHOT_TICKS` 30, `ARCHER_BASE_DAMAGE` 2.0, `ARCHER_POWER` 1.6, `ARCHER_CLIMB_TICKS` 8, `ARCHER_ARROW_RESERVE` 16, `WALL_WALK_MIN_LENGTH` 4.

### 3.4 Glue

- `Guards.onHired(level, villager, boolean archer)`. The 2-arg method calls the 3-arg with false, so every current caller stays melee. The 3-arg sets `withArcher` and equips a wooden sword only when `archer` is false and the hand is empty.
- `Workers.hireGuard(level, villager, building, boolean archer)` is the only new hire entry. It is the `GUARD` path plus the flag. Night refusal, slot choice and profession stay `JobType.GUARD`.
- New payload `HireGuardStylePayload(int containerId, UUID villager, Optional<BlockPos> building, int style)` id `heart_hire_guard_style`. `style` 0 melee, 1 archer. Any other int is `Rejection.BAD_STYLE` (appended) from `HeartAction.HireGuardStyle.malformed`, same slot as the other malformed checks. Permission `HIRE`. The old `AssignJobPayload` is unchanged and still hires a sword guard.
- `HeartScreen`: when the selected row's choices include `guard` and the viewer may hire, two buttons, "Guard" and "Archer", both using that choice's free-slot count and tier lock. "Archer" sends style 1. The Shift button is unchanged.
- `VillagerRow` gains a trailing `boolean archer` (append in `HeartCodecs.VILLAGER_ROW`). The row label is "Archer" when set, and the job id on the wire stays `guard`. `ActivityKind` appends `POSTED`, `NEEDS_BOW`, `NEEDS_ARROWS`.
- `GuardGear.read` for an archer also returns a bow as `GearSlot.WEAPON` with `attackDamage` 0. `GearPlanner.plan` for an archer uses `ArcherGear` for the weapon slot and `GearPlanner` for armour. A sword in the chest is not taken. `GearItem.usable` is not weakened for swords.
- `GuardCombat.tick` for an archer delegates to `ArcherCombat` and does not melee. `ArcherCombat` lives in `worker/guard`. It reuses `GuardTargets`, the retreat rule (step back to the ally, then shoot; do not walk into the zombie), and the kill listener.
- Ammo: `ArcherAmmo.take(server, guard)` removes one plain arrow from the Barracks chests, else `Stockpiles.withdrawFirst` for `Items.ARROW`. Empty → no arrow entity, status `NEEDS_ARROWS`, retry after 200 ticks (the ration miss delay). No bow → `NEEDS_BOW`, and no arrow is taken.
- Climb and post: `GuardRuntime` for an archer calls `ArcherPosting` instead of `patrol`, unless the alarm is up (then the standoff gather). Melee `patrol` is the same method body as today.
- Client: `EntityRenderers.register` a small `GuardArrowRenderer` that uses the arrow texture the way `TippableArrowRenderer` does. `TippableArrowRenderer` is typed to `Arrow`, so a subclass needs its own registration. `VillageHeartClient.onInitializeClient` is the place.

### 3.5 Numbers (all in `Balance`, tunable)

| Name | Value | Why |
|---|---|---|
| `ARCHER_BASE_DAMAGE` | 2.0 | Vanilla arrow base. With power 1.6 a hit is about 4, close to a wooden sword, before the level perk. |
| `ARCHER_POWER` | 1.6 | `CrossbowItem.MOB_ARROW_POWER`. |
| `ARCHER_SHOT_TICKS` | 30 | Slower than the melee cooldown of 20. A level-1 archer needs about 5 hits for a 20 HP zombie. |
| `ARCHER_RANGE` | 24 | Same as `GUARD_SIGHT`. |
| `ARCHER_ARROW_RESERVE` | 16 | Per archer, the smith's later target. Not a free stack. |
| `WALL_WALK_MIN_LENGTH` | 4 | One stand cell. |

### 3.6 Not changed

Melee target choice, the wooden-sword duel, shift counts, wound thresholds, ration maths, gate opening, lamp and tower ground patrol points, haul tables, and the `defended` / `defended_walled` assertion text.

## 4. Step-by-step build order

Each step ends green on `scripts/verify.sh --fast`. Commit after each step.

1. **Archer bit and demand.** `GuardData.archer`, codec, `ArcherDemand`, `ArcherGear`, `Balance` archer constants. L1: T-L1-1, T-L1-2. Sword `GearTest` stays green.
2. **Posts, pure.** `ArcherPosts`, tower stand cells, wall-walk text and `standCells`, `DefenceSite.length` with the 5-arg constructor. L1: T-L1-3, T-L1-4, T-L1-5.
3. **Hire flag.** `Guards.onHired(..., archer)`, `Workers.hireGuard`, night refusal still applies, no wooden sword for an archer. L2: T-L2-1. Existing `noGuardIsHiredAtNight` stays green.
4. **Shot rules.** `GuardArrow` and `ArcherCombat` aim and filter, no ammo yet (tests can grant a bow and an arrow in hand). L2: T-L2-2, T-L2-3, T-L2-4. `guardBeatsOneZombie` stays a sword guard.
5. **Ammo and bow gear.** Barracks then stockpile, one plain arrow, bow wear, hand-in. L2: T-L2-5, T-L2-6.
6. **Checkpoint, then the wall walk in the builder.** Diff `DefenceSite` against A3. Wire `wall_walk` through `DefenceBlueprints.line`, `Blueprints.all`, `BuildJobs.finish` (length), `DefenceSpecs` keys (plank at local `(0,2,1)` and fence at `(0,3,0)`). L1: T-L1-6. L2: T-L2-7. `watchtowerTownAndPatrolPoints` stays green.
7. **Standing the post.** Climb and wall-walk assign. L2: T-L2-8, T-L2-9.
8. **Alarm, training, rations.** Confirm the existing phase rules with an archer in the test, and the standoff gather. L2: T-L2-10, T-L2-11. Full `verify.sh` if this step touches a client class; otherwise `--fast` is enough.
9. **Screen.** Payload, buttons, row label, activities, lang, `LangCoverageTest`. L1: T-L1-7. L2: T-L2-12.
10. **Smith contract only.** `ArcherDemand` is already tested. Document it in DESIGN §5.6 and do not add a crafter (A1). If a smith planner is on the branch at this step, wire `missing` and add the L1 row to that planner's test; otherwise write the hook in DESIGN and stop.
11. **Client shot and docs.** L4 T-L4-1 (look at the screenshot). L3 file T-L1-8. DESIGN §5.6 "Rules as built (P1)", §5.8 wall walk, §11 (bow guards are no longer "out" once this ships), ROADMAP P1 line stays unticked until a verifier says so, PROGRESS entry. Do not edit `docs/LOOP_STATE.md`. Full `verify.sh`.

## 5. Test plan

### 5.1 Layer 1

| Id | Class | What | Fails when |
|---|---|---|---|
| T-L1-1 | `GuardDataTest` (new) or `world/GuardCodecsTest` | Round trip with `archer` true. JSON without `archer` loads false. `wounded` / `training_until` / `ration` still round-trip. | An old guard becomes an archer, or the flag is dropped. |
| T-L1-2 | `ArcherDemandTest` | 2 archers, 1 bow, 10 arrows → missing (1, 22) when the reserve is 16. 0 archers → (0, 0). Usable bows above the count do not go negative. A bow under 10% durability is not usable (the test passes the count already filtered). | The smith would craft forever, or craft nothing while a post is empty. |
| T-L1-3 | `ArcherGearTest` | Two bows: the fuller one wins. A sword is not a candidate. A bow at 9% is refused, at 10% is kept. | An archer equips a sword, or a broken bow. |
| T-L1-4 | `ArcherPostsTest` | 3 archers, 1 tower (8 cells) and no walk: the first 3 hiring ids get the first 3 platform cells, the rest of the cells stay empty. Adding a 4th archer fills the 4th cell. Order is stable if the input list is reversed and then sorted by the hiring index the test passes. 2 archers and 0 posts → both unassigned. | Posts shuffle every refresh, or two archers share a cell. |
| T-L1-5 | `DefenceSpecsTest` (extend) | The tower's eight stand cells match 3.2 #5 in local space, and `(2,6,2)` is absent. Ground patrol candidates are still the four layer-0 cells. A wall walk of length 8 has stand cells at local x = 1 and x = 5, y = 3, z = 1. Length 3 is rejected. | The archer is sent to the ladder hole, or melee patrol moves. |
| T-L1-6 | `BlueprintTest` / the defence blueprint test that already parses walls | `wall_walk` length 8: 32 cobble, 16 planks, 8 fences, 2 air stand cells. Rotation 90 still has a 2-deep cross-section. It is in `Blueprints.all()` at Village tier. | The table is missing, or the material list is short. |
| T-L1-7 | `HeartCodecsTest` | `HireGuardStylePayload` round trip. Style 99 decodes and `malformed()` is `BAD_STYLE`. `VillagerRow` round trip with `archer` true. Worst-case snapshot still under 512 KiB. | A bad int throws, or the new bool shifts every later field. |
| T-L1-8 | `ScenarioFilesTest` plus `ArcherScenarioTest` | `defended.json` and `defended_walled.json` assertions are unchanged (`deaths.mobs` ≤ 1, kills ≥ 15). New `defended_archers.json` parses: 1 archer hire, a bow and 32 arrows in the barracks chest, one creeper in `night_spawn` is not present (the scenario does not spawn a creeper as a target). Assertions are only `sum(work.guard.arrows) >= 1` and `sum(deaths.cause.arrow) == 0` over 5 days. It is not in the v1.0 gate list. | `defended` was edited, or the new file does not parse. |

`GuardTargetsTest` gains two rows: a `CREEPER` candidate is never chosen in `DUTY` or `OFF`, even when it hurt the guard; an `UNREACHABLE` candidate is never chosen. That pins the owner rule in the shared chooser the archer uses.

### 5.2 Layer 2 (`ArcherGameTests`, helpers beside `GuardTestSupport`)

Same yard rules as `GuardGameTests`: own environment per test, `VillageTestSupport.done` last. Hire through `hireGuard(..., true)` with the test night-hire flag when the clock is at night, and clear training the way `hireGuard` already can, except T-L2-11 which must stay a trainee.

| Id | Test | Setup | Assertions (and how it fails if broken) |
|---|---|---|---|
| T-L2-1 | `archerHireUsesAGuardSlot` | One Barracks, one grindstone, one bed. Hire an archer by day. | The worker's job id is `guard`, `GuardData.archer` is true, the hand is empty (no wooden sword), a second hire (sword or archer) is `NO_FREE_SLOT`. A sword hire on a fresh barracks still gets a wooden sword. A hire at day time 18000 is `GUARD_AT_NIGHT` for both buttons. Fails: a second slot appeared, or an archer starts armed. |
| T-L2-2 | `archerKillsAZombieAndIgnoresACreeper` | Qualified archer, bow in hand, 16 arrows in the Barracks chest, zombie 10 blocks away in the claim with clear air, a creeper in the same line 4 blocks from the archer. Day time so the archer is on duty. | Within 80 ticks the zombie has lost health or is dead, `work.guard.arrows >= 1`, the creeper is alive, unignited, and at full health. The chest has 15 arrows. The archer never has a melee cooldown swing (`doHurtTarget` is not required; assert the creeper and, if the zombie died, `work.guard.kills` + 1 and XP + 5). Fails: the creeper pops, or no arrow is spent. |
| T-L2-3 | `arrowPassesThroughAVillager` | A villager stands on the line between the archer and the zombie. | The villager's health stays at its max. The zombie is still hit within 100 ticks. A player (survival, on the line) stays at full health. An iron golem on the line stays at full health. Fails: friendly fire, or the arrow stops on the villager and the zombie lives. |
| T-L2-4 | `noShotWithoutABowOrAnArrow` | Archer, no bow. Then a bow and an empty stockpile and an empty barracks chest. | No `GuardArrow` entity appears in 60 ticks. Activity is `NEEDS_BOW`, then `NEEDS_ARROWS`. Arrow count in the world stays 0. Fails: a shot is free. |
| T-L2-5 | `arrowComesFromTheChestThenStorage` | 1 arrow in the Barracks chest, 1 in Storage. | The first shot takes the chest arrow (Storage still 1). The second takes Storage (0). A tipped arrow left in the chest is still there after both shots. Fails: the tipped stack is eaten, or Storage is used first. |
| T-L2-6 | `bowIsHandedBackOffShift` | Archer with a bow at 100 damage, pinned to the day shift, time moved to the off window (12000). | Within 400 ticks the bow is in the Barracks chest, the hand is empty, and the stored bow's damage is at least 100. A second archer does not take it while the first is still on duty (claim map). Fails: the bow is deleted or stuck in the hand. |
| T-L2-7 | `wallWalkBuilds` | Village tier, materials for length 8, a builder, the same line flow as `DefenceGameTests.wallTwentyBuiltComplete`. | The walk finishes, `DefenceSite.length` is 8, the two stand cells are air with planks below, and a Hamlet (set tier back before a second take) cannot start one. Fails: the site has length 0, so posts land in the pier. |
| T-L2-8 | `archerStandsOnTheTower` | Town, a finished watchtower (the `watchtowerTownAndPatrolPoints` build), one qualified archer, a bow. | Within 400 ticks the archer's feet are on one of the eight platform cells. The ground patrol point is still the one melee uses: a sword guard hired beside them still patrols to that ground cell (`guardPatrolsLampPost` style), not the platform. Fails: the archer is stuck at the door, or melee was moved upstairs. |
| T-L2-9 | `archerStandsOnTheWallWalk` | The length-8 walk from T-L2-7, two archers. | Each stands on a different stand cell (x = 1 and x = 5 in local space) within 400 ticks, and stays for 100 ticks (they are not walking the Heart loop). Fails: both share a cell, or they wander off. |
| T-L2-10 | `alarmPullsTheArcherOffThePost` | Archer on the walk, a sword guard asleep, alarm raised at a spot 20 blocks away (the existing alarm test's raise). | The archer leaves the stand cell and stops about 8 blocks from the spot (between 6 and 12) within 400 ticks, and shoots a zombie placed there. The sword guard still wakes (`alarmWakesSleepingGuardsAndTheyReturn` must stay green unmodified). When the alarm is cleared, the archer is back on a stand cell within 400 ticks. Fails: the archer ignores the alarm, or it runs into melee. |
| T-L2-11 | `traineeArcherDoesNotShoot` | Hire by day, leave `trainingUntil` set, a bow, arrows, a zombie 8 blocks away. | No arrow is taken in 200 ticks. Status is `TRAINING`. After `trainingUntil` and a night clock with the test hire already done, a shot happens. Fails: a new archer fights on the morning it was hired. |
| T-L2-12 | `screenHiresAnArcher` | Steward sends `HireGuardStylePayload` style 1, then style 99. | Style 1: archer flag set, job id `guard`. Style 99: `BAD_STYLE`, state unchanged. A Visitor's packet is `NO_PERMISSION`. The sword `AssignJobPayload` still hires a non-archer. Fails: the old hire button became an archer, or a visitor can hire. |

### 5.3 Layer 4

**T-L4-1 `ArcherClientTests.archerOnTheWalk`** (from `VillageHeartClientTests`): a short wall walk, one archer with a bow, a zombie in a glass pen on the far side of the rail, noon, peaceful natural spawns off. Screenshot `archer_on_wall_walk.png` while the archer is on the stand cell and an arrow is in the air or the zombie has just been hit. Look at it: the archer reads as a villager with a bow, standing on the planks, fence rail on the outer side, not inside the cobble. A second screenshot `heart_archer_row.png` of the Villagers tab with the row labelled Archer and the two hire buttons visible on a fresh unemployed villager. Look at that too: "Guard" and "Archer" both fit, and the Shift control is still there for the hired row.

### 5.4 Layer 3

- Do not edit `defended.json` or `defended_walled.json`. T-L1-8 locks their bars.
- Add `sim/scenarios/defended_archers.json`: the `defended` actions, plus one archer (a new action field `style: archer` on `hire`, default sword, so old hires stay sword guards), a bow and 32 arrows in the barracks `chest_items`, 5 days, scripted zombies as in `defended` but only days 1–3. Assertions: `sum(work.guard.arrows) >= 1`, `sum(deaths.cause.arrow) == 0`. No `deaths.mobs` bar. The file is parsed in `--fast`. The 5-day `scripts/simulate.sh defended_archers 5` is run once by hand and the JSON path is written in PROGRESS. It is not a v1.0 gate scenario.
- `deaths.cause.arrow` needs the existing death collector to keep the damage-type id. If a guard arrow's damage type id is not `arrow`, pin the real id in the scenario and in PROGRESS. Do not loosen `defended`.

### 5.5 Acceptance mapping

| Want | Tests |
|---|---|
| Hire from the Barracks, one slot | T-L2-1, T-L2-12 |
| Bow and arrows from the chest and the stockpile | T-L2-5, T-L2-6, T-L1-2 |
| Smith later, not now | T-L1-2, step 10 |
| Posts on a watchtower and a wall walk | T-L1-4, T-L1-5, T-L2-7, T-L2-8, T-L2-9, T-L4-1 |
| Ammo is spent | T-L2-2, T-L2-4, T-L2-5 |
| Friendly fire | T-L2-3 |
| Creepers ignored | T-L1 targets row, T-L2-2 |
| Shifts, alarm, training, rations | T-L2-1, T-L2-6, T-L2-10, T-L2-11, and the existing ration tests unchanged |
| Small village with no archer | existing `GuardGameTests` unmodified |

## 6. Risks, out of scope, open questions

1. **(Top risk) The climb.** `teleportTo` up a ladder can desync the client for a tick and can put the villager in the shaft if the ladder was replaced by the player mid-climb. If the cell at the next step is not a ladder and is not the platform, stop and mark the post unreachable for `PATROL_SKIP_TICKS`, then patrol on the ground. T-L2-8 is the check.
2. **Arrows and chunk unload.** A `GuardArrow` in an unloaded chunk is a vanilla arrow save. Pickup is `DISALLOWED`, so it cannot become an item. On load, if the owner is missing, `canHitEntity` stays false for everyone except the stored UUID, which will not be present. Acceptable.
3. **Line of sight through friends** makes a shot through a crowd. The aimed UUID is the only victim, so the shot cannot chain. A block still stops it.
4. **Wall-walk length on the site.** Forgetting `params().length()` in `finish` stores 0 and T-L2-7 fails. That is the point of the test.
5. **Snapshot size.** One bool per villager row. The M24 note said the worst case was near 512 KiB. T-L1-7 measures it. If it crosses the cap, drop something else only with a written reason; do not drop the bool's test.
6. **`defended_archers` is not balance.** It proves arrows fly and do not kill villagers. It does not promise the village survives. M26 owns that.

**Out of scope:** crossbows, tipped arrows, Power/Infinity/Punch, shooting phantoms, a free starting bow, an Archery building, role pins and squads (P2), creeper hunting, melee backup when the quiver is empty, smith crafting (the demand record is the whole of that), changing `defended` bars, visible armour, and shields.

**Open questions for the owner:**

1. Hire from the Barracks (this plan) or a separate Archery building with its own beds?
2. Should a new archer start with an empty hand (this plan, hard start) or with a free bow and a stack of arrows?
3. Plain arrows only, or any arrow in `ItemTags.ARROWS`?
4. Should archers keep ignoring phantoms and blazes, the way sword guards do?
5. On an alarm, should the archer leave the tower (this plan) or shoot only from the post?
6. Is the Wall Walk blueprint the right "wall walk", or do you want them on top of the existing 3-high wall?
7. Should P1 wait for the smith entirely, or should the Barracks grindstone slowly craft a bow the way M19a crafts tools?

## 7. Deviations (builder)

