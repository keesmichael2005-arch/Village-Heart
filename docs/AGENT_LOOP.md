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
PLAN (planner)        writes docs/plans/Mxx.md, has it critiqued by a fresh subagent, revises
  ↓
BUILD (builder)       implements the plan, tests first; small commits to `dev`, each green on verify --fast
  ↓
VERIFY (verifier)     full verify.sh + adversarial review → docs/reviews/Mxx-rN.md: PASS or FAIL + fix list
  ↓ FAIL                                   ↓ PASS
BUILD again (fresh builder, given the review) → VERIFY again      tick ROADMAP, next milestone
```
- At most **4 build/verify rounds** per milestone. After that the orchestrator marks it `BLOCKED` in
  LOOP_STATE with the reason, skips to the next milestone that doesn't depend on it, and flags it for the human.
- Only one worker runs at a time, all on `dev`. `main` only moves when the human merges.

## How the orchestrator knows when to act
- Workers run as background agents, so the orchestrator is woken **as soon as one finishes**.
  There are no fixed slots.
- **Heartbeat:** while a run is active, the orchestrator keeps one wake-up scheduled about 60 minutes
  ahead and pushes it back each time it wakes. If a worker or the orchestrator dies (usage limit,
  crash), the heartbeat brings it back, re-reads git and LOOP_STATE, and restarts the interrupted
  phase with a fresh worker ("continue from what is on `dev`").
- **5-hour usage window hit:** everything pauses. The orchestrator schedules its wake-up for just
  after the reset time and resumes from git.

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
