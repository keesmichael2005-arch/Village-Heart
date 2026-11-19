# Worker pockets

## Goal

Right-click a hired villager and you get a read-only view of the eight things they are carrying, plus their name, job, level, and what they are doing. During the work day their tool is in that pocket. At the end of work they put it back in their own chest, dump gathered goods at Storage, then go home and sleep. In the morning they take the tool out again. Each worker owns one job block and one chest, and no other villager uses that chest.

## Decisions

- The pocket is the villager's eight slots. You can look. You cannot put anything in or take anything out. Hire, fire, and wages stay on the Heart screen. An unhired villager still opens the vanilla trade window. A miner underground or a hunter out on a trip still cannot be clicked.
- The card shows the name, the job and level with the same XP bar as the Heart, the current activity, and whether they are fed, housed, and paid. A guard's card also shows the sword and armour they are wearing. There is no new sound.
- Slot 0 is reserved for the tool on a lumberjack, miner, hunter, or farmer. It looks like any other slot. Goods never go in it, so a broken tool still has a place for the replacement. A builder, cook, merchant, smith, or guard uses all eight slots for what they are carrying. A guard's sword and armour stay worn, not in those eight.
- They walk to Storage when the cargo slots are full, and always at the end of the work day. The old "one stack, then dump" rule goes away. The tool is never dumped with the logs. Dinner still comes out of Storage at day time 11000. They do not pack a lunch.
- End of work: stop gathering, walk to their chest, put the tool in, dump the cargo at Storage, then the evening they already have. If that chest has no free slot, the tool stays in slot 0 overnight. Morning, during wake, they walk to the chest and take it back. A night guard hands gear in at dawn. A day guard hands it in at dusk.
- If they die, or a zombie converts them, the tool goes back into their chest. The cargo drops. A guard's worn gear already does this.
- Hiring pairs a free job block with the nearest chest in that room that nobody else owns, and both links are saved. Firing frees both. A broken chest keeps the link and the worker shows "Needs a chest" until you replace it. A room with two job blocks and one chest stays valid and hires one worker. A double chest is one chest.
- Their chest holds the tool overnight, a spare or a worn tool, arrows you put there for that hunter, and seeds that farmer is storing. Gathered goods still go to Storage. The smith delivers a new tool into that worker's chest. You can still open the chest. Hoppers still work. Another villager never takes from it.
- An old save keeps every hired worker. Where two workers shared one chest, the nearest job block owns it and the other shows "Needs a chest" until you add one.

## Out of scope

Work speeds, XP, raids, and creepers stay as they are. A village with nobody hired is unchanged. The cook's heat stays on the smoker. A smith's craft stays a saved job, not items in the pocket. The guard's ration stays a number. Armour stays invisible on the villager model. The merchant still sells from Storage, not from the personal chest.

## Steps

1. Chest pairing in `core`: one chest per job block, nearest free chest, slots are the minimum of job blocks and chests (and beds, for a Barracks), a double chest counts once. Test `ChestOwnershipTest`. `export JAVA_HOME=$HOME/.jdk25 && ./gradlew --no-daemon test --tests dev.villageheart.core.job.ChestOwnershipTest`
2. Pocket rules in `core`: slot 0 reserved for the four tool jobs, goods cannot fill it, dump when the cargo slots are full. Test `PocketRulesTest`. Same command with that class.
3. The day in `core`: evening return, morning take, tool stays overnight when the chest is full, death sends the tool home and drops the cargo. Append `NEEDS_CHEST` to `WorkerStatus` and `ActivityKind`. Test `ToolDayTest`. Same command with that class.
4. Save the chest link as an optional `chest` on the worker attachment and on the village worker record. An old save with no chest still loads. Test `WorkerChestSaveTest`. Same command with that class.
5. In the world: hire pairs the chest, a second worker cannot take from the first chest, morning take, evening return then dump, death puts the axe back. Tests `WorkerPocketGameTests.ownsNearestChest`, `secondWorkerLeavesTheChestAlone`, `eveningReturnsTheAxeThenDumps`, `deathReturnsTheAxe`. `JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:worker_pocket_game_tests_<method>" ./gradlew --no-daemon runGameTest`
6. Guards hand the sword and armour to their own chest off shift. Smith delivery lands in the asking worker's chest. Tests `GuardPocketGameTests.handsGearToOwnChest` and `SmithPocketGameTests.deliversToTheWorkersChest`, same GameTest command.
7. The card. Client test `WorkerPocketClientTests.pocket`, screenshot stem `worker_pocket`, `-Dvillageheart.l4.only=pocket`. Update `CHANGELOG.md`, `docs/DESIGN.md`, and `docs/PLAYER_GUIDE.md` in this step.

## Risks

Old villages with two workers and one chest keep both workers, and one of them waits on a new chest. `NEEDS_CHEST` is appended, so old status numbers stay valid. The chest field is optional, so an old save loads. The one-stack carry rule changes, so a worker can hold more before a dump. A gatherer still hits the evening first.
