package dev.villageheart.core.siege;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A zombie siege that is running, or the last one that ended (docs/plans/M25.md 3.12). */
public record SiegeRecord(int startDay, List<UUID> zombieIds, int spawned, int killed, int villagerDeaths) {
	public SiegeRecord {
		zombieIds = List.copyOf(zombieIds == null ? List.of() : new ArrayList<>(zombieIds));
	}
}
