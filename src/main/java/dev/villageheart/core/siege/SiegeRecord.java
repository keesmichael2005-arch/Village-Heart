package dev.villageheart.core.siege;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A zombie siege that is running, or the last one that ended (docs/plans/M25.md 3.12). */
public record SiegeRecord(int startDay, List<UUID> zombieIds, int spawned, int killed, int villagerDeaths) {
	public SiegeRecord {
		zombieIds = List.copyOf(zombieIds == null ? List.of() : new ArrayList<>(zombieIds));
	}

	public SiegeRecord plus(UUID id) {
		List<UUID> ids = new ArrayList<>(zombieIds);
		ids.add(id);
		return new SiegeRecord(startDay, ids, spawned + 1, killed, villagerDeaths);
	}

	public SiegeRecord withKilled(int n) { return new SiegeRecord(startDay, zombieIds, spawned, n, villagerDeaths); }

	public SiegeRecord withVillagerDeaths(int n) { return new SiegeRecord(startDay, zombieIds, spawned, killed, n); }

	/** Zombies still counted alive: spawned, and not yet seen dead. */
	public int left() { return Math.max(0, spawned - killed); }
}
