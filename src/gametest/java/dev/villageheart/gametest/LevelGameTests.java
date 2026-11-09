package dev.villageheart.gametest;

import static dev.villageheart.gametest.LevelTestSupport.data;
import static dev.villageheart.gametest.LevelTestSupport.progress;
import static dev.villageheart.gametest.LevelTestSupport.put;
import static dev.villageheart.gametest.LumberTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.Tier;
import dev.villageheart.core.job.Progress;
import dev.villageheart.core.job.XpChange;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.item.ItemStack;

/**
 * Layer 2: job levels and XP (M15). Own environment per test ({@code levels_<name>}), the lumber scene, {@code begin}/{@code done}/{@code end}.
 * Levels are set by writing the attachment where a test needs a start state.
 */
public class LevelGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static int badge(Villager v) { return v.getVillagerData().level(); }

	// ---- T-L2-1 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_add_xp", structure = YARD, skyAccess = true, maxTicks = 600)
	public void addXpLevelsUpAndMirrors(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		scene(h, false, false).thenExecute(() -> {
			LevelTestSupport.tier(h, Tier.VILLAGE);
			v[0] = hire(h, spawnSpot());
			h.assertValueEqual(new Progress(1, 0), progress(v[0]), "a hire starts at level 1, 0 XP");
			h.assertValueEqual(1, badge(v[0]), "badge 1");
			Optional<XpChange> a = Workers.addXp(v[0], 99);
			h.assertValueEqual(new Progress(1, 99), progress(v[0]), "99 XP: still level 1");
			h.assertTrue(a.isPresent() && !a.get().levelledUp(), "no level-up at 99");
			Optional<XpChange> b = Workers.addXp(v[0], 1);
			h.assertValueEqual(new Progress(2, 100), progress(v[0]), "100 XP: level 2, in the same tick");
			h.assertValueEqual(2, badge(v[0]), "the vanilla level badge follows in the same tick");
			h.assertTrue(b.isPresent() && b.get().levelledUp(), "level-up reported");
			Optional<XpChange> c = Workers.addXp(v[0], 500);
			h.assertValueEqual(new Progress(2, 300), progress(v[0]), "Village caps at level 2; XP is banked to 300");
			h.assertTrue(c.isPresent() && c.get().reachedCap() && !c.get().levelledUp(), "the cap was reached");
			h.assertValueEqual(2, badge(v[0]), "badge stays 2");
			Optional<XpChange> zero = Workers.addXp(v[0], 0);
			h.assertTrue(zero.isPresent() && !zero.get().levelledUp() && !zero.get().reachedCap(), "0 XP changes nothing");
			Villager stranger = WorkerTestSupport.spawnVillager(h, spawnSpot());
			h.assertTrue(Workers.addXp(stranger, 50).isEmpty(), "a non-worker gets nothing");
			h.assertTrue(stranger.getAttached(WorkerAttachments.WORKER) == null, "and no attachment");
			try {
				Workers.addXp(v[0], -1);
				h.assertTrue(false, "negative XP must throw");
			} catch (IllegalArgumentException expected) {
				// fine
			}
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_tier_up", structure = YARD, skyAccess = true, maxTicks = 800)
	public void tierUpTurnsBankedXpIntoLevels(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		GameTestSequence seq = scene(h, true, false).thenExecute(() -> {
			v[0] = hire(h, spawnSpot());
			v[1] = hire(h, spawnSpot());
			put(v[0], 1, 300);
			put(v[1], 3, 0);
		});
		seq = seq.thenIdle(45);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(new Progress(1, 300), progress(v[0]), "at Hamlet the banked XP does not make a level");
			h.assertValueEqual(1, badge(v[0]), "badge 1");
			h.assertValueEqual(new Progress(3, 0), progress(v[1]), "a level above the cap is kept at Hamlet (the pass never lowers)");
			LevelTestSupport.tier(h, Tier.VILLAGE);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 21, "the level pass after the tier-up",
				() -> h.assertValueEqual(2, data(v[0]).level(), "level after setTier(VILLAGE)"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(2, badge(v[0]), "badge 2");
			LevelTestSupport.tier(h, Tier.TOWN);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 21, "the level pass after the second tier-up",
				() -> h.assertValueEqual(3, data(v[0]).level(), "level after setTier(TOWN)"));
		seq.thenExecute(() -> {
			h.assertValueEqual(new Progress(3, 300), progress(v[0]), "XP kept");
			h.assertValueEqual(3, badge(v[0]), "badge 3");
			h.assertValueEqual(new Progress(3, 0), progress(v[1]), "the level-3 worker is untouched");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_reload_fire", structure = YARD, skyAccess = true, maxTicks = 600)
	public void levelSurvivesReloadAndFireResets(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		scene(h, false, false).thenExecute(() -> {
			LevelTestSupport.tier(h, Tier.VILLAGE);
			v[0] = hire(h, spawnSpot());
			put(v[0], 2, 150);
			v[0] = reloadEntity(h.getLevel(), v[0]);
			h.assertValueEqual(new Progress(2, 150), progress(v[0]), "level and XP survive a save and load");
			h.assertValueEqual(2, badge(v[0]), "the badge survives too");
			h.assertTrue(Workers.fire(h.getLevel(), v[0]), "fired");
			h.assertTrue(v[0].getAttached(WorkerAttachments.WORKER) == null, "no attachment after firing");
			h.assertValueEqual(1, badge(v[0]), "vanilla level 1 after firing");
			h.assertTrue(v[0].getVillagerData().profession().is(VillagerProfession.NONE), "unemployed");
			HireResult r = Workers.hire(h.getLevel(), v[0], JobType.LUMBERJACK, Optional.empty());
			h.assertTrue(r instanceof HireResult.Hired, "rehired: " + r);
			h.assertValueEqual(new Progress(1, 0), progress(v[0]), "a rehire starts at level 1, 0 XP");
			h.assertValueEqual(1, badge(v[0]), "badge 1");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-11 ---------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_cured_zombie", structure = YARD, skyAccess = true, maxTicks = 900)
	public void curedZombieWorkerIsNotAWorker(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		java.util.UUID[] id = new java.util.UUID[1];
		net.minecraft.world.entity.monster.zombie.ZombieVillager[] zv = new net.minecraft.world.entity.monster.zombie.ZombieVillager[1];
		GameTestSequence seq = scene(h, false, false).thenExecute(() -> {
			LevelTestSupport.tier(h, Tier.VILLAGE);
			v[0] = hire(h, spawnSpot());
			put(v[0], 2, 150);
			id[0] = v[0].getUUID();
			net.minecraft.world.entity.monster.zombie.Zombie zombie = h.spawn(EntityType.ZOMBIE, spawnSpot());
			zombie.setNoAi(true);
			h.assertTrue(zombie.convertVillagerToZombieVillager(h.getLevel(), v[0]), "converted");
			var zombies = h.getLevel().getEntitiesOfClass(net.minecraft.world.entity.monster.zombie.ZombieVillager.class,
					new net.minecraft.world.phys.AABB(h.absolutePos(spawnSpot())).inflate(3));
			h.assertValueEqual(1, zombies.size(), "one zombie villager");
			zv[0] = zombies.get(0);
			zv[0].setNoAi(true);
			zombie.discard();
			h.assertTrue(zv[0].getVillagerData().profession().is(VillagerProfession.NONE), "no mod profession on the zombie villager");
			h.assertValueEqual(1, zv[0].getVillagerData().level(), "no worker level badge on the zombie villager");
			h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().worker(id[0]).isEmpty(), "record gone");
			// the vanilla cure path: finishConversion copies the zombie's VillagerData onto a new villager
			try {
				java.lang.reflect.Method start = net.minecraft.world.entity.monster.zombie.ZombieVillager.class
						.getDeclaredMethod("startConverting", java.util.UUID.class, int.class);
				start.setAccessible(true);
				start.invoke(zv[0], null, 5);
			} catch (ReflectiveOperationException e) {
				throw new AssertionError("could not start the cure", e);
			}
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 120, "the cure",
				() -> h.assertTrue(cured(h) != null, "the zombie villager was cured into a villager"));
		seq.thenExecute(() -> {
			Villager c = cured(h);
			h.assertTrue(c != null, "the cured villager exists");
			WorkerTestSupport.track(c);
			h.assertTrue(c.getAttached(WorkerAttachments.WORKER) == null, "no attachment on the cured villager");
			h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).orElseThrow().worker(c.getUUID()).isEmpty(), "no record");
			h.assertTrue(c.getVillagerData().profession().is(VillagerProfession.NONE), "no mod profession: " + c.getVillagerData().profession().getRegisteredName());
			h.assertValueEqual(1, c.getVillagerData().level(), "vanilla level 1");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	/** Samples every tick: XP must equal the start XP plus the logs felled so far (no level-up changes that), and the level must be right. */
	private static final class XpSampler {
		final java.util.Set<BlockPos> logs;
		final int startXp;
		int cap;
		boolean active;
		final java.util.List<String> problems = new java.util.ArrayList<>();
		long levelChangeTick = -1;
		int lastLevel;

		XpSampler(java.util.Set<BlockPos> logs, int startXp, int cap) {
			this.logs = logs;
			this.startXp = startXp;
			this.cap = cap;
		}

		void sample(GameTestHelper h, Villager v) {
			if (!active || problems.size() >= 5) return;
			int down = logs.size() - standing(h, logs);
			var d = data(v);
			if (d.xp() != Math.min(dev.villageheart.core.Balance.MAX_BANKED_XP, startXp + down)) {
				problems.add("@" + h.getTick() + " " + down + " logs down but xp " + d.xp());
			}
			if (d.level() != lastLevel) {
				if (levelChangeTick < 0) levelChangeTick = h.getTick();
				lastLevel = d.level();
			}
			if (d.level() > cap) problems.add("@" + h.getTick() + " level " + d.level() + " above the expected cap " + cap);
		}
	}

	/** Records the events of {@link dev.villageheart.worker.WorkerLevels}. */
	private static final class EventLog implements dev.villageheart.worker.WorkerLevels.LevelListener {
		int levelUps;
		int capped;

		@Override
		public void onLevelChanged(net.minecraft.server.level.ServerLevel level, Villager villager, XpChange change) { levelUps++; }

		@Override
		public void onCapped(net.minecraft.server.level.ServerLevel level, Villager villager, XpChange change) { capped++; }
	}

	@GameTest(environment = "villageheart-gametest:levels_felling_speed", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void levelUpSpeedsUpFelling(GameTestHelper h) {
		begin(h);
		Watcher w = new Watcher();
		XpSampler[] xs = new XpSampler[1];
		Villager[] v = new Villager[1];
		EventLog events = new EventLog();
		h.onEachTick(() -> {
			w.sample(h);
			if (xs[0] != null) xs[0].sample(h, v[0]);
		});
		BlockPos treeRel = new BlockPos(2, 0, 8);
		java.util.Set<BlockPos>[] logs = new java.util.Set[1];
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			LevelTestSupport.tier(h, Tier.VILLAGE);
			BlockPos base = growOak(h, treeRel, 3L);
			logs[0] = logsOf(h, base);
			h.assertTrue(logs[0].size() >= 6, "the oak (seed 3) has 6 logs: " + logs[0].size());
			putInChest(h, chest(), 0, axe(dev.villageheart.core.job.ToolTier.STONE));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			put(v[0], 1, 97);
			dev.villageheart.worker.WorkerLevels.register(events);
			xs[0] = new XpSampler(logs[0], 97, 2);
			xs[0].lastLevel = 1;
			xs[0].active = true;
			w.start(h, logs[0]);
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[0]), "logs of the tree left"));
		seq.thenExecute(() -> {
			dev.villageheart.worker.WorkerLevels.unregister(events);
			var gaps = w.gaps();
			System.out.println("[m15] levelUpSpeedsUpFelling: " + logs[0].size() + " logs, gaps " + gaps);
			h.assertTrue(xs[0].problems.isEmpty(), "xp per log: " + xs[0].problems);
			h.assertValueEqual(new Progress(2, 97 + logs[0].size()), progress(v[0]), "XP grew by exactly one per log; level 2 at 100");
			h.assertValueEqual(1, events.levelUps, "one level-up event");
			h.assertTrue(gaps.size() >= 5, "enough logs to see both paces: " + gaps);
			h.assertTrue(Math.abs(gaps.get(0) - 40) <= 1, "log 1 to 2 with the stone axe at level 1: " + gaps.get(0));
			h.assertTrue(Math.abs(gaps.get(1) - 40) <= 1, "log 2 to 3 with the stone axe at level 1: " + gaps.get(1));
			for (int i = 2; i < gaps.size(); i++) {
				h.assertTrue(Math.abs(gaps.get(i) - 32) <= 1, "gap " + i + " after the level-up should be 32 (80 / (2.0 * 1.25)): " + gaps.get(i));
			}
			h.assertValueEqual(logs[0].size(), StockpileTestSupport.container(h, chest()).getItem(0).getDamageValue(), "one durability per log");
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_hamlet_cap", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void hamletCapHoldsPace(GameTestHelper h) {
		begin(h);
		Watcher w = new Watcher();
		XpSampler[] xs = new XpSampler[1];
		Villager[] v = new Villager[1];
		EventLog events = new EventLog();
		h.onEachTick(() -> {
			w.sample(h);
			if (xs[0] != null) xs[0].sample(h, v[0]);
		});
		BlockPos treeRel = new BlockPos(2, 0, 8);
		java.util.Set<BlockPos>[] logs = new java.util.Set[1];
		boolean[] raised = new boolean[1];
		long[] raisedAt = new long[1];
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			BlockPos base = growOak(h, treeRel, 3L);
			logs[0] = logsOf(h, base);
			h.assertTrue(logs[0].size() >= 6, "the oak (seed 3) has 6 logs: " + logs[0].size());
			putInChest(h, chest(), 0, axe(dev.villageheart.core.job.ToolTier.STONE));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			put(v[0], 1, 99);
			dev.villageheart.worker.WorkerLevels.register(events);
			xs[0] = new XpSampler(logs[0], 99, 1);
			xs[0].lastLevel = 1;
			xs[0].active = true;
			w.start(h, logs[0]);
		});
		// the cap holds until two logs are down; then the village grows
		seq = seq.thenWaitUntil(() -> h.assertTrue(logs[0].size() - standing(h, logs[0]) >= 2, "two logs down"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(new Progress(1, 101), progress(v[0]), "XP passed 100 at Hamlet, the level stayed 1");
			h.assertValueEqual(1, events.capped, "one capped notice");
			h.assertValueEqual(0, events.levelUps, "no level-up at Hamlet");
			LevelTestSupport.tier(h, Tier.VILLAGE);
			xs[0].cap = 2;
			raisedAt[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 21, "the level after the tier-up", () -> h.assertValueEqual(2, data(v[0]).level(), "level 2"));
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(0, standing(h, logs[0]), "logs of the tree left"));
		seq.thenExecute(() -> {
			dev.villageheart.worker.WorkerLevels.unregister(events);
			var gaps = w.gaps();
			var rem = w.removals;
			System.out.println("[m15] hamletCapHoldsPace: " + logs[0].size() + " logs, removals " + rem + ", level raised after tick " + raisedAt[0] + ", gaps " + gaps);
			h.assertTrue(xs[0].problems.isEmpty(), "xp per log: " + xs[0].problems);
			h.assertTrue(xs[0].levelChangeTick >= raisedAt[0], "the level only changed after the tier-up: " + xs[0].levelChangeTick + " vs " + raisedAt[0]);
			int checked32 = 0;
			for (int i = 0; i < gaps.size(); i++) {
				long start = rem.get(i);
				if (start < xs[0].levelChangeTick - 0) {
					if (rem.get(i + 1) <= xs[0].levelChangeTick) h.assertTrue(Math.abs(gaps.get(i) - 40) <= 1, "gap " + i + " at level 1 is " + gaps.get(i));
				} else {
					h.assertTrue(Math.abs(gaps.get(i) - 32) <= 1, "gap " + i + " after the level change is " + gaps.get(i));
					checked32++;
				}
			}
			h.assertTrue(checked32 >= 1, "at least one gap measured at level 2");
			h.assertValueEqual(1, events.levelUps, "one level-up event, after the tier-up");
			assertNoGiveUps(h);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-7 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_farmer_xp", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void farmerEarnsXpPerCrop(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		long[] stat = new long[1];
		GameTestSequence seq = FarmTestSupport.scene(h, 1, true).thenExecute(() -> {
			FarmTestSupport.field(h, 8, 14, 10, 15, dev.villageheart.core.farm.CropKind.WHEAT, 7, false);
			stat[0] = dev.villageheart.worker.job.WorkStats.get("farmer", "harvested");
			h.getLevel().setDayTime(1000);
			v[0] = FarmTestSupport.hire(h, FarmTestSupport.spawnSpot());
		});
		seq = seq.thenWaitUntil(() -> h.assertValueEqual(6L, dev.villageheart.worker.job.WorkStats.get("farmer", "harvested") - stat[0], "crops harvested"));
		seq.thenExecute(() -> {
			h.assertValueEqual(new Progress(1, 6), progress(v[0]), "one XP per harvested crop");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_feedback_audience", structure = YARD, skyAccess = true, maxTicks = 600)
	public void levelUpFeedbackReachesMembersOnly(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		var owner = new BuildingTestSupport.RecordingPlayer[1];
		var resident = new BuildingTestSupport.RecordingPlayer[1];
		var visitor = new BuildingTestSupport.RecordingPlayer[1];
		java.util.List<dev.villageheart.worker.LevelUpFeedback.FeedbackEvent> seen = new java.util.ArrayList<>();
		String up = "message.villageheart.worker_level_up";
		String capped = "message.villageheart.worker_level_capped";
		GameTestSequence seq = scene(h, false, false).thenExecute(() -> {
			LevelTestSupport.tier(h, Tier.VILLAGE);
			owner[0] = LevelTestSupport.ownerRecorder(h);
			resident[0] = RankTestSupport.chatty(h, "res", dev.villageheart.core.Rank.RESIDENT);
			visitor[0] = RankTestSupport.chatty(h, "vis", dev.villageheart.core.Rank.VISITOR);
			dev.villageheart.worker.LevelUpFeedback.setSinkForTest(seen::add);
			v[0] = hire(h, spawnSpot());
			put(v[0], 1, 99);
			Workers.addXp(v[0], 1);
		}).thenIdle(2);
		seq = seq.thenExecute(() -> {
			for (var p : java.util.List.of(owner[0], resident[0])) {
				h.assertValueEqual(1L, LevelTestSupport.count(p, up), p.getGameProfile().name() + " got the level-up line");
				var line = p.messages.stream().filter(m -> LevelTestSupport.keyOf(m).equals(up)).findFirst().orElseThrow();
				var args = ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getArgs();
				h.assertValueEqual(dev.villageheart.core.heart.VillagerNames.of(v[0].getUUID()), args[0], "the worker's name");
				h.assertValueEqual("Lumberjack", ((net.minecraft.network.chat.Component) args[1]).getString(), "the job");
				h.assertValueEqual(2, args[2], "the new level");
			}
			h.assertValueEqual(0L, LevelTestSupport.count(visitor[0], up), "a Visitor is not told");
			h.assertValueEqual(1, seen.size(), "the seam saw one event");
			h.assertValueEqual(dev.villageheart.worker.LevelUpFeedback.Kind.LEVEL_UP, seen.get(0).kind(), "a level-up");
			h.assertValueEqual(1, seen.get(0).from(), "from 1");
			h.assertValueEqual(2, seen.get(0).to(), "to 2");
			// the village shrinks back to a Hamlet cap for the capped notice (the tier never goes down in the game; this is a test setup)
			LevelTestSupport.tier(h, Tier.HAMLET);
			put(v[0], 1, 99);
			seen.clear();
			Workers.addXp(v[0], 1);
		}).thenIdle(2);
		seq = seq.thenExecute(() -> {
			for (var p : java.util.List.of(owner[0], resident[0])) {
				h.assertValueEqual(1L, LevelTestSupport.count(p, capped), p.getGameProfile().name() + " got the capped notice");
				var line = p.messages.stream().filter(m -> LevelTestSupport.keyOf(m).equals(capped)).findFirst().orElseThrow();
				var args = ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getArgs();
				h.assertValueEqual(2, args[1], "the level it is waiting for");
				h.assertValueEqual("Village", args[2], "the tier it needs");
			}
			h.assertValueEqual(0L, LevelTestSupport.count(visitor[0], capped), "a Visitor is not told");
			h.assertValueEqual(1, seen.size(), "one capped event");
			h.assertValueEqual(dev.villageheart.worker.LevelUpFeedback.Kind.CAPPED, seen.get(0).kind(), "capped");
			Workers.addXp(v[0], 1);
		}).thenIdle(2);
		seq.thenExecute(() -> {
			h.assertValueEqual(1L, LevelTestSupport.count(owner[0], capped), "a second +1 sends nothing");
			h.assertValueEqual(1, seen.size(), "and the seam saw nothing new");
			dev.villageheart.worker.LevelUpFeedback.setSinkForTest(null);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	@GameTest(environment = "villageheart-gametest:levels_tier_up_flood", structure = YARD, skyAccess = true, maxTicks = 700)
	public void tierUpFloodIsSummarised(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[5];
		var owner = new BuildingTestSupport.RecordingPlayer[1];
		String up = "message.villageheart.worker_level_up";
		String more = "message.villageheart.workers_level_up_more";
		GameTestSequence seq = scene(h, true, false).thenExecute(() -> {
			// five Chopping Blocks in the camp: 2 on the north wall already (x 0 and 4), the door cell at x 2 stays free
			for (int[] c : new int[][] {{1, 0, 0}, {3, 0, 0}, {0, 0, 3}}) h.setBlock(BuildingTestSupport.inside(CAMP, c[0], c[1], c[2]), dev.villageheart.block.JobBlocks.BLOCKS.get(dev.villageheart.core.building.JobBlockType.CHOPPING_BLOCK));
		}).thenIdle(BuildingTestSupport.bound(3, 8));
		seq = seq.thenExecute(() -> {
			owner[0] = LevelTestSupport.ownerRecorder(h);
			for (int i = 0; i < 5; i++) {
				v[i] = hire(h, spawnSpot());
				put(v[i], 1, 300);
			}
			// make the five level-ups land in the same pass: wait for a tick just before a multiple of 20, then raise the tier
		});
		seq = seq.thenIdle(45);
		seq = seq.thenExecute(() -> {
			for (Villager w : v) h.assertValueEqual(1, data(w).level(), "capped at Hamlet");
			h.assertValueEqual(0L, LevelTestSupport.count(owner[0], up), "no line yet");
			LevelTestSupport.tier(h, Tier.VILLAGE);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 21, "the five level-ups after the tier-up", () -> {
			for (Villager w : v) h.assertValueEqual(2, data(w).level(), "level 2");
		});
		seq.thenExecute(() -> {
			h.assertValueEqual(3L, LevelTestSupport.count(owner[0], up), "three level-up lines");
			h.assertValueEqual(1L, LevelTestSupport.count(owner[0], more), "and one summary line");
			var line = owner[0].messages.stream().filter(m -> LevelTestSupport.keyOf(m).equals(more)).findFirst().orElseThrow();
			h.assertValueEqual(2, ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getArgs()[0], "'and 2 more'");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-10 (roadmap) -----------------------------------------------------------------------

	/** Per-tick measurement of the side-by-side test: logs per grove in the felling window, claims, axe wear, "Needs axe" spells. */
	private static final class SideSampler {
		final SideBySide.Grove[] groves;
		final Villager[] workers;
		final int[][] startDamage;
		final int baseY;
		final int[] last = new int[3];
		final int[] window = new int[3];
		final int[] total = new int[3];
		final int[] needsAxeSteps = new int[3];
		final int[] longestNeedsAxe = new int[3];
		final java.util.List<String> problems = new java.util.ArrayList<>();
		final java.util.Set<BlockPos>[] sets = new java.util.Set[3];
		long windowStartDay = 1000, windowEndDay = 5000;
		boolean active;

		SideSampler(SideBySide.Grove[] groves, Villager[] workers, int[][] startDamage, int[] maxDamageByLevel, int baseY) {
			this.groves = groves;
			this.workers = workers;
			this.startDamage = startDamage;
			this.maxDamageByLevel = maxDamageByLevel;
			this.baseY = baseY;
			for (int k = 0; k < 3; k++) sets[k] = groves[k].all();
		}

		final int[] maxDamageByLevel;

		void start(GameTestHelper h) {
			for (int k = 0; k < 3; k++) last[k] = LumberTestSupport.standing(h, sets[k]);
			active = true;
		}

		void sample(GameTestHelper h) {
			if (!active) return;
			long day = h.getLevel().getDayTime() % 24000;
			for (int k = 0; k < 3; k++) {
				int now = LumberTestSupport.standing(h, sets[k]);
				int d = last[k] - now;
				if (d > 0) {
					total[k] += d;
					if (day >= windowStartDay && day < windowEndDay) window[k] += d;
				}
				last[k] = now;
			}
			var claims = dev.villageheart.worker.lumber.LumberRuntime.get(h.getLevel().getServer()).claims();
			for (var e : claims.entrySet()) {
				long idx = Math.round((e.getKey().y() - baseY) / (double) SideBySide.DY);
				if (idx < 0 || idx > 2 || !e.getValue().equals(workers[(int) idx].getUUID())) {
					if (problems.size() < 6) problems.add("@" + h.getTick() + " tree at " + e.getKey() + " claimed by " + e.getValue() + " (level " + idx + ")");
				}
			}
			if (h.getTick() % 5 == 0) {
				for (int k = 0; k < 3; k++) {
					if (LumberTestSupport.status(h, workers[k]) == dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL) {
						needsAxeSteps[k]++;
						longestNeedsAxe[k] = Math.max(longestNeedsAxe[k], needsAxeSteps[k]);
					} else {
						needsAxeSteps[k] = 0;
					}
				}
			}
			if (h.getTick() % 100 == 0) checkWear(h);
		}

		void checkWear(GameTestHelper h) {
			for (int k = 0; k < 3; k++) {
				int wear = SideBySide.wear(h, k, startDamage[k], maxDamageByLevel[k]);
				if (wear != total[k] && problems.size() < 6) problems.add("@" + h.getTick() + " level " + k + ": axe wear " + wear + " but " + total[k] + " logs felled");
			}
		}
	}

	@GameTest(environment = "villageheart-gametest:levels_side_by_side", structure = YARD, skyAccess = true, maxTicks = 6500)
	public void ironAxeOutFellsStoneSideBySide(GameTestHelper h) {
		begin(h);
		SideBySide.clearUpper(h);
		dev.villageheart.core.job.ToolTier[] tier = {dev.villageheart.core.job.ToolTier.IRON, dev.villageheart.core.job.ToolTier.STONE,
				dev.villageheart.core.job.ToolTier.WOOD};
		SideBySide.Grove[] groves = new SideBySide.Grove[3];
		Villager[] w = new Villager[3];
		int[][] startDamage = new int[3][2];
		int[] maxDamage = new int[3];
		SideSampler[] sampler = new SideSampler[1];
		h.onEachTick(() -> {
			if (sampler[0] != null) sampler[0].sample(h);
		});
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			LumberTestSupport.sweepStrayTrees(h, 48);
			for (int k = 0; k < 3; k++) {
				SideBySide.platform(h, k);
				SideBySide.buildBuildings(h, k);
			}
		}).thenWaitUntil(() -> SideBySide.assertValid(h));
		seq = seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			var heart = dev.villageheart.world.VillageClaims.current(server).orElseThrow().heart();
			int baseY = h.absolutePos(BlockPos.ZERO).getY();
			for (int k = 0; k < 3; k++) {
				groves[k] = SideBySide.grow(h, k);
				int total = 0;
				for (var tree : groves[k].trees()) {
					h.assertTrue(tree.size() >= 20, "every tree has at least 20 logs: " + tree.size());
					total += tree.size();
				}
				h.assertValueEqual(total, groves[k].logs(), "the trees of grove " + k + " do not touch each other");
				h.assertTrue(total >= 160, "grove " + k + " holds at least 160 logs: " + total);
				for (BlockPos p : groves[k].all()) {
					h.assertTrue(dev.villageheart.core.ClaimGeometry.inClaim(heart, dev.villageheart.world.VillageClaims.locate(h.getLevel(), p)),
							"log " + p + " of grove " + k + " lies inside the claim");
					h.assertTrue(p.getY() - baseY - SideBySide.dy(k) <= 12, "a log at " + p + " is more than 12 above its ground");
				}
				h.assertValueEqual(SideBySide.normalised(groves[0]), SideBySide.normalised(groves[k]), "grove " + k + " is grove 0 translated");
				for (BlockPos p : new BlockPos[] {SideBySide.chop(k), SideBySide.storeChest1(k), SideBySide.chest(k)}) {
					h.assertTrue(dev.villageheart.core.ClaimGeometry.inClaim(heart, BuildingTestSupport.loc(h, p)), p + " lies inside the claim");
				}
				ItemStack worn = axe(tier[k]);
				maxDamage[k] = worn.getMaxDamage();
				worn.setDamageValue(maxDamage[k] - 29); // every first axe breaks after 29 logs, the second takes over
				startDamage[k][0] = worn.getDamageValue();
				putInChest(h, SideBySide.chest(k), 0, worn);
				putInChest(h, SideBySide.chest(k), 1, axe(tier[k]));
			}
			h.getLevel().setDayTime(1000);
			for (int k = 0; k < 3; k++) w[k] = SideBySide.hireAt(h, k);
			sampler[0] = new SideSampler(groves, w, startDamage, maxDamage, baseY);
			sampler[0].start(h);
		});
		seq = seq.thenIdle(5);
		seq = seq.thenExecute(() -> h.assertTrue(h.getLevel().getDayTime() > 1004, "the day advances"));
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() % 24000 >= 5000, "day tick 5000"));
		seq.thenExecute(() -> {
			try {
				SideSampler s = sampler[0];
				s.active = false;
				s.checkWear(h);
				int iron = s.window[0], stone = s.window[1], wood = s.window[2];
				double ironStone = iron / (double) Math.max(1, stone), ironWood = iron / (double) Math.max(1, wood);
				StringBuilder wlog = new StringBuilder();
				for (int k = 0; k < 3; k++) {
					double wait = s.window[k] == 0 ? -1 : (4000.0 - s.window[k] * LumberTestSupport.ticksPerLog(tier[k])) / s.window[k];
					wlog.append(String.format(" %s=%.1f", tier[k], wait));
				}
				System.out.println(String.format("[m15] side-by-side wood=%d stone=%d iron=%d ratios iron/stone=%.3f iron/wood=%.3f stone/wood=%.3f overhead(ticks per log)%s felled-total=%s",
						wood, stone, iron, ironStone, ironWood, stone / (double) Math.max(1, wood), wlog, java.util.Arrays.toString(s.total)));
				h.assertTrue(s.problems.isEmpty(), "measurement problems: " + s.problems);
				for (int k = 0; k < 3; k++) {
					int standing = LumberTestSupport.standing(h, s.sets[k]);
					h.assertTrue(standing >= 20, "grove ran out at level " + k + " (" + standing + " logs stand): the comparison is invalid");
					h.assertTrue(s.total[k] > 0 && SideBySide.wear(h, k, startDamage[k], maxDamage[k]) == s.total[k], "level " + k + ": axe wear = logs felled");
					h.assertTrue(s.longestNeedsAxe[k] <= 8, "level " + k + " was without an axe for " + s.longestNeedsAxe[k] * 5 + " ticks");
					h.assertValueEqual(1, data(w[k]).level(), "level " + k + " worker stays level 1 at Hamlet");
					h.assertValueEqual(Math.min(300, s.total[k]), data(w[k]).xp(), "level " + k + " worker: one XP per log felled");
					var collected = w[k].getInventory().countItem(net.minecraft.world.item.Items.DARK_OAK_LOG)
							+ SideBySide.count(StockpileTestSupport.container(h, SideBySide.storeChest1(k)), net.minecraft.world.item.Items.DARK_OAK_LOG)
							+ SideBySide.count(StockpileTestSupport.container(h, SideBySide.storeChest2(k)), net.minecraft.world.item.Items.DARK_OAK_LOG);
					h.assertTrue(collected >= s.total[k] - 1, "level " + k + ": " + collected + " logs carried or stored for " + s.total[k] + " felled");
				}
				h.assertTrue(wood < stone && stone < iron, "wood < stone < iron: " + wood + " " + stone + " " + iron);
				h.assertTrue(iron >= 1.3 * stone, "iron fells at least 1.3x stone (ROADMAP M15 criterion, restored by the human 2026-11-09): " + iron + " vs " + stone + " (" + ironStone + ")");
				h.assertTrue(iron >= 1.3 * wood, "iron fells at least 1.3x wood: " + iron + " vs " + wood + " (" + ironWood + ")");
			assertNoGiveUps(h);
			} finally {
				SideBySide.clearAll(h); // a failed assertion must not leave the platforms above other tests
			}
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** The villager the cure produced: any live villager around the camp (the converted one was discarded). */
	private static Villager cured(GameTestHelper h) {
		var box = new net.minecraft.world.phys.AABB(h.absolutePos(spawnSpot())).inflate(10);
		var list = h.getLevel().getEntitiesOfClass(Villager.class, box, e -> !e.isRemoved());
		return list.isEmpty() ? null : list.get(0);
	}

	// ---- M14 step 3a: the tier-up that lifts a banked worker back to a cap says "needs a Town" ----------------------------

	@GameTest(environment = "villageheart-gametest:levels_tier_up_needs_town", structure = YARD, skyAccess = true, maxTicks = 700)
	public void tierUpToVillageTellsNeedsTown(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		EventLog events = new EventLog();
		var owner = new BuildingTestSupport.RecordingPlayer[1];
		String capped = "message.villageheart.worker_level_capped";
		GameTestSequence seq = scene(h, false, false).thenExecute(() -> {
			owner[0] = LevelTestSupport.ownerRecorder(h);
			v[0] = hire(h, spawnSpot());
			put(v[0], 1, 300);
			dev.villageheart.worker.WorkerLevels.register(events);
		}).thenIdle(45);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(0, events.levelUps + events.capped, "at Hamlet the banked XP makes no event");
			LevelTestSupport.tier(h, Tier.VILLAGE);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 41, "the level pass after the tier-up",
				() -> h.assertValueEqual(2, data(v[0]).level(), "level after setTier(VILLAGE)"));
		seq.thenIdle(3).thenExecute(() -> {
			dev.villageheart.worker.WorkerLevels.unregister(events);
			h.assertValueEqual(1, events.levelUps, "one level-up event (1 -> 2)");
			h.assertValueEqual(1, events.capped, "one capped notice: level 3 needs a Town");
			h.assertValueEqual(1L, LevelTestSupport.count(owner[0], capped), "the owner was told");
			var line = owner[0].messages.stream().filter(m -> LevelTestSupport.keyOf(m).equals(capped)).findFirst().orElseThrow();
			var args = ((net.minecraft.network.chat.contents.TranslatableContents) line.getContents()).getArgs();
			h.assertValueEqual("Town", String.valueOf(args[2]), "the tier it needs");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:levels_tier_up_no_notice", structure = YARD, skyAccess = true, maxTicks = 700)
	public void tierUpWithoutEnoughXpHasNoCappedNotice(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		EventLog events = new EventLog();
		GameTestSequence seq = scene(h, false, false).thenExecute(() -> {
			v[0] = hire(h, spawnSpot());
			put(v[0], 1, 150);
			dev.villageheart.worker.WorkerLevels.register(events);
		}).thenIdle(45);
		seq = seq.thenExecute(() -> LevelTestSupport.tier(h, Tier.VILLAGE));
		seq = BuildingTestSupport.awaitWithin(h, seq, 41, "the level pass after the tier-up",
				() -> h.assertValueEqual(2, data(v[0]).level(), "level after setTier(VILLAGE)"));
		seq.thenIdle(3).thenExecute(() -> {
			dev.villageheart.worker.WorkerLevels.unregister(events);
			h.assertValueEqual(1, events.levelUps, "one level-up event");
			h.assertValueEqual(0, events.capped, "150 XP is not enough for level 3: no notice");
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
