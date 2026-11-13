# Village Heart: agent guide

A Fabric mod for **Minecraft 1.21.11** (Mojang mappings, Java 21 target). The player crafts a Village Heart and places it near a village bell. That one village becomes theirs: they hire villagers into jobs, build workplaces, share resources, and help the village survive and grow.

**Status:** v1.0.0 is built and in playtest. Fixes and features land one at a time. This file is the agent guide. `AGENTS.md` points here. Releases are in `docs/WORKFLOW.md`.

A handoff to an implementer is a prompt. It does not add another guide file.

## Role

The first line of your prompt decides this.

- **Implementer.** The prompt starts with `You are the implementer.` That prompt is your brief. Follow **Toolchain**, **Code layout**, **Definition of done**, and **Do not break**. Skip **Skills**, **Access**, and **Making a feature**.
- **Release thread.** Kees opens a session and asks for a playtest build or a release ("make a release", "build the approved ones"). Follow **Making a release**. You merge PRs, you do not write mod code.
- **Parent.** Any other session. You talk to Kees. A feature follows **Making a feature**. You write the plan. You do not write the feature's mod code. A fix follows **Making a fix**, and you may write that code yourself. A parent never merges its own PR (see **Labels and approval**).

## Labels and approval

Every PR into `dev` carries one of two labels. They are how Kees batches work into one playtest.

| Label | Meaning | Who sets it |
|---|---|---|
| `awaiting-approval` | The work is pushed and the PR is open. Kees may still be reviewing, or more work may come. Not part of the next playtest. | The parent thread, as soon as the PR is open and pushed (or when more work is sent into an approved PR). |
| `approved` | Kees said he is done with this thread. No more changes are coming. The next release thread merges it. | The same parent thread, when Kees says he is happy or done. |

```bash
gh pr edit <n> --repo keesmichael2005-arch/Village-Heart --add-label awaiting-approval
gh pr edit <n> --repo keesmichael2005-arch/Village-Heart --remove-label awaiting-approval --add-label approved
```

- Kees says it in the thread that opened the PR. Nobody else sets `approved`, and nobody sets it for him.
- If Kees sends more work to a thread whose PR is `approved`, put it back to `awaiting-approval` first, and set `approved` again only when he says he is done again.
- **CI is limited. Do not wait for `verify` and do not re-run it.** Run `scripts/verify.sh --fast` locally, push, open the PR, label it, and stop. A red or still-running `verify` does not stop the label. Mention it in one line if it already failed. The check that counts is the one on `dev` after the release thread merges the batch.
- Opening and pushing a PR and labeling it `awaiting-approval` ends the thread's normal job. The PR stays open. Parents do not merge, and do not hand Kees a jar.

## Skills

### Fix

Kees reports a bug or a balance tweak. Branch `fix/<name>` from `origin/dev` and follow **Making a fix**.

### Release

Kees wants to playtest, or to release, what has been approved. Follow **Making a release**.

### Feature

Kees wants a new feature, or brain-dumps one. You are the parent. Follow **Making a feature**: interview, a short plan, his approval, one implementer started with the prompt below, your own review of the diff, a PR into `dev`, and the new screenshots pasted into the chat. Stop at his approval before any branch or code.

## Access

**Parent**

- This chat with Kees.
- The repo, git, and `gh`. Pull requests target `dev`. `main` receives released versions only.
- Java 25 at `~/.jdk25` and `./gradlew --no-daemon`. Local check is `scripts/verify.sh --fast`.
- One implementer, started by sending the prompt in **Making a feature**.
- The `verify-reports` artifact from the GitHub `verify` workflow. That is where the client screenshots are. Download them and put the new pngs in the chat.
- The PR labels `awaiting-approval` and `approved` on its own PR.

**Release thread**

- `gh` to list, merge (squash), and label PRs into `dev`, and to open the release PR from `dev` to `main`.
- The `village-heart-jar` artifact of the `dev` and `main` runs.
- Merging to `main` and tagging, only after Kees says the playtest is good.

