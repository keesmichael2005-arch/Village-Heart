# Art

Custom textures for Village Heart. Every PNG under `src/main/resources/assets/villageheart/` except `icon.png` is drawn by the Java generators in `src/test/java/dev/villageheart/tools/` and checked pixel for pixel by `GeneratedAssetsTest`. Regenerate with:

```
export JAVA_HOME=~/.jdk25
./gradlew --no-daemon genAssets
```

`ReferencedTexturesTest` fails if a model, `fabric.mod.json`, or a GUI sprite path names a texture that is not a file. A miss is the purple-and-black missing texture.

## Style

Simple vanilla pixel art. Items and block faces are 16×16. GUI sprites that sit next to 9px text are 8×8 and drawn at 8, so they are not scaled. The three need icons are 16×16 because they replace a 16px item render. The mod icon is 128×128.

The palette is the release-prep icon: navy `#1B283A`, gold `#F6D87A` / `#D6A434` / `#B07C24` / `#704816`, heart red `#C62A36` / `#F48480` / `#6E1220`, oak `#BA844A` / `#8A562E`, ink `#120C10`. Iron, emerald, stone, and wheat sit next to those. Light is a flat band on the top and the left. Dark is a flat band on the bottom and the right. No gradients, no anti-aliasing.

Silhouettes are shared on purpose. A job, the villager badge, and the workplace icon use the same shape (wheat, axe, pot, hammer, pick, bow, shield, emerald, anvil). A place that is not a job (house, storage, mineshaft, hunting ground, training yard) has its own shape. Marker plaques keep the glyph shapes the distance tests already lock; only the frame, the background, and an edge highlight changed. Profession glyphs are the same bitmaps as before; the robe gained a fold and a hem.

Nothing here is a copy of a vanilla texture. The chopping block used to sample `oak_log`, `oak_log_top`, `stripped_oak_log`, and `iron_block`. The marker plaque edge used `stripped_oak_log`. Those faces are original now.

## What was audited

`dev`, plus read-only `origin/m19`, `origin/m23`, `origin/m25`, `origin/m25-walls`, `origin/blueprints-2`, `origin/backlog-small`, and `origin/release-prep`. `origin/m26-scenarios` does not exist.

On `dev` before this pass: the heart block was a flat green square, the mod icon was a two-colour "Mod ID" card, markers and job blocks and profession overlays were flat fills, the chopping block and the marker edge borrowed vanilla textures, and both GUIs were coloured rectangles plus, on the Heart screen, `renderItem` of bread, a red bed, and an emerald. Stockpile rows still render the real item stack. That is the item, not a placeholder.

`m19` adds no item or block texture. The blacksmith crafts vanilla tools. New activity lines: smithing, building a golem, needs material, needs villagers, nothing to smith. `m25` adds sheltering and no shelter. `m23`, `m25-walls`, and `backlog-small` add no art. `release-prep` changes only `icon.png`. `blueprints-2` adds the Blueprint Quill (its model still samples the blueprint sheet) and the Builder Scaffold (a cube of `minecraft:block/bamboo_block`).

The blueprint footprint is still coloured vanilla dust. It has no texture slot. A custom particle type would be new behaviour, so it was left.

## Wired on this branch

| Asset | Path | Where it is used | Before | Now |
| --- | --- | --- | --- | --- |
| Mod icon | `assets/villageheart/icon.png` | `fabric.mod.json` | "Mod ID" placeholder | Exact bytes of the release-prep heart-over-a-bell. Not generated |
| Village Heart | `textures/block/village_heart.png` | `models/block/village_heart.json` (`cube_all`); the item uses that model | flat green square | brass cube, bevel, inlaid heart |
| Blank Marker | `textures/item/blank_marker.png` | `models/item/blank_marker.json` | flat tan square | oak plaque, empty recess, four brass nails |
| Blueprint | `textures/item/blueprint.png` | `models/item/blueprint.json` | flat blue sheet | folded sheet, white plan, brass pin |
| Marker plaques | `textures/block/marker/<building>_<unchecked\|invalid\|valid>.png` | each marker block model `front` | flat glyph | same glyph, bevelled ground, lit edge. Frame colours stay the test values (grey, red, green) |
| Marker edge | `textures/block/marker_edge.png` | every marker model `edge` | `minecraft:block/stripped_oak_log` | oak grain. The face is 1px thick, so the texture is a material, not a picture |
| Builder's Table | `textures/block/builders_table_{top,side,front}.png` | `models/block/builders_table.json` | flat planks and a blue top | planks, pinned plan and ruler, drawer, hammer and a rolled plan |
| Market stall | `textures/block/market_stall_{front,side,awning}.png` | `models/block/market_stall.json` | flat planks, flat red/white awning | apple crate and an emerald on the counter (rows 0–3 are the rail; the front UV starts at y=4), post, striped awning |
| Chopping block | `textures/block/chopping_block_{top,side,handle,head}.png` | `models/block/chopping_block.json` | vanilla log and iron | growth rings and two axe scars on top, vertical bark grain on the side, a wood handle, an iron head. Handle and head are simple materials because the model squashes the whole texture onto a thin cuboid |
| Professions | `textures/entity/villager/profession/<job>.png` | the villager profession overlay | flat apron and a 5×5 glyph | same glyph and sash, with a fold and a hem on the front of the robe |
| Heart tabs | `textures/gui/sprites/heart/tab_{overview,villagers,buildings,stockpile,ranks}.png` | `HeartScreen` tab row, via `ModTextures` | text only | heart, villager, house, chest, crown. 8×8 |
| Tiers | `textures/gui/sprites/heart/tier_{hamlet,village,town}.png` | overview, next to the tier name | text only | cottage, hall, tower |
| Ranks | `textures/gui/sprites/heart/rank_{owner,steward,resident,visitor}.png` | member rows | a coloured pip | crown, key, door, shield |
| Goals | `textures/gui/sprites/heart/goal_{met,open}.png` | overview goals | a filled or empty box | green check, empty stone box |
| Alerts | `textures/gui/sprites/heart/alert_{danger,warning}.png` | overview alerts | a red or gold bar | red or gold exclamation at the left margin; the text starts ten pixels to the right |
| Row status | `textures/gui/sprites/heart/status_{valid,invalid,unchecked,progress,waiting,blocked}.png` | building rows and build rows | coloured text only | check, cross, dashed box, hammer, hourglass, stop sign. Deferred buildings use the unchecked mark |
| Jobs | `textures/gui/sprites/heart/job_<job>.png` | villager rows, one per `JobType` | text only | the shared silhouette |
| Buildings | `textures/gui/sprites/heart/building_<type>.png` | building rows, one per `BuildingType` | text only | the same silhouette, or a place of its own |
| Needs | `textures/gui/sprites/heart/need_{food,bed,pay}.png` | villager need slots | `renderItem` of bread, red bed, emerald, plus a red slash when unmet | a long oak loaf, a bed, an emerald. The slash is still drawn when the need is unmet |
| Builder's Table GUI | `textures/gui/sprites/table/{blueprint,locked,materials}.png` | `BuildersTableScreen` title, blueprint rows, materials header | text only | sheet, padlock, planks |

