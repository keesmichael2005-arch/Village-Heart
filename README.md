# Village Heart

A Minecraft (Fabric 1.21.11) mod: craft a **Village Heart**, place it by a village's bell, and that
village becomes yours. Hire villagers as lumberjacks, miners, farmers, hunters, guards and builders,
put up workplaces, pool resources and help the village prosper. Think *Medieval Dynasty*, inside a
vanilla village.

Status: **0.1.0 (scaffold)**. The block exists and claims a village when placed near a bell.

## Recipe
```
G E G      G = gold ingot
E D E      E = emerald
G E G      D = diamond
```

## Ranks
The one village can be run by several players: **Owner**, **Steward** (hires and fires), **Resident** (opens the village's chests) and **Visitor**
(the default). The Owner sets ranks in the Ranks tab of the Heart screen and can hand the village to a Steward or Resident. Visitors cannot open or
break containers inside the village's buildings. **This is not grief protection**: hoppers, explosions, pistons and golems are not stopped.

## Development
```bash
scripts/verify.sh      # build + all automated tests (see docs/TESTING.md)
```
Built mostly by AI agents in a build → verify loop. See `CLAUDE.md` and `docs/`.
