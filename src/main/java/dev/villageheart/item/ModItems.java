package dev.villageheart.item;

import dev.villageheart.VillageHeart;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public final class ModItems {
	public static final Identifier BLANK_MARKER_ID = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "blank_marker");
	private static final ResourceKey<Item> BLANK_MARKER_KEY = ResourceKey.create(Registries.ITEM, BLANK_MARKER_ID);

	public static final Item BLANK_MARKER = new Item(new Item.Properties().setId(BLANK_MARKER_KEY));

	/** M20: the Blueprint a Steward takes at a Builder's Table. One item; the blueprint is its data component. */
	public static final Identifier BLUEPRINT_ID = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "blueprint");
	private static final ResourceKey<Item> BLUEPRINT_KEY = ResourceKey.create(Registries.ITEM, BLUEPRINT_ID);
	public static final Item BLUEPRINT = new dev.villageheart.build.BlueprintItem(new Item.Properties().setId(BLUEPRINT_KEY).stacksTo(1));

	/** One item that holds every plan. It is not used up when a site is confirmed. */
	public static final Identifier BLUEPRINT_BOOK_ID = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "blueprint_book");
	private static final ResourceKey<Item> BLUEPRINT_BOOK_KEY = ResourceKey.create(Registries.ITEM, BLUEPRINT_BOOK_ID);
	public static final Item BLUEPRINT_BOOK = new dev.villageheart.build.BlueprintBookItem(new Item.Properties().setId(BLUEPRINT_BOOK_KEY).stacksTo(1));

	private ModItems() {}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, BLANK_MARKER_KEY, BLANK_MARKER);
		Registry.register(BuiltInRegistries.ITEM, BLUEPRINT_KEY, BLUEPRINT);
		Registry.register(BuiltInRegistries.ITEM, BLUEPRINT_BOOK_KEY, BLUEPRINT_BOOK);
	}
}
