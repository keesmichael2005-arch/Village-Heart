# Handoff: Cursor / Grok agents (owner-run), 2026-11-11

The Claude orchestrator is paused. The owner runs Cursor agents (Grok 4.7, his Cursor plan) on the workstreams below until the Cursor
usage runs out; then the Claude orchestrator resumes from what is pushed to GitHub, reviews every branch (Claude Opus verifier per
milestone, `docs/agents/verifier.md`) and merges into `dev`.

## Rules for every agent (paste with each prompt)
- Repo https://github.com/keesmichael2005-arch/Village-Heart. Work ONLY on the branch named below; push often (`git push origin HEAD:<branch>`).
  NEVER push to `dev`, never merge to `dev`, never edit `docs/LOOP_STATE.md`. Opening a PR into `dev` is fine; the orchestrator merges.
- Read `CLAUDE.md`, `docs/agents/builder.md`, `docs/TESTING.md` and the brief named below first. Follow the plan; record changes in the
  plan's "Deviations" section.
- Java: `python3 scripts/install-java25.py`, `export JAVA_HOME=~/.jdk25`, always `./gradlew --no-daemon`.
  After every step `scripts/verify.sh --fast` green, then commit + push. Finish with a full `scripts/verify.sh` exit 0.
- Never weaken, skip or delete a test. Every new behaviour gets a test (L1 core, L2 GameTest, L4 screenshot for visuals) and key rules are
  mutation-checked (break the code, see red, restore).
- Commit as kees <keesmichael2005-arch@users.noreply.github.com>; end each message with `Agent: Cursor (grok-4.7), owner-run`.
- Merge `origin/dev` into your branch now and then (keep both sides of doc conflicts).
- Last step: append to `docs/PROGRESS.md` what was done, the verify summary, and doubts.

## Workstreams (one agent each; they can run in parallel)

| # | Branch | Brief (on that branch) | State at handoff |
|---|---|---|---|
| 1 | `m23` | `docs/agents/briefs/M23-finish-grok.md` | Built; flaky outside-pair test fixed; needs a clean full verify + a `starter 10` sim |
| 2 | `m19` | `docs/agents/briefs/M19-build-grok.md` | Steps started (smith recipes, on-demand gear, smith's day) + WIP commit 77a3ec7 (may not compile) |
| 3 | `m25` | `docs/agents/briefs/M25-build-grok.md` (TRACK A: plan steps 1-8 only) | Step 1 (death log) done. NOTE: `dev` now has a sim death log (`core.sim.DeathLog`, from m26-prep): merge origin/dev and reconcile into ONE death log without dropping m26-prep's tests. Vanilla VillageSiege caused most deaths (`docs/reviews/m26-prep-findings.md`). |
| 4 | `m25-walls` | `docs/agents/briefs/M25-walls-grok.md` (TRACK B: plan steps 9-15 only) | Nothing pushed yet |
| 5 | `release-prep` (new, from dev) | the "release prep" prompt the orchestrator gave the owner | docs only |
| 6 | `backlog-small` (new, from dev) | the "small fixes" prompt the orchestrator gave the owner | market plural, toasts, sim tier metric, hunter flake |
| 7 | `blueprints-2` (new, from dev) | the owner's blueprints prompt | post-v1 feature; must not touch walls/repairs/raids |

Two Claude-hosted machines (sessions `session_0184ToXjov375dJHY3niQHfP` = track A, `session_0184ToXmQh6Zh5QPmWPNfqcf` = track B) may still
be running Grok on rows 3 and 4. Run rows 3 and 4 yourself only after stopping those sessions, so two agents never work one branch.

## After Cursor usage runs out
Tell the Claude orchestrator "resume". It will: fetch every branch above, run a Claude Opus review per milestone (M23, M19, M25 A+B merged,
then steps 16-19), merge passing work into `dev`, then M26 (balance) and the v1.0 release gate.
