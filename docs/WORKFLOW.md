# Workflow: fixes, features and releases

The initial build (milestones M1–M26) is finished. From v1.0 on, a fix is one agent straight through, as below. A feature is **Making a feature**
in `CLAUDE.md`: the session Kees opened interviews and plans, one implementer writes the code from a prompt, and that same session opens the PR
and pushes it. Fixes and features end the same way: an open PR labeled `awaiting-approval`, then `approved` when Kees
says he is done. A separate release thread merges the approved PRs and builds the playtest jar (see "The batch flow"). `CLAUDE.md` is the agent guide. `AGENTS.md` points at it. An implementer does not get a second copy. Its brief
is the prompt.

## Branches

| Branch | What it is | Who writes to it |
|---|---|---|
| `main` | **Released versions only.** Every commit on `main` is a version players can download, tagged `vX.Y.Z`. | Only a release merge from `dev` (below) |
| `dev` | **Staging.** Approved fixes and features land here, in a batch. Playtest builds come from `dev`. Always green. | The release thread, merging `approved` PRs |
| `fix/<short-name>` | One bug fix | The agent doing that fix |
| `feat/<short-name>` | One feature (e.g. `feat/archers`, plan `docs/plans/P1-archers.md`) | The agent doing that feature |
| `blueprints-2` | Post-v1 feature work started during the build (unreviewed, 29 commits). Rebase on `dev` before continuing. | — |

A `fix/*` or `feat/*` branch is deleted when the release thread merges it (`gh pr merge --squash --delete-branch`). Build-phase branches (`m*`, `*-plan`, `art-pass` and others) are all merged into `dev`, or hold old plan drafts whose final versions are in `docs/plans/`, so they can be deleted.

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
8. **Label and stop.** Label the PR `awaiting-approval` as soon as it is pushed. Do not wait for CI (minutes are limited) and do not merge. When Kees says he is done with the fix, change the label to `approved`. A release thread merges it (below).

A balance tweak is the same as a fix: change the number in `core/Balance.java` (or the table it lives in), and point any test that pins
it at the `Balance` value rather than loosening its rule.

## The batch flow

Kees plays several changes together, not one jar per PR.

1. Each fix or feature thread pushes its branch, opens a PR into `dev`, and labels it **`awaiting-approval`**. It does not wait for CI or re-run it. CI runs on the PR but does **not** upload a jar for it.
2. Kees looks at the proof in the thread (screenshots, tests). If he wants changes, that thread keeps going. When he says he is done, the thread changes the label to **`approved`**. The PR stays open.
3. When Kees wants to play, he opens a **release thread**. It lists the open `approved` PRs, merges them into `dev` one at a time (squash), and waits for the `dev` run to go green: that run is the real check of the batch. Only the run on the last merge matters: a newer push cancels the older one.
4. That `dev` run uploads the **village-heart-jar** artifact. The release thread downloads it and hands Kees the path, with the list of PRs and their CHANGELOG lines. He puts the jar in `.minecraft/mods` with Fabric API and plays.
5. Bugs he finds become new fix threads and ride the next playtest. When he says the playtest is good, the release thread does "Releasing a version" below.

The roles and the commands are in `CLAUDE.md` (**Labels and approval**, **Making a release**).

## Getting a playtest build

Only pushes to `dev` and `main` upload the **village-heart-jar** artifact. Open the run under the repo's **Actions** tab (or let the release thread download it). Unzip it and put the jar in `.minecraft/mods` with Fabric API. A PR run uploads only **verify-reports** (the screenshots).

To build locally: `scripts/verify.sh --fast` or `JAVA_HOME=~/.jdk25 ./gradlew --no-daemon build -x test`. The jar is
`build/libs/village-heart-<version>.jar`.

## Releasing a version

The release thread does this, after Kees says the playtest of `dev` is good:

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
- A visual change needs an L4 screenshot. On a feature, the parent opens the png from the `verify-reports` artifact and puts it in the chat with Kees (`CLAUDE.md`, Screenshots in the chat).
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

A feature follows **Making a feature** in `CLAUDE.md`. After Kees approves the plan, it is saved as `docs/plans/<name>.md`. One implementer builds it from a prompt on `feat/<name>`. The session he opened reviews the diff, opens the PR into `dev`, and pushes it. If a verify run has already finished it puts the new client screenshots in the chat, from the `verify-reports` artifact. It labels the PR `awaiting-approval`, and sets `approved` when Kees says he is done. It does not merge. The release thread does (see "The batch flow").

A review of a fix is a second pass over the PR diff with the "Rules that do not change" above. Break the key rule once and see the test go red (the mutation check), and confirm the full verify on the PR.

## History

- The milestone plans and reviews stay in `docs/plans/` and `docs/reviews/`, because code comments link to them.
- The build-phase orchestration docs are in `docs/archive/build-phase/`: the agent loop, the handoff, the loop state with every owner decision, the role briefs, and the full progress log.
