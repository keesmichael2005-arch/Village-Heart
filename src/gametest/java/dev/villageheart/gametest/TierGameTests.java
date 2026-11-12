package dev.villageheart.gametest;

import static dev.villageheart.gametest.TierTestSupport.*;

import dev.villageheart.building.BuildingSystem;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.building.Reason;
import dev.villageheart.core.economy.PayResult;
import dev.villageheart.core.heart.GoalLine;
import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.core.tier.TierProgress;
import dev.villageheart.core.worker.HireRefusal;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.food.FoodStats;
import dev.villageheart.needs.NeedsOverride;
import dev.villageheart.tier.TierStats;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import java.util.List;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;

/**
 * Layer 2: village tiers (M14). Layout and helpers in {@link TierTestSupport}; the counting is proved in the L1 tests, so these start from a
 * seeded history and run one real dinner and one real dawn. Dinner and wages are off unless a test switches them.
 */
public class TierGameTests {
	private static final String YARD = BuildingTestSupport.YARD;
	private static final String TIER_UP = "message.villageheart.tier_up";

	private static HeartSnapshot owner(GameTestHelper h) { return HeartTestSupport.snapshot(h, Rank.OWNER); }

	private static List<Boolean> met(HeartSnapshot s) { return s.overview().goals().stream().map(GoalLine::met).toList(); }

	private static List<String> keys(HeartSnapshot s) { return s.overview().goals().stream().map(g -> g.label().key().substring(g.label().key().lastIndexOf('.') + 1)).toList(); }

	private static long tierUps(BuildingTestSupport.RecordingPlayer p) { return LevelTestSupport.count(p, TIER_UP); }

	private static void log(String s) { System.out.println("[m14] " + s); }

