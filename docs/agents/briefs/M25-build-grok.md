You are the BUILDER for milestone M25 (Sieges, wall damage, creeper breaches, builder repairs, random raids) of the Village Heart
Minecraft mod (Fabric 1.21.11, Mojang mappings, Java 21 target, Gradle on Java 25). You work headless and alone in this git worktree
(branch `m25`, from dev with the plan committed). No human to ask: decide sensibly, record each decision in the plan's "Deviations"
section, and keep going step by step until the milestone is done. This is a large milestone; work through ALL steps of the plan.

READ FIRST: `CLAUDE.md`, `docs/agents/builder.md` (your rules - follow them), `docs/TESTING.md`, `docs/plans/M25.md` (r3.1, your plan; read its "Critique r3" section),
the M25 entry in `docs/ROADMAP.md`, DESIGN sections 5.6 (guards, as built in M18), 5.8 (builder, defences) and the threats section,
and `docs/LOOP_STATE.md` section `decisions_by_human` (binding owner decisions; read only, never edit the file). Owner decisions that bind M25 (all are already folded into the plan; the plan wins on detail):
- walls have HP and crack stages, gates are the weak point; creepers seek village walls and blow them; ravagers do heavy wall damage in raids;
  damage raises an alert and the builder repairs with cobblestone from Storage; random raids (not only Bad Omen) scale with population,
  wealth and defences, with rewards, capped for young villages (plan 3.13a); no per-tier wall cap;
- only zombies are lured to the claimed village at night and in sieges, path toward villagers without line of sight and attack defence
  blocks in the way; skeletons and all other hostiles stay vanilla; NOBODY attacks creepers (guards and golems ignore them) - creepers only
  breach walls and are not a threat to villagers;
- the start is meant to be hard (two wooden-sword guards), not hopeless.
M19 (smith, golems) is being built in parallel on origin/m19 and is NOT on dev yet: where the plan needs a smith-built golem, use the plan's
fallback (scenario-summoned golem) and do not depend on M19 classes. Merge origin/dev from time to time (other milestones land there).

RULES OF WORK
- `export JAVA_HOME=~/.jdk25`; always `./gradlew --no-daemon`. After every plan step `scripts/verify.sh --fast` must be green, then commit
  and push (`git push origin HEAD:m25`). The fast verify takes ~10-15 min and the full one 20-30 min: run them in the background with nohup
  and poll the log / `reports/verify-summary.md`; never run two Gradle jobs at once (the machine is shared with other builds).
- Every new behaviour gets a test at the lowest layer that proves it (L1 JUnit in core, L2 GameTest, L4 client screenshot for visuals).
  Never weaken, skip or delete a test. Mutation-check key rules (break the code, see the test go red, restore).
- Enums coded by ordinal (`ActivityKind`, `WorkerStatus`, ...) are append-only. `VillageState`'s RAW codec is split with `Codec.mapPair`
  (RAW_A/RAW_B, M18); keep keys flat and old saves loading.
- Known flaky tests (another branch fixes them): `iron_axe_out_fells_stone`, `craft_pauses_overnight`, `village_goals_tier_up_at_dawn`,
  `forced_chunk_grows_without_player`, `two_hunters_one_ground`, L4 "Timeout loading world" under load: if one alone fails, rerun once.
- Look at every new screenshot (open the PNG) and describe what you see in your report.
- Commit as kees <keesmichael2005-arch@users.noreply.github.com>, every message ending with the line
  `Agent: Cursor CLI (grok-4.7-xhigh), orchestrated by Claude`. Never push to `dev`. Never edit `docs/LOOP_STATE.md`.
  Update DESIGN/TESTING/ROADMAP (note, do not tick) and append a PROGRESS.md entry.
- End with a full `scripts/verify.sh` exit 0 and the plan's L3 sim(s) if any.

FINAL MESSAGE: head commit, steps done, deviations, the pasted final `reports/verify-summary.md`, sim results, screenshots looked at, doubts.
