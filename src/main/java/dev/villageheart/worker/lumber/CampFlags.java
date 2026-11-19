package dev.villageheart.worker.lumber;

import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;

/** The chopping flag linked to one Lumber Camp. */
public final class CampFlags {
	private CampFlags() {}

	/** The valid chopping flag whose saved link is this camp, or empty. */
	public static Optional<BlockPos> flag(MinecraftServer server, HeartLocation camp) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return Optional.empty();
		for (BuildingRecord b : village.get().buildings()) {
			if (!b.typeId().equals(BuildingType.CHOPPING_FLAG.id()) || b.status() != BuildingStatus.VALID || b.link() == null) continue;
			if (!b.link().equals(camp)) continue;
			return Optional.of(new BlockPos(b.marker().x(), b.marker().y(), b.marker().z()));
		}
		return Optional.empty();
	}
}
