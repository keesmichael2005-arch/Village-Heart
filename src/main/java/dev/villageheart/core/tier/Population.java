package dev.villageheart.core.tier;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import dev.villageheart.core.WorkerRecord;

/** Population (DESIGN section 10): loaded villagers in the claim plus registered workers that are not in that census. */
public final class Population {
	private Population() {}

	public static int count(Set<UUID> census, List<WorkerRecord> workers) {
		int n = census.size();
		for (WorkerRecord w : workers) if (!census.contains(w.villager())) n++;
		return n;
	}
}
