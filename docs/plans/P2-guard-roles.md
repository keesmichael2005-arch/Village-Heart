# P2 plan: Guard roles for a large village

Planner: Cursor CLI (grok-4.7-xhigh), 2026-11-11. Builder: implement in the step order of section 4. Every step ends green on
`scripts/verify.sh --fast`. Steps 8 and 10 also need the full `scripts/verify.sh` (a new block texture is an L4 shot). No Gradle and no
Minecraft were run for this plan. Code was read in this worktree (`plans-postv1`, from `origin/dev`). APIs marked **[verified]** were read
in the Loom 1.21.11 Mojang-mapped sources.

This is a **post-v1** plan. It is not part of the v1.0 gate. It comes from the backlog note "Post-v1 idea (human, 2026-11-10): guard roles
for big castle villages". v1 keeps every on-duty guard on one shared loop. Every **Proposal** below is the planner's default.

P2 does not require P1. If P1 has already landed, step 2 reads `GuardData.archer` and prefers archers for tower and wall posts. If it has
not, every guard is treated as melee and the preference is skipped.

## 1. Goal

A village with a handful of guards works exactly as it does today: they share the Heart / door / lamp / tower loop, and an alarm brings all
of them. A large village (8 or more guards on the shift, or any guard the Steward has pinned) can man the gates, stand on watchtower
platforms and on posts the player marked, walk a route the player laid out, and leave the rest as inside rovers. An alarm for a hurt
villager sends **one squad**, led by its most senior guard. A raid, and a guard's death, still call every guard who is not wounded and not
in training.

Nothing has to be configured for the small village. `defended` (2 guards) keeps today's patrol and today's full alarm.

## 2. Current state (what P2 builds on)

| Where | What is there |
|---|---|
| `core/guard/PatrolRoute` | One loop. `start(pointCount, index, count)` is `index * points / count`. `next` skips a point marked unreachable for `PATROL_SKIP_TICKS` (1200). |
| `worker/guard/PatrolPoints` | Rebuilt every 200 ticks. Order: Heart, a door point 3 cells outside each valid room (`doorPoint`), then each `Source`. `Defences.patrolPoints` is registered from `Guards.register`. |
| `defence/Defences.patrolPoints` | One walkable cell per recorded lamp and watchtower, via `core.defence.Defences.patrolPoint`. |
| `core/defence/DefenceSpecs` | Lamp candidates around the post. Tower candidates at layer 0: `(2,0), (2,4), (0,2), (4,2)`. Walls and gates return empty. Javadoc: "Walls and gates have none." |
| `core/defence/DefenceSite` | `(id, blueprintId, ground, rotation, builtAt)`. No length. A gate's opening depends on length (`o = (n - 3) / 2` in `DefenceBlueprints.gateText`), so a site without length cannot name the opening. |
| `core/blueprint/DefenceBlueprints` | `gate` is a LINE, min `Balance.GATE_MIN_LENGTH` (5), max 32. Three fence gates centred, air above, cobble lintel. |
| `core/blueprint/BlueprintParams` | `length`, `NONE` is 0. `BuildJob.params()` holds it. `BuildJobs.finish` records a site only when `Defences.recorded`, and the call is the 5-arg `DefenceSite`. |
| `core/blueprint/Blueprints.resolve` | LINE ids go through `DefenceBlueprints.line`. |
| `worker/guard/GuardRuntime.patrol` | If `GuardAlarm.spot` is more than 6 blocks away, walk there and skip the loop. Else walk `PatrolPoints`. `startIndex` counts guards on the same shift. |
| `worker/guard/GuardAlarm` | Static. `raise` on a hostile hurting a villager in the claim, and on a guard's death. `refresh` every 20 ticks while a hostile that hunts a villager is within 24 of a guard. Holds `GUARD_ALARM_HOLD_TICKS` (600). Not saved. Cleared on server start and stop. |
| `core/guard/GuardSchedule.phase` | The 7-arg form. A trainee who is not wounded is `OFF`. Wounded sleeps. Else `raid \|\| alarm` forces `DUTY`. The glue passes one boolean `GuardAlarm.active(level)` for every guard. |
| `worker/guard/RaidCheck` | `setSource` replaces the raid answer. M18's `raidCallsUpAllGuards` expects every guard awake, then `REST` within 200 ticks of `raid.stop()`. |
| `core/guard/ShiftBalancer` | `dayCount = n/4`. Pins (`GuardShift.pinned`) never move. 1–3 guards: all night. |
| `core/VillageState` | 17 components, last `guardShifts`. `guardIds()` is every `jobId == "guard"`. |
| `world/VillageCodecs` | `RAW_B` has 7 fields, last `guard_shifts`. Room for more (the group cap is 16). M25's plan wants two more keys; M19's plan wants two. Append, do not insert. |
| `core/heart/Permission` | `SET_GUARD_SHIFT` is Steward. `PermissionsTest` is a literal table: a new permission with no row fails the test. |
| `core/heart/HeartAction.SetGuardShift` | Choice 0 auto, 1 day, 2 night. |
| `client/.../HeartScreen` | Five tabs (`TAB_KEYS`). The Villagers row already has a Shift button. A sixth tab would shrink the tab strip again (M24 already noted the width). |
| `item/ModItems` | `BLANK_MARKER` and `BLUEPRINT`. Building markers are blocks in `MarkerBlocks`, one per `BuildingType`. A patrol point must not be a `BuildingType` or `BuildingSystem` will try to validate it as a room. |
| `worker/guard/GuardSystem` | Re-ranks shifts every 20 ticks. |
| L2 that must stay green | `GuardGameTests.guardPatrolsLampPost`, `alarmWakesSleepingGuardsAndTheyReturn`, `daytimeAlarmWakesTheSleepingNightGuardsAndTheyGoBackToBed`, `raidCallsUpAllGuards`, `guardDeathWakesTheOffDutyGuards`, `threeGuardsAllWorkNights`, `setGuardShiftPacket`. `DefenceGameTests.watchtowerTownAndPatrolPoints`, `gateOpens`. |
| L3 | `defended.json` hires 2 guards. `ScenarioFilesTest` parses every `sim/scenarios/*.json`. |