**Implementer**

- The `feat/<name>` branch named in the prompt, the plan file, tests, and mod code.
- `scripts/verify.sh --fast`.

**This machine (Debbie)**

Java 25 is at `~/.jdk25`. `scripts/verify.sh` installs it when it is missing. `scripts/verify.sh --fast` is the local check. The Minecraft client that takes screenshots runs on the GitHub `verify` workflow (`.github/workflows/build.yml`), which sets up its own virtual display. Leave Debbie's packages as they are.

**Neither role**

Push to `main`. Merge a PR (only the release thread merges, and only `approved` PRs). Install packages on Debbie. Start a second implementer. Add another copy of this guide.

## Making a fix

1. Branch from **`dev`**, not `main`: `git fetch origin && git checkout -b fix/<name> origin/dev`.
2. Find the code with `docs/CODEMAP.md` (feature → rules → glue → tests), then grep.
3. Write a test that shows the bug or the new behaviour, then make the change.
4. Run `scripts/verify.sh --fast` while you work. Run the full `scripts/verify.sh` before you push. Both must exit 0.
5. Add a line under `## Unreleased` in `CHANGELOG.md`. Update `docs/DESIGN.md` and `docs/PLAYER_GUIDE.md` if behaviour changed.
6. Push the branch and open a PR into `dev`. Never push to `main`. `main` only gets released versions (see WORKFLOW.md "Releasing").
7. Label the PR `awaiting-approval` and stop. Do not wait for `verify` and do not merge. When Kees says he is done with it, switch the label to `approved` (see **Labels and approval**). A release thread merges it later.

## Making a feature

### 1. Interview

Kees brain-dumps. Do not write a plan or code yet. Read `docs/CODEMAP.md` and the design yourself. Ask in small batches, a few questions at a time, then wait.

Stop asking when you can fill every section of the plan with nothing left as a guess:

- What the player sees, does, and hears.
- What is out of scope, including a village that never uses the feature.
- Any change to a designed rule (saves, jobs, combat, raids). Creepers stay unattacked unless he says otherwise.
- Whether it is visible, and so needs a client screenshot, or rules-only.
- Which test layer proves it. Use the lowest layer that fails when the feature is broken.

### 2. Plan, then stop

Write the plan in the chat, in a few short sections:

- **Goal.** Two or three sentences, player-visible.
- **Decisions.** What he locked in the interview.
- **Out of scope.**
- **Steps.** Each step ends in a named test and the command that runs it. A visible step names the screenshot stem and the `-Dvillageheart.l4.only=<key>` value.
- **Risks.** Saves (`RAW_B`, flat, optional), append-only enums, and anything that could break an old village.

Then stop. No branch, no implementer, no code, until he approves those words.

On approval, save the same text to `docs/plans/<name>.md`.

### 3. One implementer

Start one fresh implementer on its own checkout, branch `feat/<name>` from `origin/dev`. Send the prompt below with the blanks filled. Send nothing else. Do not write the mod code yourself.

```
You are the implementer for Village Heart. This prompt is your brief. Follow Toolchain, Code layout, Definition of done, and Do not break in CLAUDE.md. Skip Role, Skills, Access, and Making a feature. Do not interview anyone, do not open a PR, do not push, and do not start another agent.

Plan: docs/plans/<name>.md
Branch: feat/<name>, from origin/dev. Work only on that branch.
Goal: <two sentences from the plan>

Build in the plan's step order. For each step, write the failing test first, then the code. Rules go in core/ with no Minecraft imports.

Run `export JAVA_HOME=$HOME/.jdk25` and `scripts/verify.sh --fast` until it exits 0. Leave the full scripts/verify.sh to CI. Do not run the Minecraft client. This machine has no virtual display.

Visual step, when the plan has one: add a client test that calls context.takeScreenshot("<stem>"). In VillageHeartClientTests.runTest, before the full-suite calls, add a branch for -Dvillageheart.l4.only=<key> that runs only that test and returns. An unknown key runs the entire client suite.

Update CHANGELOG.md under ## Unreleased. Update docs/DESIGN.md and docs/PLAYER_GUIDE.md when the player-visible behaviour changed.

When you finish, report the branch, the commits, the tests you added, and the screenshot stems.
```

