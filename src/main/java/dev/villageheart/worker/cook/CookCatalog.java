package dev.villageheart.worker.cook;

import dev.villageheart.core.cook.FuelTable;
import dev.villageheart.stock.StackKey;
import java.util.OptionalInt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Which stacks play a part in the Cook's work (M13 plan 3.2 #2, #4). Inputs and fuel are only <b>component-free</b> stacks: a renamed beef or a
 * named log is a player's item and is never taken.
 */
public final class CookCatalog {
	private CookCatalog() {}

	public static String id(Item item) { return BuiltInRegistries.ITEM.getKey(item).toString(); }

	public static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }

	public static boolean plain(ItemStack s) { return !s.isEmpty() && s.getComponentsPatch().isEmpty(); }

	public static boolean plain(StackKey k) { return k.patch().isEmpty(); }

	/** The heat of one item when it is fuel, by the table of {@link FuelTable} and the game's {@code #logs_that_burn} tag. */
	public static OptionalInt heat(Item item) {
		return FuelTable.heat(id(item), item.builtInRegistryHolder().is(ItemTags.LOGS_THAT_BURN));
	}

	public static boolean isFuel(Item item) { return heat(item).isPresent(); }
}
