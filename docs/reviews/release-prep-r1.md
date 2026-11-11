verdict: FAIL

# release-prep review, round 1 (verifier)

Reviewed: `release-prep` at b86beed with `origin/dev` (c7f109b) merged in (a clean merge, no conflicts), using `git diff origin/dev...HEAD`.
Task: TASK 5 in `docs/HANDOFF_GROK.md` (docs and metadata only).

The guide is close: almost every number in `docs/PLAYER_GUIDE.md` and `CHANGELOG.md` matches `Balance`, the room rules, the recipes and
the lang file. Three things are wrong and one spec item is missing, so this round is FAIL. All four are small doc edits.

## Fix list
1. **`docs/PLAYER_GUIDE.md:177` describes a tool mode no job uses.** It says "Some jobs work at half speed with no tool. Others do nothing
   until they have one."
   - In `core/worker/JobType.java:45-48`, lumberjack (axe), miner (pickaxe) and hunter (bow or sword) are REQUIRED, and the farmer's hoe is BONUS.
   - `NO_TOOL_SPEED` (0.5, `Balance.java:74`, mode `OPTIONAL`) exists, but no job uses it.
   - Expected: "The lumberjack, miner and hunter do nothing without their tool. The farmer works at full speed without a hoe; a hoe is ×1.25."
2. **`docs/PLAYER_GUIDE.md:237-244`, the cook recipes: the order contradicts its heading "best nutrition gain first".**
   - `core/cook/CookRecipes.java:36-38` sorts by gain (output minus input nutrition), then by output nutrition.
   - The real order is: beef and porkchop (+5), **bread (+5)**, then chicken, mutton and salmon (+4), baked potato (+4), cod (+3), rabbit (+2), beetroot soup (0).
   - The table puts every cooked meat first, bread after the potato, and cod and rabbit above the potato. Either list the real order or drop the claim.
3. **Population growth is not marked as "in v1, being finished".** TASK 5 names five features for that label: sieges, random raids, wall
   damage, smith **and population growth**.
   - The guide's "Coming in v1" (`PLAYER_GUIDE.md:481-491`, "These four") and its intro (`:7`) list only four.
   - `CHANGELOG.md` ("Still coming in 1.0.0") lists the same four.
   - `README.md` (line 5) also lists four.
   - The only trace is `PLAYER_GUIDE.md:318` ("does not yet start a birth by itself").
   - The code agrees growth is not built: M23 is unchecked in ROADMAP, and the only hook is a comment in `economy/VillageDawn.java:14`.
   - Expected: add a "Population growth" entry to all three lists. Per DESIGN §7: at dawn, a free bed and spare food start at most one
     birth a day. Keep the existing note that vanilla breeding still works.
4. **The version does not match.**
   - `CHANGELOG.md` is headed "1.0.0 — 2026-11-11".
   - `gradle.properties` still has `version=0.1.0`, so `fabric.mod.json` (`${version}`) and the jar say 0.1.0, and `README.md` names `village-heart-0.1.0.jar`.
   - Either head the changelog "Unreleased (1.0.0)", or note that TASK 12 (release gate) bumps `version`.
   - If the jar name stays in the README, make it version-neutral (`village-heart-<version>.jar`).

Advisory (no change required):
- `PLAYER_GUIDE.md:39` says Residents "view every tab" and `:475` says "Ranks. Owner only."; reword one so they do not read as a contradiction
  (the Ranks tab is visible but only the Owner can change ranks, `en_us.json:111`).
- "Any natural log" for market lots means the nine overworld log types in `core/market/PriceTable.java:13`; stems and stripped logs
  do not sell.
- TASK 5 did not touch `docs/PROGRESS.md`; CLAUDE.md's definition of done asks for an entry.

## Evidence
- **No Java changed:** `git diff --stat origin/dev...HEAD -- src/main/java src/test src/gametest src/client` is empty. The diff is
  `CHANGELOG.md`, `README.md`, `docs/PLAYER_GUIDE.md`, `icon.png` and `fabric.mod.json`.
- **Build:** `./gradlew --no-daemon build -x test` (JAVA_HOME=~/.jdk25) is BUILD SUCCESSFUL in 7m 20s and writes `build/libs/village-heart-0.1.0.jar`.
- **`fabric.mod.json`:**
  - The description fits the mod. `authors` is `["kees"]`.
  - `contact.homepage`, `sources` and `issues` point at the GitHub repository.
  - The depends are `fabricloader >=0.20.1`, `minecraft ~1.21.11` and `java >=21`. They match `gradle.properties` (loader 0.20.1, Fabric API 0.141.6+1.21.11) and the README's requirements.
- **Icon:** `assets/villageheart/icon.png` is 128x128 RGBA with 17 colours, pixel art on a 32x32 grid scaled ×4.
  - I opened it at 512 px. It shows a gold bell with a wooden yoke and a red heart on the bell's body, on a dark-blue square.
  - It is drawn for this mod: not the vanilla bell texture, and no third-party art.
  - The heart is a little lopsided (the left lobe is notched). That is cosmetic and not a fix item.
- **Numbers checked against code** (a full cross-check of every numeric claim; the errors above are what failed):
  - Heart recipe; bell distance 16; claim 80 and outdoor sites 128.
  - The rank matrix, the 63-rank limit, and the old Owner becoming a Steward.
  - The marker recipes and the whole building table: floors 12/9/16/12, required blocks, tiers.
  - Room limit 4096 air.
  - Mineshaft 12 deep with 8 rails; Hunting Ground 40 blocks; Training Yard 2 dummies.
  - Day 1000/11000/12000.
  - Tool multipliers. XP 100/300, speeds 1/1.25/1.5, level caps 1/2/3. Self-made tools 2400/4800 ticks.
  - Lumberjack 80 base ticks per log (40 with stone); farmer radius 24, hoe ×1.25; miner haul 4000 and the haul table.
  - Hunter: 2/3/3 trips and the loot ranges. Cook: 125 ticks per item, batch 8, fuel 1600/300/200.
  - Nutrition 10/5; hunger 0.5/0; unhoused ×0.75.
  - Treasury cap 100,000, wages 1/1/2, tier goals 6/3/3 and 12/8/2/7/32.
  - Market lots, 600-tick sales, caps 8/12/16 and 32, reserves 3 days and 64.
  - Blueprints, wall max 32, builder 1 block a second.
  - Guards: health 20/30/40, damage +0/+1/+2, the n/4 shift split, rations 12, retreat 0.5, alarm 600 ticks, creepers ignored.
  - Gate radius 8, lamp radius 12, 64 remembered sites. Smith "coming" numbers (36 ingots and a pumpkin, 1 per 5 villagers, 25 HP per ingot).
  - Commands: `/villageheart owner` exists (op only), as do the Overview, Villagers, Buildings, Stockpile and Ranks tabs. No invented command or tab.