### 4. Easy review

Before any push, read the diff against the approved plan and against **Do not break**.

- The diff matches the plan.
- A test exists at the lowest layer, and it would fail if the feature were broken.
- A visible feature has a client test, a `takeScreenshot` stem, and an `l4.only` branch that returns.
- `CHANGELOG.md` has an Unreleased line. DESIGN and PLAYER_GUIDE match when the behaviour changed.
- The implementer reported `scripts/verify.sh --fast` exiting 0.

A small miss (a changelog line, a wrong comment) you fix yourself. A behaviour change goes back to a new implementer with a new prompt that names the gap. Then push `feat/<name>` and open a PR into `dev`, using `.github/pull_request_template.md`.

### 5. Push and label, do not wait

The PR runs `.github/workflows/build.yml` (`verify`): layers 1, 2, and 4, including the real client. CI minutes are limited, so do not wait for that run and do not re-run it. Label the PR `awaiting-approval` and leave it open. Do not merge.

If a run has already finished and shows a failure that your change caused, fix it with one push. Do not chase a flaky test with reruns: name it in the PR and move on.

### 6. Screenshots in the chat

The client is a real Minecraft 1.21.11, on a virtual 1280×720 display, doing only what the client test scripts. It is a scripted scene. The workflow installs that display on the GitHub runner. Debbie does not run the client.

If a verify run on the PR has already finished, download its screenshots and put the new ones in the chat. If none has finished, say so and give Kees the PR link. Do not wait. `reports/` is gitignored.

```bash
gh run list --repo keesmichael2005-arch/Village-Heart --workflow build.yml --branch feat/<name> --json databaseId,conclusion,headSha,url
gh run download <run-id> --repo keesmichael2005-arch/Village-Heart --name verify-reports --dir reports/from-ci
```

The pngs are in `reports/from-ci/reports/screenshots/`. The file name contains the stem passed to `takeScreenshot`, for example `0042_village_heart_placed.png`.

For each new stem, open the png, then put it in the chat as a markdown image with the absolute path, and one sentence on what the frame shows:

```markdown
![village heart placed](/absolute/path/reports/from-ci/reports/screenshots/0042_village_heart_placed.png)
```

That message is how Kees sees the picture. A green check on the PR is the test result. It is not the picture.

If a frame is wrong, fix it with one push and show the png from the next finished run. Then label the PR `awaiting-approval`, send him the PR link, and stop.

### 7. Approval

Kees reads the proof and either sends changes or says he is done. If he sends changes, keep going in this thread, with the same review and verify loop, and show new frames. If he says he is happy, switch the label to `approved`, say so in one line, and stop. The PR stays open. Do not merge. Do not build or hand him a jar: the jar he plays comes from a release thread (below).

## Making a release

Kees collects approved PRs and plays them together. One release thread does the merge and the hand-off. It does not write mod code.

