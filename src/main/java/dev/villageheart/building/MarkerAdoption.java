package dev.villageheart.building;

import dev.villageheart.block.MarkerBlock;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;

/** Markers that exist before (or outside of) a claim: adopted when the Heart is claimed, reset when it is released. */
public final class MarkerAdoption {
	/** How far from the Heart we look for loaded markers (the widest outdoor site radius). */
	private static final int REACH = 128;

	private MarkerAdoption() {}

	/**
	 * A Heart was just claimed: queue every marker in the loaded chunks around it for registration. Markers in chunks that are
	 * not loaded are adopted when right-clicked. Never loads a chunk.
	 */
	public static void adoptLoaded(ServerLevel level, BlockPos heart) {
		BuildingRuntime rt = BuildingSystem.runtime(level.getServer());
		int minCx = (heart.getX() - REACH) >> 4, maxCx = (heart.getX() + REACH) >> 4;
		int minCz = (heart.getZ() - REACH) >> 4, maxCz = (heart.getZ() + REACH) >> 4;
		for (int cx = minCx; cx <= maxCx; cx++) {
			for (int cz = minCz; cz <= maxCz; cz++) {
				LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
				if (chunk == null) continue;
				LevelChunkSection[] sections = chunk.getSections();
				for (int i = 0; i < sections.length; i++) {
					LevelChunkSection section = sections[i];
					if (section.hasOnlyAir() || !section.maybeHas(s -> s.getBlock() instanceof MarkerBlock)) continue;
					int baseY = (level.getMinSectionY() + i) << 4;
					for (int x = 0; x < 16; x++) for (int y = 0; y < 16; y++) for (int z = 0; z < 16; z++) {
						BlockState s = section.getBlockState(x, y, z);
						if (s.getBlock() instanceof MarkerBlock) {
							rt.pending.add(VillageClaims.locate(level, new BlockPos((cx << 4) + x, baseY + y, (cz << 4) + z)));
						}
					}
				}
			}
		}
	}

	/** The village was released: plaques of its (loaded) buildings go back to grey. Unloaded ones keep their colour until used. */
	public static void resetReleased(MinecraftServer server, VillageState released) {
		for (BuildingRecord b : released.buildings()) {
			HeartLocation loc = b.marker();
			ServerLevel level = BuildingSystem.levelOf(server, loc);
			if (level == null) continue;
			BlockPos pos = new BlockPos(loc.x(), loc.y(), loc.z());
			if (!BuildingSystem.isLoaded(level, pos)) continue;
			BlockState state = level.getBlockState(pos);
			if (state.getBlock() instanceof MarkerBlock) BuildingSystem.setStatus(level, pos, state, BuildingStatus.UNCHECKED);
		}
	}
}
