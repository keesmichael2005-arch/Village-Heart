verdict: FAIL

# Sim isolation review, round 1 (verifier)

Range reviewed: `0552e81..ddd44bb` on `sim-fix` (c106419, 009abb2, fbb066e, ddd44bb). Backlog items: "From M2-r1".

The real behaviour holds up. Two real sims ran at the same time and both passed, and the stub self-test ran beside them without killing either.
The FAIL is about the tests. The stub self-test cannot catch the original bug, and it never exercises the kill path that real runs depend on.

## Fix list

1. **The isolation case cannot catch the bug it was written for** (`scripts/testdata/fake-sim.sh`, `scripts/test-simulate.sh`).
   No stub process has `villageheart.sim.request=` in its command line. A read-only probe of a running stub run listed
   `timeout`, `bash fake-sim.sh`, `python3 - <SIM_DIR> sleep`, `sh -c sleep 100 & wait` and `sleep 100`: zero matches for the old pattern.
   The real server JVM does carry it (`... KnotServer villageheart.sim.request=.../sim-request.json`). So if the old machine-wide
   `pkill -f villageheart.sim.request=` came back in `kill_group`, the self-test would stay green.
   Expected: the fake's long-lived process has `villageheart.sim.request=$SIM_DIR/sim-request.json` in its argv, as the real
   server does (for example as an extra python argument). Then restoring the old pkill turns `concurrent run A survived` red.
2. **`kill_tokened` does the real work but no stub case uses it** (`scripts/simulate.sh` kill_tokened, `scripts/test-simulate.sh`).
   In the real runs, Gradle's single-use daemon starts in its own session and process group, and the server JVM is its child:
   | pid | pgid / sid | what |
   |---|---|---|
   | 17412 | 17410 / 17410 | gradle wrapper client (the group `kill -- -$PGID` reaches) |
   | 17483 | 17483 / 17483 | GradleDaemon |
   | 17837 | 17483 / 17483 | KnotServer (the Minecraft server) |
   So on a timeout or stall, only the token scan reaches the daemon and the server. I mutated `kill_group` to skip `kill_tokened`
   (`: kill_tokened`), and `test-simulate.sh` still exited 0 with 31/31 ok. Every stub process stays inside the process group.
   Expected: a fake case whose child leaves the group (for example `setsid python3 ... "$SIM_DIR"` or `setsid sleep` with a
   run-scoped argv). After a timeout, the test asserts that child is gone. The `kill_tokened` no-op mutant must then go red.
3. **A leftover check was deleted and the commit doesn't say why** (`scripts/test-simulate.sh`, c106419).
   `|| pgrep -fx "sleep 100"` was dropped from the leftover-process check. The reason is sound: the pattern is machine-wide, so
   another self-test running at the same time makes it go red. But CLAUDE.md rule 4 asks for the reason in the commit, and no
   coverage replaced it: `pgrep -f "$T/sim"` cannot see the `sleep 100` grandchild.
   Expected: give the grandchild a run-scoped command line (for example `sleep 100` replaced by
   `python3 -c 'import time; time.sleep(100)' "$SIM_DIR"`) and keep the leftover check scoped to `$T`. Fix 1 and fix 2 change the
   same fake, so one change can cover all three items.

## Evidence

### Full `scripts/verify.sh` on this clean checkout (ddd44bb): exit 0
L1 ✅, L2 "All 38 required tests passed", L3 stub self-test ✅ (31 ok), model-log gate ✅, L4 ✅. Screenshots 0000 to 0003 were
produced. This change has no visual part, so no new screenshot is needed.

### Real concurrent runs
I ran `scripts/simulate.sh bare 1` from this worktree and from a temporary second worktree of `origin/sim-fix` at the same time.
The second run used `SIM_DIR=build/run/sim2`, so it also tested claim 4 for real. The three checkouts were compiled first
(`classes gametestClasses`), and that is the only warm-up I did. Once both servers had written a report, I ran
`scripts/test-simulate.sh` from a third worktree, in full.

| Run | Exit | Port | Report |
|---|---|---|---|
| worktree 1, default SIM_DIR | **0** | 58935 | PASS, clean_shutdown true, 1 day, no crash_reports |
| worktree 2, `SIM_DIR=build/run/sim2` | **0** | 33725 | PASS, clean_shutdown true, 1 day, no crash_reports |
| stub self-test, worktree 3 | **0** | - | 31/31 ok, finished while both real sims were still alive |

