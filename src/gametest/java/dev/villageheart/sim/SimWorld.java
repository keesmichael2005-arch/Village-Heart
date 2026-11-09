package dev.villageheart.sim;

import com.mojang.datafixers.util.Pair;
import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimRules;
import dev.villageheart.core.sim.SimArea;
import dev.villageheart.core.sim.SimArea.Chunk;
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
import java.util.Set;
import java.util.UUID;

/** The fixed world facts of a simulation: seed, owner, and how the village is found. */
final class SimWorld {
	private SimWorld() {}

	static final long SEED = 1L;
	/** Fixed so a resumed run is the same owner and gets its player data back. */
	static final UUID OWNER_UUID = UUID.nameUUIDFromBytes("villageheart-sim-owner".getBytes(StandardCharsets.UTF_8));
	static final String OWNER_NAME = "SimOwner";
	/**
	 * Static ticking square, in chunks: +-6 around the Heart's chunk covers the 80-block claim from any spot in that chunk plus one chunk
	 * ({@link SimArea#staticRadiusChunks}). Before the claim the same square is centred on the village start, so the bell and the site can be found.
	 * Workers that walk past it are followed by {@link SimTickets} (a player's simulation distance), so they never freeze at its edge.
	 */
	static final int LOAD_RADIUS_CHUNKS = SimArea.staticRadiusChunks(Balance.CLAIM_RADIUS);

	/** Nearest plains village start within 100 chunks of spawn (the position is a chunk's block, not structure bounds). */
	static Optional<BlockPos> locate(ServerLevel level) {
		var reg = level.registryAccess().lookupOrThrow(Registries.STRUCTURE);
		HolderSet<Structure> plains = HolderSet.direct(reg.getOrThrow(BuiltinStructures.VILLAGE_PLAINS));
		Pair<BlockPos, Holder<Structure>> hit = level.getChunkSource().getGenerator()
				.findNearestMapStructure(level, plains, level.getRespawnData().pos(), 100, false);
		return hit == null ? Optional.empty() : Optional.of(hit.getFirst());
	}

	private static Set<Chunk> area(BlockPos around) { return SimArea.square(SimTickets.chunkOf(around), LOAD_RADIUS_CHUNKS); }

	/** Force-loads (and keeps loaded) the chunks around the village. */
	static void forceLoad(ServerLevel level, BlockPos around) {
		for (Chunk c : area(around)) level.setChunkForced(c.x(), c.z(), true);
	}

	/** Moves the forced square from {@code from} (the village start) to {@code to} (the Heart): chunks only in the old square are released. */
	static void recentre(ServerLevel level, BlockPos from, BlockPos to) {
		Set<Chunk> next = area(to);
		for (Chunk c : area(from)) if (!next.contains(c)) level.setChunkForced(c.x(), c.z(), false);
		for (Chunk c : next) level.setChunkForced(c.x(), c.z(), true);
	}

	/** True once every forced chunk around the village is entity-ticking: the chunks are promoted over the first seconds, and a site search before that is not deterministic. */
	static boolean forcedChunksTicking(ServerLevel level, BlockPos around) {
		for (Chunk c : area(around)) {
			if (!level.isPositionEntityTicking(new ChunkPos(c.x(), c.z()).getMiddleBlockPosition(level.getSeaLevel()))) return false;
		}
		return true;
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
