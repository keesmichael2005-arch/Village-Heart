package dev.villageheart.worker.mine;

import dev.villageheart.core.job.FoodKeep;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** What a miner keeps: nothing for its job. Everything that is not food is handed in (food follows the shared {@link FoodKeep} rule). */
public final class MinerKeep {
	private MinerKeep() {}

	/** True while the miner carries any item that is not one of the foods villagers keep: the deposit trip starts whatever the load. */
	public static boolean carriesNonFood(Villager v) {
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			if (!FoodKeep.POINTS.containsKey(BuiltInRegistries.ITEM.getKey(s.getItem()).toString())) return true;
		}
		return false;
	}
}
