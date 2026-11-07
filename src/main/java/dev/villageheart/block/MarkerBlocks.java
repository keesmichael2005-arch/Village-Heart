package dev.villageheart.block;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.building.BuildingType;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/** The 14 building marker blocks and their items. */
public final class MarkerBlocks {
	public static final Map<BuildingType, MarkerBlock> BLOCKS = new EnumMap<>(BuildingType.class);
	public static final Map<BuildingType, BlockItem> ITEMS = new EnumMap<>(BuildingType.class);

	private MarkerBlocks() {}

	public static void register() {
		for (BuildingType type : BuildingType.values()) {
			Identifier id = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, type.markerId());
			ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
			MarkerBlock block = new MarkerBlock(type, BlockBehaviour.Properties.of()
					.setId(blockKey)
					.mapColor(MapColor.WOOD)
					.noCollision()
					.noOcclusion()
					.strength(1.0f)
					.sound(SoundType.WOOD)
					.ignitedByLava()
					.pushReaction(PushReaction.DESTROY));
			BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
			Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
			Registry.register(BuiltInRegistries.ITEM, itemKey, item);
			BLOCKS.put(type, block);
			ITEMS.put(type, item);
		}
	}
}
