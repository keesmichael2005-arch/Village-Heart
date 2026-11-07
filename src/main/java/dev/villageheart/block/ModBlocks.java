package dev.villageheart.block;

import dev.villageheart.VillageHeart;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

public final class ModBlocks {
	public static final Identifier VILLAGE_HEART_ID = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "village_heart");
	private static final ResourceKey<Block> BLOCK_KEY = ResourceKey.create(Registries.BLOCK, VILLAGE_HEART_ID);
	private static final ResourceKey<Item> ITEM_KEY = ResourceKey.create(Registries.ITEM, VILLAGE_HEART_ID);

	public static final Block VILLAGE_HEART = new VillageHeartBlock(BlockBehaviour.Properties.of()
			.setId(BLOCK_KEY)
			.strength(3.0f, 1200.0f)
			.lightLevel(state -> 10)
			.sound(SoundType.AMETHYST));

	public static final Item VILLAGE_HEART_ITEM = new BlockItem(VILLAGE_HEART,
			new Item.Properties().setId(ITEM_KEY).useBlockDescriptionPrefix());

	private ModBlocks() {}

	public static void register() {
		Registry.register(BuiltInRegistries.BLOCK, BLOCK_KEY, VILLAGE_HEART);
		Registry.register(BuiltInRegistries.ITEM, ITEM_KEY, VILLAGE_HEART_ITEM);
		ItemGroupEvents.modifyEntriesEvent(CreativeModeTabs.FUNCTIONAL_BLOCKS).register(e -> e.accept(VILLAGE_HEART_ITEM));
	}
}