- Worktree 2 had only `build/run/sim2/` (with world, server.properties and request). It had no `build/run/sim/`. The server
  argv carried `villageheart.sim.request=.../build/run/sim2/sim-request.json`. So `SIM_DIR` reaches the real run, and the
  configuration cache does not block it.
- The two runs had different tokens (`vh-sim-17371-...` and `vh-sim-17372-...`). Each run's daemon and server carried only
  its own token.
- No process with any `SIM_RUN_TOKEN` was left after the runs.
- I removed both temporary worktrees afterwards.

### Can the /proc scan kill something outside this run?
No, apart from the pid-reuse race in note (a).
- The token is `vh-sim-<pid>-<ns time>`, which is unique per run. It uses only `[a-z0-9-]`, so it contains no regex metacharacters.
  The scan matches it with `grep -qx` against a whole `SIM_RUN_TOKEN=...` line, so a token that is a prefix of another cannot match.
- Only processes started by this script carry the token. It is exported after the script starts, so the caller and the
  script's own initial environ don't have it. A nested `simulate.sh` overwrites it with its own.
- The scan reads each environ only to compare it, and kills only on an exact match.

### Mutations (each restored with `git checkout scripts/simulate.sh`; the tree was clean afterwards)
| Mutation | Result |
|---|---|
| Old machine-wide `pkill -f villageheart.sim.request=` instead of `kill_tokened` | **Not run**: it would kill other agents' real sims, and the sandbox refused it. A read-only probe shows the pattern matches no stub process, so the self-test cannot go red for it (fix 1). |
| Machine-wide-style pkill scoped to my own self-test temp tree (`pkill -f <scratch>/mut/`, run with `TMPDIR=<scratch>/mut`) | **killed**: `FAIL concurrent run A was killed by another run`. The isolation case does work when the stub's argv matches the kill pattern. |
| `kill_tokened` replaced by a no-op | **survives**: 31/31 ok (fix 2) |
| `reloads` check in `decide()` disabled | **killed**: `FAIL reload_wrong: expected exit 2, got 0` |
| Crash: report not rewritten (`json.dump` → `pass`, exit 2 kept) | **killed**: `FAIL report does not record the crash` |

## Claims
| Claim | Holds? |
|---|---|
| (1) The kill is scoped to the process group plus the run token | Behaviour yes, in real runs and in the stub. The tests are weak (fixes 1 and 2). |
| (2) Each run gets a free server port | Yes (58935 and 33725) |
| (3) A crash is recorded in the report | Yes. The mutant is killed. |
| (4) `SIM_DIR` works for real runs | Yes (the `sim2` run) |
| (5) New stub cases reload_wrong, crash_report_recorded, isolation | reload_wrong and crash_report_recorded go red when mutated. Isolation goes red for a broad kill, but not for the literal old pkill (fix 1). |

## Non-blocking notes
a. `kill_tokened` reads a pid's environ and then kills it. If the process exits and its pid is reused between those two steps,
   an unrelated process could be killed. The window is microseconds. A pidfd (`pidfd_open` / `pidfd_send_signal`), or a re-check
   of the token right before the kill, would close it.
b. The free-port pick is check-then-use. The script binds 127.0.0.1:0, then closes it, and the server binds `*` later. If the race
   is lost, the result is a server start failure, so ERROR, never a false PASS.
c. The lock is per `SIM_DIR`, but `SIM_REPORT_DIR` is not locked. Two runs in one checkout with different `SIM_DIR` would share
   `reports/sim/<scenario>.json` and the Gradle project dir. TESTING.md only promises "different worktrees", which is correct.
   It could say that `SIM_REPORT_DIR` must differ too.
d. If `SIM_GRADLE_CMD` ever starts a persistent daemon (no `--no-daemon`), that daemon would carry the token and be killed at the
   end of the run, together with any other build that had connected to it. The default is `--no-daemon`, so this is fine today.
e. `query.port` has no effect while `enable-query` is false. The `[ "$pid" = "$$" ]` skip in `kill_tokened` is redundant,
   because the script's own initial environ has no token. Both are harmless.
