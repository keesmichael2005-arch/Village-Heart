verdict: PASS

# L4 flake fix review, round 1 (verifier)

Range reviewed: `origin/dev..origin/l4-flake` (one commit, 1fdbb3e "Fix L4 flake: lost wakeup in Fabric's client-gametest
NetworkSynchronizer"). It touches only the gametest source set (a new client mixin, its mixins json and the gametest
`fabric.mod.json`), `scripts/verify.sh` and docs. No test was changed or deleted.

## Fix list

None.

## Evidence

### 1. Root cause: confirmed in Fabric's source
I read `NetworkSynchronizer.java` from `fabric-client-gametest-api-v1-4.3.5+bfe9fcf43e-sources.jar` (Gradle cache). In
`waitForPacketHandlers` the inner loop is:

```java
while (inFlightPackets.get() > 0 && mainThreadPacketHandlers.isEmpty()) {   // tested WITHOUT the lock
    morePacketsLock.lock();
    try {
        if (!morePacketsCondition.await(10, TimeUnit.SECONDS)) { markInvalid(); checkInvalid(); }  // no re-test
    } ...
```

The wakers (`postNettyHandlePacket`: `decrementAndGet()` then `signalMorePackets()`; `preTaskAdded`: `add()` then
`signalMorePackets()`) change the state outside the lock and only take the lock to `signal()`. If the decrement and signal both
happen between the unlocked test and `lock()`, nobody is waiting yet, so the signal is lost, and `await` sleeps the full 10 s.
Then `markInvalid` logs "Detected interfacing with packets at a lower level" and `checkInvalid` throws the AssertionError. This
is a classic lost wakeup, as the builder described. The binary's line numbers put the `checkInvalid` call at
`NetworkSynchronizer.java:138`. That is the frame in every failure below and in the builder's notes. M4-r3's natural failures
were also "about 10 s after a client world finished loading", which fits the world-start explanation.

### 2. The fix is correct, minimal and test-only
- **Correct:** `@WrapOperation` replaces the `await` call, which runs while `morePacketsLock` is held. Because every waker
  takes that lock to signal, testing the predicate again under the lock closes the race by itself. If the state changed before
  the test, the test sees it and returns. If it changes after the test, the waker blocks on the lock until `await` releases it,
  so the signal arrives. The 20 ms slices are extra safety on top of that.
- **No busy-spin:** the thread parks on the real `Condition` with a 20 ms timeout, so it wakes at most about 50 times a second,
  and only while a packet is really outstanding (a few waits per run; see the forced-race logs).
- **10 s timeout kept:** there is one deadline per call, and when it passes the wrapper returns `false`, so Fabric's
  `markInvalid`/`checkInvalid` still runs. This was shown live: forced-race run "fix #2" below hit a genuinely stuck counter
  and still failed after exactly 10 s. The fix does not hide real stalls.
- **Test-only:** the class is in `src/gametest`, listed under `"client"` in `villageheart-gametest.mixins.json`, which only the
  gametest mod's `fabric.mod.json` references. `build/libs/village-heart-0.1.0.jar` has no `mixin`, `gametest` or `wakeup`
  entries and its `fabric.mod.json` declares no mixins. The compiled class exists only at
  `build/classes/java/gametest/.../NetworkSynchronizerLostWakeupFix.class`.
- `required: true` plus `defaultRequire: 1`: if Fabric changes the `await` call, the injector fails loudly instead of quietly
  doing nothing.

### 3. Red-is-red
**Forced race** (temporary verifier mixin `TmpVerifierForceRace`: `@Inject` before `Lock.lock()` in `waitForPacketHandlers`
that sleeps 30 ms, plus counter logging; removed afterwards):

