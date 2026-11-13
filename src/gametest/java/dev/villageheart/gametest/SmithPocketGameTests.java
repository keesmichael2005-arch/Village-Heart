package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** The smith puts a new tool in the chest of the worker who is waiting for one. */
public class SmithPocketGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static HeartLocation chestOf(GameTestHelper h, Villager v) {
		return VillageClaims.current(h.getLevel().getServer()).orElseThrow().worker(v.getUUID()).orElseThrow().chest().orElseThrow();
	}

	private static boolean has(GameTestHelper h, Villager v, net.minecraft.world.item.Item item) {
		HeartLocation c = chestOf(h, v);
		Container box = (Container) h.getLevel().getBlockEntity(new BlockPos(c.x(), c.y(), c.z()));
		for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).is(item)) return true;
		return false;
	}

	@GameTest(environment = "villageheart-gametest:smith_pocket_delivers", structure = YARD, skyAccess = true, maxTicks = 4000)
	public void deliversToTheWorkersChest(GameTestHelper h) {
		Villager[] jack = new Villager[2];
		SmithTestSupport.scene(h).thenExecute(() -> {
			SmithTestSupport.addLumberCamp(h);
			h.setBlock(BuildingTestSupport.inside(SmithTestSupport.CAMP, 4, 0, 0),
					dev.villageheart.block.JobBlocks.BLOCKS.get(dev.villageheart.core.building.JobBlockType.CHOPPING_BLOCK));
			h.setBlock(BuildingTestSupport.inside(SmithTestSupport.CAMP, 0, 0, 2), Blocks.CHEST);
		}).thenWaitUntil(() -> BuildingTestSupport.assertStatus(h, SmithTestSupport.campMarker(), dev.villageheart.block.MarkerStatus.VALID))
				.thenExecute(() -> {
					SmithTestSupport.putStore(h, Items.OAK_PLANKS, 5);
					SmithTestSupport.setDayTime(h, 1000);
					jack[0] = SmithTestSupport.hireLumberjack(h);
					jack[1] = WorkerTestSupport.spawnVillager(h, BuildingTestSupport.inside(SmithTestSupport.CAMP, 3, 0, 1));
					HireResult hired = Workers.hire(h.getLevel(), jack[1], JobType.LUMBERJACK, Optional.empty());
					h.assertTrue(hired instanceof HireResult.Hired, "the second lumberjack was hired: " + hired);
					Container theirs = (Container) h.getLevel().getBlockEntity(new BlockPos(chestOf(h, jack[1]).x(), chestOf(h, jack[1]).y(), chestOf(h, jack[1]).z()));
					theirs.setItem(0, new ItemStack(Items.IRON_AXE));
					SmithTestSupport.hire(h);
				})
				.thenWaitUntil(() -> {
					h.assertTrue(has(h, jack[0], Items.WOODEN_AXE), "the worker who had no axe got the new one");
					h.assertFalse(has(h, jack[1], Items.WOODEN_AXE), "it did not land in the other worker's chest");
				})
				.thenExecute(() -> VillageTestSupport.done(h));
	}
}