Sprites are blitted with `GuiGraphics.blitSprite` at their native size. The id is the path under `textures/gui/sprites/` without `.png`. Outlines are cream (`#F4F0E4`), not ink, so the shape still reads on an unselected row (`0x99000000`) and on the selected row (`#4A4A4A`).

`ReferencedTexturesTest` fails if a PNG under `assets/villageheart/textures` is not named by a model, by a `textures/...png` literal, by the job or building sprite id, or by the profession overlay path Minecraft loads from the profession id.

## Ready, wire in when that branch merges

These files are generated into `docs/art-staging/` and are not in the jar. Copy one to `src/main/resources/assets/villageheart/` (same relative path) when that branch merges, then wire it. That branch's code was not edited.

| Asset | Path | Branch | How to wire it |
| --- | --- | --- | --- |
| Refined icon | `docs/art-staging/icon_refined.png` | — | A redraw of the heart-over-a-bell. The mod icon stays the release-prep file |
| Blueprint Quill | `docs/art-staging/textures/item/blueprint_quill.png` | `blueprints-2` | In `models/item/blueprint_quill.json`, set `layer0` to `villageheart:item/blueprint_quill`. It currently points at `villageheart:item/blueprint` |
| Builder Scaffold | `docs/art-staging/textures/block/builder_scaffold.png` | `blueprints-2` | In `models/block/builder_scaffold.json`, set `all` to `villageheart:block/builder_scaffold`. It currently points at `minecraft:block/bamboo_block` |
| Smithing | `docs/art-staging/textures/gui/sprites/heart/activity_smithing.png` | `m19` | Blit beside `activity.smithing` |
| Building a golem | `docs/art-staging/textures/gui/sprites/heart/activity_golem.png` | `m19` | Blit beside `activity.building_golem` |
| Needs material | `docs/art-staging/textures/gui/sprites/heart/activity_needs_material.png` | `m19` | Blit beside `activity.needs_material` |
| Needs villagers | `docs/art-staging/textures/gui/sprites/heart/activity_needs_villagers.png` | `m19` | Blit beside `activity.needs_villagers` |
| Sheltering | `docs/art-staging/textures/gui/sprites/heart/activity_shelter.png` | `m25` | Blit beside `activity.sheltering` |
| No shelter | `docs/art-staging/textures/gui/sprites/heart/activity_no_shelter.png` | `m25` | Blit beside `activity.no_shelter` |

`m19` does not add a smith item. There is no item texture to draw for it. `activity.no_work.blacksmith` stays text; the anvil sprite above is the smith's mark.

## Generators

| Class | Draws |
| --- | --- |
| `ArtKit` | palette, planks, bevel, the sprite alphabet |
| `HeartBlockArt` | the heart block face |
| `MarkerArt` | the 14×3 plaques |
| `JobBlockArt` | table, stall, chopping block, blank marker |
| `BlueprintArt` | the blueprint item |
| `ProfessionArt` | the nine robe overlays |
| `ExtraArt` | marker edge, quill, scaffold, refined icon |
| `GuiArt` | every wired GUI sprite, and the staged activity badges |
| `AssetGenerator` | writes the PNGs and the generated JSON, writes `docs/art-staging/`, and deletes a generated file that the generator no longer claims |

`icon.png` is not claimed. The generator will not overwrite it or delete it.
