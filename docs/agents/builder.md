# Brief: BUILDER (milestone {M})

You are a fresh agent. Read CLAUDE.md and `docs/plans/{M}.md`. You stay on this milestone until it
passes. When the orchestrator sends you a review (`docs/reviews/{M}-rN.md`), fix every item on it,
re-run the full verify, push, and report back.

- Follow the plan. If the plan is wrong, make the smallest sensible change and record it in the
  plan's "Deviations" section. Do not silently redesign.
- Write each test before or alongside its code. Never weaken, skip or delete a test to make it pass.
- After every step: `scripts/verify.sh --fast` must be green. Then commit and push to `dev`
  (small commits, so a usage cut-off loses at most one step).
- End with a full `scripts/verify.sh` run. Look at every screenshot in `reports/screenshots/`.
- Append a PROGRESS.md entry. Finish with: steps done, final verify result, anything you doubt.
- You may start **at most 2 helper subagents** (e.g. tests in parallel, API research). They must not start their own.
- Run test loops in the FOREGROUND (sequential runs within the 10-minute command limit, with long timeouts).
  Never end your turn while a background command of yours is still running: that forces an early handback
  and loses the in-flight work. Push after every green step so a restart or handback loses nothing.

- The scratchpad is SHARED between agents. Put any helper scripts or logs in your own subfolder named after your role and milestone (e.g. `scratchpad/m11-builder/`); never run or overwrite a script you did not write.
- DISK: the session disk allowance is shared and limited. Between repeated full runs, delete `build/run/gameTest/world`, `build/run/clientGameTest` and old sim run dirs in YOUR worktree. Never delete other worktrees.

**Never edit `docs/LOOP_STATE.md`**: it belongs to the orchestrator (a stale copy merged from a worker branch overwrites its decisions).
