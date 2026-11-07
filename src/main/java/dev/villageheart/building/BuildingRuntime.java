package dev.villageheart.building;

import dev.villageheart.core.Balance;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.RoundRobin;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.room.RoomScan;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import net.minecraft.server.MinecraftServer;

/**
 * The transient state of the building system: nothing here is saved. One instance per (server, claim epoch); it is
 * rebuilt empty after a restart, a release or a new claim, and then every building is queued at priority.
 */
final class BuildingRuntime {
	final MinecraftServer server;
	final int epoch;
	/** Markers placed since the last tick (no world writes happen in onPlace). */
	final Set<HeartLocation> pending = new LinkedHashSet<>();
	final RoundRobin<HeartLocation> schedule = new RoundRobin<>(VillageState.locationOrder(),
			Balance.VALIDATION_INTERVAL_TICKS, Balance.MAX_PRIORITY_CHECKS_PER_TICK);
	/** Latest raw room scan (without SHARED) per room building. */
	final Map<HeartLocation, RoomScan> rawScans = new HashMap<>();
	final Map<HeartLocation, Verdict> verdicts = new HashMap<>();
	/** The markers each building shared its room with at its last check (edge-triggered re-checks). */
	final Map<HeartLocation, Set<HeartLocation>> sharedWith = new HashMap<>();
	boolean primed;
	/** Test hook: how many times onPlace noted a new marker. */
	int placedNotes;

	private List<BuildingRecord> sortedFor;
	private SortedSet<HeartLocation> sorted = new TreeSet<>(VillageState.locationOrder());

	BuildingRuntime(MinecraftServer server, int epoch) {
		this.server = server;
		this.epoch = epoch;
	}

	/** The registered markers in order; rebuilt only when the building list changed. */
	SortedSet<HeartLocation> sortedMarkers(VillageState village) {
		if (village.buildings() != sortedFor) {
			TreeSet<HeartLocation> s = new TreeSet<>(VillageState.locationOrder());
			for (BuildingRecord b : village.buildings()) s.add(b.marker());
			sorted = s;
			sortedFor = village.buildings();
		}
		return sorted;
	}

	void forget(HeartLocation loc) {
		rawScans.remove(loc);
		verdicts.remove(loc);
		sharedWith.remove(loc);
	}
}
