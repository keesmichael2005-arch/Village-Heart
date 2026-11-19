package dev.villageheart.worker.mine;

import dev.villageheart.core.job.FoodKeep;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** What a miner keeps: nothing for its job. Everything that is not food is handed in (food follows the shared {@link FoodKeep} rule). */
public final class MinerKeep {
	private MinerKeep() {}

	/**
	 * True while the miner carries a haul: any item that is not food. The tool in slot 0 is not a haul, so holding it does not
	 * start a deposit or keep the miner off the next job (a self-craft, another descent).
	 */
	public static boolean carriesNonFood(Villager v) {
		SimpleContainer inv = v.getInventory();
		boolean tool = dev.villageheart.worker.pocket.Pockets.holdingTool(v);
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (tool && i == dev.villageheart.core.job.PocketRules.TOOL_SLOT) continue;
			ItemStack s = inv.getItem(i);
			if (s.isEmpty()) continue;
			if (!FoodKeep.POINTS.containsKey(BuiltInRegistries.ITEM.getKey(s.getItem()).toString())) return true;
		}
		return false;
	}
}
