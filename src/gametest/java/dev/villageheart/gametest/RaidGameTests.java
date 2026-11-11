package dev.villageheart.gametest;

import static dev.villageheart.gametest.GuardTestSupport.BARRACKS;
import static dev.villageheart.gametest.GuardTestSupport.floor;
import static dev.villageheart.gametest.GuardTestSupport.hireGuard;
import static dev.villageheart.gametest.GuardTestSupport.hireTrainee;
import static dev.villageheart.gametest.GuardTestSupport.scene;
import static dev.villageheart.gametest.GuardTestSupport.setTime;
import static dev.villageheart.gametest.GuardTestSupport.spawnSpot;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.HeartSnapshotBuilder;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.raid.RaidFacts;
import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.raid.RaidOutcome;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomContents;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.gametest.raid.RaidStart;
import dev.villageheart.mixin.RaidGroups;
import dev.villageheart.raid.VillageRaids;
import dev.villageheart.worker.WorkerBrain;
import dev.villageheart.worker.guard.GuardActivities;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.gamerules.GameRules;

/**
 * Layer 2: a raid on the claim calls every qualified guard and shows on the Heart (M25 step 5).
 * Each test has its own environment. Time is frozen at the clock the case is about, so a long path does not walk the shift into the next phase.
 */
public class RaidGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	/** T-L2-1, asleep at noon. Four qualified guards and a pinned trainee. The alarm is not what holds them. */
	@GameTest(environment = "villageheart-gametest:raid_alert", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void raidAlertAndAllGuardsOnShift(GameTestHelper h) {
		begin(h);
		Villager[] qualified = new Villager[4];
		Villager[] trainee = new Villager[1];
		Villager[] day = new Villager[1];
		Raid[] raid = new Raid[1];
		GameTestSequence seq = wide(h).thenExecute(() -> {
			freeze(h, 6000);
			for (int i = 0; i < 4; i++) qualified[i] = hireGuard(h, spot(i), Optional.empty());
			trainee[0] = hireTrainee(h, spot(4), Optional.of(Shift.NIGHT));
			day[0] = dayGuard(h, qualified);
			var pinOpt = state(h).guardShift(trainee[0].getUUID());
			h.assertTrue(pinOpt.isPresent(), "the trainee has a shift");
			var pin = pinOpt.get();
			h.assertTrue(pin.shift() == Shift.NIGHT && pin.pinned(), "the trainee is pinned to the night");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "the day guard is up and the three night guards are asleep", () -> {
			h.assertTrue(day[0].getBrain().isActive(GuardActivities.DUTY) && !day[0].isSleeping(), "day guard");
			int asleep = 0;
			for (Villager v : qualified) if (v != day[0] && v.isSleeping()) asleep++;
			h.assertValueEqual(3, asleep, "night guards asleep");
		});
		seq = village(h, seq).thenExecute(() -> raid[0] = start(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the raid is tracked, the alert is first, and every qualified guard is up", () -> {
			RaidStart.holdWaves(raid[0]);
			var rec = tracked(h);
			var id = h.getLevel().getRaids().getId(raid[0]);
			h.assertTrue(id.isPresent(), "the raid has an id");
			h.assertValueEqual(id.getAsInt(), rec.vanillaId(), "tracked id");
			h.assertTrue(rec.origin() == RaidOrigin.TEST, "a RaidStart raid is a test raid");
			h.assertTrue(raidFirst(h), "the raid alert is first");
			for (Villager v : qualified) {
				h.assertTrue(v.getBrain().isActive(GuardActivities.DUTY) && !v.isSleeping(), "qualified guard up " + v.getBrain().getActiveActivities());
			}
			h.assertTrue(!trainee[0].getBrain().isActive(GuardActivities.DUTY), "the trainee stays off: " + trainee[0].getBrain().getActiveActivities());
		});
		seq = seq.thenExecute(() -> dev.villageheart.worker.guard.GuardAlarm.resetForTests()).thenIdle(100);
		seq = seq.thenExecute(() -> {
			RaidStart.holdWaves(raid[0]);
			h.assertTrue(!dev.villageheart.worker.guard.GuardAlarm.active(h.getLevel()), "the alarm is not what keeps them");
			for (Villager v : qualified) h.assertTrue(v.getBrain().isActive(GuardActivities.DUTY), "still on duty after the alarm is cleared");
			h.assertTrue(!trainee[0].getBrain().isActive(GuardActivities.DUTY), "the trainee was not called");
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-1, the day guard is off shift at 12500 and the raid puts them on duty. The trainee stays off. */
	@GameTest(environment = "villageheart-gametest:raid_off_shift", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void raidCallsUpTheOffShiftGuard(GameTestHelper h) {
		begin(h);
		Villager[] qualified = new Villager[4];
		Villager[] trainee = new Villager[1];
		Villager[] day = new Villager[1];
		Raid[] raid = new Raid[1];
		GameTestSequence seq = wide(h).thenExecute(() -> {
			freeze(h, 12500);
			for (int i = 0; i < 4; i++) qualified[i] = hireGuard(h, spot(i), Optional.empty());
			trainee[0] = hireTrainee(h, spot(4), Optional.of(Shift.NIGHT));
			day[0] = dayGuard(h, qualified);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the day guard is off and awake, the night guards are on duty", () -> {
			h.assertTrue(day[0].getBrain().isActive(GuardActivities.OFF) && !day[0].isSleeping(),
					"day guard off: " + day[0].getBrain().getActiveActivities() + " sleeping " + day[0].isSleeping());
			for (Villager v : qualified) if (v != day[0]) h.assertTrue(v.getBrain().isActive(GuardActivities.DUTY), "night guard");
			h.assertTrue(!trainee[0].getBrain().isActive(GuardActivities.DUTY), "trainee off before the raid");
		});
		seq = village(h, seq).thenExecute(() -> raid[0] = start(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the raid calls the day guard up and leaves the trainee", () -> {
			RaidStart.holdWaves(raid[0]);
			h.assertTrue(day[0].getBrain().isActive(GuardActivities.DUTY) && !day[0].isSleeping(), "day guard called up");
			h.assertTrue(!trainee[0].getBrain().isActive(GuardActivities.DUTY), "trainee stays off");
			h.assertTrue(raidFirst(h), "alert");
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-2. stop() clears the alert. The night guards go back to bed. A finished raid is a victory. */
	@GameTest(environment = "villageheart-gametest:raid_end", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void raidEndClearsAlert(GameTestHelper h) {
		begin(h);
		Villager[] guards = new Villager[2];
		Raid[] raid = new Raid[1];
		GameTestSequence seq = scene(h, 2).thenExecute(() -> {
			freeze(h, 6000);
			guards[0] = hireGuard(h, spawnSpot(), Optional.empty());
			guards[1] = hireGuard(h, spawnSpot().east(), Optional.empty());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "both night guards asleep", () -> {
			h.assertTrue(guards[0].isSleeping() && guards[1].isSleeping(), "asleep");
		});
		seq = village(h, seq).thenExecute(() -> raid[0] = start(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the raid wakes them", () -> {
			RaidStart.holdWaves(raid[0]);
			h.assertTrue(raidFirst(h), "alert");
			h.assertTrue(guards[0].getBrain().isActive(GuardActivities.DUTY) && guards[1].getBrain().isActive(GuardActivities.DUTY), "on duty");
		});
		seq = seq.thenExecute(() -> raid[0].stop());
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the raid is over and the alert is gone", () -> {
			h.assertTrue(state(h).threats().raid().isEmpty(), "no raid");
			var last = state(h).threats().lastRaid();
			h.assertTrue(last.isPresent() && last.get().outcome() == RaidOutcome.STOPPED, "stopped " + last.map(s -> s.outcome().name()).orElse("none"));
			h.assertTrue(!raidFirst(h), "no raid alert");
			h.assertTrue(!dev.villageheart.worker.guard.GuardAlarm.active(h.getLevel()), "alarm clear");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the night guards go back to rest", () -> {
			h.assertTrue(guards[0].getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST), "rest 0 " + guards[0].getBrain().getActiveActivities());
			h.assertTrue(guards[1].getBrain().isActive(net.minecraft.world.entity.schedule.Activity.REST), "rest 1 " + guards[1].getBrain().getActiveActivities());
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-2 victory half. Every wave is done and no raider is alive, so the raid is won. */
	@GameTest(environment = "villageheart-gametest:raid_victory", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void raidVictoryClearsAsWon(GameTestHelper h) {
		begin(h);
		Raid[] raid = new Raid[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			freeze(h, 6000);
			hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
		});
		seq = village(h, seq).thenExecute(() -> {
			raid[0] = start(h);
			var access = (dev.villageheart.gametest.mixin.RaidTestAccess) raid[0];
			access.villageheart$setStarted(true);
			access.villageheart$setGroupsSpawned(((RaidGroups) raid[0]).villageheart$numGroups());
			access.villageheart$setRaidCooldownTicks(0);
			raid[0].setRaidOmenLevel(1);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "the raid is won", () -> {
			h.assertTrue(state(h).threats().raid().isEmpty(), "cleared");
			var last = state(h).threats().lastRaid();
			h.assertTrue(last.isPresent() && last.get().outcome() == RaidOutcome.VICTORY,
					"outcome " + last.map(s -> s.outcome().name()).orElse("none"));
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-3. A raid whose centre and raiders are outside the claim is ignored. One raider inside starts it. */
	@GameTest(environment = "villageheart-gametest:raid_outside", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void raidOutsideClaimIgnored(GameTestHelper h) {
		begin(h);
		Villager[] guard = new Villager[1];
		RaidFacts[] facts = new RaidFacts[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			freeze(h, 6000);
			guard[0] = hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
			facts[0] = new RaidFacts(true, 7, false, false, false, false, false, 0, 3, 0, 1, false, false);
			VillageRaids.setSourceForTest((level, village) -> facts[0]);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the day guard is on duty", () ->
				h.assertTrue(guard[0].getBrain().isActive(GuardActivities.DUTY), "duty"));
		seq = seq.thenIdle(200).thenExecute(() -> {
			h.assertTrue(state(h).threats().raid().isEmpty(), "an outside raid is not tracked");
			h.assertTrue(!raidFirst(h), "no alert");
			h.assertTrue(guard[0].getBrain().isActive(GuardActivities.DUTY), "the guard kept the day phase");
			facts[0] = new RaidFacts(true, 7, false, false, false, false, false, 1, 3, 0, 1, false, false);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "one raider inside the claim raises the alert", () -> {
			var rec = tracked(h);
			h.assertValueEqual(7, rec.vanillaId(), "the test id");
			h.assertTrue(raidFirst(h), "alert");
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-4. A survival player with Bad Omen at the bell starts a raid the village treats as Bad Omen. */
	@GameTest(environment = "villageheart-gametest:raid_bad_omen", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void badOmenPathRaisesAlert(GameTestHelper h) {
		begin(h);
		ServerPlayer[] owner = new ServerPlayer[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			freeze(h, 6000);
			h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
			owner[0] = BuildingTestSupport.owner();
			BlockPos bell = h.absolutePos(new BlockPos(1, 1, 1));
			owner[0].setPos(bell.getX() + 0.5, bell.getY(), bell.getZ() + 0.5);
			owner[0].addEffect(new MobEffectInstance(MobEffects.RAID_OMEN, 2));
			owner[0].setRaidOmenPosition(bell);
			owner[0].doTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "Bad Omen starts a tracked raid and the effect is gone", () -> {
			if (owner[0].hasEffect(MobEffects.RAID_OMEN)) owner[0].doTick();
			var rec = tracked(h);
			h.assertTrue(rec.origin() == RaidOrigin.BAD_OMEN, "origin " + rec.origin());
			h.assertTrue(raidFirst(h), "alert");
			h.assertTrue(!owner[0].hasEffect(MobEffects.RAID_OMEN), "the omen was consumed");
			Raid live = h.getLevel().getRaids().get(rec.vanillaId());
			if (live != null) RaidStart.holdWaves(live);
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-8. A raider of this raid counts. A villager death during it is flagged. A pillager from outside does not count. */
	@GameTest(environment = "villageheart-gametest:raid_counts", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void raidCountsDeathsAndKills(GameTestHelper h) {
		begin(h);
		Raid[] raid = new Raid[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			freeze(h, 6000);
			hireGuard(h, spawnSpot(), Optional.of(Shift.DAY));
		});
		seq = village(h, seq).thenExecute(() -> raid[0] = start(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the raid is tracked before anyone dies", () -> {
			RaidStart.holdWaves(raid[0]);
			tracked(h);
		});
		seq = seq.thenExecute(() -> {
			RaidStart.holdWaves(raid[0]);
			Raider pillager = h.spawn(EntityType.PILLAGER, spawnSpot().east());
			pillager.setNoAi(true);
			raid[0].joinRaid(h.getLevel(), 1, pillager, pillager.blockPosition(), false);
			h.assertTrue(pillager.getCurrentRaid() == raid[0], "the pillager joined this raid");
			pillager.hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 1000.0F);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 20, "the raider kill is counted", () -> {
			RaidStart.holdWaves(raid[0]);
			h.assertValueEqual(1, tracked(h).raidersKilled(), "raiders killed");
		});
		seq = seq.thenExecute(() -> {
			Villager lost = WorkerTestSupport.spawnVillager(h, spawnSpot().west());
			lost.kill(h.getLevel());
			var row = state(h).deaths().recent(8).stream().filter(e -> e.villager().equals(lost.getUUID())).findFirst();
			h.assertTrue(row.isPresent(), "the villager was logged");
			h.assertTrue(row.get().inClaim() && row.get().duringRaid() && !row.get().duringSiege(), "the death was during the raid");
			h.assertValueEqual(1, tracked(h).villagerDeaths(), "one villager lost");
			Raider other = h.spawn(EntityType.PILLAGER, new BlockPos(0, 1, 0));
			other.setNoAi(true);
			BlockPos far = h.absolutePos(new BlockPos(0, 1, 0));
			other.teleportTo(far.getX() + 90.5, far.getY(), far.getZ() + 0.5);
			h.assertTrue(other.getCurrentRaid() == null, "the other pillager is not in the raid");
			other.hurtServer(h.getLevel(), h.getLevel().damageSources().genericKill(), 1000.0F);
			h.assertValueEqual(1, tracked(h).raidersKilled(), "an outsider does not count");
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-9. The owner and a resident hear the start once, and the end once. A visitor hears nothing. */
	@GameTest(environment = "villageheart-gametest:raid_notice", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void raidNoticeAudience(GameTestHelper h) {
		begin(h);
		List<Note> notes = new ArrayList<>();
		UUID[] resident = new UUID[1];
		UUID[] visitor = new UUID[1];
		int[] atStart = new int[1];
		Raid[] raid = new Raid[1];
		GameTestSequence seq = scene(h, 1).thenExecute(() -> {
			freeze(h, 6000);
			ServerPlayer owner = BuildingTestSupport.owner();
			ServerPlayer residentPlayer = VillageTestSupport.player(h, "resident", net.minecraft.world.level.GameType.SURVIVAL);
			ServerPlayer visitorPlayer = VillageTestSupport.player(h, "visitor", net.minecraft.world.level.GameType.SURVIVAL);
			resident[0] = residentPlayer.getUUID();
			visitor[0] = visitorPlayer.getUUID();
			VillageClaims.setRank(h.getLevel().getServer(), resident[0], Rank.RESIDENT);
			dev.villageheart.raid.RaidNotices.setSinkForTest((player, text) -> notes.add(new Note(player.getUUID(), text)));
			h.assertTrue(owner != null, "owner");
		});
		seq = village(h, seq).thenExecute(() -> raid[0] = start(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "one start line each for the owner and the resident", () -> {
			RaidStart.holdWaves(raid[0]);
			h.assertValueEqual(1, count(notes, BuildingTestSupport.owner().getUUID(), "A raid is coming"), "owner start");
			h.assertValueEqual(1, count(notes, resident[0], "A raid is coming"), "resident start");
			h.assertValueEqual(0, count(notes, visitor[0], "A raid is coming"), "visitor hears nothing");
		});
		seq = seq.thenExecute(() -> atStart[0] = notes.size()).thenIdle(100).thenExecute(() -> {
			RaidStart.holdWaves(raid[0]);
			h.assertValueEqual(atStart[0], notes.size(), "the start is not sent again");
			raid[0].stop();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "one end line each", () -> {
			h.assertValueEqual(1, count(notes, BuildingTestSupport.owner().getUUID(), "is over"), "owner end");
			h.assertValueEqual(1, count(notes, resident[0], "is over"), "resident end");
			h.assertValueEqual(0, count(notes, visitor[0], "is over"), "visitor still hears nothing");
			h.assertTrue(notes.stream().anyMatch(n -> n.text.contains("0 villagers lost")), "the lost count is the raid's villager deaths");
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-5. A harvesting farmer goes to its own House bed for the raid, then back to the field. */
	@GameTest(environment = "villageheart-gametest:raid_shelter", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void workerSheltersInHouse(GameTestHelper h) { shelterFarmer(h, false); }

	/**
	 * T-L2-5b. The same walk, with a raider standing off the path. Panic is allowed; the room is not.
	 * The filter id is {@code raid_game_tests_worker_shelters_past_araider}: a lone capital stays glued to the next word.
	 */
	@GameTest(environment = "villageheart-gametest:raid_shelter_raider", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void workerSheltersPastARaider(GameTestHelper h) { shelterFarmer(h, true); }

	/**
	 * T-L2-6. A fell and a build both stop for the raid. Item totals count chests, inventories and drops, so a worker picking up a log it
	 * already cut is not a new block; a log that breaks, or a block that is placed, still changes the total.
	 */
	@GameTest(environment = "villageheart-gametest:raid_shelter_jobs", structure = YARD, skyAccess = true, maxTicks = 12000)
	public void shelterInterruptsJobsCleanly(GameTestHelper h) {
		begin(h);
		BlockPos camp = new BlockPos(24, 0, 4);
		Villager[] jack = new Villager[1];
		Villager[] builder = new Villager[1];
		Set<BlockPos>[] logs = new Set[1];
		Raid[] raid = new Raid[1];
		long[] items = new long[1];
		int[] standing = new int[1];
		int[] cursor = new int[1];
		GameTestSequence seq = BuildTestSupport.layout(h, BuildTestSupport.With.STANDARD).thenExecute(() -> {
			freeze(h, 3000);
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			BuildingTestSupport.room(h, camp, 5, 4);
			h.setBlock(BuildingTestSupport.inside(camp, 0, 0, 0), JobBlocks.BLOCKS.get(JobBlockType.CHOPPING_BLOCK));
			h.setBlock(BuildingTestSupport.inside(camp, 4, 0, 3), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(camp, 2, 0, 3), Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, BuildingType.LUMBER_CAMP, BuildingTestSupport.markerPos(camp, 5), Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(camp, 5), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			BlockPos base = LumberTestSupport.growOak(h, new BlockPos(28, 0, 14), 1L);
			logs[0] = LumberTestSupport.logsOf(h, base);
			h.assertTrue(logs[0].size() > 1, "the oak has more than one log");
			jack[0] = LumberTestSupport.hire(h, BuildingTestSupport.inside(camp, 1, 0, 1));
			jack[0].getInventory().addItem(LumberTestSupport.axe(ToolTier.STONE));
			builder[0] = BuildTestSupport.hireBuilder(h);
			builder[0].getInventory().addItem(new ItemStack(Items.COBBLESTONE, 32));
			BuildTestSupport.stock(h, BuildTestSupport.shedMaterials(1));
			BuildTestSupport.startBuild(h, Blueprints.STORAGE_SHED, BuildTestSupport.GROUND, Rotation4.NONE);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 3000, "the lumberjack is mid-fell and the builder is mid-build", () -> {
			int left = LumberTestSupport.standing(h, logs[0]);
			h.assertTrue(left > 0 && left < logs[0].size(), "mid-fell, logs left " + left + " of " + logs[0].size());
			h.assertTrue(BuildTestSupport.cursor(h) > 0, "mid-build, cursor " + BuildTestSupport.cursor(h));
		});
		seq = village(h, seq).thenExecute(() -> raid[0] = start(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 200, "both workers are sheltering", () -> {
			RaidStart.holdWaves(raid[0]);
			h.assertTrue(jack[0].getBrain().isActive(WorkerBrain.SHELTER), "lumberjack");
			h.assertTrue(builder[0].getBrain().isActive(WorkerBrain.SHELTER), "builder");
			h.assertTrue(carrying(jack[0]) && carrying(builder[0]), "each is carrying items");
		});
		seq = seq.thenExecute(() -> {
			RaidStart.holdWaves(raid[0]);
			items[0] = conserved(h, jack[0], builder[0]);
			standing[0] = LumberTestSupport.standing(h, logs[0]);
			cursor[0] = BuildTestSupport.cursor(h);
		}).thenExecuteAfter(100, () -> {
			RaidStart.holdWaves(raid[0]);
			h.assertValueEqual(standing[0], LumberTestSupport.standing(h, logs[0]), "no log broken while sheltering");
			h.assertValueEqual(cursor[0], BuildTestSupport.cursor(h), "the build cursor stays put");
			h.assertValueEqual(items[0], conserved(h, jack[0], builder[0]), "inventory, chests and drops are unchanged");
			raid[0].stop();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 4000, "the tree is finished and the build carries on", () -> {
			h.assertValueEqual(0, LumberTestSupport.standing(h, logs[0]), "logs left");
			int now = BuildTestSupport.cursor(h);
			h.assertTrue(now > cursor[0] || now < 0, "cursor moved from " + cursor[0] + " to " + now);
		});
		seq.thenExecute(() -> finish(h));
	}

	/** T-L2-10. An unemployed villager still hides the vanilla way. A hired worker beside it never does. */
	@GameTest(environment = "villageheart-gametest:raid_unhired", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void unhiredVillagersKeepVanillaRaid(GameTestHelper h) {
		begin(h);
		BlockPos farm = WorkerTestSupport.ORIGIN_A;
		Villager[] hired = new Villager[1];
		Villager[] plain = new Villager[1];
		boolean[] vanilla = {false};
		Raid[] raid = new Raid[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			freeze(h, 3000);
			WorkerTestSupport.farmhouse(h, farm, 1);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, BuildingTestSupport.markerPos(farm, 5), Direction.NORTH);
		});
		seq = seq.thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(farm, 5), dev.villageheart.block.MarkerStatus.VALID));
		seq = seq.thenExecute(() -> {
			hired[0] = FarmTestSupport.hire(h, new BlockPos(3, 1, 2));
			plain[0] = WorkerTestSupport.spawnVillager(h, new BlockPos(4, 1, 2));
		});
		seq = village(h, seq).thenExecute(() -> raid[0] = start(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the unemployed villager is pre-raid and the hired worker is sheltering", () -> {
			RaidStart.holdWaves(raid[0]);
			var act = hired[0].getBrain().getActiveNonCoreActivity().orElse(null);
			if (act == Activity.PRE_RAID || act == Activity.RAID) vanilla[0] = true;
			h.assertFalse(vanilla[0], "the hired worker entered " + act);
			h.assertTrue(hired[0].getBrain().isActive(WorkerBrain.SHELTER), "hired activity " + act);
			h.assertTrue(plain[0].getBrain().isActive(Activity.PRE_RAID), "unemployed activity " + plain[0].getBrain().getActiveNonCoreActivity().orElse(null));
		});
		seq.thenExecute(() -> finish(h));
	}

	private void shelterFarmer(GameTestHelper h, boolean raider) {
		begin(h);
		BlockPos house = new BlockPos(18, 0, 4);
		BlockPos store = new BlockPos(18, 0, 16);
		BlockPos farm = WorkerTestSupport.ORIGIN_A;
		Villager[] farmer = new Villager[1];
		BlockPos[] bed = new BlockPos[1];
		RoomScan[] scan = new RoomScan[1];
		Raid[] raid = new Raid[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			freeze(h, 3000);
			LumberTestSupport.grass(h, 0, 0, 31, 31);
			WorkerTestSupport.farmhouse(h, farm, 1);
			BuildingTestSupport.room(h, house, 5, 4);
			WorkerTestSupport.bed(h, BuildingTestSupport.inside(house, 0, 0, 1));
			h.setBlock(BuildingTestSupport.inside(house, 2, 0, 3), Blocks.TORCH);
			BuildingTestSupport.room(h, store, 5, 4);
			h.setBlock(BuildingTestSupport.inside(store, 0, 0, 3), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(store, 4, 0, 3), Blocks.CHEST);
			h.setBlock(BuildingTestSupport.inside(store, 2, 0, 3), Blocks.TORCH);
			BuildingTestSupport.placeMarker(h, BuildingType.FARMHOUSE, BuildingTestSupport.markerPos(farm, 5), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.HOUSE, BuildingTestSupport.markerPos(house, 5), Direction.NORTH);
			BuildingTestSupport.placeMarker(h, BuildingType.STORAGE, BuildingTestSupport.markerPos(store, 5), Direction.NORTH);
			FarmTestSupport.field(h, 8, 14, 12, 17, CropKind.WHEAT, 7, false);
		});
		seq = seq.thenWaitUntil(() -> {
			BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(farm, 5), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(house, 5), dev.villageheart.block.MarkerStatus.VALID);
			BuildingTestSupport.assertStatus(h, BuildingTestSupport.markerPos(store, 5), dev.villageheart.block.MarkerStatus.VALID);
		});
		seq = village(h, seq).thenExecute(() -> {
			farmer[0] = FarmTestSupport.hire(h, new BlockPos(6, 1, 16));
			farmer[0].getInventory().addItem(new ItemStack(Items.IRON_HOE));
			var houses = Buildings.valid(h.getLevel().getServer(), BuildingType.HOUSE);
			h.assertTrue(houses.size() == 1 && houses.get(0).latestScan().isPresent(), "the house has a cached scan");
			scan[0] = houses.get(0).latestScan().get();
			var beds = scan[0].positions(RoomContents.BED);
			h.assertTrue(!beds.isEmpty(), "the house scan lists a bed");
			Pos p = beds.get(0);
			bed[0] = new BlockPos(p.x(), p.y(), p.z());
			farmer[0].getBrain().setMemory(MemoryModuleType.HOME, GlobalPos.of(h.getLevel().dimension(), bed[0]));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "the farmer is harvesting outdoors", () -> {
			h.assertTrue(farmer[0].getBrain().isActive(Activity.WORK), "precondition activity " + farmer[0].getBrain().getActiveNonCoreActivity().orElse(null));
			h.assertTrue(h.getLevel().canSeeSky(farmer[0].blockPosition()), "precondition sky at " + farmer[0].blockPosition()
					+ " crop " + h.getBlockState(new BlockPos(10, 0, 16)) + " under " + h.getBlockState(new BlockPos(10, -1, 16)));
			h.assertTrue(nearField(h, farmer[0]), "precondition near the field at " + farmer[0].blockPosition());
		});
		seq = seq.thenExecute(() -> {
			raid[0] = start(h);
			if (!raider) return;
			// Eight blocks south of the line from the field to the house door, so the doorway stays clear.
			Raider pillager = h.spawn(EntityType.PILLAGER, new BlockPos(14, 1, 18));
			pillager.setNoAi(true);
			raid[0].joinRaid(h.getLevel(), 1, pillager, pillager.blockPosition(), false);
		});
		if (raider) {
			seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the farmer is inside the house", () -> {
				RaidStart.holdWaves(raid[0]);
				assertIndoors(h, farmer[0], scan[0]);
			});
			seq = seq.thenExecuteAfter(200, () -> {
				RaidStart.holdWaves(raid[0]);
				assertIndoors(h, farmer[0], scan[0]);
			});
		} else {
			seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the farmer is sheltering", () -> {
				RaidStart.holdWaves(raid[0]);
				h.assertTrue(farmer[0].getBrain().isActive(WorkerBrain.SHELTER), "activity " + farmer[0].getBrain().getActiveNonCoreActivity().orElse(null));
			});
			seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the farmer is at its bed", () -> {
				RaidStart.holdWaves(raid[0]);
				assertIndoors(h, farmer[0], scan[0]);
				h.assertTrue(WalkTo.distance(farmer[0], bed[0]) <= 1.5, "within 1.5 of the bed, was " + WalkTo.distance(farmer[0], bed[0]));
			});
			seq = seq.thenExecuteAfter(200, () -> {
				RaidStart.holdWaves(raid[0]);
				assertIndoors(h, farmer[0], scan[0]);
				h.assertTrue(WalkTo.distance(farmer[0], bed[0]) <= 1.5, "still at the bed, was " + WalkTo.distance(farmer[0], bed[0]));
				raid[0].stop();
			});
			seq = BuildingTestSupport.awaitWithin(h, seq, 200, "the farmer is back at work", () ->
					h.assertTrue(farmer[0].getBrain().isActive(Activity.WORK), "activity " + farmer[0].getBrain().getActiveNonCoreActivity().orElse(null)));
			seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the farmer is harvesting again", () -> {
				h.assertTrue(farmer[0].getBrain().isActive(Activity.WORK), "WORK");
				h.assertTrue(h.getLevel().canSeeSky(farmer[0].blockPosition()), "after sky at " + farmer[0].blockPosition());
				h.assertTrue(nearField(h, farmer[0]), "after near the field at " + farmer[0].blockPosition());
			});
		}
		seq.thenExecute(() -> finish(h));
	}

	private static void assertIndoors(GameTestHelper h, Villager v, RoomScan scan) {
		Pos feet = new Pos(v.getBlockX(), v.getBlockY(), v.getBlockZ());
		h.assertTrue(scan.contains(feet), "inside the house at " + feet);
		h.assertTrue(!h.getLevel().canSeeSky(v.blockPosition()), "the roof hides the sky at " + feet);
	}

	private static boolean nearField(GameTestHelper h, Villager v) {
		for (int x = 8; x <= 12; x++) for (int z = 14; z <= 17; z++) {
			if (WorkerTestSupport.distance(h, v, new BlockPos(x, 0, z)) <= 3.0) return true;
		}
		return false;
	}

	private static boolean carrying(Villager v) {
		for (int i = 0; i < v.getInventory().getContainerSize(); i++) if (!v.getInventory().getItem(i).isEmpty()) return true;
		return false;
	}

	/** Chests, both inventories, and drops of the materials a fell or a place would change. */
	private static long conserved(GameTestHelper h, Villager a, Villager b) {
		long n = 0;
		for (Item item : java.util.List.of(Items.OAK_LOG, Items.COBBLESTONE, Items.OAK_PLANKS)) {
			n += dev.villageheart.stock.Stockpiles.count(h.getLevel().getServer(), item);
			n += a.getInventory().countItem(item);
			n += b.getInventory().countItem(item);
			for (ItemEntity drop : h.getEntities(EntityType.ITEM)) if (drop.getItem().is(item)) n += drop.getItem().getCount();
		}
		return n;
	}

	private record Note(UUID id, String text) {}

	private static int count(List<Note> notes, UUID id, String part) {
		int n = 0;
		for (Note note : notes) if (note.id.equals(id) && note.text.contains(part)) n++;
		return n;
	}

	private static GameTestSequence wide(GameTestHelper h) {
		BlockPos[] marker = new BlockPos[1];
		return BuildingTestSupport.begin(h).thenExecute(() -> {
			floor(h);
			BuildingTestSupport.setTier(h, dev.villageheart.core.Tier.VILLAGE);
			marker[0] = BuildingTestSupport.room(h, BARRACKS, 6, 4);
			for (int i = 0; i < 5; i++) {
				h.setBlock(BuildingTestSupport.inside(BARRACKS, i, 0, 0), Blocks.GRINDSTONE.defaultBlockState());
				WorkerTestSupport.bed(h, BuildingTestSupport.inside(BARRACKS, i, 0, 2));
			}
			h.setBlock(BuildingTestSupport.inside(BARRACKS, 5, 0, 0), Blocks.CHEST.defaultBlockState());
			h.setBlock(BuildingTestSupport.inside(BARRACKS, 5, 0, 1), Blocks.TORCH.defaultBlockState());
			BuildingTestSupport.placeMarker(h, BuildingType.BARRACKS, marker[0], Direction.NORTH);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, marker[0], dev.villageheart.block.MarkerStatus.VALID));
	}

	private static BlockPos spot(int i) { return BuildingTestSupport.inside(BARRACKS, i, 0, 1); }

	private static void freeze(GameTestHelper h, long dayTime) {
		setTime(h, dayTime);
		h.getLevel().getGameRules().set(GameRules.ADVANCE_TIME, false, h.getLevel().getServer());
	}

	/** Vanilla only starts a raid at a spot it already calls a village. The bell's POI is not ready on the claim tick. */
	private static GameTestSequence village(GameTestHelper h, GameTestSequence seq) {
		BlockPos at = new BlockPos(3, 0, 1);
		return BuildingTestSupport.awaitWithin(h, seq, 200, "vanilla treats the spot by the Heart as a village", () ->
				h.assertTrue(h.getLevel().isVillage(h.absolutePos(at)), "not a village yet"));
	}

	private static Raid start(GameTestHelper h) {
		h.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
		Raid raid = RaidStart.start(BuildingTestSupport.owner(), h.absolutePos(new BlockPos(3, 0, 1)), 1);
		RaidStart.holdWaves(raid);
		return raid;
	}

	private static VillageState state(GameTestHelper h) {
		var village = VillageClaims.current(h.getLevel().getServer());
		h.assertTrue(village.isPresent(), "a village is claimed");
		return village.get();
	}

	private static dev.villageheart.core.raid.RaidRecord tracked(GameTestHelper h) {
		var raid = state(h).threats().raid();
		h.assertTrue(raid.isPresent(), "a raid is tracked");
		return raid.get();
	}

	private static boolean raidFirst(GameTestHelper h) {
		var facts = dev.villageheart.heart.HeartFacts.collect(h.getLevel().getServer());
		if (facts.isEmpty()) return false;
		var alerts = HeartSnapshotBuilder.build(facts.get(), Rank.OWNER).overview().alerts();
		return !alerts.isEmpty() && alerts.get(0).code() == AlertCode.RAID;
	}

	private static Villager dayGuard(GameTestHelper h, Villager[] guards) {
		Villager day = null;
		int night = 0;
		for (Villager v : guards) {
			var shiftOpt = state(h).guardShift(v.getUUID());
			h.assertTrue(shiftOpt.isPresent(), "guard has a shift");
			Shift shift = shiftOpt.get().shift();
			if (shift == Shift.DAY) {
				h.assertTrue(day == null, "only one day guard");
				day = v;
			} else night++;
		}
		h.assertTrue(day != null && night == 3, "one day guard and three night guards");
		return day;
	}

	private static void finish(GameTestHelper h) {
		VillageTestSupport.done(h);
		end(h);
	}
}
