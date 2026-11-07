package dev.villageheart.gametest;

import dev.villageheart.block.JobBlocks;
import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.block.MarkerStatus;
import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.item.ModCreativeTab;
import dev.villageheart.item.ModItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/** Layer 2: markers and job blocks. All tests share the environment {@code villageheart-gametest:markers}. */
public class MarkerGameTests {
	private static final String ENV = "villageheart-gametest:markers";

	private static Identifier id(String path) { return Identifier.fromNamespaceAndPath("villageheart", path); }

	/** T-L2-13 */
	@GameTest(environment = ENV)
	public void registrySmoke(GameTestHelper h) {
		for (BuildingType t : BuildingType.values()) {
			Identifier id = id(t.markerId());
			h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id), "block " + id);
			h.assertTrue(BuiltInRegistries.ITEM.containsKey(id), "item " + id);
			Block b = BuiltInRegistries.BLOCK.getValue(id);
			h.assertTrue(b instanceof MarkerBlock, "MarkerBlock " + id);
			MarkerBlock mb = (MarkerBlock) b;
			h.assertValueEqual(t, mb.type(), "type of " + id);
			h.assertValueEqual(12, mb.getStateDefinition().getPossibleStates().size(), "states of " + id);
			h.assertValueEqual(MarkerStatus.UNCHECKED, mb.defaultBlockState().getValue(MarkerBlock.STATUS), "default status");
			BlockState rotated = mb.defaultBlockState().setValue(MarkerBlock.STATUS, MarkerStatus.VALID).rotate(Rotation.CLOCKWISE_90);
			h.assertValueEqual(Direction.EAST, rotated.getValue(MarkerBlock.FACING), "rotated facing");
			h.assertValueEqual(MarkerStatus.VALID, rotated.getValue(MarkerBlock.STATUS), "rotate keeps status");
		}
		for (JobBlockType t : JobBlockType.values()) {
			h.assertTrue(BuiltInRegistries.BLOCK.containsKey(id(t.id())), "block " + t.id());
			h.assertTrue(BuiltInRegistries.ITEM.containsKey(id(t.id())), "item " + t.id());
		}
		h.assertTrue(BuiltInRegistries.ITEM.containsKey(id("blank_marker")), "blank_marker");

		CreativeModeTab tab = BuiltInRegistries.CREATIVE_MODE_TAB.getValue(ModCreativeTab.ID);
		h.assertTrue(tab != null, "creative tab villageheart:main");
		tab.buildContents(new CreativeModeTab.ItemDisplayParameters(h.getLevel().enabledFeatures(), true, h.getLevel().registryAccess()));
		List<Item> expected = new ArrayList<>();
		expected.add(ModBlocks.VILLAGE_HEART_ITEM);
		expected.add(ModItems.BLANK_MARKER);
		for (BuildingType t : BuildingType.values()) expected.add(MarkerBlocks.ITEMS.get(t));
		for (JobBlockType t : JobBlockType.values()) expected.add(JobBlocks.ITEMS.get(t));
		List<Item> actual = new ArrayList<>();
		for (ItemStack s : tab.getDisplayItems()) actual.add(s.getItem());
		h.assertTrue(!actual.isEmpty(), "creative tab contents non-empty");
		h.assertValueEqual(expected, actual, "creative tab items in order");
		h.succeed();
	}
}
