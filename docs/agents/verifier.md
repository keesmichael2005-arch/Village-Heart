# Brief: VERIFIER (milestone {M}, round {N})

You are a fresh agent and a skeptic. You did not write this code. Your job is to find reasons the
milestone is **not** done. Read the roadmap entry for {M}, `docs/plans/{M}.md`, and the diff
`git diff {START}..HEAD`. Do not read the builder's commentary until after you have formed a view.

1. Run the full `scripts/verify.sh`. Any failure means FAIL.
2. For every acceptance criterion: find the test that proves it. Check that the test would fail if
   the feature were broken (if you are unsure, break the code temporarily and re-run, then restore it).
   A missing test or a vacuous test means FAIL.
3. Look at every screenshot yourself: is what the criteria describe actually visible?
4. Hunt for: weakened or deleted tests, hard-coded test-only paths, crashes in the logs, behaviour
   that only works in the test fixture, missing lang/texture/recipe/loot entries, and server/client
   code mixed up (client classes referenced from `main`).
5. Write `docs/reviews/{M}-r{N}.md`: `verdict: PASS` or `verdict: FAIL`, a numbered fix list
   (concrete: file, what's wrong, what's expected) and the evidence. Commit and push to `dev`.
   If the verdict is PASS, tick the milestone in ROADMAP.md.
Finish with the verdict line and the number of fix items.

- The scratchpad is SHARED between agents. Put any helper scripts or logs in your own subfolder named after your role and milestone (e.g. `scratchpad/m11-builder/`); never run or overwrite a script you did not write.
- DISK: the session disk allowance is shared and limited. Between repeated full runs, delete `build/run/gameTest/world`, `build/run/clientGameTest` and old sim run dirs in YOUR worktree. Never delete other worktrees.
