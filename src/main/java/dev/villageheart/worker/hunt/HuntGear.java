package dev.villageheart.worker.hunt;

import dev.villageheart.core.hunt.HunterWeapons;
import dev.villageheart.core.hunt.HuntTable;
import dev.villageheart.worker.job.CampTools;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.worker.job.CampContainers;

/** The hunter's weapons and arrows in the Lodge chest (M17 plan 3.2 #5 and #7). */
public final class HuntGear {
	private HuntGear() {}

	private static final Set<String> YIELD = Set.copyOf(HuntTable.allItems());

	/** Bow or crossbow: the same items {@link CampTools#isTool} accepts as ranged weapons. */
	public static boolean isRanged(ItemStack s) { return s.is(Items.BOW) || s.is(Items.CROSSBOW); }

	/** Any bow or crossbow before any sword; swords by tier. */
	public static int rank(ItemStack s) { return HunterWeapons.rank(isRanged(s), CampTools.tierOf(s)); }

	/** True when the villager carries any item the hunter brings back. */
	public static boolean carriesYield(Villager v) {
		SimpleContainer inv = v.getInventory();
		for (int i = 0; i < inv.getContainerSize(); i++) {
			ItemStack s = inv.getItem(i);
			if (!s.isEmpty() && YIELD.contains(BuiltInRegistries.ITEM.getKey(s.getItem()).toString())) return true;
		}
		return false;
	}

	/**
	 * Takes one arrow from the Lodge chests: the first plain arrow in container and slot order, else the first other item of the arrows
	 * tag. Returns its id, or empty when there was none (or the chests are unreadable).
	 */
	public static Optional<String> takeArrow(MinecraftServer server, HeartLocation lodge) {
		Optional<java.util.List<CampContainers.Chest>> chests = CampContainers.of(server, lodge);
		if (chests.isEmpty()) return Optional.empty();
		for (int pass = 0; pass < 2; pass++) {
			for (CampContainers.Chest chest : chests.get()) {
				Container c = chest.container();
				for (int i = 0; i < c.getContainerSize(); i++) {
					ItemStack s = c.getItem(i);
					if (s.isEmpty()) continue;
					boolean match = pass == 0 ? s.is(Items.ARROW) : s.is(ItemTags.ARROWS);
					if (!match) continue;
					String id = BuiltInRegistries.ITEM.getKey(s.getItem()).toString();
					s.shrink(1);
					if (s.isEmpty()) c.setItem(i, ItemStack.EMPTY);
					c.setChanged();
					return Optional.of(id);
				}
			}
		}
		return Optional.empty();
	}
}
