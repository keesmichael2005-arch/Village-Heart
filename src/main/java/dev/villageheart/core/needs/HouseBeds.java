package dev.villageheart.core.needs;

import dev.villageheart.core.HeartLocation;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The bed-head cells (packed positions) of the buildings that count for housing (M11 plan 3.2 #8): the valid Houses, and any building's own
 * beds for its own workers (Barracks, M18). Pure data; the glue fills it from the cached room scans.
 */
public final class HouseBeds {
	private final Map<HeartLocation, Set<Long>> bedsByBuilding;
	private final Set<Long> houseBedSet;
	private final List<Long> houseBeds;

	public HouseBeds(Map<HeartLocation, Set<Long>> bedsByBuilding, Set<HeartLocation> houses) {
		Map<HeartLocation, Set<Long>> copy = new HashMap<>();
		bedsByBuilding.forEach((k, v) -> copy.put(k, Set.copyOf(v)));
		this.bedsByBuilding = Map.copyOf(copy);
		Set<Long> all = new HashSet<>();
		for (HeartLocation h : houses) all.addAll(this.bedsByBuilding.getOrDefault(h, Set.of()));
		this.houseBedSet = Set.copyOf(all);
		List<Long> sorted = new ArrayList<>(all);
		Collections.sort(sorted);
		this.houseBeds = List.copyOf(sorted);
	}

	public static HouseBeds empty() { return new HouseBeds(Map.of(), Set.of()); }

	/** True when {@code home} is a bed of a valid House, or of the worker's own building. */
	public boolean housed(long home, Optional<HeartLocation> ownBuilding) {
		if (houseBedSet.contains(home)) return true;
		return ownBuilding.isPresent() && bedsByBuilding.getOrDefault(ownBuilding.get(), Set.of()).contains(home);
	}

	/** Every House bed, sorted. */
	public List<Long> houseBeds() { return houseBeds; }
}
