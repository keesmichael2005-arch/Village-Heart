package dev.villageheart.gametest;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/** A guard hands the sword and armour to their own chest when the shift ends. */
public class GuardPocketGameTests {
	private static final String YARD = BuildingTestSupport.YARD;

	private static HeartLocation chestOf(GameTestHelper h, Villager v) {
		return VillageClaims.current(h.getLevel().getServer()).orElseThrow().worker(v.getUUID()).orElseThrow().chest().orElseThrow();
	}

	private static int count(GameTestHelper h, HeartLocation c, net.minecraft.world.item.Item item) {
		Container box = (Container) h.getLevel().getBlockEntity(new BlockPos(c.x(), c.y(), c.z()));
		int n = 0;
		for (int i = 0; i < box.getContainerSize(); i++) if (box.getItem(i).is(item)) n++;
		return n;
	}

	@GameTest(environment = "villageheart-gametest:guard_pocket_hands_gear", structure = YARD, skyAccess = true, maxTicks = 900)
	public void handsGearToOwnChest(GameTestHelper h) {
		WorkerTestSupport.begin(h);
		Villager[] g = new Villager[2];
		GuardTestSupport.scene(h, 2).thenExecute(() -> h.setBlock(BuildingTestSupport.inside(GuardTestSupport.BARRACKS, 0, 0, 1), Blocks.CHEST))
				.thenIdle(40)
				.thenExecute(() -> {
					GuardTestSupport.setTime(h, 6000);
					g[0] = GuardTestSupport.hireGuard(h, GuardTestSupport.spawnSpot(), Optional.empty());
					g[1] = GuardTestSupport.hireGuard(h, BuildingTestSupport.inside(GuardTestSupport.BARRACKS, 1, 0, 1), Optional.empty());
					g[0].setItemSlot(EquipmentSlot.MAINHAND, new net.minecraft.world.item.ItemStack(Items.IRON_SWORD));
					g[0].setItemSlot(EquipmentSlot.HEAD, new net.minecraft.world.item.ItemStack(Items.IRON_HELMET));
					g[0].setItemSlot(EquipmentSlot.CHEST, new net.minecraft.world.item.ItemStack(Items.IRON_CHESTPLATE));
					// Two guards both take the night. Dawn is when a night guard hands gear in.
					GuardTestSupport.setTime(h, 200);
				})
				.thenWaitUntil(() -> {
					h.assertTrue(g[0].getItemBySlot(EquipmentSlot.MAINHAND).isEmpty(), "the sword was handed in");
					h.assertValueEqual(1, count(h, chestOf(h, g[0]), Items.IRON_SWORD), "the sword is in their chest");
					h.assertValueEqual(1, count(h, chestOf(h, g[0]), Items.IRON_HELMET), "the helmet is in their chest");
					h.assertValueEqual(0, count(h, chestOf(h, g[1]), Items.IRON_SWORD), "the other guard's chest was not used");
				})
				.thenExecute(() -> {
					VillageTestSupport.done(h);
					WorkerTestSupport.end(h);
				});
	}
}
