# Backlog (non-blocking review notes)

## Priority (flaky infrastructure)
- [ ] **M16 stray-item flake clue (M15-r1):** in `minerHaulsFortyCobblestone`, the stray item was a STONE PICKAXE at age 0 at tick 11007 (just after the WORK window ends at 11000). Likely a tool returned or dropped at day end (CampTools lease release / dropPending?) instead of going back into the Mine chest. Root-cause it
- [ ] **M9 `fellResumesAfterInterrupt` still flakes** (1 of 11 runs in M20-r3: "a log of the second oak fell on tick 4002" plus a "cannot reach the tree" line, same signature as M9-r1). Root-cause it (WalkTo / give-up path); not M20-caused
- [ ] **M9 `workerOpensDoors` slow outliers** (456 vs 400 once; 301 once): add walk-time logging and root-cause it
- [x] **L4 client flake:** `AssertionError: Network synchronizer in invalid state` from Fabric's client-gametest synchronizer under load, followed by a server crash. Seen in the M3 builder's first full verify and in M4-r2 verify run 2; a rerun passed both times. Investigate (world-load waits? running two client tests in one JVM?) and fix or add a robust retry with logging
  - **Fixed (branch l4-flake).** Root cause is in Fabric, not our tests: a lost-wakeup race in `NetworkSynchronizer.waitForPacketHandlers` (predicate tested outside the lock, then `await(10 s)` with no re-test). The server thread's first tick of a new world misses the signal for the client's login packet and sleeps 10 s, which the synchronizer reports as invalid. Fixed by a gametest-only mixin that re-tests before parking and parks in 20 ms slices (no retry). See TESTING.md "Layer 4 and Fabric's NetworkSynchronizer".

Planners: scan this list. Pick up an item when it touches your milestone or is cheap, and tick it
when it's fixed. Source reviews: docs/reviews/.

## From M1-r3
- [x] No test for the `already_claimed_other` message or the "released" chat notice
  - Fixed (backlog-1): `claimRefusalAndReleaseMessages` (own environment `claim_messages`); `RecordingPlayer` now also records action-bar messages.
- [x] Tidy imports in VillageHeartGameTests (duplicate ModBlocks import)
- [ ] plans/M1.md §3.4 table says evaluate() runs the stale heal; the code (correctly) doesn't
- [x] The save/reload test doesn't assert the destroyBlock return value during release
  - Fixed (backlog-1): the release step asserts `destroyBlock` returns true.
- [x] An absent owner's Heart can only be removed by an op (/setblock). Ownership transfer is planned for M24
  - Fixed in M24: `/villageheart owner <player>` (op) hands the village over while the owner is offline; the Owner can also transfer in the Ranks tab.

## From M2-r1
- [x] **Concurrent sims kill each other:** `kill_group` runs a machine-wide `pkill -f villageheart.sim.request=`. Scope it to this run's PID/process group (matters before running sims while verify runs). Fixed: kills are scoped to the run's process group plus a per-run `SIM_RUN_TOKEN` in the environment; each run also gets its own free server port (25565 collided)
- [x] The report says `clean_shutdown: true` after a real crash; mark the crash in the report itself
- [x] No stub case where phase 2 reports a wrong `reloads` (that mutation survives)
- [ ] T-L2-2 doesn't test the spherical radius (a villager in a box corner outside the sphere)
- [x] The `SIM_DIR` override only works with the stub (now `build.gradle` reads `SIM_DIR` from the environment)
- [ ] `world.save_bytes` lags the autosave (fine for the soak, not for per-day trends)

## From M3-r1/r2
- [x] The sticky-piston test should assert that the wall block actually moved
  - Fixed (backlog-1): asserts the wall block now stands at z=4 and its old spot is vacated.
- [ ] Art polish: the Farmhouse sheaf glyph and the small Chopping Block axe head
- [x] The marker_items screenshot background is night; set noon before the shot
  - Done (backlog-1): `time set noon` + `weather clear` right before the shot, looked at. Finding: the test already set noon at the start; the dark backdrop is vanilla's screen dimming of a noon sky (the marker_wall shot, same time, is bright), not night.

