# Brief: PLANNER (milestone {M})

You are a fresh agent. Read CLAUDE.md, docs/DESIGN.md, docs/ROADMAP.md (milestone {M} and its
acceptance tests), docs/PROGRESS.md and the relevant code. Do **not** write mod code.

Write `docs/plans/{M}.md` containing:
1. **Goal**: the player-visible outcome, in two or three sentences.
2. **Current state**: what exists in the code you will build on (file paths, classes).
3. **Design**: data model, classes to add or change (`core/` pure logic vs Minecraft glue), saved
   data, networking, assets. Name exact Minecraft 1.21.11 Mojang-mapped APIs you expect to use, and
   check uncertain ones against the decompiled sources (`./gradlew genSources`) or the jars in `~/.gradle`.
4. **Step-by-step build order**: small steps, each ending in something `verify.sh --fast` can check.
5. **Test plan**: for every acceptance criterion, the exact test(s) (layer, class, setup, assertion),
   plus extra edge cases. Say for each test how it would FAIL if the feature were broken.
6. **Risks & unknowns**, and **out of scope** for this milestone.

Then start a fresh critique subagent. Give it only the plan file and the roadmap entry, and ask it
to find gaps, wrong API assumptions, untestable criteria and missing edge cases. Revise the plan,
and add a short "Critique addressed" section at the end. Commit and push to `dev`.
Finish with a 3-line summary: plan path, number of steps, top risk.