`GuardData` on this branch has no role and no archer flag. P1's plan adds `archer`. This plan adds role fields beside it if P1 is already there, or adds them alone if it is not.

## 3. Design

### 3.1 Assumptions

| # | Assumption | Step | If wrong |
|---|---|---|---|
| A1 | `DefenceSite` has no `length`. P1 may add optional `length` default 0, with a 5-arg constructor kept. | 4 | If `length` exists, use it. If M25 added a different field for the same number, use that field and say so in section 8. Do not store the length twice. |
| A2 | Gates are not recorded. `BuildJobs.finish` is the one place a finished build becomes a `DefenceSite`. | 4 | If M25 already records gates, do not add a second site. Read the one it wrote. |
| A3 | P1 may be absent. `GuardData` may or may not have `archer()`. | 2 | Call it only when the method exists. No preference when it does not. |
| A4 | `RAW_B` has 7 fields. | 5 | Append `guard_roles` and `patrol_routes` after the last field. A bad list entry is dropped (the lenient list codec `VillageCodecs` already uses). One bad point never drops the village. |
| A5 | `PermissionsTest` lists every `Permission` in a hand-written table. | 7 | Add the new row in that table in the same commit as the enum value. |

### 3.2 Proposals

1. **Defaults are today's guards.** `RoleAssigner` leaves every guard as `ROVER` when two things are both true: the shift has fewer than `Balance.ROLE_AUTO_AT` (8) guards, and nobody on that shift has a pinned role. Rovers walk the current `PatrolPoints` list, staggered by `PatrolRoute.start`. The alarm boolean stays true for every non-wounded, non-trainee guard. `defended` has 2 guards and no pins, so its behaviour is the code path that already shipped.
2. **Roles.** `enum GuardRole { ROVER, GATE, POST, ROUTE }`. `ROVER` is the inside walker (Heart and doors, and the full loop when proposal 1 applies). `GATE` stands at a gate. `POST` stands on a watchtower platform cell or a player post. `ROUTE` walks one player route.
3. **When auto-fill turns on.** A shift of 8 or more, or any pin on that shift. Fill order, unpinned guards only, hiring order, one guard per seat:
   - Gate seats, one per recorded gate.
   - Tower seats, the platform cells (the same eight local cells P1 names: layer 6 `_`, not the ladder hole). P2 computes them even if P1 is absent.
   - Player posts (sneak-placed markers).
   - Player routes, one guard per route that has at least 2 points.
   - Everyone left is `ROVER`.
   If P1's archer flag is present, archers are sorted to the front of the POST seats and melee guards to the front of the GATE seats. A pinned guard keeps its role and occupies one seat of that kind (the first free seat). Two pins on one gate: the second stands 2 blocks further inside, same facing. A pin to `ROUTE` with no route falls back to `ROVER` and the screen says so.
