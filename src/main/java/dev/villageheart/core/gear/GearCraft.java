package dev.villageheart.core.gear;

import java.util.List;

/** A committed craft: the recipe, its slot, the progress in work ticks and the raw items taken from Storage (refunded on cancel). */
public record GearCraft(String recipeId, OrderKey key, int progress, List<ItemCount> committed) {
	public GearCraft {
		committed = List.copyOf(committed);
	}

	public GearCraft withProgress(int p) { return new GearCraft(recipeId, key, p, committed); }
}
