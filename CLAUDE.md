# Village Heart: agent guide

A Fabric mod for **Minecraft 1.21.11** (Mojang mappings, Java 21 target). The player crafts a Village Heart and places it near a village bell. That one village becomes theirs: they hire villagers into jobs, build workplaces, share resources, and help the village survive and grow.

**Status:** v1.0.0 is built and in playtest. Changes are now made one at a time: fixes, balance tweaks and features. **Read `docs/WORKFLOW.md`** for the full process. The short version is below.

## Making a change
1. Branch from **`dev`**, not `main`: `git fetch origin && git checkout -b fix/<name> origin/dev`. Use `feat/<name>` for features.
2. Find the code with `docs/CODEMAP.md` (feature → rules → glue → tests), then grep.
3. Write a test that shows the bug or the new behaviour, then make the change.
4. Run `scripts/verify.sh --fast` while you work. Run the full `scripts/verify.sh` before you push. Both must exit 0.
5. Add a line under `## Unreleased` in `CHANGELOG.md`. Update `docs/DESIGN.md` and `docs/PLAYER_GUIDE.md` if behaviour changed.
6. Push the branch and open a PR into `dev`. Never push to `main`. `main` only gets released versions (see WORKFLOW.md "Releasing").

## Toolchain
- Gradle/Loom need **Java 25**. `scripts/verify.sh` installs it to `~/.jdk25` if it is missing. For raw Gradle: `export JAVA_HOME=~/.jdk25`.
- Always use `./gradlew --no-daemon` in cloud sessions.
- L4 client tests need `xvfb-run`. Install `xvfb` if it is missing.
- Never run two Gradle jobs at once in one checkout.

## Code layout
- `src/main/java/dev/villageheart/core/` is **pure Java with no Minecraft imports.** All village rules live here and get JUnit tests in `src/test/`. Put logic here whenever you can: it tests in milliseconds.
- `core/Balance.java` holds every tunable number.
- Everything else under `src/main/java/dev/villageheart/` is thin Minecraft glue: blocks, entities, AI, saved data.
- `src/client/` is client-only code: screens, rendering.
- `src/gametest/` holds the in-world tests (`*GameTests`), client tests with screenshots (`*ClientTests`), and the simulation runner (`sim/`).

## Definition of done (every change)
1. `scripts/verify.sh` exits 0. CI runs it on every push.
2. New or fixed behaviour has a test at the lowest layer that can prove it: unit, then GameTest, then client.
3. Any visual change gets a client-test screenshot that you have actually **looked at**.
4. Never weaken, skip or delete a test to make it pass. If a test is wrong, say why in the commit.
5. `CHANGELOG.md` has a line under `## Unreleased`. DESIGN and PLAYER_GUIDE match the behaviour.

## Do not break
- **Old saves must load.** `VillageState` uses `Codec.mapPair(RAW_A, RAW_B)`: add new keys to RAW_B, flat and optional.
- **Ordinal-coded enums are append-only.**
- **No moving or killing mobs to make a test pass.** The stuck-raider flee rule is the only allowed removal.
- **Owner decisions are binding:** they are in `docs/DESIGN.md` and in `docs/archive/build-phase/LOOP_STATE.md` (`decisions_by_human`). Examples: nobody attacks creepers; raids are balanced with the player defending; the merchant never strikes; builder pace is 3.5 s per block.
