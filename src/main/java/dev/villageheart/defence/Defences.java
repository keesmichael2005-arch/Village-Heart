package dev.villageheart.defence;

import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.room.Pos;
import dev.villageheart.build.SiteValidation;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The patrol points of the finished lamp posts and watchtowers (docs/plans/M21.md 3.2 #8). M18 (guards) reads {@link #patrolPoints}: one
 * walkable cell next to each site, in the order the sites were finished.
 */
public final class Defences {
	private Defences() {}

	/** Feet cell and head cell hold no collision and no fluid, the cell below is solid. A cell in an unloaded chunk is assumed fine. */
	public static boolean walkable(ServerLevel level, Pos feet) {
		BlockPos p = new BlockPos(feet.x(), feet.y(), feet.z());
		if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) return true;
		BlockState below = level.getBlockState(p.below()), at = level.getBlockState(p), head = level.getBlockState(p.above());
		return SiteValidation.solid(below) && SiteValidation.free(at) && SiteValidation.free(head);
	}

	/** The patrol points of the claimed village, empty when there is none. */
	public static List<BlockPos> patrolPoints(MinecraftServer server) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return List.of();
		List<BlockPos> out = new ArrayList<>();
		for (DefenceSite s : village.get().defences()) {
			ServerLevel level = Workers.levelOf(server, s.ground());
			if (level == null) continue;
			dev.villageheart.core.defence.Defences.patrolPoint(s, p -> walkable(level, p)).ifPresent(p -> out.add(new BlockPos(p.x(), p.y(), p.z())));
		}
		return out;
	}
}
