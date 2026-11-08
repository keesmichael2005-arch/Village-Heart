verdict: PASS

# L4 flake fix 2 (disconnect leak) review, round 1 (verifier)

Range reviewed: `ae28e7d..origin/l4-flake-2` (merge base with `origin/dev`; `origin/dev` merged in, already up to date). It touches
only the gametest source set (new client mixins `DisconnectInFlightLeakFix` + `NetworkSynchronizerAccessor`, their mixins json,
a quiesce in `HeartScreenClientTests.heartTabs`), `scripts/verify.sh` and docs. No test was weakened or deleted, and nothing retries.

## Fix list

None.

## Notes (not blocking)

1. The other three client tests that kill villagers just before their world closes do not quiesce the way `heartTabs` now does:
   `WorkerClientTests.java:122` (10 villagers), `LumberClientTests.java:109` and `:174`. TESTING.md says client tests "should also
   quiesce before closing". The mixin covers them (see the natural leak in load run 2 below), so this is hygiene, not a stall. A
   shared "kill villagers and wait" helper would make the rule easy to follow.
2. PROGRESS.md says the 10 loaded runs after the fix needed 0 leak clears. In my 6 loaded runs one did: load run 2 cleared 1
   packet at the close after `WorkerClientTests` killed 10 villagers. Without the mixin that run would have failed. The quiesce
   helps, but the mixin is what keeps the suite green.
3. A disconnect from a dedicated server is not covered (documented). The guard `singleplayerServer == null -> return` is right
   for it: there the server's end belongs to another process, so this leak path does not exist.

## Evidence

### 1. Root cause: confirmed in the sources
Read from the Gradle/Loom caches: `fabric-client-gametest-api-v1-4.3.5+bfe9fcf43e-sources.jar`, the Minecraft 1.21.11 Mojang-mapped
sources, and Netty 4.2.7 `LocalChannel` (decompiled with Vineflower).
- `ConnectionMixin.onSendPacket`: `@Inject(method = "sendPacket", at = @At("HEAD"))` does `CLIENTBOUND.preSendPacket()` (an
  increment) on the server-side Connection. `onNettyReceivePacket` wraps the client's `channelRead0` and decrements in `finally`.
- `MinecraftMixin.onDisconnectCancelTasks` is the only `CLIENTBOUND.reset()`, at `dropAllTasks` in `disconnect(Screen,ZZ)`, just
  before `clientPacketListener.close()`. That close calls `Connection.disconnect` -> `channel.close().awaitUninterruptibly()`.
- Netty `LocalChannel.doClose`: sets the client's state to CLOSED, releases its inbound buffer, and closes the peer (the server's end)
  through `peerEventLoop.execute(...)`, so later, on the server channel's event loop.
- Minecraft `Connection.send`: `if (isConnected())` (= `channel.isOpen()`) `-> sendPacket` (counted at HEAD). Then
  `LocalChannel.doWrite` sees `peer.state != CONNECTED` and fails the message with `ClosedChannelException`, so it is never read and
  never uncounted. Between the client close and the deferred server-side close, every clientbound send leaks one count.
- `disconnect` then loops `while (!integratedServer.isShutdown()) runTick(false)`. `DeltaTracker.Timer.advanceTime(l, false)`
  returns 0, so Fabric's `postRunTasksHook` does not wait inside that loop. The first real tick afterwards calls
  `waitForPacketHandlers` with `inFlightPackets > 0` and nothing that can arrive, waits 10 s, then `markInvalid` -> AssertionError.
  This matches the signature (stack `postRunTasksHook` <- `runTick` <- `Minecraft.run`, right after "All dimensions are saved").

### 2. The reset point is safe and cannot mask gameplay stalls
- `MinecraftServer.isShutdown()` is `!serverThread.isAlive()`. Every clientbound send of the integrated server (ticks,
  `stopServer`'s disconnect) runs on that thread and is counted at HEAD before it returns, so no count can be added after it.
  The deferred `doSendPacket` on the event loop does not count again.
- The client's channel was closed synchronously at the start of `disconnect` (state CLOSED, inbound buffer released), so no
  `channelRead0` can run afterwards to decrement below zero.
- The mixin acts only inside `disconnect(Screen,ZZ)`, only when that call closed a singleplayer server and that server's thread has
  ended, before `setScreenAndShow` and before the next tick. Fabric's own reset has already cleared everything counted before the
  close at that point. So the mixin only removes counts added between the client close and the server's death, which by
  construction can never arrive. During gameplay a lost packet still trips Fabric's 10 s. `defaultRequire: 1` means a missing
  `setScreenAndShow` target stops start-up instead of silently disabling the fix. There is exactly one such call in `disconnect`.

### 3. Test-only
`build/libs/village-heart-0.1.0.jar` (from the full verify): 0 entries matching
`gametest|DisconnectInFlight|NetworkSynchronizer|LostWakeup`. Its `fabric.mod.json` lists only `villageheart.mixins.json`.

### 4. Red-is-red (forced variant)
Temporary probe (not committed, removed, tree clean afterwards): a client mixin on `Connection.disconnect(DisconnectionDetails)`
that, when the client's LocalChannel closes, queues a 500 ms sleep on every open server-side LocalChannel event loop of the integrated
server before the client close queues the peer close.
- Probe, mixin removed from the json: **red**. It died at the first world close: "Stopping server ... All dimensions are saved"
  at 05:17:30, then `Detected interfacing with packets at a lower level` + `AssertionError: Network synchronizer in invalid state`
  at 05:17:40 (exactly 10 s), stack `postRunTasksHook`. BUILD FAILED.
- Probe + mixin: **green**, BUILD SUCCESSFUL. 10 probed closes, 7 leaks cleared (`DISCONNECT_LEAK_CLEARED`: 1, 1, 1, 1, 2, 4, 23).
- verify.sh gate: `grep -m1 "DISCONNECT_LEAK_FIX installed"` exits 1 on the no-mixin log and 0 on the with-mixin log, so the
  step fails when the mixin is not installed.

### 5. Under load
Three of my own `while :; do :; done` loops on 4 cores, plus other agents' work (load average 12.6 to 15.8). Busy loops killed
by PID afterwards.

| L4 run | result | leaks cleared | lost wakeups rescued | invalid state |
|---|---|---|---|---|
| 1 | green | 0 | 0 | 0 |
| 2 | green | 1 (after `WorkerClientTests`' kill of 10) | 0 | 0 |
| 3 | green (ran past the tool's 10 min foreground limit and finished in the background) | 0 | 0 | 0 |
| 4 | green | 0 | 0 | 0 |
| 5 | green | 0 | 1 | 0 |
| 6 | green | 0 | 0 | 0 |

Full `scripts/verify.sh` under the same load: exit 0. All 144 GameTests pass, both L4 mixin gates pass (0 rescues, 0 leaks that
run), MP log gate passes, perf passes.

### Screenshots
Looked at `0005_workers_at_job_blocks`, `0006_lumberjack_felling`, `0007_heart_overview`, `0008_heart_villagers`,
`0010_heart_buildings` and `0014_mp_ranks` from the full verify. All render as expected (villagers at their job blocks, the felling
scene, the Heart tabs with data, the 2-player ranks tab). The `heartTabs` change only runs after its screenshots, and none of them changed.