4. **Gate seat.** Record a finished `gate` as a `DefenceSite` with `length`. The stand cell is the inside of the opening, 2 blocks in from the centre fence gate, so the guard is not in the doorway (the same reason door points sit 3 cells out). "Inside" is the side opposite the blueprint's front. The cell must pass `Defences.walkable`. If it does not, the gate has no seat and the guard is not stuck in the lintel. The guard faces the gate and does not walk the loop. Workers still open the gate themselves (`GateTraffic`); the guard does not have to be the doorman. A hostile in `GUARD_SIGHT` is fought with the existing `GuardCombat` (or `ArcherCombat` if this guard is an archer).
5. **Tower and wall seats.** Tower: platform cells, reached by the climb P1 describes if P1 is present; if P1 is absent, melee guards use the ground patrol cell they use today and the platform cells are still the POST seats, with the same climb (this plan includes the climb, because a post you cannot reach is not a post). Wall: there is no catwalk in M21. This plan does not add the Wall Walk blueprint (that is P1). A wall seat is a **player post**: a patrol marker sneak-placed on a walkable cell. The player builds the walk, the marker says "stand here".
6. **Routes.** A new block `patrol_marker`, not a `BuildingType`. Recipe: compass above a stick (shaped, cheap, not the blank-marker ring). Use on the top face of a block: the point is the cell above if `Defences.walkable`, else the use fails with a chat line. Each use appends to the open route. Sneak-use places a **post** (one cell, role `POST`) and does not append. Sneak-use on an existing marker starts a new route (the old route is closed). Caps: `Balance.MAX_PATROL_ROUTES` 4, `Balance.MAX_ROUTE_POINTS` 24. Breaking the block removes that point or post (a break listener, plus a 200-tick check in loaded chunks, the same idea as `DefenceSystem`). A point in an unloaded chunk stays in the save until the chunk loads and the block is gone. Holding the item shows the route as dust, the way a blueprint shows its footprint.
7. **Rovers once anyone is posted.** If the shift has at least one `GATE`, `POST` or `ROUTE` guard, rover loops drop the defence `Source` points (lamps and towers) and keep the Heart and the door points. If nobody is posted, the loop is the full list, byte for byte the current order. That is what keeps `guardPatrolsLampPost` green: two guards, a lamp, no pins, under the auto threshold.
8. **Squads.** `core.guard.Squads.split(guardsOnThisShiftInHiringOrder)`. Below `Balance.SQUAD_MIN_SHIFT` (6), one squad of everyone, and the alarm calls all of them. At 6 or more: squads of `Balance.SQUAD_SIZE` (4). A remainder of 1 joins the previous squad (5 and 3, not a squad of 1). 6 → 4+2, 8 → 4+4, 20 → five squads of 4. The leader is the most senior member (level, then XP, then hiring order), the same tie-break idea as `ShiftBalancer`. Squads are derived every time, not saved.
9. **The alarm sends one squad.** Only the "a hostile hurt a villager" raise. `GuardAlarm` remembers which squad index it called. The next raise while that call is still out (or within the 600-tick hold) takes the next squad, round robin. `Guards.phaseOf` passes `alarm = true` only for members of the called squad. Everyone else keeps their post or route. A guard's **death** still raises the alarm for every squad (the backlog's "if a guard dies, all off-duty guards wake" stays). A **raid** still uses the `raid` flag for everyone, and `raidCallsUpAllGuards` stays green. Wounded guards and trainees are unchanged: they do not get up.
10. **Screen.** No new tab. On the Villagers row, next to Shift, a Duty button cycles `Auto / Rover / Gate / Post / Route`. Auto clears the pin. The label shows the effective role after assign ("Gate", "Rover", "Route 2"). Route cycles the route number when the button is already on Route and more than one route exists (the choice int encodes it). Permission: new `SET_GUARD_ROLE` (Steward), appended on `Permission`. The Shift packet is not reused, so a bad client cannot change a role through the shift int.
11. **Small village, written as a test.** 2 guards, a finished gate, a finished tower, no pins: both are rovers, both still receive the alarm, the gate has no guard standing in it. 8 guards and a gate: one unpinned guard is assigned to the gate without the player doing anything. That second case is the castle default. The player can still pin everyone back to Rover.

