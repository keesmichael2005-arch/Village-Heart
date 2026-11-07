# Backlog (non-blocking review notes)

## Priority (flaky infrastructure)
- [x] **L4 client flake:** `AssertionError: Network synchronizer in invalid state` from Fabric's client-gametest synchronizer under load, followed by a server crash. Seen in the M3 builder's first full verify and in M4-r2 verify run 2; a rerun passed both times. Investigate (world-load waits? running two client tests in one JVM?) and fix or add a robust retry with logging
  - **Fixed (branch l4-flake).** Root cause is in Fabric, not our tests: a lost-wakeup race in `NetworkSynchronizer.waitForPacketHandlers` (predicate tested outside the lock, then `await(10 s)` with no re-test). The server thread's first tick of a new world misses the signal for the client's login packet and sleeps 10 s, which the synchronizer reports as invalid. Fixed by a gametest-only mixin that re-tests before parking and parks in 20 ms slices (no retry). See TESTING.md "Layer 4 and Fabric's NetworkSynchronizer".

Planners: scan this list. Pick up an item when it touches your milestone or is cheap, and tick it
when it's fixed. Source reviews: docs/reviews/.

## From M1-r3
- [ ] No test for the `already_claimed_other` message or the "released" chat notice
- [x] Tidy imports in VillageHeartGameTests (duplicate ModBlocks import)
- [ ] plans/M1.md §3.4 table says evaluate() runs the stale heal; the code (correctly) doesn't
- [ ] The save/reload test doesn't assert the destroyBlock return value during release
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
- [ ] The sticky-piston test should assert that the wall block actually moved
- [ ] Art polish: the Farmhouse sheaf glyph and the small Chopping Block axe head
- [ ] The marker_items screenshot background is night; set noon before the shot

## From M4-r1..r3
- [ ] `verify.sh` header comment says `--fast` runs "layers 1, 2 only"; it also runs L3 self-test, model-log gate, perfTest
- [ ] MarkerView forces marker cell open for cell() but not openToSky (safe only while MarkerBlock has no collision)
- [ ] Cold-cache first real scan ~7 ms (logged, not asserted)

## From simfix-r1/r2
- [ ] kill_tokened pid-reuse race (pidfd would close it); free-port check-then-use race (gives ERROR, never false PASS)
- [ ] TESTING.md: SIM_REPORT_DIR must also differ for same-checkout concurrent runs
- [ ] Escapee marker not scoped to SIM_DIR; rename `escapee_killed (exit 2)` ok-line to avoid confusion next to the real check

## From M5-r1
- [ ] `outsideClaimAndNoClaim` reads messages.get(0) without a non-empty assert (BuildingGameTests ~L205/218/239): a broken report crashes the whole GameTest server
- [ ] GameTest filter quirk: a non-exact filter runs 0 tests and "passes". Fail when a filter is set and 0 tests ran
- [ ] sweepStrayMarkers is only safe while each sweeping test has its own environment: add an assertion or a TESTING.md rule
- [ ] L4 marker_validation checks the chat only via the screenshot; add a client-side assertion
- [ ] Known leniencies (documented): Hunting Ground inside a house reads valid after a restart until re-scan; out-of-claim plaques stay red after release; late-loaded markers are adopted only on right-click

## From l4flake-r1
- [ ] Possible 2nd synchronizer stall (seen once, only under a forced 30 ms delay): the Render thread waits on CLIENTBOUND right after "Stopping server" with inFlightPackets > 0 (a counter leak at disconnect, not a lost wakeup). If the AssertionError returns with that signature, investigate it
- [ ] The wakeup wrapper doesn't check the `invalid` flag between slices (cosmetic; up to 10 s later failure)

## From M6-r1/r2
- [ ] Treat `unreadContainers > 0` like an unknown Storage when noting a short deposit (up to 10 s false FULL after a hidden chest loads)
- [ ] "Readable full + unknown" alert case is tested at L1 only; a glue mutation passing 0 for unknownSites survives. Add an L2 test
- [ ] StockpilePerfTest has 4–10× headroom (a 3× slowdown stays green); tighten the limits or document StockpileCostTest as the regression guard
- [ ] TESTING.md ~L139 still says snapshotCostReal asserts 3 ms (now print-only)
- [ ] Pre-M6 `villageTickerTimesSystems` still has a wall-clock assert (`meanMs() < 0.5`) inside a GameTest; move it to perfTest
- [ ] The depositMarksChestChanged shrink step mostly empties slots; add a partial-shrink check that alone catches a missing changed() on withdraw
- [x] M8 alert text: one readable full Storage plus one unknown shows FULL by design; say so (done in M8: "... (1 Storage not checked yet)")

## From M7-r1/r2
- [ ] Deterministic regression tests for the deposit-trip guards: (a) erase the walk target mid-trip and assert it is re-pointed at Storage, not the job block; (b) only one of the WORK/go_home trips starts
- [ ] processPending "skip blocks the current village holds" branch has no test
- [ ] Tiny window: a pending release runs up to 20 ticks after the chunk loads; a break/re-place plus a vanilla claim in that window would free the new ticket
- [ ] Crash between the POI save and the saved-data save can leave a stale ticket (documented in TESTING.md)
- [ ] Workers.levelOf/TRIPS use inline fully qualified names (cosmetic)
- [ ] T-L2-14 breeding retry windows hide a vanilla breeding abort (~1 in 16); root-cause later
- [ ] DepositTrip keeps all crops forever; M10 changes this to keep food only up to the breeding threshold

## Cross-milestone (from the M12 plan)
- [ ] **M11 `starter` scenario must seed the treasury** (`villageheart treasury add 32` or equivalent) once M12 lands, otherwise both workers strike from day 2 and "logs increase every day" fails. The M12 builder must re-run `starter` by hand. The M11 builder should leave a hook for it

## From M8-r1
- [ ] No L2 test reaches the `STALE` rejection (the menu closes first). Add a one-tick test: break and re-place the Heart, send from the old menu, expect STALE
- [ ] The `nearHeart` check in HeartActions is untested: move the player away and send in the same tick
- [ ] Villagers-tab need icons show M7 placeholder values (looks housed while Overview says beds 0). Re-check in M11
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
