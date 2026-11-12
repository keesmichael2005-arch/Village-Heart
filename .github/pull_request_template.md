## What and why

<!-- One or two sentences: the bug or change, and how it shows in game. -->

## How it was tested

- [ ] A test shows the bug or the new behaviour (layer: L1 unit / L2 GameTest / L4 client):
- [ ] `scripts/verify.sh` exit 0 (CI green)
- [ ] Visual change: screenshot looked at (name it), or no visual change

## Checklist

- [ ] Branch is from `dev`, and this PR targets `dev` (only release PRs target `main`)
- [ ] `CHANGELOG.md` has a line under `## Unreleased`
- [ ] `docs/DESIGN.md` / `docs/PLAYER_GUIDE.md` updated if behaviour or a number the player sees changed
- [ ] No test weakened, skipped or deleted
- [ ] Old saves still load (new save keys in RAW_B, optional)
