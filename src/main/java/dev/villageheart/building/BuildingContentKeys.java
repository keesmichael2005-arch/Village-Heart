package dev.villageheart.building;

import dev.villageheart.core.building.BuildingRequirements;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** The group content keys the building rules add to the room scanner's block ids (called by {@code RoomCells}). */
public final class BuildingContentKeys {
	private BuildingContentKeys() {}

	public static List<String> groupsFor(BlockState state) {
		List<String> keys = new ArrayList<>(1);
		if (state.is(BlockTags.RAILS)) keys.add(BuildingRequirements.RAIL);
		if (state.is(Blocks.GRASS_BLOCK) || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)) keys.add(BuildingRequirements.NATURAL);
		if (state.is(BlockTags.ANVIL)) keys.add(BuildingRequirements.ANVIL);
		return keys;
	}
}
