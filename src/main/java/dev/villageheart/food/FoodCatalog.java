package dev.villageheart.food;

import dev.villageheart.core.food.FoodRules;
import dev.villageheart.core.food.FoodValue;
import dev.villageheart.stock.StackKey;
import java.util.Optional;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.ItemStack;

/** What the village eats: the item's own FOOD component (so components count), minus the items it never eats. */
public final class FoodCatalog {
	private FoodCatalog() {}

	public static Optional<FoodValue> of(StackKey key) {
		String id = key.id();
		if (FoodRules.neverEat(id)) return Optional.empty();
		ItemStack stack = key.toStack(1);
		FoodProperties food = stack.get(DataComponents.FOOD);
		if (food == null || food.nutrition() <= 0) return Optional.empty();
		return Optional.of(new FoodValue(food.nutrition(), food.saturation(), FoodRules.isRaw(id)));
	}
}
