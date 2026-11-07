# Overnight agent loop

A scheduled task starts a **fresh cloud session every hour** during the overnight window. Each
session is one "tick" of the loop. Nothing lives in an agent's memory: all state is in git
(`dev` branch), so any tick can die at any moment (usage limit, crash, timeout) and the next
one carries on.

## One tick
1. `git fetch origin && git checkout dev && git pull` (create `dev` from `main` if missing).
2. **Stop checks** (exit immediately, spending almost nothing):
   - `docs/LOOP_STATE.md` says `status: DONE` or `status: PAUSED`.
   - `.loop/lock` exists and its timestamp is less than 90 minutes old (another tick is running).
3. Take the lock: write `.loop/lock` (UTC timestamp + session URL), commit, push. If the push is
   rejected, someone else took it, so exit.
4. **Build:** pick the first unchecked item in `docs/ROADMAP.md`. If `docs/PROGRESS.md` lists an
   open verifier rejection, fix that first. Work in small steps. After each step that passes
   `scripts/verify.sh --fast`, commit and push to `dev`, so a usage-limit cut loses at most one step.
5. **Verify:** run the full `scripts/verify.sh`. Then start a *separate* verifier subagent. Give it
   the milestone's acceptance criteria, the diff (`git diff <start>..HEAD`), `reports/verify-summary.md`
   and the screenshots, but **not** the builder's reasoning. Its brief: try to prove the milestone is
   NOT done (missing tests, tests that can't fail, weakened tests, screenshots that look wrong,
   behaviour that only works in the test fixture).
6. **Record:** if the verifier passes it, tick the milestone in ROADMAP. Otherwise write the
   rejection reasons into PROGRESS as an open item. Append a PROGRESS entry either way.
   Delete `.loop/lock`, commit, push.
7. Keep going with the next item while time and usage last (stop starting new work after ~50
   minutes so the next tick isn't blocked by the lock).

## Usage limits (5-hour windows)
When the plan's usage limit is hit, the running tick just stops. Its last pushed step is safe;
its lock goes stale after 90 minutes. Ticks that fire while still limited end immediately. The
first tick after the window resets picks up from git. No manual restart needed.

## Release gate → v1
When every ROADMAP item including the **v1.0 release gate** is ticked:
- build `build/libs/village-heart-*.jar`, create a GitHub release `v1.0.0-rc1` on the `dev` commit
  with the jar attached, and add a screenshot gallery from `reports/screenshots/` to its notes;
- set `docs/LOOP_STATE.md` to `status: DONE`; later ticks then exit straight away.
The human tests the release in a real launcher and merges `dev` → `main`.

## Human controls
- Pause: set `status: PAUSED` in `docs/LOOP_STATE.md` on `dev` (or disable the scheduled task).
- Redirect: edit ROADMAP order, or add a `## Human notes` entry at the top of PROGRESS. Ticks read it first.
