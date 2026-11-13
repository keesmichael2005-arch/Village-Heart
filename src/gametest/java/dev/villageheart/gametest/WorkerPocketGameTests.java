package dev.villageheart.gametest;

import static dev.villageheart.gametest.LumberTestSupport.*;
import static dev.villageheart.gametest.WorkerTestSupport.begin;
import static dev.villageheart.gametest.WorkerTestSupport.end;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.world.VillageClaims;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** The worker's own chest and the eight slots they carry. */
public class WorkerPocketGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static HeartLocation chestOf(GameTestHelper h, Villager v) {
		return VillageClaims.current(h.getLevel().getServer()).orElseThrow().worker(v.getUUID()).orElseThrow().chest().orElseThrow();
	}

	private static Container at(GameTestHelper h, HeartLocation c) {
		return (Container) h.getLevel().getBlockEntity(new BlockPos(c.x(), c.y(), c.z()));
	}

	private static int count(Container c, net.minecraft.world.item.Item item) {
		int n = 0;
		for (int i = 0; i < c.getContainerSize(); i++) if (c.getItem(i).is(item)) n += c.getItem(i).getCount();
		return n;
	}

	@GameTest(environment = "villageheart-gametest:worker_pocket_owns_nearest_chest", structure = YARD, skyAccess = true, maxTicks = 600)
	public void ownsNearestChest(GameTestHelper h) {
		begin(h);
		BlockPos near = BuildingTestSupport.inside(CAMP, 0, 0, 1);
		Villager[] v = new Villager[1];
		GameTestSequence seq = BuildingTestSupport.begin(h).thenExecute(() -> {
			buildCamp(h);
			h.setBlock(near, Blocks.CHEST);
			placeMarkers(h, false);
		}).thenWaitUntil(() -> assertValid(h, false));
		seq.thenExecute(() -> {
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			HeartLocation owned = chestOf(h, v[0]);
			BlockPos absNear = h.absolutePos(near);
			BlockPos absFar = h.absolutePos(chest());
			h.assertTrue(owned.x() == absNear.getX() && owned.y() == absNear.getY() && owned.z() == absNear.getZ(),
					"the nearer chest is the one saved: " + owned);
			h.assertFalse(owned.x() == absFar.getX() && owned.z() == absFar.getZ(), "the far chest was not taken");
			var data = v[0].getAttached(WorkerAttachments.WORKER);
			h.assertTrue(data != null && data.chest().orElseThrow().equals(owned), "the attachment has the same chest");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:worker_pocket_second_worker", structure = YARD, skyAccess = true, maxTicks = 800)
	public void secondWorkerLeavesTheChestAlone(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[2];
		GameTestSequence seq = scene(h, true, false).thenExecute(() -> {
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			v[1] = hire(h, BuildingTestSupport.inside(CAMP, 3, 0, 2));
			at(h, chestOf(h, v[0])).setItem(0, axe(ToolTier.IRON));
		});
		seq = seq.thenIdle(40);
		seq.thenExecute(() -> {
			h.assertTrue(CampTools.lease(h.getLevel().getServer(), v[0], ToolKind.AXE) instanceof CampTools.Held, "the owner still has the axe");
			h.assertValueEqual(CampTools.Missing.NONE, CampTools.lease(h.getLevel().getServer(), v[1], ToolKind.AXE), "the other worker does not take it");
			h.assertValueEqual(1, count(at(h, chestOf(h, v[0])), Items.IRON_AXE), "the axe is still in the owner's chest");
			h.assertValueEqual(0, count(at(h, chestOf(h, v[1])), Items.IRON_AXE), "and not in the other chest");
			VillageTestSupport.done(h);
			end(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:worker_pocket_evening", structure = YARD, skyAccess = true, maxTicks = 1200)
	public void eveningReturnsTheAxeThenDumps(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		GameTestSequence seq = scene(h, false, true).thenExecute(() -> {
			h.getLevel().setDayTime(200);
			v[0] = hire(h, spawnSpot());
			putInChest(h, chest(), 0, axe(ToolTier.IRON));
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 400, "morning takes the axe", () ->
				h.assertTrue(v[0].getInventory().getItem(0).is(Items.IRON_AXE), "slot 0"));
		seq = seq.thenExecute(() -> {
			for (int i = 1; i <= 3; i++) v[0].getInventory().setItem(i, new ItemStack(Items.OAK_LOG, 16));
			h.getLevel().setDayTime(11000);
		});
		seq = BuildingTestSupport.awaitWithin(h, seq, 600, "the axe is home and the logs are in Storage", () -> {
			h.assertTrue(v[0].getInventory().getItem(0).isEmpty(), "the axe left the pocket");
			h.assertValueEqual(1, count(at(h, chestOf(h, v[0])), Items.IRON_AXE), "the axe is in the chest");
			h.assertValueEqual(48L, dev.villageheart.stock.Stockpiles.totalsById(h.getLevel().getServer()).getOrDefault("minecraft:oak_log", 0L), "logs dumped");
		});
		seq.thenExecute(() -> {
			VillageTestSupport.done(h);
			end(h);
		});
	}

	@GameTest(environment = "villageheart-gametest:worker_pocket_death", structure = YARD, skyAccess = true, maxTicks = 600)
	public void deathReturnsTheAxe(GameTestHelper h) {
		begin(h);
		Villager[] v = new Villager[1];
		HeartLocation[] chest = new HeartLocation[1];
		GameTestSequence seq = scene(h, false, false).thenExecute(() -> {
			h.getLevel().setDayTime(1000);
			v[0] = hire(h, spawnSpot());
			chest[0] = chestOf(h, v[0]);
			v[0].getInventory().setItem(0, axe(ToolTier.IRON));
			v[0].getInventory().setItem(1, new ItemStack(Items.OAK_LOG, 8));
			v[0].hurt(h.getLevel().damageSources().genericKill(), 1000.0F);
		});
		seq = seq.thenIdle(10);
		seq.thenExecute(() -> {
			h.assertValueEqual(1, count(at(h, chest[0]), Items.IRON_AXE), "the axe went back to the chest");
			h.assertValueEqual(0, count(at(h, chest[0]), Items.OAK_LOG), "the logs were not put in the chest");
			int dropped = 0;
			for (ItemEntity e : h.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(v[0].blockPosition()).inflate(8), x -> x.getItem().is(Items.OAK_LOG))) {
				dropped += e.getItem().getCount();
			}
			h.assertValueEqual(8, dropped, "the logs dropped");
			VillageTestSupport.done(h);
			end(h);
		});
	}
}
