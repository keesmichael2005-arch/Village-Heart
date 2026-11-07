package dev.villageheart.worker.job;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.stock.StorageContainers;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;

/**
 * The chests of a worker's own workplace (the camp chest holds the tools). Read from the building's latest scan with
 * {@link StorageContainers#at}, so a chunk is never loaded.
 */
public final class CampContainers {
	private CampContainers() {}

	/** One chest of the camp. */
	public record Chest(BlockPos pos, Container container) {}

	public static Optional<Buildings.BuildingView> building(MinecraftServer server, HeartLocation building) {
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.level().dimension().identifier().toString().equals(building.dimension()) && b.marker().getX() == building.x()
					&& b.marker().getY() == building.y() && b.marker().getZ() == building.z()) {
				return Optional.of(b);
			}
		}
		return Optional.empty();
	}

	/**
	 * The readable chests of the building (scan order); empty when the contents are unknown: no scan yet (after a restart), or one of
	 * the chest positions sits in an unloaded chunk. A chest that was removed since the scan is simply skipped.
	 */
	public static Optional<List<Chest>> of(MinecraftServer server, HeartLocation building) {
		Optional<Buildings.BuildingView> view = building(server, building);
		if (view.isEmpty() || view.get().latestScan().isEmpty()) return Optional.empty();
		ServerLevel level = view.get().level();
		RoomScan scan = view.get().latestScan().get();
		List<Chest> out = new ArrayList<>();
		for (Pos p : scan.positions(BuildingRequirements.CHEST)) {
			BlockPos bp = new BlockPos(p.x(), p.y(), p.z());
			if (level.getChunkSource().getChunkNow(bp.getX() >> 4, bp.getZ() >> 4) == null) return Optional.empty();
			Container c = StorageContainers.at(level, bp);
			if (c != null) out.add(new Chest(bp, c));
		}
		return Optional.of(out);
	}
}
