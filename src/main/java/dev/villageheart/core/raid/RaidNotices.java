package dev.villageheart.core.raid;

import dev.villageheart.core.Rank;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Who hears a raid line. Chat itself is the glue (step 5). The owner is not in {@code VillageState.ranks}, so the caller puts them in. */
public final class RaidNotices {
	private RaidNotices() {}

	public static Set<UUID> audience(Map<UUID, Rank> ranks, Set<UUID> online) {
		Set<UUID> out = new HashSet<>();
		for (Map.Entry<UUID, Rank> e : ranks.entrySet()) {
			if (online.contains(e.getKey()) && e.getValue().atLeast(Rank.RESIDENT)) out.add(e.getKey());
		}
		return Set.copyOf(out);
	}
}