## From M4-r1..r3
- [x] `verify.sh` header comment says `--fast` runs "layers 1, 2 only"; it also runs L3 self-test, model-log gate, perfTest
- [ ] MarkerView forces marker cell open for cell() but not openToSky (safe only while MarkerBlock has no collision)
- [ ] Cold-cache first real scan ~7 ms (logged, not asserted)

## From simfix-r1/r2
- [ ] kill_tokened pid-reuse race (pidfd would close it); free-port check-then-use race (gives ERROR, never false PASS)
- [ ] TESTING.md: SIM_REPORT_DIR must also differ for same-checkout concurrent runs
- [ ] Escapee marker not scoped to SIM_DIR; rename `escapee_killed (exit 2)` ok-line to avoid confusion next to the real check

## From M5-r1
- [x] `outsideClaimAndNoClaim` reads messages.get(0) without a non-empty assert (BuildingGameTests ~L205/218/239): a broken report crashes the whole GameTest server
  - Fixed (backlog-1): every `messages.get(n)` in the Building GameTests is preceded by a non-empty/size assert.
- [x] GameTest filter quirk: a non-exact filter runs 0 tests and "passes". Fail when a filter is set and 0 tests ran
  - Fixed (backlog-1): `scripts/check-gametest-log.sh` (run by verify.sh, self-tested) fails on "All 0 required tests passed"; documented in TESTING.md.
- [x] sweepStrayMarkers is only safe while each sweeping test has its own environment: add an assertion or a TESTING.md rule
  - Fixed (backlog-1): TESTING.md rule plus `BuildingTestSupport.assertOwnEnvironment` (run-time guard inside `sweepStrayMarkers`).
- [ ] L4 marker_validation checks the chat only via the screenshot; add a client-side assertion
- [ ] Known leniencies (documented): Hunting Ground inside a house reads valid after a restart until re-scan; out-of-claim plaques stay red after release; late-loaded markers are adopted only on right-click

## From l4flake-r1
- [x] Possible 2nd synchronizer stall (seen once, only under a forced 30 ms delay): the Render thread waits on CLIENTBOUND right after "Stopping server" with inFlightPackets > 0 (a counter leak at disconnect, not a lost wakeup). If the AssertionError returns with that signature, investigate it
  - **Fixed (branch l4-flake-2).** It returned in full verifies (M20 builder: 2 of 3, at the end of `heartTabs`). Root cause, in Fabric: `Minecraft.disconnect` resets `CLIENTBOUND` once, right after the client closed its end of the in-memory channel, but the server's end closes later on its own Netty event loop while the integrated server keeps ticking; packets the server sends in that window are counted after the reset and can never be received, so the next client tick waits 10 s for them. Instrumented natural failure: 41 packets (20 `set_entity_data`, 20 `set_entity_motion` from the 20 killed villagers, 1 `disconnect`). Fixed by a gametest-only mixin (`DisconnectInFlightLeakFix`) that resets the count again once the integrated server has stopped, plus `heartTabs` waiting for the deaths to finish before it closes. No retry. See TESTING.md "Layer 4 and Fabric's NetworkSynchronizer".
- [ ] The wakeup wrapper doesn't check the `invalid` flag between slices (cosmetic; up to 10 s later failure)

## From M6-r1/r2
- [ ] Treat `unreadContainers > 0` like an unknown Storage when noting a short deposit (up to 10 s false FULL after a hidden chest loads)
- [x] "Readable full + unknown" alert case is tested at L1 only; a glue mutation passing 0 for unknownSites survives. Add an L2 test
  - Fixed (backlog-1): `readableFullPlusUnknownStorageIsFullWithOneUnknown` (restart-like scan loss, polls for the one-readable/one-unknown window).
- [ ] StockpilePerfTest has 4–10× headroom (a 3× slowdown stays green); tighten the limits or document StockpileCostTest as the regression guard
- [x] TESTING.md ~L139 still says snapshotCostReal asserts 3 ms (now print-only)
  - Fixed (backlog-1).
