# Agent loop (on-demand runs)

The loop only runs when the human starts one ("run until 07:00", "run until weekly usage hits 70%").
It never runs on a fixed schedule.

## Who does what
- **Orchestrator:** the long-running main session the human talks to. It doesn't write mod code.
  It starts workers, reads their output files, decides what happens next, enforces the budget, and
  keeps `docs/LOOP_STATE.md` up to date. Auto-compaction keeps it alive for long runs. All real state is in git.
- **Workers:** a **fresh** agent for every phase, briefed only from files in the repo, so no single
  agent's context gets stale or biased. Briefs: `docs/agents/`.

## One milestone = plan → build → verify (roughly 40 / 20 / 40)
```
PLAN    planner (Opus)    writes docs/plans/Mxx.md, has a fresh subagent critique it, revises
  ↓
BUILD   builder (Sonnet)  implements the plan, tests first; small commits to `dev`, each green on verify --fast
  ↓
VERIFY  verifier (Opus)   fresh every round: full verify.sh + adversarial review → docs/reviews/Mxx-rN.md
  ↓ FAIL: the SAME builder gets the review and fixes it (it auto-compacts on long milestones) → new verifier
  ↓ PASS: tick ROADMAP, next milestone
```
- **Models:** Opus for planning and verifying (judgement), Sonnet for building (volume). If a
  milestone is failing repeatedly on hard problems, the orchestrator may switch the builder to Opus.
- **Helpers:** any worker, mostly builders, may start **at most 2 subagents** of its own (e.g. one to
  write tests while it writes code, or one to research an API). No deeper nesting.
- **5 failed verifications:** the orchestrator reads the reviews and decides. If each round is fixing
  real issues and the list is shrinking (a healthy loop), it continues. If the same items keep coming
  back, or the blocker needs a human decision, it marks the milestone `BLOCKED` in LOOP_STATE with
  the reason and moves on to the next milestone that doesn't depend on it.
- **Parallelism** (added after run 1 used only ~40% of a 5-hour window):
  - **Plan ahead:** while milestone N builds or verifies, the planner for the next milestone(s) can run.
    Plans are docs only.
  - **Parallel builds:** at most **2** build/verify tracks at once (the container has 4 CPUs and 15 GB;
    each track runs its own Minecraft). Only milestones whose ROADMAP dependencies have all passed can run.
  - **Isolation:** every parallel worker runs in its **own git worktree** on a branch `mNN`, created from
    `dev`. When its review passes, the orchestrator merges `mNN` into `dev` (rebasing first) and re-runs
    `verify.sh --fast` on dev. A worker never shares a working directory with another worker.
  - **Simulations:** don't run two `scripts/simulate.sh` at once, and don't run one while another track runs
    `verify.sh`, until the BACKLOG item "concurrent sims kill each other" is fixed.
- `main` only moves when the human merges.

## How the orchestrator knows when to act
- **It doesn't watch workers.** They run in the background and report back when they finish, and
  the orchestrator acts on that report straight away.
- If the orchestrator hasn't heard from the current worker in **over an hour**, it checks on it once
  (still going? died? stuck?).
- **Reset wake-ups:** usage limits come in 5-hour windows. While the orchestrator still has usage, it
  schedules wake-ups for **5 minutes after each upcoming window reset** for the whole run. The first one
  is 5 minutes after the current window's reset. Each later one is 5 h 05 min after the previous wake,
  because a new window only starts at first use after a reset. So whenever a limit hits mid-run,
  something is already scheduled to wake the orchestrator, and it never needs usage to set up its own
  wake-up. The times are recorded in LOOP_STATE (`wakes:`). On wake it re-reads git and LOOP_STATE and
  resumes. A worker cut off mid-phase is resumed, or restarted fresh with "continue from what is on `dev`".

## Budget
Set per run by the human and recorded in `docs/LOOP_STATE.md` (`budget:`), e.g. a stop time,
"stop at 70% weekly", or "spend $X". The orchestrator checks it **before starting each new worker**,
never mid-phase, so work is always left committed and green.
The weekly-usage % isn't machine-readable from a session. The orchestrator tracks the summed cost
of the run and converts it using a %-per-dollar figure the human calibrates (see LOOP_STATE).

## End of a run
- Budget reached: finish nothing new, write a morning summary to `docs/PROGRESS.md`, set
  `status: PAUSED`, and send the human a notification.
- v1.0 release gate passed: build the jar, create GitHub release `v1.0.0-rc1` with screenshots,
  set `status: DONE`. The human tests it in a real launcher and merges `dev` → `main`.
