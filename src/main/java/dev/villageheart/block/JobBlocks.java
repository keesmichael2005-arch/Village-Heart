package dev.villageheart.block;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.building.JobBlockType;
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

/** The three new job blocks and their items. */
public final class JobBlocks {
	public static final Map<JobBlockType, JobBlock> BLOCKS = new EnumMap<>(JobBlockType.class);
	public static final Map<JobBlockType, BlockItem> ITEMS = new EnumMap<>(JobBlockType.class);

	private JobBlocks() {}

	public static void register() {
		for (JobBlockType type : JobBlockType.values()) {
			Identifier id = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, type.id());
			ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, id);
			ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, id);
			BlockBehaviour.Properties props = BlockBehaviour.Properties.of()
					.setId(blockKey)
					.mapColor(MapColor.WOOD)
					.strength(2.0f)
					.sound(SoundType.WOOD)
					.ignitedByLava();
			if (type == JobBlockType.CHOPPING_BLOCK) props = props.noOcclusion();
			JobBlock block = new JobBlock(type, props);
			BlockItem item = new BlockItem(block, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix());
			Registry.register(BuiltInRegistries.BLOCK, blockKey, block);
			Registry.register(BuiltInRegistries.ITEM, itemKey, item);
			BLOCKS.put(type, block);
			ITEMS.put(type, item);
		}
	}
}