- [x] Pre-M6 `villageTickerTimesSystems` still has a wall-clock assert (`meanMs() < 0.5`) inside a GameTest; move it to perfTest
  - Fixed (backlog-1): the GameTest now counts runs (deterministic); the idle-tick wall-clock bound is `VillageTickerPerfTest` in perfTest.
- [x] The depositMarksChestChanged shrink step mostly empties slots; add a partial-shrink check that alone catches a missing changed() on withdraw
  - Fixed (backlog-1): a 10-item withdraw from a part-filled stack that empties no slot, comparator 8 -> 7. Mutation: dropping `touched.add` in `take` is now caught (survived before).
- [x] M8 alert text: one readable full Storage plus one unknown shows FULL by design; say so (done in M8: "... (1 Storage not checked yet)")

## From M7-r1/r2
- [ ] Deterministic regression tests for the deposit-trip guards: (a) erase the walk target mid-trip and assert it is re-pointed at Storage, not the job block; (b) only one of the WORK/go_home trips starts
- [ ] processPending "skip blocks the current village holds" branch has no test
- [ ] Tiny window: a pending release runs up to 20 ticks after the chunk loads; a break/re-place plus a vanilla claim in that window would free the new ticket
- [ ] Crash between the POI save and the saved-data save can leave a stale ticket (documented in TESTING.md)
- [x] Workers.levelOf/TRIPS use inline fully qualified names (cosmetic) (fixed in M15)
- [ ] T-L2-14 breeding retry windows hide a vanilla breeding abort (~1 in 16); root-cause later
- [ ] DepositTrip keeps all crops forever; M10 changes this to keep food only up to the breeding threshold

## Cross-milestone (from the M12 plan)
- [ ] **M11 `starter` scenario must seed the treasury** (`villageheart treasury add 32` or equivalent) once M12 lands, otherwise both workers strike from day 2 and "logs increase every day" fails. The M12 builder must re-run `starter` by hand. The M11 builder should leave a hook for it

## From M8-r1
- [x] No L2 test reaches the `STALE` rejection (the menu closes first). Add a one-tick test: break and re-place the Heart, send from the old menu, expect STALE
  - Fixed (backlog-1): `oldMenuAfterReplaceIsStaleInTheSameTick`.
- [x] The `nearHeart` check in HeartActions is untested: move the player away and send in the same tick
  - Fixed (backlog-1): `actionFromOutOfReachRejectedInTheSameTick`.
- [x] Villagers-tab need icons show M7 placeholder values (looks housed while Overview says beds 0). Re-check in M11
  - Done in M11: `WorkerData.needs` is now real (fed from the diet, housed from the House beds), so the icons follow it. Non-worker villagers still show no icons.
- [ ] An invalid Farmhouse with a worker reads "Slots 1/0" (confusing)

## From M24
- [ ] **Indirect container access by Visitors:** a hopper under or next to a protected container, copper golems, pistons, explosions, chest minecarts (v1 scope, documented in DESIGN section 2)
- [ ] **Creative pick-block with data copies a protected chest:** `PlayerPickItemEvents.BLOCK` exists in Fabric for a later fix
- [ ] Should breaking a registered marker be Steward-only (today Residents can, Visitors cannot)?
- [ ] No self step-down: an Owner can demote anyone but must transfer to stop being Owner
- [ ] M12 must append `DEPOSIT_TREASURY(RESIDENT)` and `WITHDRAW_TREASURY(OWNER)` to `Permission` and add its rows to `PermissionsTest`; then add the plan's T-L2-6 `onlyOwnerCanWithdraw`, and a deposit/withdraw step to `MultiplayerClientTests`
- [ ] `SimPlayer` could use `FakePlayer` (its own owner needs creative mode and a different position rule)
- [ ] Heart snapshot worst case is 481 KiB of 512 KiB: bound the next list before adding it
- [ ] Ranks tab: no "you" marker on the Owner's own row, no rename-collision hint when two players share a name

## From M24-r1
- [x] Add a dedicated GameTest for the double-chest partner check (a ready-to-copy test is in docs/reviews/M24-r1.md); the demotion double-chest close branch is also untested
  - Fixed (backlog-1): `doubleChestOuterHalfIsProtectedThroughItsPartner` (the demotion close branch `compoundIsProtected` is still untested).
