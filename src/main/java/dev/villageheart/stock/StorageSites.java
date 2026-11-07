package dev.villageheart.stock;

import dev.villageheart.building.Buildings;
import dev.villageheart.building.Buildings.BuildingView;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Balance;
import dev.villageheart.core.building.BuildingKind;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.stock.StorageSet;
import dev.villageheart.core.stock.StorageSet.SiteCandidate;
import dev.villageheart.core.stock.StorageSite;
import dev.villageheart.room.RoomScans;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.resources.Identifier;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import org.jetbrains.annotations.Nullable;

/** The stockpile's sites for this moment, from the building registry's cached scans (see DESIGN section 6). */
final class StorageSites {
	private StorageSites() {}

	/**
	 * @param level        the claim's level; null when there is no claim
	 * @param unknownSites valid Storages whose contents are unknown (no scan yet, an unscanned neighbour, nothing readable)
	 */
	record Collected(@Nullable ServerLevel level, List<StorageSite<StackKey>> sites, int unknownSites, int unreadContainers) {}

	static final Collected NONE = new Collected(null, List.of(), 0, 0);

	static Collected collect(MinecraftServer server) {
		var village = VillageClaims.current(server);
		if (village.isEmpty()) return NONE;
		HeartLocation heart = village.get().heart();
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(heart.dimension())));
		if (level == null) return NONE;

		List<SiteCandidate> candidates = new ArrayList<>();
		Set<Pos> excluded = new HashSet<>();
		List<Pos> unscannedOthers = new ArrayList<>();
		int noScan = 0;
		for (BuildingView v : Buildings.all(server)) {
			if (v.level() != level || v.type().kind() != BuildingKind.ROOM) continue;
			Pos marker = RoomScans.pos(v.marker());
			RoomScan scan = v.latestScan().orElse(null);
			if (v.type() == BuildingType.STORAGE) {
				if (v.status() != dev.villageheart.core.building.BuildingStatus.VALID) continue;
				if (scan == null) {
					noScan++;
					continue;
				}
				List<Pos> cs = new ArrayList<>(scan.positions(BuildingRequirements.CHEST));
				cs.addAll(scan.positions(BuildingRequirements.BARREL));
				candidates.add(new SiteCandidate(marker, scan.door() != null ? scan.door() : marker, cs));
			} else if (scan == null) {
				unscannedOthers.add(marker);
			} else {
				excluded.addAll(scan.positions(BuildingRequirements.CHEST));
				excluded.addAll(scan.positions(BuildingRequirements.BARREL));
			}
		}
		StorageSet.Resolved resolved = StorageSet.resolve(candidates, excluded, unscannedOthers, Balance.STOCKPILE_NEIGHBOUR_RANGE);
		ContainerBinder.Bound bound = ContainerBinder.bind(level, resolved.sites());
		return new Collected(level, bound.sites(), noScan + resolved.unknownMarkers().size() + bound.emptySites(), bound.unreadContainers());
	}
}
