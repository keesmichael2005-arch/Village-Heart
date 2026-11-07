package dev.villageheart.room;

import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.DoorPart;
import dev.villageheart.core.room.RoomContents;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Block state to scanner {@link Cell} (DESIGN section 3.2: doors, glass, panes, fences, trapdoors and slabs count as wall).
 * The rule is "anything with a collision box is wall"; the one exception is the open fence gate, whose collision box is empty.
 * Cached per state; the cache is cleared when tags can change.
 */
public final class RoomCells {
	private RoomCells() {}

	private static final Map<BlockState, Cell> CACHE = new ConcurrentHashMap<>();

	public static void register() {
		ServerLifecycleEvents.SERVER_STARTING.register(server -> CACHE.clear());
		ServerLifecycleEvents.END_DATA_PACK_RELOAD.register((server, resourceManager, success) -> CACHE.clear());
	}

	public static Cell of(BlockState state) {
		Cell c = CACHE.get(state);
		if (c == null) {
			c = classify(state);
			CACHE.put(state, c);
		}
		return c;
	}

	static Cell classify(BlockState state) {
		if (state.isAir()) return Cell.OPEN;
		VoxelShape shape = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
		CellKind kind = shape.isEmpty() && !state.is(BlockTags.FENCE_GATES) ? CellKind.OPEN : CellKind.SOLID;

		DoorPart door = DoorPart.NONE;
		if (state.is(BlockTags.DOORS) && state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
			door = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER ? DoorPart.LOWER : DoorPart.UPPER;
		}

		// the half of a two-block thing that carries the contents is the bed HEAD / the lower half; the other half points at it
		Dir partner = null;
		if (state.hasProperty(BedBlock.PART) && state.getValue(BedBlock.PART) == BedPart.FOOT) {
			partner = Dir.valueOf(state.getValue(BedBlock.FACING).name());
		} else if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
				&& state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) {
			partner = Dir.DOWN;
		}

		List<String> contents = new ArrayList<>();
		if (partner == null) {
			contents.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
			if (state.hasProperty(BedBlock.PART)) contents.add(RoomContents.BED);
			if (door == DoorPart.LOWER) contents.add(RoomContents.DOOR);
			if (state.getBlock() instanceof ChestBlock) contents.add(RoomContents.CHEST);
			if (state.getLightEmission() > 0) contents.add(RoomContents.LIGHT);
			contents.addAll(dev.villageheart.building.BuildingContentKeys.groupsFor(state));
		}
		return new Cell(kind, door, partner, contents);
	}
}