- [x] Add to DESIGN's "not stopped" list: a Visitor's projectile breaking a protected decorated pot (vanilla onProjectileHit skips the break event); a button sneak-placed on a protected dispenser/dropper/crafter; lectern books, item frames, armor stands
  - Fixed (backlog-1): DESIGN section 2 now names them.
- [ ] Heart snapshot headroom is now 31 KiB (481/512 KiB); recheck the caps when adding fields
- [ ] MP_SESSION end is logged before the fake player leaves, so the gate misses shutdown errors

## From backlog1-r1
- [ ] VillageTickerPerfTest times only the empty-registry path (~600× headroom): time the real systems with a live claim, or document its scope
- [ ] assertOwnEnvironment reads the private GameTestHelper.testInfo by reflection; fix it on a Minecraft version bump

## From M9-r1/r2
- [ ] WalkTo clock restart after a gap in ticking has no test
- [ ] Path re-plan on vanilla path replacement has only a statistical guard; add a deterministic GameTest (open a door on the worker's path, assert the long path is planned again)
- [ ] assertNoGiveUpsAt comment and PROGRESS give the wrong reason (a regrown tree stands on the same base)
- [ ] Trees-before-drops: in a big forest early drops may despawn (collect window only from day time 5000 to despawn); plan T-L1-9 row still states the old order
- [ ] ToolKind Javadoc names the removed status.villageheart key; CANT_REACH_WALK_TARGET_SINCE not erased by DepositTrip

## From M20-r1 (builder)
- [ ] `workerOpensDoors` (M9) rarely fails with "reaching the job block through the closed door took 456 ticks, bound 400". Seen once in about 18 full Layer 2 runs on the M20 branch, never alone (6 of 6 pass) and never in the 17 other runs. Measured: it takes exactly 156 ticks every time otherwise, and the failure is 300 ticks later, which is three `GoToJobBlock` back-offs of 100 ticks (three consecutive failed paths out of the closed room). Not reproduced, so no root cause yet. Next step if it shows again: run the suite with `-Dvillageheart.lumber.trace=true` plus a log of `CANT_REACH_WALK_TARGET_SINCE` in `GoToJobBlock.start`.

## From M20-r2 (verifier)
- [ ] `SiteSupport` touched-cell exemption has no L2 test (a check-pass case with the build's own torch on a cell turned to dirt)
- [ ] Falling non-natural blocks (anvil, concrete powder) above a cleared cell fall into the box (not broken; untested)
- [ ] `workerOpensDoors` took 301 ticks once in 16 measured runs (156 otherwise): slowdown is not only 100-tick back-offs

## From l4flake2-r1
- [ ] Shared "kill villagers and wait until gone" helper for client tests; WorkerClientTests:122 and LumberClientTests:109/:174 kill villagers right before closing without waiting (the mixin covers it; this is cleanup)

## From M10-r1/r2
- [ ] No test isolates a COLLECT interrupted without a trip (the `before != COLLECT` restart path)
- [ ] clearStrayFarmland discards every item entity; javadoc and TESTING say crop items only
- [ ] workerOpensDoors doesn't log its walk time (near misses invisible); it flaked once at 456 vs 400 in the M20 builder's run
- [ ] The original storageFullStopsHarvest flake's cause is unconfirmed (not seen in ~40 runs since)

## From M11-r1
- [ ] T-L2-10(d) deferred: no L2 test for a villager 81 blocks (or at the box corner 84.9) from the Heart not eating; the census is `world.VillageCensus` over `ClaimGeometry` (L1-tested)
- [ ] T-L2-2 (`noFoodAllHungryNoDeaths`) only jumps dinner to dinner and never crosses dawn; M12's dawn wages need the plan's version (each round also crosses 23980)
- [ ] `sum(deaths.cause.minecraft:starve) == 0` in `starter` can never fail (villagers take no hunger damage); the L2 full-health check is the real guard
- [ ] `starter` workers sometimes die at night (run with 2 deaths, re-hired); the lumberjack's `no_work` on many days is the grove being felled on day 1 (saplings regrow slowly)
- [ ] The site ramp (`StarterActions.ramp`, `core/sim/RampProfile`) is 9 blocks wide plus up to 12 more while it still differs from the land by more than 1: a `max_deviation` above 8 leaves a larger step at the ring (the cone is floor +- k)
- [ ] `LeafEscape` is not a general unstick: it only frees a worker whose four sides at feet and head height are all blocked and at least one is natural leaves (M11 r2)

## From M16 (builder notes)
- [ ] A hidden miner's hitbox still catches the crosshair at the bottom of the shaft (placing or breaking rails there while a miner is down); fix with a `Villager.isPickable` mixin if players complain (the hold spot is the deepest cell, so the entrance and the marker stay free)
- [ ] The wooden pickaxe mines iron and lapis (vanilla's "wood cannot mine iron" is not modelled; it uses the stone table, only slower); redstone/lapis counts (4) are planner choices
- [ ] "Walk to the stonecutter first when more than 40 blocks from the shaft" (planner row 7) is tested at L1 only; the 32-wide yard cannot host a Storage 35 blocks from a shaft
- [ ] Lightning at an open vertical shaft can turn the hidden miner into a witch (vanilla, `Villager.thunderHit`); the conversion handler restores the witch's flags and M7 fires the record, the miner is lost
- [ ] Mod uninstalled while a miner is down leaves a hidden villager: `/data merge entity <uuid> {NoAI:0b,Invulnerable:0b,Silent:0b}` and `/effect clear` (Invisible is not saved, so it shows again by itself after a reload)
- [x] Heart snapshot crashed on any registered outdoor site (`BuildingRequirements.of(MINESHAFT)` in `HeartFacts`)
  - Fixed in M16: `DetailKind.NONE` for non-room buildings; covered by `minerStopsWhenShaftInvalid`.

## From M20-r1..r4
- [ ] checkPassLeavesWaterloggedPlayerBlocks stocks exactly one house of materials, so on old code it fails via "needs planks" rather than at the chest check; add a spare stack
- [ ] "Needs materials" fallback text has no test
- [ ] Falling non-natural blocks (anvil, concrete powder) above a cleared cell are untested
- [ ] BuildSystem.refresh returns early with no builds, so it never releases orphaned reservations (defensive)
- [ ] Known gap (documented): player-placed dirt/stone/pumpkins/melons outside rooms look natural and can be cleared

## From M16-r1
- [ ] A miner stuck hidden (no free cell to come up) has job status SITE_UNREACHABLE, but the Heart row shows "Away (mining)" (`HeartFacts` checks `isAway` first), not "Can't reach the Mineshaft"
- [ ] No committed test releases the Heart while a hidden miner is unloaded (the verifier's probe passed)
- [ ] `PriorFlags.invisible` records a potion's Invisible flag; if the effect ends underground the miner comes up invisible until the next effect change or reload (cosmetic)
- [ ] A hidden miner that dies (`/kill`, void) loses its pending haul (release drops it, death does not)
- [ ] The once-seen stray item in `minerHaulsFortyCobblestone` is still unexplained (0 of 8 in M16-r1); keep the diagnostic

## From M15-r1
- [ ] "needs a Town" never fires for a worker that reached level 2 through a tier-up with 300 XP banked (XP clamps at 300, so no threshold is crossed again; the gold bar shows it)
- [ ] `LevelUpFeedback.onCapped` lines beyond the 3-per-tick budget are dropped, not counted in the "and N more" summary
- [ ] `ironAxeOutFellsStoneSideBySide` clears its upper platforms only on success; a failed run leaves glass, dark oaks and villagers above its slot
- [ ] T-L2-11 starts the cure by reflection (`ZombieVillager.startConverting`); the public path (Weakness + golden apple via `mobInteract`) would survive a rename
- [ ] M16's stray item in `minerHaulsFortyCobblestone` seen again once (a stone_pickaxe at age 0, tick 11007) in an M15-r1 mutated run unrelated to mining

- [x] (M15 r1 fix) side-by-side cleanup in finally for assertion failures. [ ] Timeout cleanup and a public cure path for T-L2-11 are still open

## From M21
- [ ] **BLOCKING for M18 (guards): register the defence patrol points.** M18 step 6 must add `dev.villageheart.defence.Defences::patrolPoints` (a `List<BlockPos>` for the claimed village: finished Lamp Posts and Watchtowers, one walkable cell each) as a source of its `PatrolPoints.of(server)`. M21 could not do it (M18 not merged); until then guards do not patrol at defences. M21's T-L2-7 asserts the M21 side only
- [ ] Villagers cannot open fence gates (vanilla `InteractWithDoor` handles doors only): a wall ring with only gates cuts workers off; a later milestone could add gate opening to villager pathing
- [ ] Two builders working on two touching defence builds at once are untested (T-L2-9 runs three touching walls with the instant builder only)
- [ ] Walls are level at the start's height (no terrain following); a hilly village needs several segments. A `heights` list in `BlueprintParams` would allow a sloped wall
- [ ] The lamp test is time-warped (the day time is set per sample), not a real night of 11 000 ticks; the live spawner is not exercised (needs a player; random)
- [ ] The Watchtower platform centre cell (above the ladder hole) is ANY, not cleared: a natural block there (leaf) would stay in the rail


## From M17 (builder notes)
- [ ] **Before v1:** a hidden hunter in the open keeps an invisible hitbox that can catch a player's crosshair (placing or breaking blocks next to the ground marker while a hunter is out); fix with a `Villager.isPickable` mixin (client side, guessing from the synced flags). Raised from the miner note: the hunter hides where players walk, the miner at the bottom of a pit
- [ ] `DepositTrip` walks with `WalkTo` (range 48) and the Storage may be anywhere in the claim: a hunter that comes back 46 blocks out from a far Storage reports "Storage unreachable" and waits; give `DepositTrip` a walk-via-the-job-block step for all away jobs
- [ ] Infinity bows are not modelled (an arrow is taken anyway); the crossbow counts as a bow; spears, the mace and the trident are not hunter weapons
- [ ] The biome weights are read at the marker and 8 points 12 blocks away; a sample in an unloaded chunk is skipped and a partial result is not cached (the yield of a trip after a restart can differ from the one before it if a sample chunk is unloaded)
- [ ] "Hunting Ground inside a house reads valid after a restart until re-scan" (M5): a hunter can go out to such a ground in the first re-check cycle after a restart (one trip at most); still open
- [ ] `farHunterWalksViaLodge` forces two chunks outside the structure and unforces them at its end; a failed run leaves them forced (harmless, they are 10 blocks from the yard)
- [ ] A hunter that arrives with a weapon that is unreadable for a moment (chest scan missing) does not go out and walks back to its table; the next plan decides again (not tested)

## From M17-r1 (orchestrator, at merge)
- [x] Hunter awards no XP: M17 was built before M15 merged. Wire `WorkXp.award(v, GAME_HUNTED, 1)` per completed trip, with a GameTest (sent to the M17 builder)
- [ ] Hidden away villagers stay solid for every walker; consider making away villagers non-colliding (would replace the narrow touch rule)
- [ ] `MineTestSupport.clearHigh` spills chest contents into the world; emptying chests first would explain and close the M16 stray-pickaxe clue
- [ ] `touchesAwayVillager` also matches away miners (harmless today)

## From M26 plan (orchestrator)
- [ ] Village-logic tick timer double-counts nested sections (e.g. `BuildSystem.tick` inside `tickAll`) and misses work outside sections (`AwayTrips.processLoaded`, `LevelUpFeedback.flush`, event handlers). M26 step plans an outermost-only timer plus a JFR cross-check
- [ ] (M17-xp) `farHunterWalksViaLodge` timed out at tick 2602 of 2600 under heavy machine load (another agent's GameTests running at the same time); M9 `sixOaksTwoDays` replant flaked again in the same window. Check the margin when the machine is quiet

## From M21-r1
- [ ] No test with two builders on two touching defence builds (risk: slowness/stale stand ring, not safety)
- [ ] Watchtower rail centre cell is ANY: a natural block (e.g. leaf) left there can block climbing out of the ladder hole. Clear it or document it
- [ ] `walled_section` screenshot: the line action-bar text is still fading in (cosmetic)
