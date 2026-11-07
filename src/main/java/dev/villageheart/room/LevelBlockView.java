package dev.villageheart.room;

import dev.villageheart.core.room.BlockView;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.CellKind;
import dev.villageheart.core.room.Pos;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Scanner view over a loaded {@link ServerLevel}. Never loads a chunk: a position in an unloaded chunk is
 * {@link Cell#UNLOADED}. One view per scan (it caches the last chunk and the top solid block of each column).
 * Server thread only.
 */
public final class LevelBlockView implements BlockView {
	private final ServerLevel level;
	private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
	private final Map<Long, Integer> topSolid = new HashMap<>();
	private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
	private LevelChunk chunk;

	public LevelBlockView(ServerLevel level) { this.level = level; }

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

	@Override
	public Cell cell(int x, int y, int z) {
		if (y < level.getMinY()) return Cell.SOLID; // nothing passes below the world
		if (y > level.getMaxY()) return Cell.OPEN;
		LevelChunk c = chunk(x, z);
		if (c == null) return Cell.UNLOADED;
		return RoomCells.of(c.getBlockState(mutable.set(x, y, z)));
	}

	@Override
	public boolean openToSky(int x, int y, int z) {
		if (y > level.getMaxY()) return true;
		LevelChunk c = chunk(x, z);
		if (c == null) return false;
		return y > topSolid(c, x, z);
	}

	/** y of the highest SOLID cell (by the same rule as {@link #cell}) in the column, minY - 1 when there is none. */
	private int topSolid(LevelChunk c, int x, int z) {
		return topSolid.computeIfAbsent(Pos.pack(x, 0, z), k -> {
			int y = c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15); // y of the top non-air block
			for (; y >= level.getMinY(); y--) {
				if (RoomCells.of(c.getBlockState(mutable.set(x, y, z))).kind() == CellKind.SOLID) return y;
			}
			return level.getMinY() - 1;
		});
	}
}
