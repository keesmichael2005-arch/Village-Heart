verdict: PASS

# art-pass review, round 2 (verifier)

Reviewed: `art-pass` at db81919 (round-1 fixes b52fbf4 and db81919), merged with `origin/dev` as 25e026a. The only conflict was
`docs/PROGRESS.md`, and both sides were kept. `docs/LOOP_STATE.md` is byte-identical to `origin/dev`. The final verify ran at bba04a1,
which adds only the review screenshots.

**Short version.** All six round-1 items are fixed, and each fix is checked by a test or a screenshot I looked at. No gameplay code,
data or test was loosened. A full `scripts/verify.sh` exited 0. It took four full runs to get one exit 0, but every red came from one of two
flaky tests that this branch does not touch (details under "Runs"). No fix items.

## Runs

| Run | Commit | Result |
|---|---|---|
| 1 | a48306c (merge, before the author fix) | exit 1. **L1 red only**: `./gradlew build` ran the GameTests and `heart_action_game_tests_rate_limited` failed ("Expected one WARN for 50 forbidden requests to be 1: was 2"). L2 in the same script passed 388/388, the same test included. L4, all gates and perf were green, with 41 screenshots |
| 2 | 25e026a | exit 1. **L2 red only**: the same `rate_limited` failure. L1 was green (that test passed inside `build`). L4, all gates and perf were green, with 41 screenshots |
| 3 | 25e026a | exit 1. L1 and L2 were green (388/388). **L4 red**: `GuardClientTests` threw "client side differs: the night guard is not at its bed on the client". The server-side check just before it had passed (the guard was in its bed), so the client's copy of the position had not caught up. L4 stopped there, so the 2-player session never ran and its log gate failed with "begin/end markers missing" |
| 4 | bba04a1 | **exit 0.** L1, L2 388/388, L3 self-test, L4 (41 screenshots, 3 lost wakeups rescued), all gates, perf (worst 4039 us) |

Neither flake comes from this branch:
- `rate_limited` asserts on `HeartActions.warnCount`, a static counter that any GameTest running at the same time can bump.
  `docs/PROGRESS.md:893` already records it failing once and passing on the rerun. `git diff origin/dev HEAD -- src/main/java` is empty.
- `GuardClientTests` is unchanged. The branch's `VillageHeartClientTests` diff only moves the first screenshot into `placedHeart()` and
  adds the opt-in `-Dvillageheart.l4.only=art` subset. The full run order is the same as on dev.

Both are worth an orchestrator ticket (make `warnCount` per test or per player, and have the guard client test wait for the client
position to match), but neither blocks this branch.

## The six round-1 items

1. **Orphaned textures. Fixed.** The nine staged files (`item/blueprint_quill`, `block/builder_scaffold`, six `activity_*`,
   `icon_refined.png`) are now under `docs/art-staging/` and are gone from `src/main/resources`. `unzip -l build/libs/village-heart-0.1.0.jar`:
   0 hits for `activity_|blueprint_quill|builder_scaffold|icon_refined|/tools/`. The jar holds 116 texture PNGs, the same as the source tree.
   The new `ReferencedTexturesTest.noOrphanTextures` collects every `villageheart:` texture in the model and blockstate JSON, every
   `textures/...png` literal in `src/main` and `src/client`, and the enum-built job, building and profession paths.
   **Mutation:** I copied `blueprint_quill.png` back into `textures/item/`, and `noOrphanTextures` failed with "textures nothing references:
   textures/item/blueprint_quill.png" (`GeneratedAssetsTest.committedFilesMatchGenerator` failed too). I removed it and `git status` was clean.
   `GeneratedAssetsTest.stagedFilesMatchGenerator` checks that the staged files match the generator pixel for pixel. `docs/ART.md` is updated.
2. **Dark outlines on dark panels. Fixed.** `GuiArt` recolours ink to cream on every GUI sprite. `guiOutlinesAreLightAndFoodIsALoaf`
   fails if any sprite, live or staged, still has an `ArtKit.INK` pixel. I composited the sprites at 8× on black, `#4A4A4A` and light grey,
   and in-game checked 0016, 0017 and 0018 (`r2_zoom_fixes.png`, top two strips). The blueprint sheet has a cream frame with a blue rule, and
   the padlock has a shackle and a gold body. Both read on the dark rows and on the selected grey row (0018 has Watchtower selected).
3. **Food icon reads as a coin. Fixed.** `need_food` is now a wide oak-coloured loaf with a slash of crust. The test asserts it is at
   least 4 px wider than it is tall. In 0009, 0011, 0015 and 0029–0036 it sits beside the bed and the emerald and reads as bread, not money.
4. **Tier and alert icons at x=0. Fixed.** `HeartScreen` now blits both at `lx` and starts the text at `lx + 10`. Tab, tier, alert and the
   two `x + 240` status icons are passed to `note(...)`, so `layoutProblems()` checks them. In 0010 and 0014 the cottage and the red and gold "!"
   line up with the goal checks, with a gap before the text (`r2_zoom_fixes.png`, middle strip).
5. **Chopping block reads as dirt. Fixed.** In 0002 and 0005 the side has vertical bark grain and the top has rings. It reads as a stump
   with the axe in it (`r2_zoom_fixes.png`, bottom).
6. **Blueprint not checked. Fixed.** `MarkerClientTests.markerItems` now has `"blueprint"` in `ids`, so the missing-model check covers it. It
   passed in runs 1, 2 and 4, and the sheet icon is in the 0003 hotbar.

## Screenshots

I looked at all 41 from run 1 one by one (the GUI shots at full size or in 2×2 sheets, plus 2–3× crops of every icon area). I checked runs 2
and 4 as 41-up contact sheets. A script found no missing-texture magenta in runs 1, 2 or 4. The client log of run 4 has no `missing
texture`, `missing model`, `Unable to load model` or `Unable to resolve texture` lines, and `Client log has no villageheart model
warnings` passed. Everything listed as good in round 1 still renders the same: heart block, marker plaques, robes, rank and job icons,
stockpile rows, builds.

Copies are in `docs/reviews/art-pass-shots/`, under `r2_*`. The round-1 copies are kept beside them for comparison:
`r2_0002_marker_wall.png`, `r2_0003_marker_items.png`, `r2_0014_treasury_overview.png`, `r2_0015_treasury_villagers.png`,
`r2_0018_builders_table_watchtower.png`, and `r2_zoom_fixes.png` (crops at 2× of 0016, 0018, 0014, 0015 and 0002).

## Gameplay and tests

- `git diff origin/dev HEAD` under `src/main/java`, `src/main/resources/data` and `src/test/java/dev/villageheart/core` is empty.
- Round-2 test changes only add checks: `noOrphanTextures`, `stagedFilesMatchGenerator`, `guiOutlinesAreLightAndFoodIsALoaf`, and the
  `"blueprint"` id. No assertion was removed or relaxed in b52fbf4 or db81919. The one removed assertion in `77e7b1b..HEAD`
  (`DefendedScenarioTest`, "defended stays the harsh case") came in from dev's TASK 8 (`0be115e`), and the file is identical to dev.
- `build.gradle` changes only the `genAssets` task: it gains a third arg for `docs/art-staging`. The jar is unaffected, and the generators
  stay in `src/test`.

## Fix list

None.

Cosmetic, not blocking: on the light vanilla inventory background a cream outline would vanish, but no mod sprite is drawn on a light panel
today. If a later screen does that, give it the dark outline back.
