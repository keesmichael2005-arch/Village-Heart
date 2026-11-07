verdict: PASS

# Sim isolation review, round 2 (verifier)

Range reviewed: `a9d01c1..8c8a5b1` on `sim-fix` (one commit, 8c8a5b1). It changes only the test harness:
`scripts/testdata/fake-sim.sh` and `scripts/test-simulate.sh`. `scripts/simulate.sh` is unchanged since round 1.

All three round-1 fix items hold. I checked each one with a mutation, and each mutation turns its test red.

## Round-1 fix items

1. **The isolation case can now catch a restored broad pkill. Fixed.** The fake's long-lived python now carries
   `villageheart.sim.request=$SIM_DIR/sim-request.json` in its argv, like the real server does. Under mutation 1 (below),
   `concurrent run A was killed by another run` goes red. The mutant logged what it matched, and the log shows run B's or run C's
   `kill_group` hitting run A's process (`python3 - .../simA sleep villageheart.sim.request=...`). So the mutant did the kill,
   not an outside process.
2. **`kill_tokened` is now exercised. Fixed.** The new `escape` case starts a `setsid` child (its own session and process group,
   like Gradle's daemon). Only the run token can reach it. With `kill_tokened` as a no-op, `escapee outlived its run` goes red
   and nothing else does, in a clean run with no overlapping old-code self-test.
3. **The leftover check is restored and the reason is in the commit. Fixed.** The grandchild is now
   `python3 -c 'import time; time.sleep(100)' '<SIM_DIR>/grandchild'`, so `pgrep -f "$T/sim"` sees it. The 8c8a5b1 message
   explains the dropped check: the old `pgrep -fx 'sleep 100'` was machine-wide, so another self-test running at the same time
   made it fail. That covers CLAUDE.md rule 4.

## Fix list

None.

## Evidence

### `scripts/test-simulate.sh` on 8c8a5b1, 3 runs
| Run | Exit | Result |
|---|---|---|
| 1 | 0 | 33 ok, 0 FAIL |
| 2 | 0 | 33 ok, 0 FAIL |
| 3 | 0 | 33 ok, 0 FAIL |

No other `test-simulate` process was running during these three runs.

### Full `scripts/verify.sh` on 8c8a5b1
**Exit 0** (12:24:58 to 12:28:57 UTC). L1 ✅. L2 ✅ "All 38 required tests passed". L3 stub self-test ✅. Model-log gate ✅.
L4 ✅. No villageheart model warnings in the client log. Screenshots 0000 to 0003 were produced. This change has no visual part.

### Mutations
Each mutation was made in `scripts/simulate.sh` and restored with `git checkout scripts/simulate.sh`. The tree was clean afterwards.
Every kill was scoped to my own scratch directory (`TMPDIR` set to it). No mutant contained a machine-wide pattern, and the
pkill mutant was guarded with `[ -n "$MUT_SCOPE" ]`.

| # | Mutation | Result |
|---|---|---|
| 1 | In `kill_group`, `kill_tokened` replaced by `pkill -f "villageheart.sim.request=$MUT_SCOPE/"` (the old pkill, scoped to my self-test's temp tree) | **killed**: `FAIL concurrent run A was killed by another run`, plus `FAIL escapee outlived its run`. The mutant's own match log shows it killed simA's python. |
| 2 | `kill_tokened() { return 0; ...}` (no-op) | **killed**: `FAIL escapee outlived its run (kill_tokened)`. Isolation stayed green on the clean rerun. |
| 3 | Group kill removed (`kill -TERM/-KILL -- -$PGID` replaced by `:`), and `kill_tokened` skips any pid whose cmdline contains `grandchild` (so the escapee is still killed and only the leftover check is tested) | **killed**: `FAIL leftover fake-sim processes` lists `sh -c python3 ... /sim/grandchild` and its python child. `FAIL run A left processes behind after SIGTERM` also goes red. I killed the leaked grandchildren afterwards by my scratch path only. |

### Cross-track flake (seen, not caused by sim-fix)
Worktree `agent-a51cb8299446d9e06` was running a 3x `verify.sh` loop on old code. Its `scripts/simulate.sh:117` still has
`pkill -KILL -f -- "villageheart.sim.request="`. Its stub self-tests overlapped some of my runs:
- During my first mutation-2 run, isolation also went red (`concurrent run A was killed`). A `verify.sh` from that worktree had
  started inside my run's window. A rerun with no overlap left isolation green and only the escapee red. That red was the old
  machine-wide pkill acting from outside. It is the original bug, and it goes away once every track has this change.
- My first mutation-1 run also overlapped that worktree's self-test (pid 30701, cwd `agent-a51cb8299446d9e06`). That is why I
  reran mutation 1 with a match log. The log proves that the mutant's own pkill killed run A.
- I saw no red on unmutated 8c8a5b1 code.

## Non-blocking notes
a. The fake's marker argument also makes this branch's stub runs a target for old-code tracks' machine-wide pkill. This is the
   expected cross-track flake until all tracks merge sim-fix. It is not a defect in this branch.
b. The escapee's argv carries `villageheart.sim.request=x`. That is harmless, but it is not scoped to `SIM_DIR`. If it were
   `...=$SIM_DIR/...`, mutation 1 would show only the isolation failure, which would read more clearly.
c. The test names `escapee_killed (exit 2)` and `ok` print before the real escapee assertion on the next line. A reader may see
   `ok escapee_killed` next to `FAIL escapee outlived its run`. Renaming the expect case to `escape_timeout` would avoid that.
d. Round-1 notes still apply: the pid-reuse race in `kill_tokened`, the free-port check-then-use, `SIM_REPORT_DIR` missing from
   the TESTING.md concurrency note, and a persistent `SIM_GRADLE_CMD` daemon.
