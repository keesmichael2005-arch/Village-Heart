package dev.villageheart.gametest;

import static dev.villageheart.gametest.MineTestSupport.*;
import static dev.villageheart.gametest.SelfCraftGameTests.items;
import static dev.villageheart.gametest.SelfCraftGameTests.mineScene;
import static dev.villageheart.gametest.SelfCraftTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.gear.GearCraft;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SelfCraft;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.gametest.SelfCraftTestSupport.Sampler;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.gear.GearOrders;
import dev.villageheart.worker.gear.SelfCraftRuntime;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.WorkStats;
import java.util.List;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Layer 2: the life cycle of a self-craft (M19a): wood then stone, the lumberjack's bootstrap, pause and reload, refunds, sharing,
 * Storage tools, a full chest, worn-first. Same rules as {@link SelfCraftGameTests}.
 */
public class SelfCraftLifecycleGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	static dev.villageheart.core.heart.VillagerRow row(GameTestHelper h, Villager v) {
		return HeartTestSupport.snapshot(h, dev.villageheart.core.Rank.OWNER).villagers().stream().filter(r -> r.id().equals(v.getUUID())).findFirst().orElseThrow();
	}

	static String words(GameTestHelper h, Villager v) {
		var r = row(h, v);
		return dev.villageheart.core.heart.HeartTexts.EN.get(dev.villageheart.core.heart.HeartTexts.activityKey(r.activity(), r.jobId()));
	}

	private static WorkerStatus status(GameTestHelper h, Villager v) { return LumberTestSupport.status(h, v); }

	/** A Lumber Camp (one or two chopping blocks) with Storage; {@code storage} goes into the first Storage chest; day time 1000; no villager yet. */
	static GameTestSequence lumberScene(GameTestHelper h, boolean twoChops, ItemStack[] storage, BlockPos... trees) {
		return LumberTestSupport.scene(h, twoChops, true).thenExecute(() -> {
			long seed = 1;
			for (BlockPos t : trees) LumberTestSupport.growOak(h, t, seed++);
			LumberTestSupport.lightYard(h);
			add(h, LumberTestSupport.storeChest1(), storage);
			h.getLevel().setDayTime(1000);
		});
	}

	static Villager lumberjack(GameTestHelper h) { return LumberTestSupport.hire(h, LumberTestSupport.spawnSpot()); }

	static Villager lumberjack2(GameTestHelper h) { return LumberTestSupport.hire(h, BuildingTestSupport.inside(LumberTestSupport.CAMP, 2, 0, 1)); }

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	/** The owner's clarification: wood first, then stone as the miner brings it in. */
	@GameTest(environment = "villageheart-gametest:selfcraft_wood_then_stone", structure = YARD, skyAccess = true, maxTicks = 30000)
	public void woodThenStone(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		MineTestSupport.Tracker tr = new MineTestSupport.Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		boolean[] aged = {false};
		h.onEachTick(() -> {
			if (v[0] != null && !aged[0] && tr.ascents.size() == 1) {
				// the first haul is up: fast-forward the wear to the worn line (natural wear takes three more hauls) and give the sticks' planks
				aged[0] = true;
				ItemStack pick = StockpileTestSupport.container(h, chest(0)).getItem(0);
				pick.setDamageValue(pick.getMaxDamage() - 19);
				add(h, storeChest1(0), new ItemStack(Items.OAK_PLANKS, 2));
			}
		});
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.OAK_PLANKS, 5)));
		seq = seq.thenExecute(() -> tr.start());
		seq = seq.thenWaitUntil(() -> h.assertTrue(countIn(h, chest(0), Items.WOODEN_PICKAXE) == 1, "the wooden pickaxe is not made yet: " + s.log));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual("minecraft:wooden_pickaxe", s.recipeOf(v[0].getUUID()), "the first tool is wooden");
			h.assertValueEqual(0L, stored(h, Items.COBBLESTONE) + stored(h, Items.COBBLED_DEEPSLATE), "no stone yet");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(stored(h, Items.COBBLESTONE) + stored(h, Items.COBBLED_DEEPSLATE) >= 10, "the first haul has not arrived: " + s.log));
		seq = seq.thenWaitUntil(() -> h.assertTrue(countIn(h, chest(0), Items.STONE_PICKAXE) == 1, "no stone pickaxe yet: " + s.log));
		seq = seq.thenExecute(() -> {
			System.out.println("[m19a] woodThenStone: " + s.log);
			h.assertTrue(s.log.stream().anyMatch(l -> l.contains("commit minecraft:stone_pickaxe")), "the second recipe is stone: " + s.log);
			h.assertValueEqual(0, countIn(h, chest(0), Items.WOODEN_PICKAXE), "the worn wooden pickaxe left the chest");
			h.assertValueEqual(1L, stored(h, Items.WOODEN_PICKAXE), "...and waits in Storage");
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(tr.descents.size() >= 2, "no second descent: " + status(h, v[0])));
		seq.thenExecute(() -> {
			var r = CampTools.lease(h.getLevel().getServer(), v[0], ToolKind.PICKAXE);
			h.assertTrue(r instanceof CampTools.Held held && held.lease().tier() == ToolTier.STONE, "the leased pickaxe is stone: " + r);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	/** A lumberjack with two logs in Storage and no axe: wooden axe, planks and sticks left over, then it fells. */
	@GameTest(environment = "villageheart-gametest:selfcraft_lumber_logs", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void lumberjackBootstrapsFromLogs(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		GameTestSequence seq = lumberScene(h, false, items(new ItemStack(Items.OAK_LOG, 2)), new BlockPos(2, 0, 8), new BlockPos(6, 0, 14))
				.thenExecute(() -> v[0] = lumberjack(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, selfBound(2400, 20), "a wooden axe in the camp chest",
				() -> h.assertTrue(countIn(h, LumberTestSupport.chest(), Items.WOODEN_AXE) == 1, "no wooden axe yet: " + s.log));
		seq = seq.thenExecute(() -> {
			System.out.println("[m19a] lumberjackBootstrapsFromLogs: " + s.log);
			int clock = s.clock(v[0].getUUID());
			h.assertTrue(clock >= 2400 - 20 && clock <= 2400 + 100, "the wooden time: " + clock);
			h.assertValueEqual(3L, stored(h, Items.OAK_PLANKS), "leftover planks");
			h.assertValueEqual(2L, stored(h, Items.STICK), "leftover sticks");
			h.assertValueEqual(0L, stored(h, Items.OAK_LOG), "the logs were converted (and no tree has fallen yet)");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the axe is used", () -> {
			ItemStack axe = StockpileTestSupport.container(h, LumberTestSupport.chest()).getItem(0);
			h.assertTrue(axe.is(Items.WOODEN_AXE) && axe.getDamageValue() > 0, "the axe is unused");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-7 ----------------------------------------------------------------------------------

	private static SelfCraft committed(GameTestHelper h, Villager miner, int progress) {
		OrderKey key = new OrderKey(BuildingTestSupport.loc(h, mineMarker(0)), GearNeed.PICKAXE, 0);
		return new SelfCraft(miner.getUUID(), new GearCraft("minecraft:stone_pickaxe", key, progress,
				List.of(new ItemCount("minecraft:cobblestone", 3), new ItemCount("minecraft:stick", 2))), false);
	}

	private static void insertRecord(GameTestHelper h, SelfCraft craft) {
		VillageClaims.data(h.getLevel().getServer()).update(vs -> vs.withSelfCraft(craft));
		GearOrders.rebuild(h.getLevel().getServer());
	}

	/**
	 * The "progress after the evening: 4003.0" flake: {@code WalkTo} reports ARRIVED up to 0.1 block beyond the reach it was given, but
	 * the craft only works within the reach itself, so a worker standing 1.5-1.6 blocks from its job block neither walked nor worked,
	 * for ever (a push by the other miner puts it there at random). Here the worker is put at 1.55 blocks and must still craft.
	 */
	@GameTest(environment = "villageheart-gametest:selfcraft_dead_band", structure = YARD, skyAccess = true, maxTicks = 600)
	public void craftWorksFromJustOutsideTheReach(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, 0, true, SHAFT1).thenExecute(() -> {
			lightYard(h, 0);
			add(h, storeChest1(0), new ItemStack(Items.OAK_PLANKS, 5));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			insertRecord(h, committed(h, v[0], 4000));
		}).thenIdle(3);
		seq = seq.thenExecute(() -> {
			var jbl = dev.villageheart.worker.WorkerAttachments.get(v[0]).orElseThrow().jobBlock();
			BlockPos jbPos = new BlockPos(jbl.x(), jbl.y(), jbl.z());
			net.minecraft.world.phys.Vec3 jb = net.minecraft.world.phys.Vec3.atBottomCenterOf(jbPos);
			net.minecraft.world.phys.Vec3 dir = v[0].position().subtract(jb).multiply(1, 0, 1).normalize();
			net.minecraft.world.phys.Vec3 spot = jb.add(dir.scale(1.55));
			v[0].setPos(spot.x, jb.y, spot.z);
			v[0].getNavigation().stop();
			double d = dev.villageheart.worker.job.WalkTo.distance(v[0], jbPos);
			h.assertTrue(d > 1.5 && d <= 1.6, "the worker stands in the band: " + d);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 150, "the craft makes progress", () -> {
			var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID());
			h.assertTrue(st != null && st.progress >= 4020, "progress " + (st == null ? "none" : st.progress));
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** The craft stops at the end of the work day, survives a reload (the restart path) and finishes next morning. */
	@GameTest(environment = "villageheart-gametest:selfcraft_overnight", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void craftPausesOvernightAndSurvivesReload(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		Sampler s = new Sampler();
		double[] frozen = new double[1];
		long[] t0 = new long[1];
		h.onEachTick(() -> s.sample(h));
		java.util.List<String> trace = new java.util.ArrayList<>();
		h.onEachTick(() -> {
			if (v[0] == null || h.getTick() % 25 != 0) return;
			var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID());
			trace.add("t" + h.getTick() + " day" + h.getLevel().getDayTime() % 24000 + " " + v[0].blockPosition().toShortString() + " entityTicking=" + h.getLevel().isPositionEntityTicking(v[0].blockPosition()) + " away=" + dev.villageheart.worker.job.AwayTrips.isAway(v[0])
					+ (st == null ? " nostate" : " dec=" + st.decision + " prog=" + st.progress + " owning=" + st.owning));
		});
		GameTestSequence seq = scene(h, 0, true, SHAFT1).thenExecute(() -> {
			lightYard(h, 0);
			add(h, storeChest1(0), new ItemStack(Items.OAK_PLANKS, 5));
			v[0] = hire(h, 0);
			v[1] = hireAt(h, BuildingTestSupport.inside(MINE, 2, 0, 1));
			insertRecord(h, committed(h, v[0], 4000));
			h.getLevel().setDayTime(10_400);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() % 24000 >= 11_050, "waiting for the evening"));
		seq = seq.thenExecute(() -> {
			var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID());
			frozen[0] = st.progress;
			System.out.println("[m19a] craftPausesOvernight: progress at 11050 = " + frozen[0]);
			h.assertTrue(frozen[0] >= 4100 && frozen[0] <= 4800, "progress after the evening: " + frozen[0] + " " + trace + " " + s.log);
		});
		seq = seq.thenIdle(200);
		seq = seq.thenExecute(() -> {
			var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID());
			h.assertTrue(Math.abs(st.progress - frozen[0]) < 1e-6, "the clock is frozen at night: " + frozen[0] + " -> " + st.progress);
			// the restart path: transient state gone, the entity reloaded, the orders rebuilt from the saved records
			SelfCraftRuntime.resetForTests();
			v[0] = SelfCraftTestSupport.reload(h, v[0], () -> { });
			GearOrders.rebuild(h.getLevel().getServer());
			h.assertTrue(record(h, v[0].getUUID()).isPresent(), "the record survived");
			h.getLevel().setDayTime(980);
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getTick() - t0[0] >= 80 && GearOrders.size() == 2, "orders: " + GearOrders.size()));
		seq = seq.thenWaitUntil(() -> h.assertTrue(countIn(h, chest(0), Items.STONE_PICKAXE) == 1, "the first craft is not finished: " + s.log));
		seq.thenExecute(() -> {
			System.out.println("[m19a] craftPausesOvernight: finished after " + (h.getTick() - t0[0]) + " ticks of the new day; " + s.log);
			h.assertTrue(h.getTick() - t0[0] <= (4800 - frozen[0]) + 400, "at most 200 ticks lost: " + (h.getTick() - t0[0]));
			h.assertTrue(GearOrders.size() <= 2, "never a third order");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	/** A fired worker's committed items go back to Storage once, and a late stop cannot bring the record back. */
	@GameTest(environment = "villageheart-gametest:selfcraft_fired", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void firedWorkerRefunds(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		long cancelledBefore = WorkStats.get("miner", "tools_cancelled");
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.DIRT)));
		seq = seq.thenExecute(() -> {
			insertRecord(h, committed(h, v[0], 1000));
			dev.villageheart.worker.Workers.fire(h.getLevel(), v[0]);
			dev.villageheart.worker.gear.SelfCraftJob.stop(h.getLevel(), v[0]); // a late stop right after the fire
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 260, "the record is refunded", () -> h.assertTrue(records(h).isEmpty(), "the record is still there"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE), "cobblestone refunded");
			h.assertValueEqual(2L, stored(h, Items.STICK), "sticks refunded");
			h.assertValueEqual(1L, WorkStats.get("miner", "tools_cancelled") - cancelledBefore, "counter");
		});
		seq = seq.thenIdle(400);
		seq.thenExecute(() -> {
			h.assertTrue(records(h).isEmpty(), "no record came back");
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE), "refunded once only");
			h.assertValueEqual(2L, stored(h, Items.STICK), "refunded once only");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** M19 round 2: a committed id that is not a registered item used to hang the refund loop (AIR stacks count 0). It is dropped with a warning, the rest is refunded and the record goes. */
	@GameTest(environment = "villageheart-gametest:selfcraft_unknown_id", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void refundSkipsUnknownItemId(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.DIRT)));
		seq = seq.thenExecute(() -> {
			insertRecord(h, withUnknown(committed(h, v[0], 1000)));
			dev.villageheart.worker.Workers.fire(h.getLevel(), v[0]);
			dev.villageheart.worker.gear.SelfCraftJob.stop(h.getLevel(), v[0]); // a late stop right after the fire
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 260, "the record is refunded", () -> h.assertTrue(records(h).isEmpty(), "the record is still there"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE), "cobblestone refunded");
			h.assertValueEqual(2L, stored(h, Items.STICK), "sticks refunded");
		});
		seq = seq.thenIdle(400);
		seq.thenExecute(() -> {
			h.assertTrue(records(h).isEmpty(), "no record came back");
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE), "refunded once only");
			h.assertValueEqual(2L, stored(h, Items.STICK), "refunded once only");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	private static SelfCraft withUnknown(SelfCraft sc) {
		List<ItemCount> items = new java.util.ArrayList<>(sc.craft().committed());
		items.add(0, new ItemCount("minecraft:no_such_item", 3));
		return new SelfCraft(sc.worker(), new GearCraft(sc.craft().recipeId(), sc.craft().key(), sc.craft().progress(), items), false);
	}


	/** The same when the worker dies. */
	@GameTest(environment = "villageheart-gametest:selfcraft_dead", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void deadWorkerRefunds(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.DIRT)));
		seq = seq.thenExecute(() -> {
			insertRecord(h, committed(h, v[0], 1000));
			v[0].kill(h.getLevel());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the record is refunded", () -> h.assertTrue(records(h).isEmpty(), "the record is still there"));
		seq = seq.thenIdle(300);
		seq.thenExecute(() -> {
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE), "cobblestone refunded once");
			h.assertValueEqual(2L, stored(h, Items.STICK), "sticks refunded once");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	/** Two lumberjacks, one fresh stone axe: only the second-hired makes an axe, the first keeps felling. */
	@GameTest(environment = "villageheart-gametest:selfcraft_two_lumberjacks", structure = YARD, skyAccess = true, maxTicks = 6500)
	public void twoLumberjacksOneAxe(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		Sampler s = new Sampler();
		boolean[] firstMaking = {false};
		h.onEachTick(() -> {
			s.sample(h);
			if (v[0] != null && h.getTick() % 20 == 0) {
				var st = safeStatus(h, v[0]);
				if (st == WorkerStatus.MAKING_STONE_TOOL || st == WorkerStatus.MAKING_WOODEN_TOOL) firstMaking[0] = true;
			}
		});
		GameTestSequence seq = lumberScene(h, true, items(new ItemStack(Items.OAK_PLANKS, 5), new ItemStack(Items.COBBLESTONE, 3)),
				new BlockPos(2, 0, 8), new BlockPos(6, 0, 14), new BlockPos(12, 0, 16)).thenExecute(() -> {
					add(h, LumberTestSupport.chest(), new ItemStack(Items.STONE_AXE));
					v[0] = lumberjack(h);
					v[1] = lumberjack2(h);
				});
		seq = seq.thenWaitUntil(() -> h.assertTrue(countIn(h, LumberTestSupport.chest(), Items.STONE_AXE) == 2, "the second axe is not made yet: " + s.log));
		seq.thenExecute(() -> {
			System.out.println("[m19a] twoLumberjacksOneAxe: " + s.log);
			h.assertTrue(s.committed(v[1].getUUID()) && !s.committed(v[0].getUUID()), "only the second-hired crafted: " + s.log);
			h.assertFalse(firstMaking[0], "the first-hired never made a tool");
			var r1 = CampTools.lease(h.getLevel().getServer(), v[0], ToolKind.AXE);
			var r2 = CampTools.lease(h.getLevel().getServer(), v[1], ToolKind.AXE);
			h.assertTrue(r1 instanceof CampTools.Held && r2 instanceof CampTools.Held, "both lease an axe now");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Two lumberjacks, no axe: both craft; the tool the second-hired finishes first goes to it, not to the one still crafting. */
	@GameTest(environment = "villageheart-gametest:selfcraft_two_crafting", structure = YARD, skyAccess = true, maxTicks = 3500)
	public void aFinishedAxeSkipsTheWorkerStillCrafting(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		GameTestSequence seq = lumberScene(h, true, items(new ItemStack(Items.OAK_PLANKS, 10)), new BlockPos(2, 0, 8), new BlockPos(6, 0, 14))
				.thenExecute(() -> {
					v[0] = lumberjack(h);
					v[1] = lumberjack2(h);
				});
		seq = seq.thenWaitUntil(() -> h.assertTrue(s.committed(v[0].getUUID()) && s.committed(v[1].getUUID()), "both have not committed: " + s.log));
		seq = seq.thenWaitUntil(() -> {
			var st = SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[1].getUUID());
			h.assertTrue(st != null && st.progress >= 1, "the second craft has not started working");
		});
		seq = seq.thenExecute(() -> SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[1].getUUID()).progress = 2395);
		seq = seq.thenWaitUntil(() -> h.assertTrue(countIn(h, LumberTestSupport.chest(), Items.WOODEN_AXE) == 1, "the second axe is not finished: " + s.log));
		seq.thenExecute(() -> {
			h.assertTrue(s.finished(v[1].getUUID()) && !s.finished(v[0].getUUID()), "the first is still crafting: " + s.log);
			var r1 = CampTools.lease(h.getLevel().getServer(), v[0], ToolKind.AXE);
			var r2 = CampTools.lease(h.getLevel().getServer(), v[1], ToolKind.AXE);
			h.assertTrue(r2 instanceof CampTools.Held, "the finished axe is the second-hired's: " + r2);
			h.assertTrue(!(r1 instanceof CampTools.Held), "the one still crafting is skipped: " + r1);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-10 ---------------------------------------------------------------------------------

	/** A usable tool in Storage is fetched into the camp chest, nothing is crafted. */
	@GameTest(environment = "villageheart-gametest:selfcraft_stored_tool", structure = YARD, skyAccess = true, maxTicks = 1500)
	public void storedToolFetchedNotCrafted(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		long fetchedBefore = WorkStats.get("lumberjack", "tools_fetched");
		h.onEachTick(() -> s.sample(h));
		ItemStack iron = new ItemStack(Items.IRON_AXE);
		iron.setDamageValue(10);
		GameTestSequence seq = lumberScene(h, false, items(iron, new ItemStack(Items.OAK_PLANKS, 5), new ItemStack(Items.COBBLESTONE, 3)))
				.thenExecute(() -> v[0] = lumberjack(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the iron axe reaches the camp chest", () -> h.assertTrue(countIn(h, LumberTestSupport.chest(), Items.IRON_AXE) == 1, "not yet"));
		seq.thenExecute(() -> {
			h.assertTrue(s.log.isEmpty(), "no record is ever written: " + s.log);
			h.assertValueEqual(5L, stored(h, Items.OAK_PLANKS), "planks untouched");
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE), "cobblestone untouched");
			h.assertValueEqual(0L, stored(h, Items.IRON_AXE), "the axe left Storage");
			h.assertValueEqual(1L, WorkStats.get("lumberjack", "tools_fetched") - fetchedBefore, "counter");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** An enchanted tool in Storage is the player's: it stays, and a stone axe is made instead. */
	@GameTest(environment = "villageheart-gametest:selfcraft_enchanted", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void enchantedStoredToolIsLeftAlone(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		ItemStack iron = new ItemStack(Items.IRON_AXE);
		iron.enchant(h.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
				.getOrThrow(net.minecraft.world.item.enchantment.Enchantments.UNBREAKING), 1);
		GameTestSequence seq = lumberScene(h, false, items(iron, new ItemStack(Items.OAK_PLANKS, 5), new ItemStack(Items.COBBLESTONE, 3)))
				.thenExecute(() -> v[0] = lumberjack(h));
		seq = seq.thenWaitUntil(() -> h.assertTrue(s.committed(v[0].getUUID()), "no craft yet"));
		seq.thenExecute(() -> {
			h.assertValueEqual("minecraft:stone_axe", s.recipeOf(v[0].getUUID()), "a stone axe is made");
			h.assertValueEqual(1L, stored(h, Items.IRON_AXE), "the enchanted axe stays in Storage");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-11 ---------------------------------------------------------------------------------

	/** A full camp chest shows its own words; a full chest never makes the worker carry a Storage tool; a freed slot starts the craft. */
	@GameTest(environment = "villageheart-gametest:selfcraft_full_chest", structure = YARD, skyAccess = true, maxTicks = 3000)
	public void fullChestBlocks(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		long[] t0 = new long[1];
		GameTestSequence seq = lumberScene(h, false, items(new ItemStack(Items.OAK_PLANKS, 5)), new BlockPos(2, 0, 8)).thenExecute(() -> {
			fillWithDirt(h, LumberTestSupport.chest());
			add(h, LumberTestSupport.storeChest2(), new ItemStack(Items.IRON_AXE));
			v[0] = lumberjack(h);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 120, "Needs axe (no room)", () -> h.assertValueEqual("Needs axe (no room)", words(h, v[0]), "words"));
		seq = seq.thenExecute(() -> t0[0] = h.getTick());
		seq = seq.thenWaitUntil(() -> {
			h.assertValueEqual("Needs axe (no room)", words(h, v[0]), "the job's Needs axe never wins");
			h.assertTrue(h.getTick() - t0[0] >= 300, "sampling");
		});
		seq = seq.thenExecute(() -> {
			h.assertTrue(s.log.isEmpty(), "no craft: " + s.log);
			h.assertValueEqual(5L, stored(h, Items.OAK_PLANKS), "materials untouched");
			h.assertValueEqual(1L, stored(h, Items.IRON_AXE), "a full chest never makes the worker carry a tool back and forth");
			StockpileTestSupport.container(h, LumberTestSupport.chest()).setItem(5, ItemStack.EMPTY);
			StockpileTestSupport.container(h, LumberTestSupport.storeChest2()).setItem(0, ItemStack.EMPTY);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 140, "the craft starts when a slot is free",
				() -> h.assertTrue(status(h, v[0]) == WorkerStatus.MAKING_WOODEN_TOOL, "not making: " + words(h, v[0])));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** Nothing to make a tool from: "Needs axe (no wood)". */
	@GameTest(environment = "villageheart-gametest:selfcraft_no_wood", structure = YARD, skyAccess = true, maxTicks = 600)
	public void noWoodSaysSo(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = lumberScene(h, false, items()).thenExecute(() -> v[0] = lumberjack(h));
		seq = BuildingTestSupport.awaitWithin(h, seq, 120, "Needs axe (no wood)", () -> h.assertValueEqual("Needs axe (no wood)", words(h, v[0]), "words"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** The chest fills up while the axe is made: the axe goes to Storage and is fetched back when a slot frees. */
	@GameTest(environment = "villageheart-gametest:selfcraft_chest_filled_midway", structure = YARD, skyAccess = true, maxTicks = 4500)
	public void chestFilledMidCraftSendsTheToolToStorage(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		GameTestSequence seq = lumberScene(h, false, items(new ItemStack(Items.OAK_PLANKS, 5)), new BlockPos(2, 0, 8)).thenExecute(() -> v[0] = lumberjack(h));
		seq = seq.thenWaitUntil(() -> h.assertTrue(s.committed(v[0].getUUID()), "no commit yet"));
		seq = seq.thenExecute(() -> fillWithDirt(h, LumberTestSupport.chest()));
		seq = seq.thenWaitUntil(() -> h.assertTrue(s.finished(v[0].getUUID()), "not finished: " + s.log));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1L, stored(h, Items.WOODEN_AXE), "the axe went to Storage");
			h.assertValueEqual(0, countIn(h, LumberTestSupport.chest(), Items.WOODEN_AXE), "...not the chest");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 140, "Needs axe (no room)", () -> h.assertValueEqual("Needs axe (no room)", words(h, v[0]), "words"));
		seq = seq.thenExecute(() -> StockpileTestSupport.container(h, LumberTestSupport.chest()).setItem(7, ItemStack.EMPTY));
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the axe is fetched into the chest", () -> h.assertTrue(countIn(h, LumberTestSupport.chest(), Items.WOODEN_AXE) == 1, "not yet"));
		seq.thenExecute(() -> {
			h.assertValueEqual(1L, s.log.stream().filter(l -> l.contains("commit")).count(), "nothing is crafted again");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-12 ---------------------------------------------------------------------------------

	/** The worn tool is used up first; no craft starts while a fresh one lies in the chest. */
	@GameTest(environment = "villageheart-gametest:selfcraft_worn_first", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void wornToolUsedFirst(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		GameTestSequence seq = lumberScene(h, false, items(new ItemStack(Items.OAK_PLANKS, 5), new ItemStack(Items.COBBLESTONE, 3)),
				new BlockPos(2, 0, 8), new BlockPos(6, 0, 14), new BlockPos(12, 0, 16)).thenExecute(() -> {
			var c = StockpileTestSupport.container(h, LumberTestSupport.chest());
			ItemStack worn = new ItemStack(Items.STONE_AXE);
			worn.setDamageValue(worn.getMaxDamage() - 4);
			c.setItem(4, worn);
			c.setItem(0, new ItemStack(Items.STONE_AXE));
			c.setChanged();
			v[0] = lumberjack(h);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(StockpileTestSupport.container(h, LumberTestSupport.chest()).getItem(4).isEmpty(), "the worn axe has not broken"));
		seq = seq.thenExecute(() -> h.assertValueEqual(0, StockpileTestSupport.container(h, LumberTestSupport.chest()).getItem(0).getDamageValue(),
				"the fresh axe was not used meanwhile"));
		seq = seq.thenWaitUntil(() -> h.assertTrue(StockpileTestSupport.container(h, LumberTestSupport.chest()).getItem(0).getDamageValue() > 0,
				"the fresh axe is used after the worn one"));
		seq.thenExecute(() -> {
			h.assertTrue(s.log.isEmpty(), "no craft started: " + s.log);
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
