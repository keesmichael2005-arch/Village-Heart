verdict: FAIL

# release-prep review, round 2 (verifier)

Reviewed: `release-prep` at 003b702 (the round-1 fixes), with `origin/dev` merged in as b03c824.
- The only merge conflict was `docs/PROGRESS.md`. I resolved it by keeping both sides: dev's art-pass entries, then this branch's entry.
- Diff reviewed: `git show 003b702`, and `git diff origin/dev...HEAD`.
- Still docs and metadata only. The only file under `src` that differs from dev is `src/main/resources/fabric.mod.json`, from round 0.

Three of the four round-1 items are fixed exactly. The version is consistent. One player-facing statement is now wrong, and round 1
caused it: its advisory about the Ranks tab was mistaken, and the builder followed it. That one line is the only fix item.

## Fix list
1. **The guide now says Residents can view the Ranks tab. They cannot.** (`docs/PLAYER_GUIDE.md:39` and `:478`)
   - The guide now says:
     - `:39`, Resident: "view every tab (only the Owner can change ranks)";
     - `:478`: "**Ranks.** Residents and above see the tab; only the Owner can change ranks."
   - The code says:
     - `HeartScreen.tabAllowed` (`client/heart/HeartScreen.java:180-183`) opens Ranks only with `Permission.MANAGE_RANKS`, which is `OWNER` (`core/heart/Permission.java:17`).
     - `selectTab` never shows a locked tab.
     - `HeartSnapshotBuilder:55` sends the member list only to a viewer with `MANAGE_RANKS`.
     - A Steward or Resident sees the Ranks label greyed out, with the tooltip "Only the Owner can manage ranks" (`tab.ranks_locked`).
   - Round 1's advisory ("the Ranks tab is visible but only the Owner can change ranks") misread `tab.ranks_locked`. The original "Ranks. Owner only." was right.
   - Expected:
     - `:39` Resident: "view every tab except Ranks".
     - `:478`: "**Ranks.** Owner only. Others see the tab greyed out ("Only the Owner can manage ranks")."
     - `CHANGELOG.md:17` is already correct; leave it.

Advisory (no change required):
- `PLAYER_GUIDE.md:177` "a hoe is ×1.25" sits right under the material multiplier table. Write "any hoe is a flat ×1.25"
  (`Balance.HOE_BONUS`, "any tier") so nobody multiplies it by the table.
- README promises "the release gate sets it to 1.0.0". Neither the ROADMAP "v1.0 release gate" checklist nor TASK 12 in
  `docs/HANDOFF_GROK.md` names the `gradle.properties` bump. Orchestrator: add "`version=1.0.0` in `gradle.properties`; CHANGELOG
  heading dated" to the release-gate list, so the promise is tracked.

## Round-1 items
| R1 item | Status | Checked against |
|---|---|---|
| 1 Tool text | **Fixed** | `JobType.tool()` (`core/worker/JobType.java:43-50`): lumberjack AXE, miner PICKAXE, hunter BOW_OR_SWORD are required; farmer HOE is `BONUS`; every other job has none. `Balance.HOE_BONUS` = 1.25. The guide's line 177 says exactly this. |
| 2 Cook order | **Fixed** | `CookRecipes` sorts by gain, then by output nutrition, then by id. From the table's own numbers: beef and porkchop 3→8 (+5, out 8); bread 0→5 (+5, out 5); chicken, mutton and salmon 2→6 (+4, out 6); potato 1→5 (+4, out 5); cod 2→5 (+3); rabbit 3→5 (+2); soup 6→6 (0). The new table has the same rows, order and gains. |
| 3 Population growth "in v1, being finished" | **Fixed** | Guide intro (`:7`, "these five"), the tier note (`:321`), "Coming in v1" ("These five", new entry), the CHANGELOG intro and "Still coming in 1.0.0", and the README line 5. The wording matches DESIGN §7: dawn, ≥1 free bed in a valid House, food days ≥3, ≥2 fed and housed adults, at most 1 birth a day. Vanilla breeding still works. M23 is unchecked in ROADMAP, which agrees. |
| 4 Version | **Consistent** | See below. |

**Version judgement.** Keeping 0.1.0 until the release gate is consistent:
- `CHANGELOG.md` is headed "Unreleased (1.0.0)", so it no longer claims a 1.0.0 release.
- `gradle.properties` `version=0.1.0` flows through `${version}` into the jar (`unzip -p` shows `"version": "0.1.0"`) and into the jar name `village-heart-0.1.0.jar`.
- The README names `village-heart-<version>.jar`.
- Nothing else hard-codes a version. `src/gametest/resources/fabric.mod.json` "1.0.0" is the test-only mod and does not ship.
- The one gap is tracking the bump; see the advisory.

## Evidence
- **Build:** `./gradlew --no-daemon build -x test` (JAVA_HOME=~/.jdk25) at b03c824: **BUILD SUCCESSFUL in 5m 52s**, `build/libs/village-heart-0.1.0.jar`.
- The Ranks claim was checked in `HeartScreen.java:180-188` (`tabAllowed`, `selectTab`), `:746-750` (greyed label and locked tooltip),
  `Permission.java:13,17` and `HeartSnapshotBuilder.java:55`.
- `git diff --stat origin/dev...HEAD -- src` lists only `src/main/resources/fabric.mod.json`.
