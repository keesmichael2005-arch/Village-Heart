package dev.villageheart.needs;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.needs.HouseBeds;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomContents;
import dev.villageheart.world.VillageClaims;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Who sleeps in a valid House (M11 plan 3.2 #8). The bed index is rebuilt from the cached room scans by the needs system; a building with no
 * cached scan (right after a restart) contributes no beds.
 */
public final class Housing {
	private static HouseBeds beds = HouseBeds.empty();
	private static MinecraftServer builtFor;

	private Housing() {}

	/** Rebuilds the index from the buildings registry. */
	public static void rebuild(MinecraftServer server) {
		Map<HeartLocation, Set<Long>> byBuilding = new HashMap<>();
		Set<HeartLocation> houses = new HashSet<>();
		for (Buildings.BuildingView b : Buildings.all(server)) {
			if (b.status() != BuildingStatus.VALID || b.latestScan().isEmpty()) continue;
			HeartLocation loc = VillageClaims.locate(b.level(), b.marker());
			Set<Long> set = new HashSet<>();
			for (Pos p : b.latestScan().get().positions(RoomContents.BED)) set.add(p.pack());
			byBuilding.put(loc, set);
			if (b.type() == BuildingType.HOUSE) houses.add(loc);
		}
		beds = new HouseBeds(byBuilding, houses);
		builtFor = server;
	}

	public static HouseBeds beds(MinecraftServer server) {
		if (builtFor != server) rebuild(server);
		return beds;
	}

	public static void reset() {
		beds = HouseBeds.empty();
		builtFor = null;
	}

	public static Optional<GlobalPos> home(Villager v) { return v.getBrain().getMemory(MemoryModuleType.HOME); }

	/** True when the villager's bed (its HOME) is in a valid House, or in {@code ownBuilding}. */
	public static boolean housed(MinecraftServer server, Villager v, Optional<HeartLocation> ownBuilding) {
		Optional<GlobalPos> home = home(v);
		if (home.isEmpty()) return false;
		if (!home.get().dimension().equals(v.level().dimension())) return false;
		BlockPos p = home.get().pos();
		return beds(server).housed(p.asLong(), ownBuilding);
	}
}
