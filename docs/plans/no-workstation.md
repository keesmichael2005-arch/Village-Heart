# Plan: no workstation

Breaking a workplace no longer fires anyone. They keep the job, the level, and their pay, and they spend the work day wandering inside the claim. The Heart shows that they have no workstation. A valid workplace of their own job puts them back to work. An unpaid strike wanders the same way.

## Decisions

- Only you firing them, their death, a zombie conversion, or breaking the Heart ends the job.
- A missing workplace stays on the payroll. The Paid icon stays lit when the treasury can pay them.
- During the work day (ticks 1000–11000) they wander inside the claim and do no work. They do not walk to the job block and they do not make a deposit trip. They still go home, eat dinner, and sleep. No new sound.
- The same wander applies to an unpaid strike. The Villagers row still says "On strike", and that wins even when the workplace is also missing. The emerald warning stays.
- A paid worker with no valid workplace shows "No workstation" on their row. The Overview danger line reads "1 worker has no workstation" or "N workers have no workstation".
- A building that is still registered, including a red one, keeps its workers. They wander until that building is valid again, then they work there.
- A building that is gone releases its workers onto the waiting list. In hiring order, each takes the first free job block of their own job, in the same order a new hire would. One free block and two waiting: the earlier hire gets it. The other stays paid and wandering.
- They go back to work only from a valid building of their own job. A House is a home.
- A guard wanders only while already off duty and awake. A raid still calls them up. They still sleep.
- A merchant never wage-strikes. With no Market they wander, stay paid, and sell again when a stall is free.
- A tool craft in progress still refunds to Storage when its building is unregistered. The worker stays hired.

## Out of scope

- A village whose workplaces stay valid, and whose treasury covers wages, works as it does today.
- Pay rates, who is allowed to wage-strike, hunger, housing, and creeper rules.
- Someone already fired in an old save. They stay unemployed.
- Moving a worker between two valid workplaces by hand.

## Steps

1. A pure rule in `core/`: a worker whose building is still registered stays on it, and a worker whose building is gone takes the next free block of their job in hiring order, keeping level and job. A second waiter stays waiting. A red building does not give its workers away. Test: `WorkplaceRebindTest`. Command: `export JAVA_HOME=$HOME/.jdk25 && ./gradlew --no-daemon test --tests dev.villageheart.core.worker.WorkplaceRebindTest`

2. The Heart copy and the row choice live in `core/`. Paid and no workplace produces the "no workstation" danger line and the row "No workstation". Unpaid produces "On strike" and the emerald line, including when the workplace is also missing. Payroll does not look at the workplace. Test: `NoWorkstationTextTest`. Command: `export JAVA_HOME=$HOME/.jdk25 && ./gradlew --no-daemon test --tests dev.villageheart.core.heart.NoWorkstationTextTest`

3. Rewrite `WorkerGameTests.markerRemovedFiresWorker`. Breaking the marker keeps the farmer hired, with the profession, the record, and the level. A new valid Farmhouse then gives them its composter. The commit says why the old assertion changed. Test: `markerRemovedKeepsWorker`. Command: `export JAVA_HOME=$HOME/.jdk25 && JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:worker_game_tests_marker_removed_keeps_worker" ./gradlew --no-daemon runGameTest`

4. Two camps. Removing one marker gives its earlier hire the free block at the other camp, and leaves the later hire waiting. A red camp that is still registered leaves its worker there. Test: `waitingWorkerTakesTheFreeSlot`. Command: `export JAVA_HOME=$HOME/.jdk25 && JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:worker_game_tests_waiting_worker_takes_the_free_slot" ./gradlew --no-daemon runGameTest`

5. During the work day, an unpaid striker and a paid worker with no workplace both move, stay inside the claim, and do not deposit. At sleep time the striker is in bed. A striking guard, off duty and awake, moves, and a raid still puts that guard on duty. Test: `strikeWandersInsideTheClaim`. Command: `export JAVA_HOME=$HOME/.jdk25 && JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dfabric-api.gametest.filter=villageheart-gametest:worker_game_tests_strike_wanders_inside_the_claim" ./gradlew --no-daemon runGameTest`

6. Client test, Heart open. The Overview shows the workstation line, then the Villagers tab shows the job still held and the row "No workstation". Stems: `no_workstation_overview`, `no_workstation_villager`. Key: `-Dvillageheart.l4.only=workstation`, returning after that test. Test: `NoWorkstationClientTests.noWorkstation`. Command: `export JAVA_HOME=$HOME/.jdk25 && JAVA_TOOL_OPTIONS="$JAVA_TOOL_OPTIONS -Dvillageheart.l4.only=workstation" xvfb-run -a ./gradlew --no-daemon runClientGameTest`

Update `CHANGELOG.md`, `docs/DESIGN.md`, and `docs/PLAYER_GUIDE.md` with this behaviour.

## Risks

- No new saved field. A waiting worker keeps the old building location on the existing record. Old saves load. Anyone already fired stays fired.
- No new enum value. The existing workplace alert and status stay, with new words.
- Rebind runs only when the building record is gone. A recheck that leaves the building registered must not move its workers.
- The wander has to stay inside the 80-block claim, and it has to lose to going home, sleeping, and a raid.