### 3.3 Core

- `core.guard.GuardRole` as in 3.2 #2.
- `record RolePin(GuardRole role, int routeId)` with `routeId` 0 when the role is not `ROUTE`. `role == null` is not used; absence of a pin means Auto.
- `RoleAssigner.assign(List<GuardSeat> guards, List<Seat> seats, int autoAt)` pure. `GuardSeat` is `(UUID id, int level, int xp, int hireIndex, Optional<RolePin> pin, boolean archer)`. `Seat` is `(SeatKind kind, int id, Pos stand)`. Output: `Map<UUID, Assignment>` with the role, the seat id or empty, and the route id or empty. Guards below `autoAt` with no pins map every id to `ROVER` and ignore seats.
- `Squads.split` as in 3.2 #8. `SquadCall.pick(int squadCount, int previousIndex)` is the round robin. `previousIndex` −1 means "none yet".
- `core.defence.GateSeat.stand(DefenceSite, length)` returns the inside cell in local space, or empty when `length < 5`. Tested without a world.
- `core.defence.TowerSeats.platform(DefenceSite)` returns the eight cells. Ground patrol candidates stay in `DefenceSpecs` untouched.
- Saved on the village, both lists, both optional, both lenient:
  - `guard_roles`: `{ "id": uuid, "role": "gate"|"post"|"route"|"rover", "route": int }`. Only pins. Auto is the absence of a row. Unknown role string dropped.
  - `patrol_routes`: `{ "id": int, "points": [ heart locations ], "closed": bool }` and `patrol_posts`: `{ "id": int, "at": heart location }`. Caps applied on load, extras dropped, one WARN.
- `VillageState` gains the three lists as one new component `GuardOrders` (a record of pins, routes, posts) so the component count grows by one, not three. `RAW_B` gains one key `guard_orders`. A missing key is empty orders.

### 3.4 Glue

- `BuildJobs.finish` writes `length` onto the `DefenceSite` (see A1) and records `gate` as well as lamp and tower. `DefenceSpecs.of("gate")` gains a key: the centre fence gate. `DefenceSystem` already drops a site whose key block is gone; a gate whose centre gate block was broken loses its seat.
- `PatrolMarkerBlock` in `block/`, item in `ModItems`, registered the way `MarkerBlocks` registers a block but **not** added to `BuildingType`. Break and place go through the server (block use / break events), Steward or Owner (`Permissions.allows` of `SET_GUARD_ROLE`). A Visitor's place is cancelled and the item stays in hand.
- `GuardRuntime.patrol` asks `RoleAssigner` (cached 20 ticks, same cadence as the shift re-rank). `ROVER` uses `PatrolPoints` with defence sources omitted when proposal 7 says so. `GATE` and `POST` walk to the stand cell and dwell. `ROUTE` uses a `PatrolRoute` over that route's points only. Alarm gather replaces the walk only for guards whose squad was called.
- `Guards.phaseOf` computes the alarm boolean per guard. `GuardSchedule.phase` is not given a new signature.
- Heart: `SetGuardRolePayload(containerId, UUID, int choice)`. Choice 0 Auto, 1 Rover, 2 Gate, 3 Post, 4+ Route `(choice - 4)` is the route id. `malformed` when the int is negative or the route id is not one the village has (`BAD_ROLE`, appended on `Rejection`). `HeartActions` re-reads the villager and refuses a non-guard.
- Climb: `ArcherPosting`-shaped helper `PostClimb` in `worker/guard`, used for a tower seat. One block per 8 ticks, `Entity.teleportTo`, stop if the next cell is not a ladder and not the platform. If P1 already added this, call that class instead of copying it (say which in section 8).

### 3.5 Numbers

| Name | Value |
|---|---|
| `ROLE_AUTO_AT` | 8 guards on that shift |
| `SQUAD_MIN_SHIFT` | 6 |
| `SQUAD_SIZE` | 4 |
| `MAX_PATROL_ROUTES` | 4 |
| `MAX_ROUTE_POINTS` | 24 |
| `MAX_PLAYER_POSTS` | 32 |
| Gate stand | 2 blocks inside the centre gate |

20 guards on one shift is an L1 case (five squads, seats filled, the rest rovers). The sim uses 8, not 20.

### 3.6 Not changed

`ShiftBalancer` maths, training, rations, gear, melee targeting, creepers, the raid flag, the death wake for **all** squads, lamp ground patrol points, and the text of `defended.json` / `defended_walled.json`.

