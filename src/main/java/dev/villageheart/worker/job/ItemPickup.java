package dev.villageheart.worker.job;

import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;

/** Mod-side pickup of dropped items: vanilla villagers only pick up what their profession asks for (and only with mob griefing on). */
public final class ItemPickup {
	private ItemPickup() {}

	/** True when the whole villager inventory could take at least part of the stack. */
	public static boolean fits(Villager villager, ItemEntity item) {
		ItemStack s = item.getItem();
		if (s.isEmpty()) return false;
		var data = villager.getAttached(dev.villageheart.worker.WorkerAttachments.WORKER);
		if (data == null || !dev.villageheart.core.job.PocketRules.reservesToolSlot(data.job())) return villager.getInventory().canAddItem(s);
		var inv = villager.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			if (!dev.villageheart.core.job.PocketRules.acceptsGoods(data.job(), i)) continue;
			ItemStack have = inv.getItem(i);
			if (have.isEmpty()) return true;
			if (ItemStack.isSameItemSameComponents(have, s) && have.getCount() < have.getMaxStackSize()) return true;
		}
		return false;
	}

	/** Takes what fits (the entity keeps the rest); returns the number of items taken. */
	public static int collect(Villager villager, ItemEntity item) {
		if (item.isRemoved() || item.hasPickUpDelay()) return 0;
		ItemStack stack = item.getItem();
		int before = stack.getCount();
		ItemStack rest = dev.villageheart.worker.pocket.Pockets.insertGoods(villager, stack.copy());
		int taken = before - rest.getCount();
		if (taken <= 0) return 0;
		villager.take(item, taken);
		if (rest.isEmpty()) item.discard();
		else item.setItem(rest);
		return taken;
	}
}
