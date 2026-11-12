# Workflow: fixes, features and releases

The initial build (milestones M1–M26) is finished. From v1.0 on, changes are made by one agent (or person) per change, straight in the
repo. No orchestrator is involved. This file is the process; `CLAUDE.md` is the short version an agent reads first.

## Branches

| Branch | What it is | Who writes to it |
|---|---|---|
| `main` | **Released versions only.** Every commit on `main` is a version players can download, tagged `vX.Y.Z`. | Only a release merge from `dev` (below) |
| `dev` | **Staging.** Fixes and features land here first. Playtest builds come from `dev`. Always green. | Merges of `fix/*` and `feat/*` branches |
| `fix/<short-name>` | One bug fix | The agent doing that fix |
| `feat/<short-name>` | One feature (e.g. `feat/archers`, plan `docs/plans/P1-archers.md`) | The agent doing that feature |
| `blueprints-2` | Post-v1 feature work started during the build (unreviewed, 29 commits). Rebase on `dev` before continuing. | — |

Delete a `fix/*` or `feat/*` branch once it is merged. Build-phase branches (`m*`, `*-plan`, `art-pass` and others) are all merged into `dev`, or hold old plan drafts whose final versions are in `docs/plans/`, so they can be deleted.

**Default branch:** set the GitHub default branch to `dev` (Settings → General → Default branch). A new agent session then starts on `dev`, which has this workflow. `main` stays the release branch.

## Making a fix (the normal case)

1. **Start from `dev`:** `git fetch origin && git checkout -b fix/<short-name> origin/dev`.
2. **Find the code.** `docs/CODEMAP.md` maps each feature to its rules (`core/`), its Minecraft glue, and its tests. Then grep.
3. **Reproduce it with a test first**, at the lowest layer that can show it (see "Which test layer"). Watch it fail.
4. **Fix it.** Rules belong in `core/` (pure Java). Glue stays thin.
5. **Run `scripts/verify.sh --fast`** while iterating, and the **full `scripts/verify.sh`** once before you push. Both must exit 0.
6. **Write it down:** add a line to `CHANGELOG.md` under `## Unreleased` (player-facing words). If you changed a rule or a number the
   player sees, update `docs/DESIGN.md` and `docs/PLAYER_GUIDE.md` too.
7. **Push and open a PR into `dev`:** `git push -u origin fix/<short-name>`. Fill in the PR template. CI (`.github/workflows/build.yml`)
   runs the full verify on every push.
8. **Merge** when CI is green (squash or merge commit, either is fine). Delete the branch.

A balance tweak is the same as a fix: change the number in `core/Balance.java` (or the table it lives in), and point any test that pins
it at the `Balance` value rather than loosening its rule.

## Getting a playtest build

Every push runs CI. Open the run under the repo's **Actions** tab. The **village-heart-jar** artifact is the mod jar for that commit.
Download it, unzip it, and put the jar in `.minecraft/mods` with Fabric API.

To build locally: `scripts/verify.sh --fast` or `JAVA_HOME=~/.jdk25 ./gradlew --no-daemon build -x test`. The jar is
`build/libs/village-heart-<version>.jar`.

## Releasing a version

When `dev` has been playtested and you are happy with it:

1. **Bump the version** in `gradle.properties` on `dev` with a small PR. Use 1.0.1 for fixes only, 1.1.0 for new features, and 2.0.0 for a save-breaking change.
2. **Update `CHANGELOG.md`.** Rename `## Unreleased` to `## X.Y.Z`, then start a new empty `## Unreleased`.
3. **Merge `dev` into `main`** (a PR from `dev` to `main`). CI must be green.
4. **Tag and release on GitHub.** Go to Releases, then "Draft a new release". Create tag `vX.Y.Z` on `main`, paste that version's CHANGELOG section, and attach the jar from the `main` CI run.
5. **Optional: upload to Modrinth or CurseForge** (Minecraft 1.21.11, Fabric, with Fabric API as a dependency).

A hot fix for a released version follows the same path. Fix on `dev`, then release a patch version.

## Which test layer

| Layer | What | Where | Speed |
|---|---|---|---|
| L1 | Pure rules in `core/` (JUnit) | `src/test/java/dev/villageheart/core/...` | milliseconds |
| L2 | In-world GameTests on a headless server | `src/gametest/java/dev/villageheart/gametest/*GameTests.java` | ~10–15 min for all |
| L3 | Multi-day village simulations | `sim/scenarios/*.json`, run with `scripts/simulate.sh <name>` | 10–60 min each |
| L4 | Real client with screenshots (needs xvfb) | `*ClientTests.java`, screenshots in `reports/screenshots/` | part of the full verify |

- Use the lowest layer that proves the change.
- A visual change needs an L4 screenshot that you have actually opened and looked at.
- L3 is for balance work. It is not part of `verify.sh`, so run the scenario you touched.
- `docs/TESTING.md` has the details and the known flake rules. A test that fails once under load and passes alone is a known flake: note its name in the PR. Never "fix" a flaky test by loosening it.

## Rules that do not change

- **Never weaken, skip or delete a test to make it pass.** If a test is wrong, fix it and say why in the commit and the PR.
- **Saves must keep loading.** `VillageState` saves through `Codec.mapPair(RAW_A, RAW_B)`. Add new keys to RAW_B, keep them flat, and make them optional.
- **Ordinal-coded enums are append-only.** This covers `ActivityKind`, `WorkerStatus`, `AlertCode` and the others.
- **`core/` has no Minecraft imports.**
- **No hacks that move or kill mobs** to make a test pass. The only allowed removal is the stuck-raider "flee" rule.
- **Owner decisions in `docs/DESIGN.md` are binding.** The build-phase decisions log is `docs/archive/build-phase/LOOP_STATE.md` (`decisions_by_human`). Check it before you change a designed behaviour.

## Bigger work

For a feature, write a short plan first in `docs/plans/<name>.md`: goal, steps, tests per layer and risks. Use the post-v1 plans P1–P3 as the format. Then build on a `feat/*` branch and get a review before merging.

For a review, a second agent reads the PR diff with the "Rules that do not change" above. It should break the key rule once to see the test go red ("mutation check"), and confirm the full verify.

## History

- The milestone plans and reviews stay in `docs/plans/` and `docs/reviews/`, because code comments link to them.
- The build-phase orchestration docs are in `docs/archive/build-phase/`: the agent loop, the handoff, the loop state with every owner decision, the role briefs, and the full progress log.
