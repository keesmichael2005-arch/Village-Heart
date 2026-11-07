# Village Heart — agent guide

A Fabric mod for **Minecraft 1.21.11** (Mojang mappings, Java 21 target). The player crafts a
Village Heart, places it near a village bell, and that one village becomes theirs: they hire
villagers into jobs, build workplaces, share resources and help the village survive and grow.

Design and scope: `docs/DESIGN.md`. Milestones and acceptance tests: `docs/ROADMAP.md`.
How testing works: `docs/TESTING.md`. Overnight loop protocol: `docs/AGENT_LOOP.md`.

## Toolchain
- Gradle/Loom need **Java 25**; `scripts/verify.sh` installs Mojang's runtime to `~/.jdk25` if missing.
  For raw gradle calls: `export JAVA_HOME=~/.jdk25`.
- Always use `./gradlew --no-daemon` in cloud sessions.

## Code layout
- `src/main/java/dev/villageheart/core/` — **pure Java, no Minecraft imports.** All village rules
  (claiming, stockpile, jobs, economy, needs) live here and get JUnit tests in `src/test/`.
  Keep this layer as big as possible: it tests in milliseconds.
- `src/main/java/dev/villageheart/...` (outside `core`) — thin Minecraft glue: blocks, entities, AI goals, saved data.
- `src/client/` — client-only code (rendering, screens).
- `src/gametest/` — in-world tests (`VillageHeartGameTests`) and client tests with screenshots (`VillageHeartClientTests`).

## Definition of done (every change)
1. `scripts/verify.sh` exits 0. Use `--fast` while iterating and the full run before you push.
2. New behaviour has a test at the lowest layer that can prove it: unit, then GameTest, then client.
3. Any visual change gets a client-test screenshot that you have actually **looked at**.
4. Never weaken or delete a test to make it pass. If a test is wrong, say why in the commit.
5. Update `docs/ROADMAP.md` status and append to `docs/PROGRESS.md` (what changed, what's next, any blockers).
