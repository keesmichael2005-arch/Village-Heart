verdict: FAIL

# art-pass review, round 1 (verifier)

Reviewed: `art-pass` at fd69a9d, merged with `origin/dev` (merge 8370971, only `docs/LOOP_STATE.md` came in from dev).
I reviewed the art pass's own diff (`git diff origin/dev...HEAD`): 9 tool classes under `src/test/java/dev/villageheart/tools/` (one of them
the new `ReferencedTexturesTest`), `ModTextures`, `HeartScreen`, `BuildersTableScreen`, 2 client test files, `build.gradle`, 43 model JSONs,
126 PNGs, and the ART, PROGRESS and ROADMAP docs.

**Short version.** The art is original and renders everywhere. Not one of the 41 screenshots shows the purple-and-black missing texture, the
client log has no model or texture warnings, no gameplay code or test was loosened, and the generators are not in the jar. It fails round 1 on
two of the owner's criteria. **Nine shipped textures are orphaned** (item 1). **Some GUI icons do not read on the dark panels**
(items 2 and 3). All the fixes are small; nothing structural is wrong.

## Runs

| Run | Result |
|---|---|
| `scripts/verify.sh` #1 (full) | exit 1. Layer 1 was red only because `./gradlew build` also runs the GameTests, and one of them, `gate_game_tests_mobs_still_cannot_pass_the_gate`, failed once. The unit tests all passed (188 classes, 0 failures). Layer 2 ran separately in the same script and passed 385/385, that test included. Layer 4 and every gate were green, as was perf |
| `scripts/verify.sh` #2 (full; `build/run/gameTest/world` and `build/run/clientGameTest` deleted first) | **exit 0.** L1 green, L2 385/385, L3 self-test, L4 green, all 4 L4 gates green, perf green, 0 lost wakeups |

The gate test is the flake that is already known on dev: `docs/PROGRESS.md:893` records the same test failing once in a full run and passing
on the rerun. This branch changes no server code (see "Gameplay" below), so the flake is not caused by it.

**Client log.** `Client log has no villageheart model warnings` passed in both runs. I also grepped `reports/clienttest.log` for missing
texture, missing model, `Unable to load model` and `Unable to resolve texture`: no hits. The only WARN lines are the usual offline-mode,
chat-key and JVM `Unsafe` warnings.

**Mutation of `ReferencedTexturesTest`** (both files restored afterwards; `git status` clean). I deleted `gui/sprites/heart/tab_ranks.png` and
`block/marker_edge.png`, and the test went red on both: `ModTextures.java -> .../tab_ranks.png`, and all 42 marker models `edge
villageheart:block/marker_edge`. So the test is not vacuous for the java literals or the model JSONs.

## Screenshots (all 41 looked at, in both runs)

I viewed every run-1 shot at full size: the GUI shots one by one, the world shots in 2×2 sheets. For run 2 I viewed a 41-up contact sheet.
A script scanned every run-2 pixel for the missing-texture magenta and found none. Copies are in `docs/reviews/art-pass-shots/`.

- `0000_village_heart_placed.png`: the new Heart block is a brass cube with a bevel and an inlaid red heart, beside a vanilla bell. It reads
  well from across the field.
- `0002_marker_wall.png`: all 14 marker plaques have new frames and the new `marker_edge`, along with the three job blocks and the hanging
  sign. Every glyph is distinct. The chopping block's side and top read more like coarse dirt than a log (item 5).
- `0003_marker_items.png`: the inventory holds 20 mod items (Heart, Blank Marker, Blueprint, 14 markers, 3 job blocks). Each has its own
  icon, with no missing models.
- `0014_treasury_overview.png`: the Heart overview with tab icons, a tier icon and goal checks (an 8×8 green tick when met, a grey box when
  open), plus red/gold "!" alert icons. All of it is legible, but the tier and alert icons are jammed against the window edge (item 4).
- `0016_builders_table.png`: the Builder's Table with a title icon and an 8px blueprint icon on each row. On the unselected dark row,
  Watchtower's lock icon shows only as an orange dash (item 2).
- `zoom_gui_icons.png`: 2–4× crops of 0016, 0014 and 0015. The lock is the dash, the blueprint icon is a pale dot, the tier and "!" icons sit
  at x=0 against the text, and the round orange `need_food` looks like a coin next to the emerald (item 3).

Everything else (villager badges on robes, the job icon in front of each name on the Villagers tab, building icons, valid/invalid/progress
status icons, rank icons with the online badge, stockpile rows) renders, and its text is uncut and legible.

## Checks

1. **Every referenced texture and model exists.** Yes. `ReferencedTexturesTest` passes and is not vacuous (mutation above). No model
   references vanilla textures any more except the parents `minecraft:block/block` and `cube_all`, which is fine.
