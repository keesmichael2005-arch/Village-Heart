# Field and flags

## Goal

A farmer tends the whole connected field, including the part that steps outside the circle around the composter, and stops where the farmland breaks. A lumberjack cuts only inside a circle around a chopping flag the player places, and a hunter walks to a hunting flag, vanishes, and comes back with the same haul. Both flags can sit out to the edge of the outdoor sites, and a long walk sends that worker home in time for the end of the work day.

## Decisions

- The Farmhouse stays an enclosed room. The seed is the farmland already in range: 24 blocks horizontally and 8 blocks of height from each composter. From those cells, farmland that shares a side is included, including a step of one block up or down. A two-block rise stops the field. A diagonal touch stops it. A one-block gap of path, fence, or water stops it. Every farmer of that Farmhouse shares one field. The cap is 4096 cells, nearest to a composter first. Past the cap, the rest is left.
- The Lumber Camp stays an enclosed room. It cuts nothing until it has a chopping flag. The old circle around the Chopping Block is gone. The circle is 32 blocks horizontally and 16 blocks vertically of the flag. One flag per camp. A second grove is a second camp.
- The hunting flag is today's Hunting Ground. The block id `hunting_ground` and the feather-and-stick recipe stay, so an old ground keeps working. The name the player sees is Hunting Flag. The 40-block link to the lodge is gone. The requirement for 40 grass, leaf, or log blocks is gone. Hunters walk to the flag, vanish for the same trip, and return with the same haul. The meat still follows the biomes around the flag. No animals appear. A lodge is a valid room without a flag. The hunter waits, and the Villagers tab says "Needs a hunting flag". A lumberjack in the same spot says "Needs a chopping flag".
- The chopping flag is a new outdoor plaque. Hamlet tier. Recipe: a Blank Marker in the centre, logs in the corners, saplings on the edges. It is placed on a post or a fence.
- Both flags may sit up to 128 blocks from the Heart, the same edge as a Mineshaft. There is no extra limit from the camp or the lodge.
- The first flag of a job links to the nearest camp of that job that has none. The link is the outdoor site's existing saved link, and it stays until that flag is picked up. A flag with no free camp waits. When a camp is free, the nearest waiting flag takes it. Two flags the same distance away: the one registered first wins. A living link is never moved to a different camp.
- They are back at the workplace when the work day ends. The walk home is straight-line distance times 10 ticks a block. A short walk leaves the day as it is. A long one starts no new hunt, and no new tree, unless the work and the walk back both finish in time. The lumberjack's existing stop on new trees at day time 5000 stays, and the earlier of the two limits wins. A tree already started is finished, then they walk. A hunter already out comes back with no haul when staying would make the return late. The trip out counts as well: a hunt starts only when the walk there, the time away, and the walk back all fit.
- The Mine and the Mineshaft stay as they are.

## Out of scope

- An open camp with no roof. A guard walking them home. A circle that grows when you hire another worker. A painted region. Animals you can watch.
- The cook, smith, builder, merchant, guard, and miner.
- The open guide pull request. This work updates `docs/DESIGN.md` and `docs/PLAYER_GUIDE.md`. If the in-game guide is already on `dev` when it is built, three hints change with it: the field is the connected farmland, the lumber camp needs a chopping flag, and the hunting flag does not have to stand beside the lodge. If that guide is not on `dev` yet, those lines wait.

## Steps

1. The connected field, as a pure rule: seed disc, side-by-side steps of one block, cap 4096. Test `FieldReachTest`. `export JAVA_HOME=$HOME/.jdk25 && ./gradlew --no-daemon test --tests dev.villageheart.core.farm.FieldReachTest`
2. The farmer uses that field, one set for the whole Farmhouse. A crop touching the disc and sitting outside the 24 blocks is harvested. A crop past a one-block gap is left. Test `FarmerGameTests.connectedField`. `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:farmer_game_tests_connected_field" ./gradlew --no-daemon runGameTest`
3. Flag links: nearest free camp, sticky, first registered wins a tie. Test `FlagLinksTest`. `export JAVA_HOME=$HOME/.jdk25 && ./gradlew --no-daemon test --tests dev.villageheart.core.site.FlagLinksTest`
4. The walk-home clock for a new tree and a new hunt. Test `FlagDayTest`. `export JAVA_HOME=$HOME/.jdk25 && ./gradlew --no-daemon test --tests dev.villageheart.core.site.FlagDayTest`
5. The chopping flag, the recipe, and the 128-block edge. A camp with no flag cuts nothing. A tree inside the flag's circle is felled. A tree beside the camp and outside that circle is left. Test `LumberjackGameTests.flagCircle`. `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:lumberjack_game_tests_flag_circle" ./gradlew --no-daemon runGameTest`
6. The hunting flag under the new rules. A lodge with no flag does not hunt. A flag 50 blocks from the lodge, with no grass quota, is where the hunter goes. Test `HunterGameTests.huntingFlag`. `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:hunter_game_tests_hunting_flag" ./gradlew --no-daemon runGameTest`
7. Both plaques, seen in the world. Screenshot stem `work_flags`. `-Dvillageheart.l4.only=flags`. Test `FlagClientTests.workFlags`. `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dvillageheart.l4.only=flags" ./gradlew --no-daemon runClientGameTest`

## Risks

- An old village loads. A lumber camp cuts nothing until a chopping flag is placed. A saved hunting ground keeps its block and its link, and it works under the looser rules.
- The chopping flag is a new building type, appended after the training yard. It is saved by its string id. The hunting ground id does not change. No new village save field: the link is the optional one outdoor sites already have.
- A large tree that was already started can keep a lumberjack out past the end of the work day.
