package dev.villageheart.sim;

import com.mojang.datafixers.util.Pair;
import dev.villageheart.core.ClaimRules;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.Structure;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;

/** The fixed world facts of a simulation: seed, owner, and how the village is found. */
final class SimWorld {
	private SimWorld() {}

	static final long SEED = 1L;
	/** Fixed so a resumed run is the same owner and gets its player data back. */
	static final UUID OWNER_UUID = UUID.nameUUIDFromBytes("villageheart-sim-owner".getBytes(StandardCharsets.UTF_8));
	static final String OWNER_NAME = "SimOwner";
	static final int LOAD_RADIUS_CHUNKS = 4;

	/** Nearest plains village start within 100 chunks of spawn (the position is a chunk's block, not structure bounds). */
	static Optional<BlockPos> locate(ServerLevel level) {
		var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		HolderSet<Structure> plains = HolderSet.direct(reg.getOrThrow(BuiltinStructures.VILLAGE_PLAINS));
		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, plains, level.getRespawnData().pos(), 100, false);
		return hit == null ? Optional.empty() : Optional.of(hit.getFirst());
	}

	/** Force-loads (and keeps loaded) the chunks around the village. */
	static void forceLoad(ServerLevel level, BlockPos around) {
		ChunkPos c = new ChunkPos(around);
		for (int dx = -LOAD_RADIUS_CHUNKS; dx <= LOAD_RADIUS_CHUNKS; dx++) {
			for (int dz = -LOAD_RADIUS_CHUNKS; dz <= LOAD_RADIUS_CHUNKS; dz++) {
				level.setChunkForced(c.x + dx, c.z + dz, true);
			}
		}
	}

	static Optional<BlockPos> findBell(ServerLevel level, BlockPos near) {
		return level.getPoiManager().findClosest(t -> t.is(PoiTypes.MEETING), near, 96, PoiManager.Occupancy.ANY);
	}

	/** The solid block (its top face is where the Heart stands) on the first acceptable spot east/south/west/north of the bell. */
	static Optional<BlockPos> heartSpot(ServerLevel level, BlockPos bell) {
		Direction[] dirs = {Direction.EAST, Direction.SOUTH, Direction.WEST, Direction.NORTH};
		for (int r = 2; r <= 6; r++) {
			for (Direction d : dirs) {
				BlockPos column = bell.relative(d, r);
				BlockPos ground = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column).below();
				if (level.getBlockState(ground).is(Blocks.BELL)) continue;
				if (!level.getBlockState(ground).isFaceSturdy(level, ground, Direction.UP)) continue;
				if (!level.getBlockState(ground.above()).isAir()) continue;
				if (Math.sqrt(ground.above().distSqr(bell)) > ClaimRules.MAX_BELL_DISTANCE) continue;
				return Optional.of(ground);
			}
		}
		return Optional.empty();
	}
}
