CONTINUATION (moved to a new machine): continue from the current state of branch m23 (git log, git status, the plan's Deviations, PROGRESS.md). Earlier runs of you already did part of this; do not redo finished work. Push with: git push origin HEAD:m23

You are the BUILDER finishing milestone M23 (Population growth) of the Village Heart Minecraft mod (Fabric 1.21.11, Java).
You work headless and alone in this git worktree (branch `m23-grok`, starting at origin/m23 = 28f2382). There is no human to ask:
decide sensibly, write down what you decided, and keep going until done.

READ FIRST: `CLAUDE.md` (toolchain, code layout, definition of done), `docs/agents/builder.md` (your rules), `docs/TESTING.md`,
`docs/plans/M23.md` (the plan, including its Deviations section written by the previous builder), and the M23 entry in `docs/ROADMAP.md`.

STATE: M23 is fully built by a previous builder. Its last full verify was NOT green:
- L4 (client tests) failed twice with "Timeout loading world" under machine load (a known infrastructure flake, since fixed on another branch);
- inside the "Layer 1: build + unit tests" step (which also runs the in-world GameTests) two growth tests failed once:
  `outside_pair_cannot_take_claim_bed` (no make-love run happened at all; cause NOT found, it was only given more time) and
  `seeded_licence_resumes_after_brain_reset` (assertion fixed afterwards). Both pass alone. No full verify was run after those fixes.
- The builder also warned: in GameTests `h.relativePos(abs)` is not the inverse of `h.absolutePos(rel)`; assertions built on it can pass vacuously.

YOUR JOB:
1. `git fetch origin dev && git merge origin/dev` (keep both sides of doc conflicts; resolve code conflicts carefully and explain them).
2. Find the real cause of `outside_pair_cannot_take_claim_bed` failing in the full suite (run the full GameTest suite, not only the test alone;
   tests share a world and a clock, see TESTING.md). Fix the cause, not by loosening or deleting assertions. Never weaken, skip or delete a test.
3. Check every M23 test for the `relativePos` trap and fix any vacuous assertion.
4. Run the full `scripts/verify.sh` until it exits 0 (`export JAVA_HOME=~/.jdk25`; always `./gradlew --no-daemon`). It takes 15-25 minutes:
   run it in the background (`nohup scripts/verify.sh > /tmp/verify-m23.log 2>&1 &`) and poll `reports/verify-summary.md` / the log every few
   minutes; do not start a second Gradle job while one runs. If L4 alone fails with "Timeout loading world", rerun once.
   Also run `scripts/verify.sh --fast` once more at the end.
5. Run `scripts/simulate.sh starter 10` once as a smoke test and report its result.
6. Commit small and often as kees <keesmichael2005-arch@users.noreply.github.com>, each message ending with the line
   `Agent: Cursor CLI (grok-4.7-xhigh), orchestrated by Claude` and push after each green step with `git push origin HEAD:m23`.
   Never push to `dev`. Never edit `docs/LOOP_STATE.md`. Append a PROGRESS.md entry.
7. Leave the worktree clean (no temporary mutations, no debug code).

FINAL MESSAGE (your last output): the head commit, what you changed and why, the cause you found for the flaky test, the pasted
`reports/verify-summary.md` of the final full run, the sim result, and anything you doubt.
