package dev.villageheart.worker.mine;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.ShaftScanner.ShaftDepth;
import dev.villageheart.core.mine.ShaftChoice;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;

/** Which Mineshaft a Mine's miners use, and where a miner is held while it is inside (M16 plan 3.2 #1 and #7). */
public final class MineSites {
	private MineSites() {}

	/** A Mineshaft: its saved location, marker and level. */
	public record ShaftSite(HeartLocation loc, BlockPos marker, ServerLevel level) {}

	/** The nearest VALID Mineshaft linked to the Mine at {@code mine}, or empty. */
	public static Optional<ShaftSite> shaftFor(MinecraftServer server, HeartLocation mine) {
		Optional<Buildings.BuildingView> mineView = Optional.empty();
		List<Buildings.BuildingView> all = Buildings.all(server);
		for (Buildings.BuildingView b : all) {
			if (b.type() == BuildingType.MINE && sameLoc(b, mine)) {
				mineView = Optional.of(b);
				break;
			}
		}
		if (mineView.isEmpty()) return Optional.empty();
		BlockPos mineMarker = mineView.get().marker();
		List<ShaftSite> linked = new ArrayList<>();
		for (Buildings.BuildingView b : all) {
			if (b.type() != BuildingType.MINESHAFT || b.status() != BuildingStatus.VALID || b.link() == null) continue;
			if (!b.link().equals(mineMarker) || b.level() != mineView.get().level()) continue;
			linked.add(new ShaftSite(VillageClaims.locate(b.level(), b.marker()), b.marker(), b.level()));
		}
		List<HeartLocation> locs = linked.stream().map(ShaftSite::loc).toList();
		Optional<HeartLocation> pick = ShaftChoice.pick(mine, locs);
		if (pick.isEmpty()) return Optional.empty();
		return linked.stream().filter(s -> s.loc().equals(pick.get())).findFirst();
	}

	/** The shaft at {@code loc} when it is VALID and still linked to the Mine at {@code mine} (a trip's shaft is checked every step). */
	public static Optional<ShaftSite> validLinkedShaft(MinecraftServer server, HeartLocation loc, HeartLocation mine) {
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.type() != BuildingType.MINESHAFT || b.status() != BuildingStatus.VALID || b.link() == null) continue;
			HeartLocation bl = VillageClaims.locate(b.level(), b.marker());
			if (!bl.equals(loc)) continue;
			HeartLocation link = VillageClaims.locate(b.level(), b.link());
			return link.equals(mine) ? Optional.of(new ShaftSite(bl, b.marker(), b.level())) : Optional.empty();
		}
		return Optional.empty();
	}

	private static boolean sameLoc(Buildings.BuildingView b, HeartLocation loc) {
		return VillageClaims.locate(b.level(), b.marker()).equals(loc);
	}

	/**
	 * Where a miner is held while it is down: the shaft's deepest cell. If that cell or the one above it holds a fluid, or the cell has
	 * a full collision shape, the spot moves up the column to the first cell pair that is not fluid and not a full block. Never the
	 * marker cell (it would block clicks on the marker).
	 */
	public static BlockPos holdSpot(ServerLevel level, BlockPos marker, ShaftDepth depth) {
		BlockPos deepest = new BlockPos(depth.deepestCell().x(), depth.deepestCell().y(), depth.deepestCell().z());
		BlockPos p = deepest;
		for (int i = 0; i < 64 && p.getY() < marker.getY(); i++, p = p.above()) {
			if (level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) == null) break;
			if (p.equals(marker) || p.above().equals(marker)) continue;
			if (passable(level, p) && passable(level, p.above())) return p;
		}
		return deepest;
	}

	private static boolean passable(ServerLevel level, BlockPos p) {
		BlockState s = level.getBlockState(p);
		return s.getFluidState().isEmpty() && !s.isCollisionShapeFullBlock(level, p);
	}
}
