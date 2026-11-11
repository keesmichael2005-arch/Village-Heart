package dev.villageheart.worker.smith;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Where the smith stands, and where a golem waits outside the door. */
public final class SmithyLayout {
	private SmithyLayout() {}

	public static HeartLocation loc(ServerLevel level, BlockPos pos) { return VillageClaims.locate(level, pos); }

	public static BlockPos block(HeartLocation l) { return new BlockPos(l.x(), l.y(), l.z()); }

	/** The registered Smithy whose last scan contains this furnace. */
	public static Optional<HeartLocation> smithyAt(MinecraftServer server, HeartLocation furnace) {
		for (Buildings.BuildingView b : Buildings.valid(server, BuildingType.SMITHY)) {
			if (b.latestScan().isEmpty()) continue;
			for (Pos p : b.latestScan().get().positions(BuildingRequirements.BLAST_FURNACE)) {
				if (p.x() == furnace.x() && p.y() == furnace.y() && p.z() == furnace.z()) return Optional.of(loc(b.level(), b.marker()));
			}
		}
		return Optional.empty();
	}

	public static Optional<RoomScan> scan(MinecraftServer server, HeartLocation smithy) {
		for (Buildings.BuildingView b : Buildings.valid(server, BuildingType.SMITHY)) {
			if (loc(b.level(), b.marker()).equals(smithy)) return b.latestScan();
		}
		return Optional.empty();
	}

	public static Optional<BlockPos> anvil(MinecraftServer server, HeartLocation smithy, BlockPos furnace) {
		return scan(server, smithy).flatMap(s -> Optional.ofNullable(nearest(s.positions(BuildingRequirements.ANVIL), furnace)));
	}

	public static Optional<BlockPos> chest(MinecraftServer server, HeartLocation smithy, BlockPos furnace) {
		return scan(server, smithy).flatMap(s -> Optional.ofNullable(nearest(s.positions(BuildingRequirements.CHEST), furnace)));
	}

	private static BlockPos nearest(java.util.List<Pos> positions, BlockPos furnace) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (Pos p : positions) {
			BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
			double d = furnace.distSqr(bp);
			if (d < bestD) {
				bestD = d;
				best = bp;
			}
		}
		return best;
	}

	/**
	 * A 2×2×3 space of air on solid ground, three to six blocks out from the door, on the side the door opens toward.
	 * Empty when the scan has no door or nothing out there fits.
	 */
	public static Optional<BlockPos> waitSpot(ServerLevel level, RoomScan scan) {
		if (scan.door() == null || scan.start() == null) return Optional.empty();
		int dx = Integer.signum(scan.door().x() - scan.start().x());
		int dz = Integer.signum(scan.door().z() - scan.start().z());
		if (dx == 0 && dz == 0) dz = -1;
		BlockPos door = new BlockPos(scan.door().x(), scan.door().y(), scan.door().z());
		for (int dist = 3; dist <= 6; dist++) {
			BlockPos spot = door.offset(dx * dist, 0, dz * dist);
			if (fits(level, spot, dx, dz)) return Optional.of(spot);
		}
		return Optional.empty();
	}

	/** The spot and the block beside it (the golem is wider than one block), three high, with solid ground under both. */
	private static boolean fits(ServerLevel level, BlockPos spot, int dx, int dz) {
		int sx = dz == 0 ? 0 : 1;
		int sz = dx == 0 ? 0 : 1;
		if (dx != 0 && dz != 0) {
			sx = 1;
			sz = 0;
		}
		BlockPos side = spot.offset(sx, 0, sz);
		return column(level, spot) && column(level, side);
	}

	private static boolean column(ServerLevel level, BlockPos feet) {
		BlockState ground = level.getBlockState(feet.below());
		if (!ground.isFaceSturdy(level, feet.below(), net.minecraft.core.Direction.UP) && !ground.isSolid()) return false;
		for (int y = 0; y < 3; y++) if (!level.getBlockState(feet.above(y)).isAir()) return false;
		return true;
	}
}
