verdict: PASS

# Backlog hardening batch 1, round 1 (verifier)

Range reviewed: `ac22a81..8eb650a` on `backlog-1` (e53cea5, 8eb650a). Merging `origin/dev` into it was a no-op (already up to date).

The eight item groups are all present. Every new test I mutated went red with the expected message, and so did the second-sweeper branch of the
own-environment guard. Nothing was weakened in a way that loses coverage the old test had on purpose. Three Layer 2 runs, 0 flakes.

## Fix list

None.

## Notes (not blocking)

1. **`VillageTickerPerfTest` is close to vacuous.** It times `VillageSystems.tickAll(null)` with an empty registry, which is one `isEmpty()`
   check. The measured time was 0.0008 ms against a 0.5 ms limit, so it has about 600x headroom and no realistic regression can turn it red.
   The removed GameTest assert (`after.meanMs() < 0.5`) also implicitly bounded the mod's 4 real systems with a live claim.
   The *intent* of that assert ("an unregistered system costs nothing") is now checked more strongly and deterministically by the spin counter
   (`spins[0] == spinsAtClose`), and the real per-tick village cost is measured by the Layer 3 soak (`SimulationRun`, `VillageTicker.TIMER`).
   So this is not a weakened test, but the perf test adds little. Suggestion: time `VillageTicker`'s idle tick with the real systems and a claim
   somewhere (L3 already reports it), or say in the perf test's javadoc that it only guards the empty-registry fast path.
2. **marker_items (looked at):** the inventory still sits on a dark, grey-blue backdrop. Next to `marker_wall` (same session, bright blue noon sky),
   the uniform darkening looks like vanilla's screen dim over a noon world, not night, as the builder says. The new `time set noon` / `weather clear`
   lines are harmless, but they make no visible change. All 18 item icons are clear and readable.
3. `assertOwnEnvironment` reads the private `GameTestHelper.testInfo` field by reflection. If a Minecraft update renames it, the guard fails
   loudly (assertionException) rather than passing silently, which is the right direction. It is fragile on version bumps, though.
4. BACKLOG "From M4-r1..r3": the `verify.sh --fast` header-comment item is still `[ ]`, but this batch rewrote that comment. Tick it.
   The full-run header line still doesn't mention perfTest.
5. Still untested, as the builder disclosed: the M24 demotion double-chest close branch (`compoundIsProtected`). The `destroyBlock` assert in the
   save/reload release has no separate mutation.

## Evidence

### Full `scripts/verify.sh` on 8eb650a: exit 0
All steps green. L2: "All 130 required tests passed". The zero-test gate printed "130 GameTests ran", and its self-test passed. L4 has 13 screenshots,
with 0 lost-wakeup rescues. perfTest passed (`village ticker idle tick: best batch mean 7.9E-4 ms`).

### Layer 2 flake runs (unmutated)
| Run | Result |
|---|---|
| 1 (inside verify.sh) | All 130 required tests passed |
| 2 | All 130 required tests passed |
| 3 | All 130 required tests passed |

### Mutations (all restored, `git status` clean afterwards)
Run A, four mutations at once (each aimed at a different test):
| Mutation | Test that went red | Message |
|---|---|---|
| `ContainerProtection.isProtected`: partner branch `return false` | `doubleChestOuterHalfIsProtectedThroughItsPartner` | "the outer half is outside the box but its partner is inside: refused" |
| `HeartActions.handle`: `sameEpoch = menu != null` (epoch ignored) | `oldMenuAfterReplaceIsStaleInTheSameTick` | expected `rejected.stale`, was `rejected.no_permission` |
| `Stockpiles.snapshot`: pass `0` for `unknownSites` | `readableFullPlusUnknownStorageIsFullWithOneUnknown` | "Expected both unknown to be 2: was 0" |
| `HeartActions.handle`: drop `&& m.nearHeart(player)` | `actionFromOutOfReachRejectedInTheSameTick` | "player out of reach: not ok" |

Only these 4 failed ("4 required tests failed"). The zero-test gate exited 0 on this failing log, so it doesn't stack a second failure on a real one.

Run B:
| Mutation | Test that went red | Message |
|---|---|---|
| `actionFromOutOfReachRejectedInTheSameTick` moved into `heart_stale_one_tick` (shared with the STALE test; both sweep through `begin`) | the second sweeper | "sweepStrayMarkers: environment villageheart-gametest:heart_stale_one_tick is shared by ..._old_menu_after_replace_... and ..._action_from_out_of_reach_...". This is the untested branch of the guard; it works. |
| `VillageClaims`: drop the "released" `sendSystemMessage` | `claimRefusalAndReleaseMessages` | "alice got exactly one notice: []" |

### Zero-test gate: false-fail check
- A normal passing log has "All 130 required tests passed", so the gate passes.
- A failing log has "N required tests failed", so the gate exits 0. The failure is already reported by the runGameTest step.
- Only a log with neither line fails twice (a crash before the summary). In that case the run already failed.
- `verify.sh` always runs the full suite with no filter, so 0 can't happen in a normal run.

### Item checks
- Item 2: the GameTest's wall-clock `< 0.5` is replaced by a deterministic run counter. The 0.5 ms bound is in `VillageTickerPerfTest` (tag `perf`, run by
  `perfTest` in verify.sh, and in `--fast`). See note 1.
- Item 4: every `messages.get(n)` in BuildingGameTests now has a non-empty or size assert before it. The guard is called first thing in `sweepStrayMarkers`.
- Items 6/7: the DESIGN "not stopped" list names projectile/decorated pot, sneak-placed button on dispenser/dropper/crafter, and lectern books, item frames and
  armor stands. The TESTING `snapshotCostReal` line is corrected. The partial-shrink step withdraws 10 from a part-filled stack, empties no slot, and expects comparator 8 -> 7.
- Item 8: the piston test now asserts stone at z=4 and the old wall spot vacated.
