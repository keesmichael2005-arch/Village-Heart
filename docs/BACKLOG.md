# Backlog (non-blocking review notes)

Planners: scan this list. Pick up an item when it touches your milestone or is cheap, and tick it
when it's fixed. Source reviews: docs/reviews/.

## From M1-r3
- [ ] No test for the `already_claimed_other` message or the "released" chat notice
- [x] Tidy imports in VillageHeartGameTests (duplicate ModBlocks import)
- [ ] plans/M1.md §3.4 table says evaluate() runs the stale heal; the code (correctly) doesn't
- [ ] The save/reload test doesn't assert the destroyBlock return value during release
- [ ] An absent owner's Heart can only be removed by an op (/setblock). Ownership transfer is planned for M24

## From M2-r1
- [ ] **Concurrent sims kill each other:** `kill_group` runs a machine-wide `pkill -f villageheart.sim.request=`. Scope it to this run's PID/process group (matters before running sims while verify runs)
- [ ] The report says `clean_shutdown: true` after a real crash; mark the crash in the report itself
- [ ] No stub case where phase 2 reports a wrong `reloads` (that mutation survives)
- [ ] T-L2-2 doesn't test the spherical radius (a villager in a box corner outside the sphere)
- [ ] The `SIM_DIR` override only works with the stub
- [ ] `world.save_bytes` lags the autosave (fine for the soak, not for per-day trends)
