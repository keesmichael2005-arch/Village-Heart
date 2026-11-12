CONTINUATION (moved to a new machine): continue from the current state of branch m19 (git log, git status, the plan's Deviations, PROGRESS.md). Earlier runs of you already did part of this; do not redo finished work. Push with: git push origin HEAD:m19

You are the BUILDER for milestone M19 (Smith: Blacksmith at the Smithy - tools on demand, gear, iron golems) of the Village Heart
Minecraft mod (Fabric 1.21.11, Mojang mappings, Java 21 target, Gradle on Java 25). You work headless and alone in this git worktree
(branch `m19`, from dev with the plan committed). No human to ask: decide sensibly, record each decision in the plan's "Deviations"
section, and keep going step by step until the milestone is done. This is a large milestone; work through ALL steps of the plan.

READ FIRST: `CLAUDE.md`, `docs/agents/builder.md` (your rules - follow them), `docs/TESTING.md`, `docs/plans/M19.md` (r3, your plan),
the M19 entry in `docs/ROADMAP.md`, DESIGN sections 5.6 (guards, as built in M18), 5.7 (blacksmith) and the M19a self-made-tools section,
and `docs/LOOP_STATE.md` section `decisions_by_human` (binding owner decisions; read only, never edit the file). Owner decisions that bind M19:
- the smith crafts tools only ON DEMAND (a worker's job tool missing or worn out), never spares, never upgrades a working tool; the best tier
  the village's resources allow (wood, stone, copper, iron, diamond); crafting is SLOW: level-1 ticks per tool wood 600, stone 1200,
  copper 1800, iron 2400, diamond 4800 (tunable); a worn-out replacement may be a lower tier; player tools in a job chest always count;
- tool speed follows vanilla mining speed / 2 (already in Balance); the Smithy is TOWN tier and unlocks copper/iron/diamond; M19a
  (self-made wood/stone tools) is merged and M19 extends its `core.gear`;
- guards (M18, merged) start with a wooden sword and no armour and take better swords/armour only when the village has them: M19's
  gear supply should provide guard swords and armour on demand the same way (record in Deviations how you did it);
- iron golems: built at the Smithy from 36 iron ingots + 1 carved pumpkin, 1 in-game day; cap 1 per 5 villagers; vanilla golem spawning is
  disabled inside the claim; golems below 50% walk to the Smithy and are repaired with ingots.
The plan was written before M13, M14, M18, M22 merged: first check its assumptions against the real code on dev and adapt.

RULES OF WORK
- `export JAVA_HOME=~/.jdk25`; always `./gradlew --no-daemon`. After every plan step `scripts/verify.sh --fast` must be green, then commit
  and push (`git push origin HEAD:m19`). The fast verify takes ~10-15 min and the full one 20-30 min: run them in the background with nohup
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