| Variant | Runs | Result |
|---|---|---|
| Fix removed from the mixins json, sleep in | 2 | **2/2 FAIL**, exit 1: `AssertionError: Network synchronizer in invalid state` at `NetworkSynchronizer.java:138`, exactly 10 s after the first forced sleep (13:45:45 → 13:45:55), at world start |
| Fix + sleep | 6 | **5/6 pass**, with 2 to 6 `LOST_WAKEUP_RESCUED` per run on both the Server thread and the Render thread. The counter log shows e.g. `before: inFlight=1 queued=0` → `after: inFlight=0 queued=0` → rescued. **1/6 failed differently**: see note 1 |

**Install check:** with `NetworkSynchronizerLostWakeupFix` removed from the mixins json, full `scripts/verify.sh` exited 1 with
`❌ Layer 4: NetworkSynchronizer lost-wakeup fix installed` (Layer 4 itself passed that time, and the check still caught it).
Restored afterwards.

### 4. Under load
I ran 4 busy loops of my own on 4 cores (PIDs 3661 to 3664, killed by the script's trap and confirmed gone). Load average
during the runs was 5.8 to 15.3, and other agents were also running Minecraft. Each run was
`xvfb-run -a ./gradlew --no-daemon runClientGameTest` with the fix:

| Run | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 |
|---|---|---|---|---|---|---|---|---|
| exit | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |
| load (end) | 5.8 | 15.3 | 12.9 | 14.0 | 12.9 | 12.1 | 11.7 | 12.6 |
| rescues | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |

**8/8 green, 0 synchronizer errors.** I also had one unloaded baseline L4 run with the fix (green).

### 5. Full `scripts/verify.sh` on 1fdbb3e
Exit 0: L1 ✅, L2 46/46 ✅, L3 stub ✅, model-log self-test ✅, L4 ✅, fix-installed check ✅ (0 rescues), model-log gate ✅,
perf ✅ (2 831 / 2 199 µs). I looked at all 4 screenshots. `0000` shows the heart next to the bell. `0001` shows the "must be placed
near a village bell" message. `0002` shows the marker wall with all 16 plaques plus the chopping block, builder's table, market
stall and a sign-mounted marker. `0003` shows the inventory with the marker items. None show a crash or a missing texture.

## Non-blocking notes
1. **A second, separate stall appeared at disconnect (only under the forced-race mutation).** In forced run "fix #2" the
   Render thread waited on `CLIENTBOUND` right after "Stopping singleplayer server … All dimensions are saved". The fix's
   slices kept testing for the full 10 s, so `inFlightPackets` stayed above 0 the whole time: a clientbound packet was counted
   by `preSendPacket` but never reached `channelRead0`. That is not a lost wakeup. It is a counter leak while the connection
   closes, before `CLIENTBOUND.reset()` runs in `Minecraft.disconnect`. The fix rightly kept Fabric's 10 s failure. It happened
   1 of 6 times with an artificial 30 ms delay, and 0 times in 8 loaded runs, 1 baseline run and 2 full verifies. Worth a
   backlog line: if `invalid state` ever comes back with the Render thread waiting just after "Stopping server", it is this
   leak, not the lost wakeup.
2. **There were no natural rescues in my runs** (0 in 8 loaded runs and the full verify). So the load runs show no recurrence,
   but they did not exercise the rescue path naturally. The forced experiment did that. I did not run an unpatched baseline
   under my load. The builder reports 1/10 unpatched failures at similar load, so 8/8 is consistent with the fix but is not
   strong statistical proof by itself. The `lost wakeups rescued` line in the verify summary will build that evidence over time.
3. The wrapper's loop tests only the counters, not `invalid`. If another thread calls `markInvalid` while this one is between
   slices, its signal can be missed and this thread throws up to 10 s later instead of at once. It is the same failure, only
   later, and it was already possible in Fabric's code. Cosmetic.
4. TESTING.md says a changed `await` call "stops the client at start-up". Strictly, it fails when `NetworkSynchronizer` is
   first loaded, which is early in the client's start-up. Fine as written.
5. Layer 3 (dedicated server running the gametest source set) was not run (simulate.sh is off-limits). The mixin is
   `client`-only, and Layer 2 (a server environment with the same gametest mod and mixins json) ran green, so Layer 3 should
   behave the same.
