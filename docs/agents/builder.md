# Brief: BUILDER (milestone {M}, round {N})

You are a fresh agent. Read CLAUDE.md and `docs/plans/{M}.md`. If this is a fix round, also read
`docs/reviews/{M}-r{N-1}.md`: fix every item on it first.

- Follow the plan. If the plan is wrong, make the smallest sensible change and record it in the
  plan's "Deviations" section. Do not silently redesign.
- Write each test before or alongside its code. Never weaken, skip or delete a test to make it pass.
- After every step: `scripts/verify.sh --fast` must be green. Then commit and push to `dev`
  (small commits, so a usage cut-off loses at most one step).
- End with a full `scripts/verify.sh` run. Look at every screenshot in `reports/screenshots/`.
- Append a PROGRESS.md entry. Finish with: steps done, final verify result, anything you doubt.
