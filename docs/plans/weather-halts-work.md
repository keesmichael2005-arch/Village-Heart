# Weather halts villager work

## Goal

While it is raining or snowing, a hired worker stops work and walks to their workplace building. They stand there until the weather clears, then go straight back to work if it is still work time. At the usual time they still eat dinner, walk home, and sleep, even if it is still raining. A guard who is on duty keeps the shift in any weather.

## Decisions

- Weather means Minecraft rain, snow, and thunderstorms. The same flag covers all three.
- This replaces work time only, from day time 1000 to 11000. Waking, dinner at 11000, the walk home, and sleep stay on the clock, including in the rain.
- They wait inside the job building: Farmhouse, Lumber Camp, Kitchen, Mine, Hunting Lodge, Builder's Hut, Market, or Smithy. They stand beside the workstation. They do not keep working there. A farmer leaves the field, a lumberjack leaves the tree, a hunter leaves the hunting ground, a miner leaves the Mineshaft, and a builder leaves the site.
- If they are already putting goods into Storage when the rain starts, they finish that drop-off, then go to the workplace. Anything still in their hands stays there until the usual deposit, at the end of the day or when they are working again and the stack is full.
- The moment it clears, if it is still work time, they resume the job.
- Guards on duty patrol and fight in the rain. A raid or the alarm still calls them out. A guard who is asleep stays asleep. A trainee is not on duty, so a trainee waits in the Barracks instead of sparring in the rain.
- A raid or a siege still sends everyone to shelter the way it does now. Guards still fight. If the threat ends and it is still raining during work time, workers go to the workplace and wait, rather than back to work.
- The Villagers tab says **Out of the rain** while they are waiting. No new sound and no chat line.
- There is no bell meeting in the day right now, so this does not add one and does not skip one. A meeting skipped in the rain can be added when the meeting itself exists.

## Out of scope

- A village where it never rains, or a day that stays clear, is unchanged.
- Unhired villagers. They already hide the way vanilla does.
- Golems, creepers, raids, sieges, and guard combat.
- Storms as a disaster, crop damage, lightning strikes, and seasons.
- A button to force them to work in the rain.

## Steps

1. A pure rule: precipitation plus work time means wait at the workplace and do not work. An on-duty guard never waits. Clearing the weather during work time means work again. Test: `WeatherShelterTest`. Command: `export JAVA_HOME=$HOME/.jdk25 && ./gradlew --no-daemon test --tests dev.villageheart.core.worker.WeatherShelterTest`.
2. In the world, a farmer in the rain walks into the Farmhouse and does not harvest. When the weather is cleared during work time, the farmer harvests again. A guard on duty stays outside and on patrol. Test: `WorkerGameTests` rain cases. Command: `export JAVA_HOME=$HOME/.jdk25 && ./gradlew --no-daemon runGameTestServer --tests dev.villageheart.gametest.WorkerGameTests`.
3. The Villagers tab shows "Out of the rain". One client frame, stem `rain_shelter`, key `-Dvillageheart.l4.only=rain-shelter`: the farmer is inside the Farmhouse while rain is falling, not out in the field.

## Risks

- No new saved data. Old worlds load as they are.
- The new tab line is appended to the activity list, so older list entries keep their indexes.
- Creepers stay unattacked. Raid and siege shelter stays as it is.