## 4. Step-by-step build order

Each step ends green on `scripts/verify.sh --fast`.

1. **Assigner and squads.** `GuardRole`, `RoleAssigner`, `Squads`, `SquadCall`, `Balance` constants. L1: T-L1-1, T-L1-2, T-L1-3.
2. **Checkpoint for P1.** If `GuardData.archer` exists, thread it into `GuardSeat`. If not, the archer bit is always false. L1: T-L1-4.
3. **Seats, pure.** `GateSeat`, `TowerSeats`. L1: T-L1-5. `DefenceSpecs` ground candidates unchanged (`DefencesTest` stays green).
4. **Record gates.** `DefenceSite.length` if A1 says it is missing, `DefenceSpecs` gate key, `BuildJobs.finish` passes length and records gates. L1: T-L1-6. L2: T-L2-1. `gateOpens` and `watchtowerTownAndPatrolPoints` stay green.
5. **Save.** `GuardOrders`, codec key `guard_orders`, repair. L1: T-L1-7.
6. **Runtime for the default and the filled shift.** `GuardRuntime` branch, alarm squad filter, death calls every squad, raid unchanged. L2: T-L2-2, T-L2-3, T-L2-4, T-L2-5. The four existing alarm/raid/patrol tests stay green with no assertion edits.
7. **Marker block and permission.** Block, recipe, place/break, dust. `Permission.SET_GUARD_ROLE` and the `PermissionsTest` row. L1: T-L1-8. L2: T-L2-6, T-L2-7.
8. **Screen.** Payload, Duty button, lang. L2: T-L2-8. L4: T-L4-1. Full `verify.sh`.
9. **Climb.** Tower post is actually reached. L2: T-L2-9.
10. **Docs and the castle file.** DESIGN §5.6 roles, ROADMAP line left unticked, PROGRESS. L1: T-L1-9. Do not edit `docs/LOOP_STATE.md`. Full `verify.sh`.

## 5. Test plan

### 5.1 Layer 1

| Id | Class | What | Fails when |
|---|---|---|---|
| T-L1-1 | `RoleAssignerTest` | 2 guards, a gate seat, no pins, `autoAt` 8 → both `ROVER`, no seat. 8 guards, one gate, one tower (8 cells), no pins → 1 gate, 7 tower posts, 0 left over (8 seats and 8 guards: the gate is filled first, then 7 of 8 tower cells). 8 guards, no seats → 8 rovers. A pin to `ROVER` on a shift of 8 with a gate: that guard is a rover, another takes the gate. Two pins to the same gate: both `GATE`, different stand offsets. | A 2-guard village mans the gate, or a pin is ignored. |
| T-L1-2 | `RoleAssignerTest` | 20 guards, 2 gates, 1 tower (8), 2 routes → 2 gate, 8 post, 2 route, 8 rover. Hiring order is stable across two calls. With the archer flag on the first 4, those 4 are among the 8 posts, not on the gates, when there are enough posts. | The 20-stack collapses onto one loop, or archers take the gates first. |
| T-L1-3 | `SquadsTest` | 2 → 1 squad of 2. 5 → 1 squad of 5. 6 → 4+2. 8 → 4+4. 20 → five of 4. Leader of a squad is the higher level, then higher XP, then the earlier hire. `SquadCall.pick(5, -1)` is 0, then 1, then 2, wrapping to 0. | A squad of 1, or the alarm index sticks. |
| T-L1-4 | `RoleAssignerTest` | The archer preference is off when the flag is false for everyone, even if the method exists. | Melee guards are shoved onto towers because the default flipped. |
| T-L1-5 | `GateSeatTest` | Length 5: the centre gate is the middle column, stand is 2 blocks to the inside. Length 0 or 4: empty. Tower platform has 8 cells and does not include `(2,6,2)`. The four layer-0 patrol candidates are still exactly `(2,0), (2,4), (0,2), (4,2)`. | The guard is told to stand in the gate opening, or melee patrol moved. |
| T-L1-6 | `VillageCodecsTest` or `DefencesTest` | A gate site round-trips `length` 11. An old JSON site with no `length` loads 0. `patrolPoint` for that old tower is unchanged. | A save fails to load, or every old tower loses its patrol point. |
| T-L1-7 | `VillageCodecsTest` | `guard_orders` round trip. A point with a bad UUID is dropped, the village remains, the other points remain. 5 routes load as 4. 40 points on one route load as 24. Missing key → empty. | One bad marker wipes the claim. |
| T-L1-8 | `PermissionsTest` | The new row: `SET_GUARD_ROLE` is Owner and Steward, not Resident or Visitor. The table still has a row for every `Permission.values()` entry. | A Resident can edit the route, or the test no longer covers the enum. |
| T-L1-9 | `CastleScenarioTest` | `castle.json` parses. `defended.json` still hires 2 guards and still asserts `deaths.mobs` ≤ 1 and kills ≥ 15. `castle` asserts `work.guard.alarm_squads == 1` on the day the script raises one alarm, and `work.guard.gate_samples >= 1`. It does not assert `deaths.mobs`. | `defended` changed, or the castle file is not parsed. |

