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
