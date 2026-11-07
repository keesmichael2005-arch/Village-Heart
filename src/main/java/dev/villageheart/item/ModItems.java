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

	private ModItems() {}

	public static void register() {
		Registry.register(BuiltInRegistries.ITEM, BLANK_MARKER_KEY, BLANK_MARKER);
	}
}
