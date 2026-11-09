package dev.villageheart.worker.lumber;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.tree.Species;
import dev.villageheart.core.tree.TreeCell;
import dev.villageheart.core.tree.TreeView;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Tree-scanner view over a loaded {@link ServerLevel}. Never loads a chunk: a position in an unloaded chunk is UNLOADED. One view per
 * search or per scan (it caches the last chunk and the rooms of the valid buildings, taken once). Server thread only.
 */
public final class LevelTreeView implements TreeView {
	private final ServerLevel level;
	private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
	private final List<RoomScan> rooms;
	private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
	private LevelChunk chunk;

	public LevelTreeView(ServerLevel level) {
		this.level = level;
		this.rooms = validRooms(level.getServer());
	}

	/** The cached scans of the valid buildings (any type), snapshot once. */
	static List<RoomScan> validRooms(MinecraftServer server) {
		List<RoomScan> out = new ArrayList<>();
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.status() == BuildingStatus.VALID && b.latestScan().isPresent()) out.add(b.latestScan().get());
		}
		return out;
	}

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
	public TreeCell cell(int x, int y, int z) {
		if (y < level.getMinY()) return TreeCell.OTHER;
		if (y > level.getMaxY()) return TreeCell.AIR;
		LevelChunk c = chunk(x, z);
		if (c == null) return TreeCell.UNLOADED;
		return classify(c.getBlockState(mutable.set(x, y, z)));
	}

	/** Public for tests. */
	public static TreeCell classify(BlockState state) {
		if (state.isAir()) return TreeCell.AIR;
		if (state.is(BlockTags.OVERWORLD_NATURAL_LOGS)) {
			Species s = speciesOf(state.getBlock());
			return s == null ? TreeCell.OTHER : TreeCell.log(s);
		}
		if (state.is(BlockTags.LEAVES)) {
			if (state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT)) return TreeCell.PLACED_LEAVES;
			Species s = speciesOf(state.getBlock());
			return s == null ? TreeCell.PLACED_LEAVES : TreeCell.naturalLeaves(s);
		}
		if (state.is(BlockTags.DIRT) || state.is(Blocks.FARMLAND)) return TreeCell.SOIL;
		if (state.canBeReplaced()) return TreeCell.AIR;
		return TreeCell.OTHER;
	}

	/** The species a log or leaves block belongs to, by its id ("minecraft:dark_oak_log" is DARK_OAK); null when unknown. */
	public static Species speciesOf(net.minecraft.world.level.block.Block block) {
		String path = BuiltInRegistries.BLOCK.getKey(block).getPath();
		if (path.startsWith("azalea_") || path.startsWith("flowering_azalea_")) return Species.AZALEA;
		if (path.startsWith("dark_oak_")) return Species.DARK_OAK;
		if (path.startsWith("pale_oak_")) return Species.PALE_OAK;
		if (path.startsWith("oak_")) return Species.OAK;
		if (path.startsWith("spruce_")) return Species.SPRUCE;
		if (path.startsWith("birch_")) return Species.BIRCH;
		if (path.startsWith("jungle_")) return Species.JUNGLE;
		if (path.startsWith("acacia_")) return Species.ACACIA;
		if (path.startsWith("mangrove_")) return Species.MANGROVE;
		if (path.startsWith("cherry_")) return Species.CHERRY;
		return null;
	}

	@Override
	public int top(int x, int z) {
		LevelChunk c = chunk(x, z);
		if (c == null) return Integer.MIN_VALUE;
		return c.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
	}

	@Override
	public boolean inRoom(Pos p) {
		for (RoomScan r : rooms) if (r.occupies(p)) return true;
		return false;
	}
}
