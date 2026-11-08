package dev.villageheart.gametest;

import static dev.villageheart.gametest.MineTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.building.BuildingSystem;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.ShaftScanner.ShaftDepth;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.core.mine.SurfaceReason;
import dev.villageheart.gametest.MineTestSupport.Ascent;
import dev.villageheart.gametest.MineTestSupport.Descent;
import dev.villageheart.gametest.MineTestSupport.Tracker;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.mine.MineSites;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ConversionParams;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * Layer 2: the miner (M16). Rules (docs/TESTING.md, "Miner GameTests"): own environment per test ({@code mine_<name>}), the {@code yard}
 * structure with {@code skyAccess}, {@code MineTestSupport.scene} first and {@code VillageTestSupport.done} + {@code WorkerTestSupport.end}
 * last, measured bounds. The roadmap tests log their tick counts ({@code [m16] ...}).
 */
public class MinerGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	// ---- T-L2-0 ----------------------------------------------------------------------------------

	/** The shaft's deepest point is runtime data from the survey: right after the survey it is known, after a restart it is not until the re-check. */
	@GameTest(environment = "villageheart-gametest:mine_shaft_depth", structure = YARD, skyAccess = true, maxTicks = 600)
	public void shaftDepthIsExposed(GameTestHelper h) {
		begin(h);
		GameTestSequence seq = scene(h, 0, false, SHAFT1);
		seq = seq.thenExecute(() -> {
			HeartLocation loc = BuildingTestSupport.loc(h, shaftMarker(0, SHAFT1));
			ShaftDepth d = depth(h, 0, SHAFT1).orElseThrow();
			BlockPos floor = h.absolutePos(new BlockPos(SHAFT1[0], 1, SHAFT1[1]));
			h.assertValueEqual(floor.getY(), d.deepestY(), "the deepest Y is the shaft floor");
			h.assertValueEqual(floor.getY() + 0, d.deepestCell().y(), "the deepest cell is a floor cell");
			h.assertTrue(Math.abs(d.deepestCell().x() - floor.getX()) <= 1 && Math.abs(d.deepestCell().z() - floor.getZ()) <= 1, "inside the chamber: " + d.deepestCell());
			h.assertTrue(d.deepestY() < 0, "the yard is below Y 0, so this shaft is in the DEEP band: " + d.deepestY());
			BlockPos hold = MineSites.holdSpot(h.getLevel(), h.absolutePos(shaftMarker(0, SHAFT1)), d);
			h.assertValueEqual(d.deepestY(), hold.getY(), "the hold spot is the deepest cell");
			h.assertTrue(!hold.equals(h.absolutePos(shaftMarker(0, SHAFT1))), "never the marker cell");
			// the nearest valid shaft of the Mine is this one
			var site = MineSites.shaftFor(h.getLevel().getServer(), BuildingTestSupport.loc(h, mineMarker(0))).orElseThrow();
			h.assertValueEqual(loc, site.loc(), "the Mine's shaft");
			BuildingSystem.resetRuntimeForTests();
			h.assertTrue(Buildings.shaftDepth(h.getLevel().getServer(), loc).isEmpty(), "unknown right after a restart");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(3, 1) + 40, "the depth is surveyed again",
				() -> h.assertTrue(depth(h, 0, SHAFT1).isPresent(), "the depth is known again"));
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-3 ----------------------------------------------------------------------------------

	/** The miner walks to the shaft, vanishes (hidden, frozen, untouchable), comes up after 2 in-game hours with the DEEP-band haul, and hands it in. */
	@GameTest(environment = "villageheart-gametest:mine_vanishes", structure = YARD, skyAccess = true, maxTicks = 7000)
	public void minerVanishesAndReturns(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		net.minecraft.world.entity.monster.zombie.Zombie[] z = new net.minecraft.world.entity.monster.zombie.Zombie[1];
		int[] targeted = {0};
		h.onEachTick(() -> {
			if (z[0] != null && v[0] != null && z[0].getTarget() == v[0]) targeted[0]++;
		});
		long[] t0 = new long[1];
		BlockPos[] hold = new BlockPos[1];
		GameTestSequence seq = scene(h, 0, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(0), 0, pickaxe(ToolTier.STONE));
			lightYard(h, 0);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			tr.start();
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1200, "the miner walks to the shaft and goes down", () -> h.assertTrue(tr.away(), "away: "
				+ v[0].position() + " " + dev.villageheart.worker.mine.MinerJob.decisionOf(v[0].getUUID()) + " " + status(h, v[0])));
		seq = seq.thenExecute(() -> {
			System.out.println("[m16] minerVanishesAndReturns: went down " + (h.getTick() - t0[0]) + " ticks after hiring");
			ServerLevel level = h.getLevel();
			Villager m = v[0];
			ShaftDepth d = depth(h, 0, SHAFT1).orElseThrow();
			h.assertTrue(dev.villageheart.core.mine.DepthBand.of(d.deepestY()) == dev.villageheart.core.mine.DepthBand.DEEP, "the yard shaft is in the DEEP band: " + d.deepestY());
			hold[0] = MineSites.holdSpot(level, h.absolutePos(shaftMarker(0, SHAFT1)), d);
			h.assertTrue(m.isInvisible() && m.isInvulnerable() && m.isSilent() && m.isNoAi(), "hidden: invisible, invulnerable, silent, NoAI");
			h.assertTrue(m.position().distanceTo(Vec3.atBottomCenterOf(hold[0])) < 0.5, "at the hold spot " + m.position() + " vs " + hold[0]);
			h.assertFalse(m.getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET) || m.getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET)
					|| m.getBrain().hasMemoryValue(MemoryModuleType.PATH), "no stale walk, look or path memory");
			// a player cannot trade with it or hit it
			var player = VillageTestSupport.player(h, "visitor", net.minecraft.world.level.GameType.SURVIVAL);
			var use = net.fabricmc.fabric.api.event.player.UseEntityCallback.EVENT.invoker().interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, m, null);
			h.assertValueEqual(net.minecraft.world.InteractionResult.FAIL, use, "using a hidden miner fails");
			var hit = net.fabricmc.fabric.api.event.player.AttackEntityCallback.EVENT.invoker().interact(player, level, net.minecraft.world.InteractionHand.MAIN_HAND, m, null);
			h.assertValueEqual(net.minecraft.world.InteractionResult.FAIL, hit, "hitting a hidden miner fails");
			// a zombie next to it never targets it
			net.minecraft.world.entity.monster.zombie.Zombie zb = net.minecraft.world.entity.EntityType.ZOMBIE.create(level, net.minecraft.world.entity.EntitySpawnReason.COMMAND);
			zb.setPos(hold[0].getX() + 0.5, hold[0].getY(), hold[0].getZ() + 0.8);
			zb.setPersistenceRequired();
			level.addFreshEntity(zb);
			z[0] = zb;
		});
		seq = seq.thenIdle(200);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(0, targeted[0], "ticks the zombie spent targeting the hidden miner");
			h.assertTrue(v[0].getHealth() >= v[0].getMaxHealth(), "unhurt");
			z[0].discard();
			z[0] = null;
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2300, "the miner comes up with a haul", () -> h.assertTrue(!tr.ascents.isEmpty(), "still underground"));
		seq = seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(0);
			Descent d0 = tr.descents.get(0);
			long underground = a.record().gameTime() - d0.since();
			System.out.println("[m16] minerVanishesAndReturns: " + underground + " underground ticks, haul " + a.inventory() + ", came up at " + a.pos() + " (down at tick " + d0.tick() + ", up at " + a.tick() + ")");
			h.assertTrue(underground >= 1975 && underground <= 2025, "2 in-game hours (2000 +- 25 ticks) underground, got " + underground);
			h.assertValueEqual(SurfaceReason.HAUL, a.record().reason(), "reason");
			h.assertFalse(a.invisible() || a.noAi() || a.invulnerable(), "the flags are back when it comes up");
			Vec3 want = Vec3.atBottomCenterOf(d0.surface());
			h.assertTrue(a.record().pos().distanceTo(want) < 0.01, "it came up at the surface spot " + a.record().pos() + " vs " + want);
			h.assertValueEqual(predicted(h.getLevel(), v[0].getUUID(), dev.villageheart.core.mine.DepthBand.DEEP, ToolTier.STONE, 1), a.inventory(),
					"the haul of forHaul(seed, uuid, 0) in the DEEP band");
			h.assertTrue(a.inventory().containsKey("minecraft:cobbled_deepslate"), "cobbled deepslate, not cobblestone");
			h.assertValueEqual(10, chestSlot(h, chest(0), 0).getDamageValue(), "the pickaxe wore by one haul");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1000, "the haul reaches Storage", () -> {
			for (var e : tr.ascents.get(0).inventory().entrySet()) {
				var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(e.getKey()));
				h.assertValueEqual((long) e.getValue(), count(h, item), "Storage " + e.getKey());
			}
		});
		seq = seq.thenExecute(() -> System.out.println("[m16] minerVanishesAndReturns: delivered at tick " + (h.getTick() - t0[0])));
		seq = BuildingTestSupport.awaitWithin(h, seq, 1500, "it goes down again", () -> h.assertTrue(tr.descents.size() >= 2, "second descent"));
		seq.thenExecute(() -> {
			System.out.println("[m16] minerVanishesAndReturns: second descent at tick " + (tr.descents.get(1).tick() - t0[0]));
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-1 ----------------------------------------------------------------------------------

	/** Roadmap: a stone pickaxe and a valid shaft give at least 40 cobblestone in Storage within one day. High scene (HIGH band). */
	@GameTest(environment = "villageheart-gametest:mine_forty_cobble", structure = YARD, skyAccess = true, maxTicks = 12600)
	public void minerHaulsFortyCobblestone(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		long[] t0 = new long[1], hauls0 = new long[1];
		GameTestSequence seq = scene(h, dy, false, SHAFT1).thenExecute(() -> {
			ShaftDepth d = depth(h, dy, SHAFT1).orElseThrow();
			h.assertValueEqual(52, d.deepestY(), "the high shaft's floor is at absolute Y 52");
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.STONE));
			lightYard(h, dy);
			h.getLevel().setDayTime(1000);
			hauls0[0] = dev.villageheart.worker.job.WorkStats.get("miner", "hauls");
			v[0] = hire(h, dy);
			tr.start();
			t0[0] = h.getTick();
		});
		seq = seq.thenWaitUntil(() -> h.assertTrue(h.getLevel().getDayTime() % 24000 >= 12000, "waiting for the end of the day's deposit trip"));
		seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			long rolls = AwayTrips.state(v[0]).rolls();
			long cobble = count(h, net.minecraft.world.item.Items.COBBLESTONE);
			StringBuilder log = new StringBuilder();
			for (int i = 0; i < tr.ascents.size(); i++) log.append(" haul").append(i).append("@").append(tr.ascents.get(i).tick() - t0[0]);
			System.out.println("[m16] minerHaulsFortyCobblestone: " + rolls + " hauls, " + cobble + " cobblestone;" + log + "; descents "
					+ tr.descents.stream().map(d -> d.tick() - t0[0]).toList() + "; day time " + level.getDayTime() % 24000);
			h.assertTrue(cobble >= 40, "at least 40 cobblestone in Storage, got " + cobble);
			h.assertTrue(rolls >= 3, "at least three hauls, got " + rolls);
			var want = predicted(level, v[0].getUUID(), dev.villageheart.core.mine.DepthBand.HIGH, ToolTier.STONE, rolls);
			for (var e : want.entrySet()) {
				var item = net.minecraft.core.registries.BuiltInRegistries.ITEM.getValue(net.minecraft.resources.Identifier.parse(e.getKey()));
				h.assertValueEqual((long) e.getValue(), count(h, item), "Storage holds exactly the predicted " + e.getKey());
			}
			int hauled = want.values().stream().mapToInt(Integer::intValue).sum();
			h.assertValueEqual(Math.min(300, hauled), dev.villageheart.worker.WorkerAttachments.get(v[0]).orElseThrow().xp(), "M15: one XP per item hauled");
			h.assertFalse(want.containsKey("minecraft:cobbled_deepslate"), "no deepslate in the HIGH band");
			h.assertValueEqual(0L, count(h, net.minecraft.world.item.Items.COBBLED_DEEPSLATE), "no cobbled deepslate in Storage");
			h.assertValueEqual((int) (10 * rolls), chestSlot(h, chest(dy), 0).getDamageValue(), "10 durability per haul");
			h.assertValueEqual(rolls, dev.villageheart.worker.job.WorkStats.get("miner", "hauls") - hauls0[0], "work.miner.hauls");
			h.assertFalse(AwayTrips.isAway(v[0]), "up again at the end of the day");
			var items = level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new net.minecraft.world.phys.AABB(h.absolutePos(new BlockPos(16, dy, 16))).inflate(40.0));
			h.assertTrue(items.isEmpty(), "no item entity lying around: " + items.stream().map(e -> e.getItem() + " at " + e.blockPosition() + " age " + e.getAge()).toList());
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- shared bits -------------------------------------------------------------------------------

	private static dev.villageheart.core.heart.VillagerRow row(GameTestHelper h, Villager v) {
		return HeartTestSupport.snapshot(h, dev.villageheart.core.Rank.OWNER).villagers().stream().filter(r -> r.id().equals(v.getUUID())).findFirst().orElseThrow();
	}

	private static GameTestSequence awaitAway(GameTestHelper h, GameTestSequence seq, Tracker tr, Villager[] v, int bound, String what) {
		return BuildingTestSupport.awaitWithin(h, seq, bound, what, () -> h.assertTrue(tr.away(), what + ": at " + v[0].position() + " "
				+ dev.villageheart.worker.mine.MinerJob.decisionOf(v[0].getUUID()) + " " + status(h, v[0])));
	}

	private static net.minecraft.network.chat.contents.TranslatableContents contents(Component c) { return (net.minecraft.network.chat.contents.TranslatableContents) c.getContents(); }

	// ---- T-L2-2 ----------------------------------------------------------------------------------

	/** Roadmap: rails removed from the shaft: the miner comes up, stops, and the reasons say why; with the rails back it goes down again. */
	@GameTest(environment = "villageheart-gametest:mine_shaft_invalid", structure = YARD, skyAccess = true, maxTicks = 9000)
	public void minerStopsWhenShaftInvalid(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		long[] t0 = new long[1], cobble = new long[1];
		GameTestSequence seq = scene(h, dy, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.STONE));
			lightYard(h, dy);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, dy);
			tr.start();
		});
		seq = awaitAway(h, seq, tr, v, 900, "the miner goes down");
		seq = seq.thenExecute(() -> {
			cobble[0] = count(h, net.minecraft.world.item.Items.COBBLESTONE);
			h.setBlock(rail(dy, SHAFT1, 0), net.minecraft.world.level.block.Blocks.AIR);
			h.setBlock(rail(dy, SHAFT1, 1), net.minecraft.world.level.block.Blocks.AIR);
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(3, 1) + 40, "the miner comes up", () -> h.assertTrue(!tr.ascents.isEmpty(), "still underground"));
		seq = seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(0);
			System.out.println("[m16] minerStopsWhenShaftInvalid: up " + (h.getTick() - t0[0]) + " ticks after the rails were removed");
			h.assertValueEqual(SurfaceReason.SITE_INVALID, a.record().reason(), "the reason");
			h.assertFalse(a.invisible() || a.noAi() || a.invulnerable(), "visible, AI on");
			Vec3 want = Vec3.atBottomCenterOf(tr.descents.get(0).surface());
			h.assertTrue(a.record().pos().distanceTo(want) < 0.01, "at the saved surface spot " + a.record().pos() + " vs " + want);
			h.assertFalse(AwayTrips.isAway(v[0]), "no trip");
			h.assertValueEqual(0.0, AwayTrips.state(v[0]).progress().units(), "the progress belongs to the old shaft: reset");
			h.assertTrue(a.inventory().isEmpty(), "no haul: " + a.inventory());
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(3, 1), "the Mine is re-checked", () -> {
			BuildingTestSupport.assertStatus(h, mineMarker(dy), dev.villageheart.block.MarkerStatus.INVALID);
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.WORKPLACE_INVALID, status(h, v[0]), "the worker's status");
		});
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(java.util.List.of(dev.villageheart.core.building.Reason.needsLinkedSite(dev.villageheart.core.building.BuildingType.MINESHAFT)),
					BuildingTestSupport.reasons(h, mineMarker(dy)), "the Mine's reasons");
			h.assertValueEqual(java.util.List.of(dev.villageheart.core.building.Reason.needsMore(2, dev.villageheart.core.building.Thing.RAIL)),
					BuildingTestSupport.reasons(h, shaftMarker(dy, SHAFT1)), "the shaft's reasons");
			var text = dev.villageheart.worker.job.WorkerStatusText.of(dev.villageheart.core.worker.WorkerStatus.WORKPLACE_INVALID, dev.villageheart.core.worker.JobType.MINER);
			h.assertValueEqual("gui.villageheart.heart.activity.workplace_invalid", contents(text).getKey(), "text key");
			h.assertValueEqual("Workplace invalid", contents(text).getFallback(), "text");
			var snap = HeartTestSupport.snapshot(h, dev.villageheart.core.Rank.OWNER);
			h.assertTrue(row(h, v[0]).workplaceInvalid(), "the Villagers tab row says workplace invalid");
			var mineRow = snap.buildings().stream().filter(b -> b.typeId().equals("mine")).findFirst().orElseThrow();
			h.assertValueEqual(1, mineRow.reasons().size(), "the Buildings tab carries the Mine's reason");
			var shaftRow = snap.buildings().stream().filter(b -> b.typeId().equals(dev.villageheart.core.building.BuildingType.MINESHAFT.id())).findFirst().orElseThrow();
			h.assertValueEqual(dev.villageheart.core.heart.DetailKind.NONE, shaftRow.detail(), "an outdoor site row has no detail (the Heart snapshot used to throw for it)");
		});
		seq = seq.thenIdle(3000);
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(1, tr.descents.size(), "no second descent");
			h.assertValueEqual(cobble[0], count(h, net.minecraft.world.item.Items.COBBLESTONE), "Storage cobblestone unchanged");
			h.assertValueEqual(0.0, AwayTrips.state(v[0]).progress().units(), "saved progress still 0");
			h.setBlock(rail(dy, SHAFT1, 0), net.minecraft.world.level.block.Blocks.RAIL);
			h.setBlock(rail(dy, SHAFT1, 1), net.minecraft.world.level.block.Blocks.RAIL);
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(3, 2) + 700, "with the rails back the miner goes down again", () -> h.assertTrue(tr.descents.size() >= 2, "second descent"));
		seq.thenExecute(() -> {
			System.out.println("[m16] minerStopsWhenShaftInvalid: second descent " + (h.getTick() - t0[0]) + " ticks after the rails came back");
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-4 ----------------------------------------------------------------------------------

	/** No pickaxe: "Needs pickaxe" and never underground; a wooden pickaxe sends it down; taking it away brings it up without a haul. */
	@GameTest(environment = "villageheart-gametest:mine_needs_pickaxe", structure = YARD, skyAccess = true, maxTicks = 5000)
	public void minerNeedsPickaxe(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		boolean[] everInvisible = new boolean[1];
		h.onEachTick(() -> {
			if (v[0] != null && v[0].isInvisible()) everInvisible[0] = true;
		});
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, dy, false, SHAFT1).thenExecute(() -> {
			lightYard(h, dy);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, dy);
			tr.start();
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 100, "the status becomes NEEDS_TOOL", () ->
				h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL, status(h, v[0]), "status"));
		seq = seq.thenExecute(() -> {
			var text = dev.villageheart.worker.job.WorkerStatusText.of(dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL, dev.villageheart.core.worker.JobType.MINER);
			h.assertValueEqual("gui.villageheart.heart.activity.needs_tool.pickaxe", contents(text).getKey(), "text key");
			h.assertValueEqual("Needs pickaxe", contents(text).getFallback(), "text");
			h.assertValueEqual(dev.villageheart.core.heart.ActivityKind.NEEDS_TOOL, row(h, v[0]).activity(), "the Villagers tab row");
		});
		seq = seq.thenIdle(600);
		seq = seq.thenExecute(() -> {
			h.assertTrue(tr.descents.isEmpty() && !everInvisible[0], "never underground without a pickaxe");
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.WOOD));
			t0[0] = h.getTick();
		});
		seq = awaitAway(h, seq, tr, v, 700, "a wooden pickaxe sends the miner down");
		seq = seq.thenExecute(() -> System.out.println("[m16] minerNeedsPickaxe: down " + (h.getTick() - t0[0]) + " ticks after the pickaxe was put in"));
		seq = seq.thenIdle(200);
		seq = seq.thenExecute(() -> {
			h.assertTrue(AwayTrips.isAway(v[0]), "still underground");
			var c = StockpileTestSupport.container(h, chest(dy));
			c.setItem(0, net.minecraft.world.item.ItemStack.EMPTY);
			c.setChanged();
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 40, "the miner comes up when the pickaxe is gone", () -> h.assertTrue(!tr.ascents.isEmpty(), "still underground"));
		seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(0);
			h.assertValueEqual(SurfaceReason.NO_TOOL, a.record().reason(), "the reason");
			h.assertTrue(a.inventory().isEmpty(), "no haul: " + a.inventory());
			h.assertValueEqual(0L, AwayTrips.state(v[0]).rolls(), "no haul was rolled");
			h.assertTrue(a.progress() >= 160 && a.progress() <= 300, "progress is kept (wood: 1 unit a tick), got " + a.progress());
			h.assertValueEqual(dev.villageheart.core.worker.WorkerStatus.NEEDS_TOOL, status(h, v[0]), "NEEDS_TOOL again");
			System.out.println("[m16] minerNeedsPickaxe: up " + (h.getTick() - t0[0]) + " ticks after the pickaxe was taken, progress " + a.progress());
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-5 ----------------------------------------------------------------------------------

	/** The work day ends: the miner comes up at 11000 without a haul and keeps its progress for the next morning. */
	@GameTest(environment = "villageheart-gametest:mine_day_end", structure = YARD, skyAccess = true, maxTicks = 4500)
	public void minerComesUpAtDayEnd(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		double[] progress = new double[1];
		GameTestSequence seq = scene(h, dy, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.STONE));
			lightYard(h, dy);
			h.getLevel().setDayTime(9800);
			v[0] = hire(h, dy);
			tr.start();
		});
		seq = awaitAway(h, seq, tr, v, 700, "the miner goes down");
		seq = BuildingTestSupport.awaitWithin(h, seq, 1300, "it comes up at the end of the work day", () -> h.assertTrue(!tr.ascents.isEmpty(), "still underground"));
		seq = seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(0);
			Descent d = tr.descents.get(0);
			long underground = a.record().gameTime() - d.since();
			System.out.println("[m16] minerComesUpAtDayEnd: down at day time " + d.dayTime() + ", up at day time " + a.dayTime() + ", " + underground + " underground ticks, progress " + a.progress());
			h.assertTrue(d.dayTime() <= 10400, "it went down before the last descent time: " + d.dayTime());
			h.assertValueEqual(SurfaceReason.DAY_END, a.record().reason(), "the reason");
			h.assertTrue(a.dayTime() >= 11000 && a.dayTime() <= 11030, "up at 11000 + one step: " + a.dayTime());
			h.assertTrue(a.inventory().isEmpty(), "no haul");
			h.assertTrue(Math.abs(a.progress() - underground * 1.25) <= 25, "progress = underground ticks x 1.25 (+-25), got " + a.progress() + " for " + underground);
			h.assertValueEqual(0L, AwayTrips.state(v[0]).rolls(), "no haul rolled");
			progress[0] = a.progress();
			h.getLevel().setDayTime(1000); // the next morning
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1000, "the next morning it goes down again", () -> h.assertTrue(tr.descents.size() >= 2, "second descent"));
		seq = BuildingTestSupport.awaitWithin(h, seq, 1300, "the first haul", () -> h.assertTrue(tr.ascents.size() >= 2, "second ascent"));
		seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(1);
			long underground = a.record().gameTime() - tr.descents.get(1).since();
			double want = (2500 - progress[0]) / 1.25;
			System.out.println("[m16] minerComesUpAtDayEnd: the haul came after " + underground + " underground ticks, expected " + want);
			h.assertValueEqual(SurfaceReason.HAUL, a.record().reason(), "the reason");
			h.assertTrue(Math.abs(underground - want) <= 20, "(2500 - progress) / 1.25 = " + want + " +-20, got " + underground);
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-6 ----------------------------------------------------------------------------------

	/** A chunk that unloads and loads while the miner is down (save, discard, load a copy) keeps its trip and progress. */
	@GameTest(environment = "villageheart-gametest:mine_reload", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void awayMinerSurvivesReload(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		double[] before = new double[1];
		AwayState.AwayTrip[] trip = new AwayState.AwayTrip[1];
		GameTestSequence seq = scene(h, dy, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.STONE));
			lightYard(h, dy);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, dy);
			tr.start();
		});
		seq = awaitAway(h, seq, tr, v, 700, "the miner goes down");
		seq = seq.thenWaitUntil(() -> h.assertTrue(AwayTrips.state(v[0]).progress().units() >= 1000, "about 800 ticks underground"));
		seq = seq.thenExecute(() -> {
			h.assertValueEqual(dev.villageheart.core.heart.ActivityKind.AWAY_WORKING, row(h, v[0]).activity(), "the Villagers tab row before");
			trip[0] = AwayTrips.state(v[0]).trip().orElseThrow();
			before[0] = AwayTrips.state(v[0]).progress().units();
			Villager copy = LumberTestSupport.reloadEntity(h.getLevel(), v[0]);
			v[0] = copy;
			h.assertTrue(AwayTrips.isAway(copy), "the copy is still down");
			h.assertTrue(copy.isInvisible() && copy.isInvulnerable() && copy.isSilent() && copy.isNoAi(), "and still hidden");
			h.assertValueEqual(trip[0], AwayTrips.state(copy).trip().orElseThrow(), "with the same trip");
			h.assertValueEqual(before[0], AwayTrips.state(copy).progress().units(), "and the same progress");
		});
		seq = seq.thenIdle(3);
		seq = seq.thenExecute(() -> {
			h.assertTrue(AwayTrips.isAway(v[0]), "still down after the load tick");
			h.assertValueEqual(dev.villageheart.core.heart.ActivityKind.AWAY_WORKING, row(h, v[0]).activity(), "the Villagers tab row after");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1300, "it comes up on time", () -> h.assertTrue(!tr.ascents.isEmpty(), "still underground"));
		seq.thenExecute(() -> {
			Ascent a = tr.ascents.get(0);
			long underground = a.record().gameTime() - trip[0].since();
			// the reloaded copy has another entity id, so its 20-tick step phase moves by up to 19 ticks
			h.assertTrue(underground >= 1955 && underground <= 2045, "2000 +- 45 ticks underground across the reload (the step phase follows the entity id), got " + underground);
			h.assertValueEqual(predicted(h.getLevel(), v[0].getUUID(), dev.villageheart.core.mine.DepthBand.HIGH, ToolTier.STONE, 1), a.inventory(), "the predicted haul");
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-8 ----------------------------------------------------------------------------------

	/** The Villagers tab says "Away (mining)" with the miner's name while it is down, and something else once it is up. */
	@GameTest(environment = "villageheart-gametest:mine_heart_row", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void heartShowsAwayMining(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		GameTestSequence seq = scene(h, dy, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.IRON));
			lightYard(h, dy);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, dy);
			tr.start();
		});
		seq = awaitAway(h, seq, tr, v, 700, "the miner goes down");
		seq = seq.thenExecute(() -> {
			var r = row(h, v[0]);
			h.assertValueEqual(dev.villageheart.core.heart.ActivityKind.AWAY_WORKING, r.activity(), "activity");
			h.assertTrue(!r.name().isEmpty(), "it has its name: '" + r.name() + "'");
			h.assertTrue(r.loaded(), "loaded");
			h.assertValueEqual("Away (mining)", dev.villageheart.core.heart.HeartTexts.EN.get(dev.villageheart.core.heart.HeartTexts.activityKey(r.activity(), r.jobId())), "text");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1800, "the miner comes up", () -> h.assertTrue(!tr.ascents.isEmpty(), "still underground"));
		seq.thenExecute(() -> {
			var r = row(h, v[0]);
			h.assertTrue(r.activity() != dev.villageheart.core.heart.ActivityKind.AWAY_WORKING, "not away any more: " + r.activity());
			h.assertTrue(!r.name().isEmpty(), "still named");
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-9 ----------------------------------------------------------------------------------

	/** A fired miner (loaded, or unloaded and loaded later) never stays hidden. */
	@GameTest(environment = "villageheart-gametest:mine_fire", structure = YARD, skyAccess = true, maxTicks = 3500)
	public void fireWhileAway(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		GameTestSequence seq = scene(h, dy, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.STONE));
			lightYard(h, dy);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, dy);
			tr.start();
		});
		seq = awaitAway(h, seq, tr, v, 700, "the miner goes down");
		seq = seq.thenExecute(() -> {
			Vec3 want = Vec3.atBottomCenterOf(tr.descents.get(0).surface());
			h.assertTrue(dev.villageheart.worker.Workers.fire(h.getLevel(), v[0]), "fired");
			h.assertFalse(AwayTrips.isAway(v[0]), "(a) no trip");
			h.assertFalse(v[0].isInvisible() || v[0].isNoAi() || v[0].isInvulnerable() || v[0].isSilent(), "(a) visible, AI on");
			h.assertTrue(v[0].position().distanceTo(want) < 0.01, "(a) at the surface spot " + v[0].position() + " vs " + want);
			h.assertFalse(dev.villageheart.worker.WorkerAttachments.isWorker(v[0]), "(a) unemployed");
			h.assertTrue(v[0].getInventory().isEmpty(), "(a) no haul");
		});
		seq = seq.thenIdle(40);
		seq = seq.thenExecute(() -> {
			// hire the same villager again, wait until it is down, then unload it, fire it by id and load it later
			v[0] = hire2(h, v[0]);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "the re-hired miner goes down", () -> h.assertTrue(AwayTrips.isAway(v[0]), "away again: " + v[0].position()));
		seq = seq.thenExecute(() -> {
			var server = h.getLevel().getServer();
			var id = v[0].getUUID();
			Villager copy = LumberTestSupport.reloadEntity(h.getLevel(), v[0], () -> h.assertTrue(dev.villageheart.worker.Workers.fireById(server, id), "(c) fired by id while unloaded"));
			v[0] = copy;
		});
		seq = seq.thenIdle(3);
		seq.thenExecute(() -> {
			h.assertFalse(AwayTrips.isAway(v[0]), "(c) the loaded copy came up");
			h.assertFalse(v[0].isInvisible() || v[0].isNoAi() || v[0].isInvulnerable() || v[0].isSilent(), "(c) visible, AI on");
			h.assertFalse(dev.villageheart.worker.WorkerAttachments.isWorker(v[0]), "(c) unemployed");
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	private static Villager hire2(GameTestHelper h, Villager v) {
		var r = dev.villageheart.worker.Workers.hire(h.getLevel(), v, dev.villageheart.core.worker.JobType.MINER, java.util.Optional.empty());
		h.assertTrue(r instanceof dev.villageheart.worker.HireResult.Hired, "hired again: " + r);
		return v;
	}

	/** Breaking the Heart (releasing the village) brings a hidden miner up. */
	@GameTest(environment = "villageheart-gametest:mine_release", structure = YARD, skyAccess = true, maxTicks = 2000)
	public void releaseWhileAway(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		GameTestSequence seq = scene(h, dy, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.STONE));
			lightYard(h, dy);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, dy);
			tr.start();
		});
		seq = awaitAway(h, seq, tr, v, 700, "the miner goes down");
		seq = seq.thenExecute(() -> {
			BuildingTestSupport.owner().gameMode.destroyBlock(h.absolutePos(new BlockPos(3, dy + 1, 1)));
			h.assertTrue(dev.villageheart.world.VillageClaims.current(h.getLevel().getServer()).isEmpty(), "released");
			h.assertFalse(AwayTrips.isAway(v[0]), "(b) no trip");
			h.assertFalse(v[0].isInvisible() || v[0].isNoAi() || v[0].isInvulnerable() || v[0].isSilent(), "(b) visible, AI on");
			h.assertFalse(dev.villageheart.worker.WorkerAttachments.isWorker(v[0]), "(b) a vanilla villager again");
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-10 ---------------------------------------------------------------------------------

	/** Two miners share the nearest shaft; when its marker is broken both come up, lose their progress and use the other shaft at their own pace. */
	@GameTest(environment = "villageheart-gametest:mine_two_miners", structure = YARD, skyAccess = true, maxTicks = 7000)
	public void twoMinersAndBrokenMarker(GameTestHelper h) {
		begin(h);
		int dy = high(h);
		Villager[] v = new Villager[2];
		Tracker t0 = new Tracker(() -> v[0]), t1 = new Tracker(() -> v[1]);
		h.onEachTick(() -> {
			t0.sample(h);
			t1.sample(h);
		});
		GameTestSequence seq = scene(h, dy, true, SHAFT1, SHAFT2).thenExecute(() -> {
			putInChest(h, chest(dy), 0, pickaxe(ToolTier.STONE));
			putInChest(h, chest(dy), 1, pickaxe(ToolTier.IRON));
			lightYard(h, dy);
			h.getLevel().setDayTime(1000);
			v[0] = hireAt(h, spawnSpot(dy));
			v[1] = hireAt(h, spawnSpot(dy).offset(1, 0, 1));
			t0.start();
			t1.start();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "both miners go down", () -> h.assertTrue(t0.away() && t1.away(), "both away: " + t0.away() + " " + t1.away()));
		long[] brokeAt = new long[1];
		HeartLocation[] near = new HeartLocation[1];
		seq = seq.thenExecute(() -> {
			near[0] = BuildingTestSupport.loc(h, shaftMarker(dy, SHAFT1));
			h.assertValueEqual(near[0], AwayTrips.state(v[0]).trip().orElseThrow().site(), "miner 0 uses the nearer shaft");
			h.assertValueEqual(near[0], AwayTrips.state(v[1]).trip().orElseThrow().site(), "miner 1 uses the nearer shaft");
			h.setBlock(shaftMarker(dy, SHAFT1), net.minecraft.world.level.block.Blocks.AIR);
			brokeAt[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, BuildingTestSupport.bound(4, 1) + 40, "both come up", () -> h.assertTrue(!t0.ascents.isEmpty() && !t1.ascents.isEmpty(), "still down"));
		seq = seq.thenExecute(() -> {
			for (Tracker t : new Tracker[] {t0, t1}) {
				h.assertValueEqual(SurfaceReason.SITE_INVALID, t.ascents.get(0).record().reason(), "the reason");
				h.assertTrue(t.ascents.get(0).inventory().isEmpty(), "no haul");
			}
			for (Villager m : v) {
				h.assertValueEqual(0.0, AwayTrips.state(m).progress().units(), "progress reset");
				h.assertValueEqual(java.util.Optional.of(SurfaceReason.SITE_INVALID), AwayTrips.state(m).lastReason(), "saved reason");
			}
			h.assertValueEqual(java.util.List.of(), dev.villageheart.building.Buildings.valid(h.getLevel().getServer(), dev.villageheart.core.building.BuildingType.MINESHAFT).stream()
					.filter(b -> b.marker().equals(h.absolutePos(shaftMarker(dy, SHAFT1)))).toList(), "the broken shaft is gone");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 900, "both go down the other shaft", () -> h.assertTrue(t0.descents.size() >= 2 && t1.descents.size() >= 2, "second descents"));
		seq = seq.thenExecute(() -> {
			HeartLocation far = BuildingTestSupport.loc(h, shaftMarker(dy, SHAFT2));
			for (Villager m : v) h.assertValueEqual(far, AwayTrips.state(m).trip().orElseThrow().site(), "the other shaft");
			h.assertTrue(VillageClaimsMineValid(h), "the Mine stays valid");
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 2200, "both hauls", () -> h.assertTrue(t0.ascents.size() >= 2 && t1.ascents.size() >= 2, "hauls pending"));
		seq.thenExecute(() -> {
			// the first hired miner got the best pickaxe (iron, x1.5: 2500 / 30 = 84 steps), the second the stone one (x1.25: 100 steps)
			long iron = t0.ascents.get(1).record().gameTime() - t0.descents.get(1).since();
			long stone = t1.ascents.get(1).record().gameTime() - t1.descents.get(1).since();
			System.out.println("[m16] twoMinersAndBrokenMarker: iron haul after " + iron + " underground ticks, stone haul after " + stone);
			h.assertValueEqual(SurfaceReason.HAUL, t0.ascents.get(1).record().reason(), "iron: reason");
			h.assertValueEqual(SurfaceReason.HAUL, t1.ascents.get(1).record().reason(), "stone: reason");
			h.assertTrue(Math.abs(iron - 1667) <= 25, "iron: 1667 +- 25, got " + iron);
			h.assertTrue(Math.abs(stone - 2000) <= 25, "stone: 2000 +- 25, got " + stone);
			h.assertValueEqual(10, chestSlot(h, chest(dy), 1).getDamageValue(), "the iron pickaxe wore by one haul");
			h.assertValueEqual(10, chestSlot(h, chest(dy), 0).getDamageValue(), "the stone pickaxe wore by one haul");
			clearHigh(h, dy);
			VillageTestSupport.done(h);
			end(h);
		});
	}

	private static boolean VillageClaimsMineValid(GameTestHelper h) {
		return h.getBlockState(mineMarker(high(h))).getValue(dev.villageheart.block.MarkerBlock.STATUS) == dev.villageheart.block.MarkerStatus.VALID;
	}

	// ---- unreachable shaft ------------------------------------------------------------------------

	/** A staircase with a gap: "Can't reach the Mineshaft", nobody goes down; with the stairs mended it retries and goes down. Low scene (the tower). */
	@GameTest(environment = "villageheart-gametest:mine_unreachable", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void minerReportsUnreachableShaft(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		Tracker tr = new Tracker(() -> v[0]);
		h.onEachTick(() -> tr.sample(h));
		long[] t0 = new long[1];
		GameTestSequence seq = scene(h, 0, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(0), 0, pickaxe(ToolTier.STONE));
			lightYard(h, 0);
			int sx = SHAFT1[0], sz = SHAFT1[1], top = TOWER_TOP;
			for (int k = 6; k <= 7; k++) h.setBlock(new BlockPos(sx, top - k, sz - 3 - k), net.minecraft.world.level.block.Blocks.AIR);
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			tr.start();
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 1000, "the status becomes SITE_UNREACHABLE", () -> h.assertValueEqual(
				dev.villageheart.core.worker.WorkerStatus.SITE_UNREACHABLE, status(h, v[0]), "status"));
		seq = seq.thenExecute(() -> {
			System.out.println("[m16] minerReportsUnreachableShaft: SITE_UNREACHABLE after " + (h.getTick() - t0[0]) + " ticks");
			var text = dev.villageheart.worker.job.WorkerStatusText.of(dev.villageheart.core.worker.WorkerStatus.SITE_UNREACHABLE, dev.villageheart.core.worker.JobType.MINER);
			h.assertValueEqual("gui.villageheart.heart.activity.site_unreachable.miner", contents(text).getKey(), "text key");
			h.assertValueEqual("Can't reach the Mineshaft", contents(text).getFallback(), "text");
			h.assertValueEqual(dev.villageheart.core.heart.ActivityKind.SITE_UNREACHABLE, row(h, v[0]).activity(), "the Villagers tab row");
			h.assertTrue(tr.descents.isEmpty(), "nobody went down");
		});
		seq = seq.thenIdle(300);
		seq = seq.thenExecute(() -> {
			h.assertTrue(tr.descents.isEmpty(), "still nobody down while the stairs are broken");
			int sx = SHAFT1[0], sz = SHAFT1[1], top = TOWER_TOP;
			for (int k = 6; k <= 7; k++) h.setBlock(new BlockPos(sx, top - k, sz - 3 - k),
					net.minecraft.world.level.block.Blocks.STONE_STAIRS.defaultBlockState().setValue(net.minecraft.world.level.block.StairBlock.FACING, net.minecraft.core.Direction.SOUTH));
			t0[0] = h.getTick();
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, dev.villageheart.core.Balance.MINER_RETRY_TICKS + 500, "it retries and goes down", () -> h.assertTrue(tr.away(), "away"));
		seq.thenExecute(() -> {
			System.out.println("[m16] minerReportsUnreachableShaft: down " + (h.getTick() - t0[0]) + " ticks after the stairs were mended");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	// ---- T-L2-7 ----------------------------------------------------------------------------------

	/** Hiding and surfacing give the flags back as they were; orphans and conversions never keep the hidden state. */
	@GameTest(environment = "villageheart-gametest:mine_away_flags", structure = YARD, skyAccess = true, maxTicks = 900)
	public void awayTripsRestoreFlags(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		Vec3[] hold = new Vec3[1];
		GameTestSequence seq = scene(h, 0, false, SHAFT1).thenExecute(() -> {
			putInChest(h, chest(0), 0, pickaxe(ToolTier.STONE));
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, 0);
			v[0].setSilent(true);
			v[0].setCustomName(Component.literal("Bob"));
			v[0].setCustomNameVisible(true);
			v[0].getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new net.minecraft.world.entity.ai.behavior.BlockPosTracker(h.absolutePos(new BlockPos(5, 0, 5))));
			ServerLevel level = h.getLevel();
			HeartLocation loc = BuildingTestSupport.loc(h, shaftMarker(0, SHAFT1));
			BlockPos marker = h.absolutePos(shaftMarker(0, SHAFT1));
			BlockPos holdPos = MineSites.holdSpot(level, marker, depth(h, 0, SHAFT1).orElseThrow());
			AwayTrips.hide(level, v[0], "miner", loc, holdPos);
			hold[0] = new Vec3(holdPos.getX() + 0.5, holdPos.getY(), holdPos.getZ() + 0.5);
			h.assertTrue(v[0].isInvisible() && v[0].isInvulnerable() && v[0].isSilent() && v[0].isNoAi(), "hidden: invisible, invulnerable, silent, NoAI");
			h.assertFalse(v[0].isCustomNameVisible(), "the name tag is hidden");
			h.assertTrue(AwayTrips.isAway(v[0]), "has a trip");
			h.assertFalse(v[0].getBrain().hasMemoryValue(MemoryModuleType.LOOK_TARGET) || v[0].getBrain().hasMemoryValue(MemoryModuleType.WALK_TARGET)
					|| v[0].getBrain().hasMemoryValue(MemoryModuleType.PATH), "the brain's walk, look and path memories are empty");
			h.assertTrue(v[0].position().distanceTo(hold[0]) < 0.01, "at the hold spot " + v[0].position() + " vs " + hold[0]);
			// something pushes it (a piston): the next underground step puts it back
			v[0].teleportTo(hold[0].x + 1.0, hold[0].y, hold[0].z);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 25, "the underground step re-pins the villager", () -> {
			h.assertTrue(v[0].position().distanceTo(hold[0]) < 0.5, "re-pinned: " + v[0].position());
		});
		seq = seq.thenExecute(() -> {
			ServerLevel level = h.getLevel();
			Vec3 surfaceWanted = Vec3.atBottomCenterOf(BlockPos.containing(AwayTrips.state(v[0]).trip().orElseThrow().surface().x(),
					AwayTrips.state(v[0]).trip().orElseThrow().surface().y(), AwayTrips.state(v[0]).trip().orElseThrow().surface().z()));
			h.assertTrue(AwayTrips.surface(level, v[0], SurfaceReason.STOPPED_WORKING), "surfaced");
			h.assertFalse(AwayTrips.isAway(v[0]), "no trip");
			h.assertFalse(v[0].isInvisible(), "visible again");
			h.assertFalse(v[0].isInvulnerable(), "vulnerable again");
			h.assertFalse(v[0].isNoAi(), "AI on again");
			h.assertTrue(v[0].isSilent(), "it was silent before: still silent");
			h.assertTrue(v[0].isCustomNameVisible(), "the name tag is back");
			var rec = AwayTrips.lastSurface(v[0].getUUID()).orElseThrow();
			h.assertValueEqual(SurfaceReason.STOPPED_WORKING, rec.reason(), "the recorded reason");
			h.assertTrue(rec.pos().distanceTo(surfaceWanted) < 0.01, "it came up at the saved surface spot " + rec.pos() + " vs " + surfaceWanted);
			h.assertValueEqual(java.util.Optional.of(SurfaceReason.STOPPED_WORKING), AwayTrips.state(v[0]).lastReason(), "the saved reason");
			// an orphan: a vanilla villager with a trip is repaired after it loads (at the end of that tick)
			Villager o = WorkerTestSupport.spawnVillager(h, spawnSpot(0));
			BlockPos holdPos = BlockPos.containing(hold[0]);
			AwayTrips.hide(level, o, "miner", BuildingTestSupport.loc(h, shaftMarker(0, SHAFT1)), holdPos);
			h.assertTrue(o.isNoAi(), "the orphan is hidden");
			v[1] = LumberTestSupport.reloadEntity(level, o);
			h.assertTrue(AwayTrips.isAway(v[1]), "the copy still has its trip when it has just loaded");
		});
		seq = seq.thenIdle(2).thenExecute(() -> {
			h.assertFalse(AwayTrips.isAway(v[1]), "the orphan came up");
			h.assertFalse(v[1].isInvisible() || v[1].isInvulnerable() || v[1].isNoAi() || v[1].isSilent(), "with the flags it had before");
			// a conversion: a witch made from a hidden villager does not inherit NoAI, invulnerability or silence
			Villager c = WorkerTestSupport.spawnVillager(h, spawnSpot(0));
			AwayTrips.hide(h.getLevel(), c, "miner", BuildingTestSupport.loc(h, shaftMarker(0, SHAFT1)), BlockPos.containing(hold[0]));
			Witch w = c.convertTo(EntityType.WITCH, ConversionParams.single(c, false, false), witch -> { });
			h.assertTrue(w != null, "converted");
			h.assertFalse(w.isNoAi() || w.isInvulnerable() || w.isSilent() || w.isInvisible(), "the witch is an ordinary witch: " + w.isNoAi() + " " + w.isInvulnerable() + " " + w.isSilent());
			w.discard();
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
