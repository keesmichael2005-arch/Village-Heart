package dev.villageheart.item;

import dev.villageheart.VillageHeart;
import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;

public final class ModCreativeTab {
	public static final Identifier ID = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "main");
	public static final ResourceKey<CreativeModeTab> KEY = ResourceKey.create(Registries.CREATIVE_MODE_TAB, ID);

	private ModCreativeTab() {}

	public static void register() {
		CreativeModeTab tab = FabricItemGroup.builder()
				.title(Component.translatable("itemGroup.villageheart.main"))
				.icon(() -> new ItemStack(ModBlocks.VILLAGE_HEART_ITEM))
				.displayItems((params, output) -> {
					output.accept(ModBlocks.VILLAGE_HEART_ITEM);
					output.accept(ModItems.BLANK_MARKER);
					for (BuildingType t : BuildingType.values()) output.accept(MarkerBlocks.ITEMS.get(t));
					for (JobBlockType t : JobBlockType.values()) output.accept(JobBlocks.ITEMS.get(t));
				})
				.build();
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, KEY, tab);
	}
}