	// ---- T-L2-1 (roadmap) -------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:tiers_village_goals", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void villageGoalsTierUpAtDawn(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		DawnProbe probe = new DawnProbe();
		long[] day = new long[1];
		PayResult[] pay = new PayResult[1];
		var rita = new BuildingTestSupport.RecordingPlayer[1];
		GameTestSequence seq = layout(h, Layout.FULL).thenExecute(() -> {
			pinClockEarlyInDay(h);
			NeedsOverride.dinnerEnabled = true;
			seedProgress(h, new TierProgress(2, today(h), 0));
			rita[0] = RankTestSupport.chatty(h, "rita", Rank.RESIDENT);
			probe.register(h);
			FoodStats.reset();
		}).thenIdle(40);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(Tier.HAMLET, village(h).tier(), "starts as a Hamlet");
			h.assertValueEqual(List.of(Reason.requiresTier(Tier.VILLAGE)), BuildingTestSupport.reasons(h, hutMarker()), "the Hut is gated");
			h.assertValueEqual("Requires Village tier", BuildingTestSupport.reasons(h, hutMarker()).get(0).message(), "and says so");
			HeartSnapshot s = owner(h);
			h.assertValueEqual(6, s.overview().population(), "six villagers");
			h.assertValueEqual(List.of("population", "workers", "storage", "farmhouse", "kitchen", "fed_days"), keys(s), "the Village checklist");
			h.assertValueEqual(List.of(true, true, true, true, true, false), met(s), "all but the fed days");
			h.assertValueEqual(List.of("2", "3"), s.overview().goals().get(5).label().args(), "fed days 2 / 3");
			h.assertTrue(s.jobChoices().stream().anyMatch(j -> j.jobId().equals("builder") && j.tierLocked()), "the builder is shown tier-locked");
			Villager seventh = WorkerTestSupport.spawnVillager(h, new BlockPos(4, 0, 24));
			HireResult r = Workers.hire(h.getLevel(), seventh, JobType.BUILDER, Optional.empty());
			h.assertTrue(r instanceof HireResult.Refused f && f.reason() == HireRefusal.TIER_LOCKED, "a Builder cannot be hired yet: " + r);
			seventh.discard();
			h.assertValueEqual(0, BuildingSystem.priorityQueuedForTests(h.getLevel().getServer()), "no check queued before the dinner");
		});
		seq = toDinner(h, seq, day);
		seq = seq.thenExecute(() -> {
			FoodStats.Day d = FoodStats.snapshot();
			h.assertValueEqual(1L, d.dinners(), "one dinner (day time " + h.getLevel().getDayTime() + ", forced dinner of day " + day[0] + ")");
			h.assertValueEqual(6L, d.diners(), "six diners");
			h.assertValueEqual(6L, d.fed(), "all six fed");
			h.assertValueEqual(20L - d.eatenItems(), NeedsTestSupport.count(h, Items.BREAD), "the bread they ate is gone");
			h.assertValueEqual(3, progress(h).fedStreak(), "the fed streak is 3");
			h.assertValueEqual(day[0], progress(h).lastDinnerDay(), "the dinner day was counted");
			h.assertValueEqual(Tier.HAMLET, village(h).tier(), "no tier-up at dinner");
			h.assertTrue(met(owner(h)).stream().allMatch(b -> b), "every goal is met");
			probe.tier = null;
			probe.priorityQueued = -1;
		}).thenIdle(40);
		seq = seq.thenExecute(() -> h.assertValueEqual(0, BuildingSystem.priorityQueuedForTests(h.getLevel().getServer()), "the queue is empty before the dawn"));
		seq = toDawn(h, seq, pay);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(Tier.VILLAGE, probe.tier, "the probe (after the tier listener) sees a Village in the dawn tick");
			h.assertTrue(probe.priorityQueued >= 4, "every building was queued for a check in that tick: " + probe.priorityQueued);
			h.assertValueEqual(Tier.VILLAGE, village(h).tier(), "the village is a Village");
			log("tier-up queued " + probe.priorityQueued + " checks");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 12, "the Hut re-check after the tier-up", () -> {
			BuildingTestSupport.assertStatus(h, hutMarker(), dev.villageheart.block.MarkerStatus.VALID);
			h.assertTrue(BuildingTestSupport.reasons(h, hutMarker()).isEmpty(), "no reasons left");
		});
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1L, tierUps(rita[0]), "Rita was told once");
			Component line = rita[0].messages.stream().filter(m -> LevelTestSupport.keyOf(m).equals(TIER_UP)).findFirst().orElseThrow();
			Object arg = ((TranslatableContents) line.getContents()).getArgs()[0];
			h.assertTrue(arg instanceof Component c && c.getContents() instanceof TranslatableContents t && t.getKey().equals("tier.villageheart.village"),
					"the tier is a translatable: " + arg);
			Villager seventh = WorkerTestSupport.spawnVillager(h, new BlockPos(4, 0, 24));
			HireResult r = Workers.hire(h.getLevel(), seventh, JobType.BUILDER, Optional.empty());
			h.assertTrue(r instanceof HireResult.Hired, "a Builder can be hired now: " + r);
			HeartSnapshot s = owner(h);
			h.assertValueEqual(Optional.of(Tier.TOWN), s.overview().nextTier(), "next tier is Town");
			h.assertValueEqual(List.of("population", "workers", "guards", "fed_days", "dawn_treasury"), keys(s), "the Town checklist");
			WageTestSupport.done(h);
		});
	}

	// ---- T-L2-2 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:tiers_missing_kitchen", structure = YARD, skyAccess = true, maxTicks = 1200)
	public void missingKitchenNoTierUp(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		PayResult[] pay = new PayResult[1];
		var rita = new BuildingTestSupport.RecordingPlayer[1];
		GameTestSequence seq = layout(h, Layout.FULL.withoutKitchen()).thenExecute(() -> {
			seedProgress(h, new TierProgress(3, today(h), 0));
			rita[0] = RankTestSupport.chatty(h, "rita", Rank.RESIDENT);
		}).thenIdle(20);
		seq = toDawn(h, seq, pay);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(Tier.HAMLET, village(h).tier(), "no Kitchen, no tier-up");
			h.assertValueEqual(0L, tierUps(rita[0]), "no message");
			h.assertValueEqual(List.of(Reason.requiresTier(Tier.VILLAGE)), BuildingTestSupport.reasons(h, hutMarker()), "the Hut is still gated");
			h.assertValueEqual(List.of(true, true, true, true, false, true), met(owner(h)), "only the Kitchen is missing");
			buildKitchen(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(4, 1), "the Kitchen turns green",
				() -> BuildingTestSupport.assertStatus(h, kitchenMarker(), dev.villageheart.block.MarkerStatus.VALID));
		seq = toDawn(h, seq, pay);
		seq.thenExecute(() -> {
			h.assertValueEqual(Tier.VILLAGE, village(h).tier(), "with the Kitchen the next dawn tiers up");
			h.assertValueEqual(1L, tierUps(rita[0]), "one message");
			WageTestSupport.done(h);
		});
	}

	// ---- T-L2-3 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:tiers_hungry_dinner", structure = YARD, skyAccess = true, maxTicks = 1200)
	public void hungryDinnerResetsStreak(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		long[] day = new long[1];
		PayResult[] pay = new PayResult[1];
		int[] resets = new int[1];
		GameTestSequence seq = layout(h, Layout.FULL.withoutFood()).thenExecute(() -> {
			pinClockEarlyInDay(h);
			NeedsOverride.dinnerEnabled = true;
			seedProgress(h, new TierProgress(2, today(h), 0));
			FoodStats.reset();
			resets[0] = TierStats.streakResets;
		}).thenIdle(20);
		seq = toDinner(h, seq, day);
		seq = seq.thenExecute(() -> {
			FoodStats.Day d = FoodStats.snapshot();
			h.assertValueEqual(6L, d.diners(), "six diners");
			h.assertValueEqual(0L, d.fed(), "none fed: a hungry day, not an empty one");
			h.assertValueEqual(0, progress(h).fedStreak(), "the streak is reset");
			h.assertValueEqual(day[0], progress(h).lastDinnerDay(), "the day was counted");
			h.assertValueEqual(resets[0] + 1, TierStats.streakResets, "one reset was recorded");
		});
		seq = toDawn(h, seq, pay);
		seq.thenExecute(() -> {
			h.assertValueEqual(Tier.HAMLET, village(h).tier(), "a hungry village does not grow");
			h.assertValueEqual(List.of("0", "3"), owner(h).overview().goals().get(5).label().args(), "fed days 0 / 3");
			WageTestSupport.done(h);
		});
	}

	// ---- T-L2-4 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:tiers_never_down", structure = YARD, skyAccess = true, maxTicks = 900)
	public void tierNeverGoesDown(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		PayResult[] pay = new PayResult[1];
		var rita = new BuildingTestSupport.RecordingPlayer[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			NeedsTestSupport.sweepVillagers(h);
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			rita[0] = RankTestSupport.chatty(h, "rita", Rank.RESIDENT);
		}).thenIdle(5);
		seq = toDawn(h, seq, pay);
		seq = toDawn(h, seq, pay);
		seq.thenExecute(() -> {
			h.assertValueEqual(Tier.VILLAGE, village(h).tier(), "an empty Village stays a Village");
			HeartSnapshot s = owner(h);
			h.assertValueEqual(Optional.of(Tier.TOWN), s.overview().nextTier(), "next tier is Town");
			h.assertValueEqual(List.of(false, false, false, false, false), met(s), "five unmet Town goals");
			h.assertValueEqual(0L, tierUps(rita[0]), "no message");
			WageTestSupport.done(h);
		});
	}

	// ---- T-L2-5 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:tiers_dawn_treasury", structure = YARD, skyAccess = true, maxTicks = 1200)
	public void dawnTreasuryIsAfterWages(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		PayResult[] pay = new PayResult[1];
		GameTestSequence seq = WageTestSupport.scene(h, 1, 1).thenExecute(() -> {
			WageTestSupport.hireFarmer(h);
			WageTestSupport.wagesOn();
			WageTestSupport.deposit(h, 33);
		}).thenIdle(5);
		seq = toDawn(h, seq, pay);
		seq = seq.thenExecute(() -> {
			h.assertTrue(pay[0] != null, "wages ran");
			h.assertValueEqual(1, pay[0].spent(), "one emerald of wages");
			h.assertValueEqual(32, WageTestSupport.treasury(h), "33 - 1");
			h.assertValueEqual(32, progress(h).bestDawnTreasury(), "the best is the treasury after wages");
			dev.villageheart.world.VillageClaims.data(h.getLevel().getServer()).update(v -> v.withTreasury(32));
		});
		seq = toDawn(h, seq, pay);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(31, WageTestSupport.treasury(h), "32 - 1");
			h.assertValueEqual(32, progress(h).bestDawnTreasury(), "the best never goes down");
			dev.villageheart.world.VillageClaims.data(h.getLevel().getServer()).update(v -> v.withTreasury(0));
		});
		seq = toDawn(h, seq, pay);
		seq.thenExecute(() -> {
			h.assertValueEqual(32, progress(h).bestDawnTreasury(), "and not with an empty purse either");
			WageTestSupport.done(h);
		});
	}

	// ---- T-L2-6 -----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:tiers_reload", structure = YARD, skyAccess = true, maxTicks = 300)
	public void tierProgressSurvivesReload(GameTestHelper h) {
		BuildingTestSupport.begin(h).thenExecute(() -> {
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			TierProgress p = new TierProgress(5, today(h), 20);
			seedProgress(h, p);
			try {
				var back = VillageTestSupport.reload(h.getLevel().getServer()).village().orElseThrow();
				h.assertValueEqual(Tier.VILLAGE, back.tier(), "the tier was saved");
				h.assertValueEqual(p, back.tierProgress(), "the progress was saved");
			} catch (java.io.IOException e) {
				throw new AssertionError(e);
			}
			VillageTestSupport.done(h);
		});
	}

	// ---- the sim report's tier source -------------------------------------------------------------

	/**
	 * The L3 report's {@code village.tier} and {@code tier.fed_streak} come from {@code SimMetricsCollector.collect}. A VILLAGE with a streak
	 * of 4 must read 1 / 4 from the real collector, so a collector that returned {@code TierDay.NONE} (hamlet / 0) fails here.
	 */
	@GameTest(environment = "villageheart-gametest:tiers_sim_day", structure = YARD, skyAccess = true, maxTicks = 200)
	public void simCollectorReadsTheVillageTier(GameTestHelper h) {
		BuildingTestSupport.begin(h).thenExecute(() -> {
			var level = h.getLevel();
			var collector = new dev.villageheart.sim.SimMetricsCollector(level, h.absoluteVec(net.minecraft.world.phys.Vec3.ZERO), 16);
			var hamlet = collector.collect(level.getServer(), 1, level.getGameTime(), dev.villageheart.core.sim.TickStats.EMPTY, 1.0, 20);
			h.assertValueEqual("hamlet", hamlet.tier().name(), "a fresh claim is a Hamlet");
			h.assertValueEqual(0L, hamlet.tier().fedStreak(), "no fed days yet");
			BuildingTestSupport.setTier(h, Tier.VILLAGE);
			seedProgress(h, new TierProgress(4, today(h), 7));
			var day = collector.collect(level.getServer(), 2, level.getGameTime(), dev.villageheart.core.sim.TickStats.EMPTY, 1.0, 20);
			h.assertValueEqual("village", day.tier().name(), "the report's tier is the village's tier");
			h.assertValueEqual(1, day.tier().ordinal(), "village.tier metric is 1");
			h.assertValueEqual(4L, day.tier().fedStreak(), "the report's fed streak is the village's streak");
			VillageTestSupport.done(h);
		});
	}
}
