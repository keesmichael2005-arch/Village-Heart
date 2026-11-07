package dev.villageheart.stock;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.level.block.entity.BarrelBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/** Finds the live container at a stockpile position without ever loading a chunk. */
public final class StorageContainers {
	private StorageContainers() {}

	/**
	 * The chest half (chest, trapped chest, copper chests) or barrel at {@code pos}, or null when the chunk is not loaded or
	 * something else stands there. Deliberately not {@code level.getBlockEntity}: on the server thread that loads the chunk.
	 */
	@Nullable
	public static Container at(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		if (chunk == null) return null;
		BlockEntity be = chunk.getBlockEntity(pos);
		return (be instanceof ChestBlockEntity || be instanceof BarrelBlockEntity) && !be.isRemoved() ? (Container) be : null;
	}
}
