package dev.villageheart.worker.smith;

import dev.villageheart.core.cook.FuelTable;
import dev.villageheart.stock.StackKey;
import dev.villageheart.worker.cook.CookCatalog;
import java.util.OptionalInt;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;

/** Item ids and fuel for the smith. Inputs are component-free, the same rule as the cook. */
public final class SmithCatalog {
	private SmithCatalog() {}

	public static String id(Item item) { return CookCatalog.id(item); }

	public static Item item(String id) { return BuiltInRegistries.ITEM.getValue(Identifier.parse(id)); }

	public static boolean plain(StackKey key) { return CookCatalog.plain(key); }

	/** Heat of one fuel item: coal, charcoal, or a log that burns. */
	public static OptionalInt heat(Item item) { return CookCatalog.heat(item); }

	public static boolean isFuel(Item item) { return heat(item).isPresent(); }

	public static int heatOrZero(String id) {
		OptionalInt h = heat(item(id));
		return h.orElse(0);
	}

	/** One smelted item costs this much of the furnace bank. */
	public static int heatPerItem() { return FuelTable.HEAT_PER_ITEM; }
}