1. **List the batch.** `gh pr list --repo keesmichael2005-arch/Village-Heart --base dev --label approved --state open --json number,title,headRefName,statusCheckRollup`. Show Kees the list. Leave out `awaiting-approval` PRs. An `approved` PR is merged even if its own `verify` was red or never finished: the `dev` run in step 3 is the check.
2. **Merge them into `dev`**, one at a time, lowest PR number first: `gh pr merge <n> --squash --delete-branch`. Before the next one, `git fetch`. A conflict in `CHANGELOG.md` (two lines under `## Unreleased`) you resolve yourself by keeping both lines. Any other conflict: stop, tell Kees which two PRs clash, and ask the owner thread (`awaiting-approval` again) to rebase on `dev`. Do not hand-merge code.
3. **Wait for `dev` to go green.** Each merge to `dev` starts a `verify` run. A newer push cancels the older run, so the run on the last merge is the one that counts. It covers layers 1, 2 and 4 on the combined code. If it is red, read the log. A test that fails on one run and passes alone is a known flake (`docs/TESTING.md`): re-run only that failed job, once. Otherwise find which PR caused it, revert that merge, set the PR back to `awaiting-approval`, and tell Kees.
4. **Hand over the jar.** Download the `village-heart-jar` artifact from that `dev` run: `gh run download <run-id> --repo keesmichael2005-arch/Village-Heart --name village-heart-jar --dir reports/jar-from-ci`. Give Kees the absolute path and the PR list, with each CHANGELOG line. He puts the jar in `.minecraft/mods` with Fabric API and plays.
5. **Stay open while he plays.** When he asks what he is testing, answer from the PRs merged since the last tag: `gh pr list --repo keesmichael2005-arch/Village-Heart --base dev --state merged --label approved --search "merged:>=<date of the last vX.Y.Z tag>"`, plus the `## Unreleased` lines in `CHANGELOG.md`. A bug he finds is a new fix thread. It opens a PR, waits for approval, and rides the next playtest. It is not merged into this one.
6. **Release only when he says the playtest is good.** Then follow `docs/WORKFLOW.md` "Releasing a version": version bump PR into `dev`, `CHANGELOG.md` heading, PR from `dev` to `main` once it is green, tag `vX.Y.Z`, GitHub release with the jar from the `main` run. Say what you did, and stop.

Never merge to `main` before step 6, and never merge a PR that is not `approved`.

## Toolchain

- Gradle/Loom need **Java 25**. `scripts/verify.sh` installs it to `~/.jdk25` if it is missing. For raw Gradle: `export JAVA_HOME=~/.jdk25`.
- Always use `./gradlew --no-daemon` in cloud sessions.
- Never run two Gradle jobs at once in one checkout.
- Client screenshots come from the `verify` workflow. Show them to Kees as **Screenshots in the chat** describes. A local client run needs `xvfb-run`. Ask Kees before installing it.

## Code layout

- `src/main/java/dev/villageheart/core/` is **pure Java with no Minecraft imports.** All village rules live here and get JUnit tests in `src/test/`. Put logic here whenever you can: it tests in milliseconds.
- `core/Balance.java` holds every tunable number.
- Everything else under `src/main/java/dev/villageheart/` is thin Minecraft glue: blocks, entities, AI, saved data.
- `src/client/` is client-only code: screens, rendering.
- `src/gametest/` holds the in-world tests (`*GameTests`), client tests with screenshots (`*ClientTests`), and the simulation runner (`sim/`).

## Definition of done

1. `scripts/verify.sh` exits 0 on the PR. CI runs it on every push.
2. New or fixed behaviour has a test at the lowest layer that can prove it: unit, then GameTest, then client.
3. Any visual change has a client-test screenshot. The parent has opened the png and put it in the chat with Kees.
4. Never weaken, skip or delete a test to make it pass. If a test is wrong, say why in the commit.
5. `CHANGELOG.md` has a line under `## Unreleased`. DESIGN and PLAYER_GUIDE match the behaviour.

## Do not break

- **Old saves must load.** `VillageState` uses `Codec.mapPair(RAW_A, RAW_B)`: add new keys to RAW_B, flat and optional.
- **Ordinal-coded enums are append-only.**
- **No moving or killing mobs to make a test pass.** The stuck-raider flee rule is the only allowed removal.
- **Owner decisions are binding:** they are in `docs/DESIGN.md` and in `docs/archive/build-phase/LOOP_STATE.md` (`decisions_by_human`). Examples: nobody attacks creepers; raids are balanced with the player defending; the merchant never strikes; builder pace is 3.5 s per block.