### 5.2 Layer 2 (`RoleGameTests`)

| Id | Test | Setup | Assertions (and how it fails if broken) |
|---|---|---|---|
| T-L2-1 | `finishedGateIsASeat` | Build a length-5 gate the way `DefenceGameTests.gateOpens` does. | The site is recorded, `length` is 5, the centre block is a fence gate, `GateSeat.stand` is walkable and is not the gate block. Breaking the centre gate drops the site within 200 ticks. A sword guard's patrol point list does not gain the gate while only 1 guard exists. Fails: the gate was not saved, or the one guard left the loop to stand in it. |
| T-L2-2 | `twoGuardsStillShareTheLoop` | The `guardPatrolsLampPost` setup (lamp, 2 guards). | Both still arrive at the lamp's ground point over 2 loops. Raising the alarm wakes both (the existing alarm test's timing: within 200 ticks). No `guard_orders` in the save. Fails: roles turned on by the mere presence of a lamp. |
| T-L2-3 | `eightGuardsManTheGate` | 8 qualified guards, one gate, no pins, night. | Exactly one guard is within 2 blocks of the gate stand and still there 100 ticks later. The other 7 are not in the gate opening. `work.guard.duty_samples` increases. Fails: nobody stands the gate, or all 8 do. |
| T-L2-4 | `alarmSendsOneSquad` | 8 guards **pinned day**, clock at 18000, so all 8 are asleep (day shift sleeps `[13000, 24000)`). Pinning keeps the shift size at 8; unpinned, the balancer would put only 2 on days. One villager hurt by a zombie. | Within 40 ticks exactly 4 of the 8 are `DUTY`, and the other 4 are still asleep. `work.guard.alarm_squads` is 1. A second hurt 100 ticks later, while the hold is up, wakes 4 who were still asleep (`alarm_squads` 2) and the first 4 stay called. Fails: all 8 wake, which is today's behaviour and the bug this test exists to catch. |
| T-L2-5 | `deathAndRaidStillCallEveryone` | Same 8, then kill one guard. Separately, the `raidCallsUpAllGuards` setup with 2 guards must be left as it is. This test uses 8 and a raid via the existing `RaidCheck` test hook. | After the death, every living non-wounded non-trainee is `DUTY` within 40 ticks. After a raid starts, the same. After `raid.stop()`, they are back to `REST` or `OFF` within 200 ticks (the M18 bound). Fails: a death only wakes one squad, or a raid never ends. |
| T-L2-6 | `markerBuildsARoute` | Steward places 3 markers in order, then sneak-places a 4th as a post. A Visitor places one. | The save has one route of 3 and one post. The visitor's item is still in hand and the route is still 3. Breaking the middle marker leaves points 1 and 3, in that order. A 25th point is refused and the list stays 24. Fails: the route reshuffles, or a visitor can edit it. |
| T-L2-7 | `routeIsWalked` | 8 guards, one route of 3 points, no other seats. | One guard visits all 3 points in order within 600 ticks. A 2-guard village with the same route and no pins does not: both keep the old loop (assert they still reach the lamp if one is placed). Fails: the route hijacks a small village. |
| T-L2-8 | `dutyButtonPins` | Steward sends `SetGuardRolePayload` choice 1 (Rover) for one guard of 8, with a gate present. | That guard is not the one at the gate. Choice −1 is `BAD_ROLE`. A Resident's packet is `NO_PERMISSION`. Reload keeps the pin. Fails: the pin is client-only. |
| T-L2-9 | `towerPostIsReached` | Town, one watchtower, 8 guards. | One guard stands on a platform cell within 400 ticks. The other tower ground patrol point is still what a 2-guard control uses. Fails: the post is assigned but the guard never arrives, so the seat is fiction. |

