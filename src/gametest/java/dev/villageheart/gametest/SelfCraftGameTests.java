package dev.villageheart.gametest;

import static dev.villageheart.gametest.MineTestSupport.*;
import static dev.villageheart.gametest.SelfCraftTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.gametest.SelfCraftTestSupport.Sampler;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.job.WorkStats;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Layer 2: workers make their own wooden and stone tools (M19a). Rules in {@link SelfCraftTestSupport}; the plan's test ids are in the
 * comments. The scene is the miner's (a Mine with a Mineshaft and a Storage), the Lumber Camp, the Hunting Lodge or the Farmhouse.
 */
public class SelfCraftGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	/** A Mine scene with the village's Storage holding {@code stack}s, day time 1000, a hired miner and the sampler running. */
	static GameTestSequence mineScene(GameTestHelper h, Villager[] v, Sampler s, ItemStack[] storage, ItemStack... mineChest) {
		h.onEachTick(() -> s.sample(h));
		return scene(h, 0, false, SHAFT1).thenExecute(() -> {
			lightYard(h, 0);
			add(h, storeChest1(0), storage);
			for (ItemStack st : mineChest) add(h, chest(0), st);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
		});
	}

	static ItemStack[] items(ItemStack... s) { return s; }

	// ---- T-L2-1 ----------------------------------------------------------------------------------

	/** Roadmap: a miner with no pickaxe, planks and cobblestone in Storage: a stone pickaxe in about the stone time, then it mines. */
	@GameTest(environment = "villageheart-gametest:selfcraft_miner_stone", structure = YARD, skyAccess = true, maxTicks = 8000)
	public void minerMakesStonePickaxe(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		long[] t0 = new long[1];
		long madeBefore = WorkStats.get("miner", "tools_made.stone");
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.COBBLESTONE, 3)));
		seq = seq.thenExecute(() -> t0[0] = h.getTick());
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "the status becomes Making a stone pickaxe",
				() -> h.assertValueEqual(WorkerStatus.MAKING_STONE_TOOL, status(h, v[0]), "status"));
		seq = seq.thenExecute(() -> {
			var row = HeartTestSupport.snapshot(h, dev.villageheart.core.Rank.OWNER).villagers().stream().filter(r -> r.id().equals(v[0].getUUID())).findFirst().orElseThrow();
			h.assertValueEqual(dev.villageheart.core.heart.ActivityKind.MAKING_STONE_TOOL, row.activity(), "the Villagers tab row");
			h.assertValueEqual("Making a stone pickaxe", dev.villageheart.core.heart.HeartTexts.EN.get(
					dev.villageheart.core.heart.HeartTexts.activityKey(row.activity(), row.jobId())), "the row's words");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, selfBound(4800, 20), "a stone pickaxe in the Mine chest",
				() -> h.assertTrue(countIn(h, chest(0), Items.STONE_PICKAXE) == 1, "no stone pickaxe yet: " + s.log));
		seq = seq.thenExecute(() -> {
			int clock = s.clock(v[0].getUUID());
			System.out.println("[m19a] minerMakesStonePickaxe: commit tick " + s.commitTick(v[0].getUUID()) + ", gone " + s.goneTick(v[0].getUUID())
					+ ", clock " + clock + ", made after " + (h.getTick() - t0[0]) + " ticks; " + s.log);
			h.assertTrue(clock >= 4800 - 20 && clock <= 4800 + 100, "the craft clock is the stone time: " + clock);
			h.assertValueEqual("minecraft:stone_pickaxe", s.recipeOf(v[0].getUUID()), "the recipe");
			h.assertValueEqual(0L, stored(h, Items.COBBLESTONE), "cobblestone used");
			h.assertValueEqual(0L, stored(h, Items.OAK_PLANKS), "planks used");
			h.assertValueEqual(2L, stored(h, Items.STICK), "the leftover sticks of the batch");
			h.assertValueEqual(1L, WorkStats.get("miner", "tools_made.stone") - madeBefore, "counter");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "the miner uses the pickaxe and goes down", () -> h.assertTrue(AwayTrips.isAway(v[0]), "not away"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	/** Roadmap: only planks in Storage: a wooden pickaxe in the wooden time. */
	@GameTest(environment = "villageheart-gametest:selfcraft_miner_wooden", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void minerMakesWoodenWithPlanksOnly(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.OAK_PLANKS, 5)));
		seq = BuildingTestSupport.awaitWithin(h, seq, selfBound(2400, 20), "a wooden pickaxe in the Mine chest",
				() -> h.assertTrue(countIn(h, chest(0), Items.WOODEN_PICKAXE) == 1, "no wooden pickaxe yet: " + s.log));
		seq = seq.thenExecute(() -> {
			int clock = s.clock(v[0].getUUID());
			System.out.println("[m19a] minerMakesWoodenWithPlanksOnly: " + s.log);
			h.assertTrue(clock >= 2400 - 20 && clock <= 2400 + 100, "the craft clock is the wooden time: " + clock);
			h.assertValueEqual(0L, stored(h, Items.OAK_PLANKS), "planks used");
			h.assertValueEqual(2L, stored(h, Items.STICK), "the leftover sticks");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 800, "the miner goes down", () -> h.assertTrue(AwayTrips.isAway(v[0]), "not away"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	/** Roadmap: a working pickaxe: nothing is crafted over a day, whatever Storage holds. */
	@GameTest(environment = "villageheart-gametest:selfcraft_working", structure = YARD, skyAccess = true, maxTicks = 10600)
	public void workingPickaxeNothingCrafted(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		boolean[] making = {false};
		h.onEachTick(() -> {
			if (v[0] == null || h.getTick() % 20 != 0) return;
			WorkerStatus st = safeStatus(h, v[0]);
			if (st == WorkerStatus.MAKING_STONE_TOOL || st == WorkerStatus.MAKING_WOODEN_TOOL) making[0] = true;
		});
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.OAK_PLANKS, 10), new ItemStack(Items.COBBLESTONE, 20), new ItemStack(Items.STICK, 4),
				new ItemStack(Items.IRON_INGOT, 30)), new ItemStack(Items.STONE_PICKAXE));
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() % 24000 >= 10800, "waiting for the end of the work day"));
		seq = seq.thenExecute(() -> {
			System.out.println("[m19a] workingPickaxeNothingCrafted: records " + s.log);
			h.assertTrue(s.log.isEmpty(), "no craft record ever: " + s.log);
			h.assertFalse(making[0], "never Making a tool");
			h.assertValueEqual(1, countIn(h, chest(0), Items.STONE_PICKAXE), "exactly one pickaxe in the chest");
			h.assertValueEqual(0, countIn(h, chest(0), Items.WOODEN_PICKAXE) + countIn(h, chest(0), Items.IRON_PICKAXE), "no other pickaxe");
			h.assertValueEqual(10L, stored(h, Items.OAK_PLANKS), "planks untouched");
			h.assertValueEqual(4L, stored(h, Items.STICK), "sticks untouched");
			h.assertValueEqual(30L, stored(h, Items.IRON_INGOT), "iron untouched");
			h.assertTrue(stored(h, Items.COBBLESTONE) + stored(h, Items.COBBLED_DEEPSLATE) >= 20, "the stone only grows (hauls)");
			ItemStack pick = StockpileTestSupport.container(h, chest(0)).getItem(0);
			h.assertTrue(pick.is(Items.STONE_PICKAXE) && pick.getDamageValue() > 0 && pick.getDamageValue() % 10 == 0, "worn by whole hauls: " + pick.getDamageValue());
			h.assertTrue(pick.getMaxDamage() - pick.getDamageValue() >= 20, "still above the worn line");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	/** Roadmap: the player puts a tool in mid-craft: the craft is cancelled and every item comes back. */
	@GameTest(environment = "villageheart-gametest:selfcraft_player_tool", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void playerToolCancelsAndRefunds(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		boolean[] put = {false};
		long cancelledBefore = WorkStats.get("miner", "tools_cancelled");
		GameTestSequence seq = mineScene(h, v, s, items(new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.COBBLESTONE, 3)));
		seq = seq.thenWaitUntil(() -> {
			var st = dev.villageheart.worker.gear.SelfCraftRuntime.get(h.getLevel().getServer()).peek(v[0].getUUID());
			h.assertTrue(st != null && st.progress >= 600, "the craft has not reached 600 yet");
		});
		long[] t0 = new long[1];
		seq = seq.thenExecute(() -> {
			t0[0] = h.getTick();
			add(h, chest(0), pickaxe(ToolTier.IRON));
			put[0] = true;
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 240, "the craft is cancelled", () -> h.assertTrue(records(h).isEmpty(), "record still there"));
		seq = seq.thenExecute(() -> {
			System.out.println("[m19a] playerToolCancelsAndRefunds: cancelled after " + (h.getTick() - t0[0]) + " ticks; " + s.log);
			h.assertValueEqual(2L, stored(h, Items.OAK_PLANKS), "planks back");
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE), "cobblestone back");
			h.assertValueEqual(0, countIn(h, chest(0), Items.STONE_PICKAXE), "no stone pickaxe in the chest");
			h.assertValueEqual(0L, stored(h, Items.STONE_PICKAXE), "no stone pickaxe in Storage");
			h.assertValueEqual(1L, WorkStats.get("miner", "tools_cancelled") - cancelledBefore, "counter");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 700, "the miner uses the iron pickaxe", () -> h.assertTrue(AwayTrips.isAway(v[0]), "not away"));
		seq = seq.thenIdle(400);
		seq.thenExecute(() -> {
			h.assertValueEqual(2L, stored(h, Items.OAK_PLANKS), "still exactly the start contents (no double refund)");
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE) + 0L, "cobblestone");
			h.assertTrue(put[0], "the tool was put in");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	/** The same before the commit: the tool appears on the tick the status first shows (the worker is still walking to Storage). */
	@GameTest(environment = "villageheart-gametest:selfcraft_player_tool_early", structure = YARD, skyAccess = true, maxTicks = 2500)
	public void playerToolBeforeCommitReleasesTheReservation(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Sampler s = new Sampler();
		h.onEachTick(() -> s.sample(h));
		GameTestSequence seq = scene(h, 0, false, SHAFT1).thenExecute(() -> {
			lightYard(h, 0);
			// the Storage is as far from the Mine as the yard allows: the walk takes a while
			add(h, storeChest1(0), new ItemStack(Items.OAK_PLANKS, 2), new ItemStack(Items.COBBLESTONE, 3));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(status(h, v[0]) == WorkerStatus.MAKING_STONE_TOOL, "no status yet"));
		seq = seq.thenExecute(() -> {
			h.assertTrue(records(h).isEmpty(), "no record before the commit");
			h.assertTrue(reservedBy(h, v[0].getUUID(), Items.COBBLESTONE) > 0, "the cobblestone is reserved");
			add(h, chest(0), pickaxe(ToolTier.IRON));
		});
		seq = seq.thenIdle(60);
		seq = seq.thenExecute(() -> {
			h.assertTrue(records(h).isEmpty() && !s.committed(v[0].getUUID()), "no record is ever written: " + s.log);
			h.assertValueEqual(0, reservedBy(h, v[0].getUUID(), Items.COBBLESTONE), "the reservation is gone");
			h.assertValueEqual(3L, stored(h, Items.COBBLESTONE), "Storage unchanged");
			h.assertValueEqual(2L, stored(h, Items.OAK_PLANKS), "Storage unchanged");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
