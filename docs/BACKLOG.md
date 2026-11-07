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
- [ ] An absent owner's Heart can only be removed by an op (/setblock). Ownership transfer is planned for M24

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
