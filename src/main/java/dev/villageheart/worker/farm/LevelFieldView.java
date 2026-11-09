package dev.villageheart.worker.farm;

import dev.villageheart.core.farm.CropKind;
import dev.villageheart.core.farm.FieldCell;
import dev.villageheart.core.farm.FieldView;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * Field-index view over a loaded {@link ServerLevel}. Never loads a chunk: a position in an unloaded chunk is UNLOADED. A crop counts only
 * when the block below is farmland (a crop on dirt reads OTHER); crops are matched by block id, never by {@code instanceof CropBlock}
 * (the torchflower crop is one). A cell costs one block read, two when it holds a crop or air (the farmland check). Server thread only.
 */
public final class LevelFieldView implements FieldView {
	private final ServerLevel level;
	private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
	private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
	private LevelChunk chunk;
	/** Block reads so far (tests assert the budget the index assumes). */
	public long reads;

	public LevelFieldView(ServerLevel level) { this.level = level; }

	private LevelChunk chunk(int x, int z) {
		int cx = x >> 4, cz = z >> 4;
		if (chunk != null && cx == chunkX && cz == chunkZ) return chunk;
		LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
		if (c != null) {
			chunk = c;
			chunkX = cx;
			chunkZ = cz;
		}
		return c;
	}

	private BlockState state(LevelChunk c, int x, int y, int z) {
		reads++;
		return c.getBlockState(mutable.set(x, y, z));
	}

	@Override
	public FieldCell cell(int x, int y, int z) {
		LevelChunk c = chunk(x, z);
		if (c == null || y < level.getMinY() + 1 || y >= level.getMaxY()) return c == null ? FieldCell.UNLOADED : FieldCell.OTHER;
		BlockState at = state(c, x, y, z);
		boolean air = at.isAir();
		CropKind kind = null;
		if (!air) {
			kind = CropKind.byBlockId(BuiltInRegistries.BLOCK.getKey(at.getBlock()).toString()).orElse(null);
			if (kind == null) return FieldCell.OTHER;
		}
		if (!state(c, x, y - 1, z).is(Blocks.FARMLAND)) return FieldCell.OTHER;
		if (air) return FieldCell.EMPTY;
		return FieldCell.crop(kind, ((CropBlock) at.getBlock()).getAge(at));
	}
}
