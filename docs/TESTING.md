# Testing layers

| Layer | What | Command | Time |
|---|---|---|---|
| 1 | Unit tests on `core/` (pure logic) | `./gradlew build` | seconds |
| 2 | GameTests: headless server, real blocks/entities/ticks | `./gradlew runGameTest` | ~1 min |
| 3 | Long simulation runs: seeded world, claim a real village, fast-forward days, write metrics JSON | *(milestone: not built yet)* | 10–30 min |
| 4 | Client GameTests: real game on Xvfb, scripted, screenshots | `xvfb-run -a ./gradlew runClientGameTest` | ~2 min |

`scripts/verify.sh` runs layers 1, 2 and 4 and writes `reports/verify-summary.md` plus `reports/screenshots/`.
GitHub Actions runs the same script on every push.

Proven at setup: a deliberately broken GameTest fails the run (exit 1), so a green run means something.

## GameTests that touch the village claim (M1+)
The claim is global (one per world) and vanilla runs GameTests of the same test environment at the same time, so
claim tests would interfere. **Rule:** every test that reads or writes the claim declares its own environment,
`@GameTest(environment = "villageheart-gametest:<name>")`, backed by a JSON file
`src/gametest/resources/data/villageheart-gametest/test_environment/<name>.json` containing
`{"type": "minecraft:all_of", "definitions": []}`. It must call `VillageTestSupport.reset(h)` first and
`VillageTestSupport.done(h)` last. Use `VillageTestSupport.player(h, name, mode)` for players (a real `ServerPlayer`; the
vanilla mock player is always creative). `reset` clears the claim, leftover players, emerald items, the block-drops gamerule, and all POIs
(bells) within 48 blocks of the test, because bells of neighbouring or failed tests otherwise leak into "no bell nearby" checks.
Approach used in M1: custom environments (the documented single-sequence fallback was not needed). Probe tests `isolationProbeA/B` guard it.
Tests that wait need `maxTicks` set on the annotation (the default is short).