### 5.3 Layer 4

**T-L4-1 `RoleClientTests.dutyAndMarkers`:** a gate, three patrol markers, the Heart screen on the Villagers tab with 3 guards (the test may spawn the extras server-side). Screenshot `heart_guard_duty.png`: the Duty button reads a real role word and does not overlap the Shift button. Screenshot `patrol_markers.png`: the three markers and the dust line, in daylight, labels readable. Look at both.

### 5.4 Layer 3

- Do not edit `defended.json` or `defended_walled.json`. T-L1-9 locks `defended`.
- Add `sim/scenarios/castle.json`, 3 days, not a v1.0 gate file. Setup: `defended`'s buildings, tier Town, a gate (a new sim action `gate` that places a length-5 gate the way `GuardActions.wallRing` places blocks, or reuses the builder if that is already how the sim places walls — prefer a small `GuardActions.gate` so the scenario does not depend on a 60-block build), 8 guard hires pinned night, one `night_spawn` of 1 zombie next to a villager on day 2 to raise the alarm. Assertions: `work.guard.alarm_squads == 1` on day 2, `work.guard.gate_samples >= 1` on day 2. No death bar. `gate_samples` is a counter incremented when a guard is within 2 blocks of a gate seat, at most once per guard per 100 ticks, the same sampling idea as `duty_samples`.
- The builder runs `scripts/simulate.sh castle 3` once and writes the result in PROGRESS. `--fast` only parses the file.
- `alarm_squads` must count squads called, not guards called. A bug that wakes all 8 would show 2 if someone counted squads of 4 wrongly; the L2 test is the one that counts guards. The sim number is a sanity check.

### 5.5 Acceptance mapping

| Want | Tests |
|---|---|
| Gate guards | T-L1-5, T-L2-1, T-L2-3, T-L1-9 |
| Wall / tower posts | T-L1-5, T-L2-6, T-L2-9 |
| Inside rovers | T-L1-1, T-L2-2, T-L2-7 |
| Player routes | T-L2-6, T-L2-7, T-L4-1 |
| One squad on the alarm | T-L1-3, T-L2-4 |
| Death and raid still call everyone | T-L2-5, existing `raidCallsUpAllGuards` |
| Small village, no setup | T-L1-1, T-L2-2, T-L2-7, `defended` unchanged |

## 6. Risks, out of scope, open questions

1. **(Top risk) Auto-fill at 8 changes a village the player liked as a blob of patrollers.** The pin-to-Rover button is the way back, and under 8 nothing changes. If 8 is too low, `ROLE_AUTO_AT` is the one constant to raise. T-L2-2 is the lock for the small case.
2. **Gate length.** A site saved with length 0 has no seat (T-L1-5). A gate finished before this field existed will not be manned until it is rebuilt. Accepted: there are no shipped gates in old worlds that this mod has to migrate, because gates were not recorded at all.
3. **M25 records the same gates** for wall HP. Step 4's checkpoint is mandatory. Two sites for one gate would double the seats.
4. **Squads and the 600-tick hold.** A second slap during the hold must not call a third squad in the same tick as the refresh. `pick` advances only in `raise`, not in `refresh`.
5. **Climb**, same risk as P1: a broken ladder aborts the post for `PATROL_SKIP_TICKS` and that guard becomes a rover for that window rather than standing in the shaft.
6. **Duty button width.** Three buttons (job actions, Shift, Duty) on the Villagers action row. T-L4-1 is there so a clipped label is caught. If it clips, shorten the label to "Duty" plus the choice, do not add a tab.

**Out of scope:** the Wall Walk blueprint (P1), archery (P1, except the preference hook), editing a route on the Heart screen, more than 4 routes, squads that follow the leader as a formation, a per-gate "only this guard" picker beyond the pin, changing shift maths, and any change to `defended`'s bars.

**Open questions for the owner:**

1. Is 8 guards on a shift the right moment to start manning posts without being asked?
2. Should a hurt villager call one squad (this plan), while a death and a raid still call everyone?
3. Are squads of 4, starting at 6 guards, the right size?
4. Is a placed marker the route editor, with only the Duty button on the Heart screen, or do you want the route drawn on the screen and no marker item?
5. Is 2 blocks inside the gate the right stand, or should the guard stand in the opening?

## 7. Deviations (builder)