2. **Nothing is orphaned.** **No.** Nine PNGs ship in the jar and are referenced by no model, no blockstate and no java code (my own scan
   of every `villageheart:` string in `assets/**/*.json` and every java literal, with the job_/building_ sprites built from enum ids
   counted as used):
   `textures/item/blueprint_quill.png`, `textures/block/builder_scaffold.png`,
   `gui/sprites/heart/activity_{smithing,golem,needs_material,needs_villagers,shelter,no_shelter}.png`, and `icon_refined.png`.
   `docs/ART.md` says this is on purpose: they are drawn in advance for `blueprints-2`, `m19` and `m25`, and the refined icon is "nowhere (on
   purpose)". All the same, they are dead weight in v1 and no test would catch the next orphan.
3. **The art is original.** Yes. Every PNG except `icon.png` is drawn pixel by pixel by the generators, from Java string bitmaps and a palette
   (`ArtKit`, `GuiArt`, `ExtraArt`, …). No generator reads an image (`ImageIO.read` appears only in `GeneratedAssetsTest`, which compares
   outputs). `icon.png` is byte-identical to this project's own `release-prep` icon. I compared all 126 mod PNGs against all 3383 vanilla
   textures of the same size in the 1.21.11 client jar. There is no exact match. Counting only non-transparent pixels, the highest overlap is
   23% (`market_stall_awning` against `lightning_rod_on`, from plain white pixels), and every other PNG is at 5% or less. The chopping block
   and marker edge no longer sample `oak_log`, `stripped_oak_log` or `iron_block`.
4. **Gameplay and tests.** No change under `src/main/java`, `core/` or `data/`, and the generator's recipe, loot and tag output is unchanged.
   Client changes are drawing only: `drawNeed` now blits a sprite instead of `renderItem`, and two row texts move right to make room for an
   icon. No test was deleted or loosened, and no assertion was removed. The full L4 run order is unchanged. `-Dvillageheart.l4.only=art` adds
   an opt-in subset that `verify.sh` does not use. `markerItems` now also gives a Heart and a Blueprint; the Heart was already in its
   missing-model list, but the Blueprint is not (item 6).
5. **The generators are kept out of the jar.** Yes. They live in `src/test/java`. `unzip -l build/libs/village-heart-0.1.0.jar` lists no
   `tools/` classes; only the PNGs they wrote ship.

## Fix list

1. **Orphaned textures ship in the jar** (`src/main/resources/assets/villageheart/`, `AssetGenerator`). Expected: nothing under
   `src/main/resources` that nothing uses. Have `AssetGenerator` write the 9 staged files (`item/blueprint_quill`, `block/builder_scaffold`,
   the 6 `activity_*` sprites, `icon_refined.png`) to a staging folder outside the resources, e.g. `build/asset-preview/staged/` or
   `docs/art-staging/`. The branch that wires each one moves it in. Then add an orphan check to `ReferencedTexturesTest`: every PNG under
   `assets/villageheart/textures` must be named by a model, by a `ModTextures` literal, or by the job_/building_ enum loop, or the test fails.
   Update the `docs/ART.md` table to match.
2. **Dark outlines vanish on the dark GUI panels** (`GuiArt`, `textures/gui/sprites/table/locked.png`, `table/blueprint.png`, and every other
   sprite outlined in ink `#120C10`). On the unselected `0x99000000` rows, `locked` shows only its gold body as an orange dash, so it no
   longer reads as a lock; `blueprint` shrinks to a pale dot. Expected: each 8×8 GUI sprite keeps its silhouette on both the dark list
   background and the `#4A4A4A` selected row. Either drop the ink outline from GUI sprites or use a light one (for example `#5A4A3A` or the
   light band), then re-check 0016 and 0018 in a fresh L4 run.
3. **The food need icon reads as a coin** (`GuiArt`, `heart/need_food.png`). It is a round orange disc with a dot, beside the emerald for
   wages, so at a glance it says "money". Expected: an unmistakable food shape, such as a loaf or a drumstick. Check it in `0011` and `0015`.
4. **Tier and alert icons are drawn at x=0 and missing from the layout check** (`HeartScreen.java`: the tier blit
   `ModTextures.blit(g, ModTextures.tier(o.tier()), 0, y + 12, 8)` and the alert blit `..., 0, ay, 8)`). The icon touches the window edge
   and the text starting at `MARGIN` (8) with a 0px gap. Expected: put the icon at `lx` and move the text to `lx + 10`, as the goal rows
   already do. Also call `note(...)` for the new tab, tier, alert and status (`x + 240`) icons so that `layoutProblems()` checks them, as
   it already checks the job, building, rank and need icons.
5. **The chopping block side reads as dirt** (`JobBlockArt`, `textures/block/chopping_block_side.png`, `_top.png`). In 0002 the side is a
   dark speckle very close to coarse dirt. Expected: bark with vertical grain on the side and rings on the top, so it reads as a stump. Cosmetic.
6. **The Blueprint is given but not checked** (`MarkerClientTests.markerItems`). It now gives `villageheart:blueprint`, but `ids` does not
   contain `"blueprint"`, so a missing blueprint item model would only show in the screenshot. Expected: add `"blueprint"` to `ids`.

Items 1–4 block the merge; items 5 and 6 are small and can ride along. After the fixes: a full `scripts/verify.sh` exit 0, and a fresh look at
0003, 0011, 0014 and 0016.
