package dev.villageheart.core.gear;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What one camp needs: {@code needed} slots (hired minus fresh tools), the state of each hired worker, and the tool counts. {@code fresh}
 * and {@code worn} are -1 when the chests cannot be read ({@link #unknown(List)}).
 */
public record CampDemand(List<GearDemand> slots, int needed, Map<UUID, WorkerToolState> workers, int fresh, int worn) {
	public CampDemand {
		slots = List.copyOf(slots);
		workers = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(workers));
	}

	/** The chests cannot be read: nothing new starts and an open craft pauses. */
	public static CampDemand unknown(List<UUID> hired) {
		Map<UUID, WorkerToolState> m = new LinkedHashMap<>();
		for (UUID u : hired) m.put(u, WorkerToolState.UNKNOWN);
		return new CampDemand(List.of(), 0, m, -1, -1);
	}

	public boolean isUnknown() { return fresh < 0; }

	public WorkerToolState stateOf(UUID worker) { return workers.getOrDefault(worker, WorkerToolState.UNKNOWN); }
}
