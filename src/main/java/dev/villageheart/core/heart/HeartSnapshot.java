package dev.villageheart.core.heart;

import dev.villageheart.core.Rank;
import java.util.List;
import java.util.Objects;

/**
 * Everything one viewer's Heart screen shows. Holds only records, lists, strings, enums and primitives (no arrays), so
 * {@code equals} is the refresh dedupe: the server sends a new snapshot only when it differs from the last one sent.
 * Built for one viewer: a Visitor's snapshot has no details in it at all, and only a viewer who manages ranks gets {@code members}.
 */
public record HeartSnapshot(Rank viewerRank, Overview overview, List<VillagerRow> villagers, int moreVillagers,
		List<JobChoice> jobChoices, List<BuildingRow> buildings, int moreBuildings, Stock stock,
		List<MemberRow> members, int moreMembers) {
	public HeartSnapshot {
		Objects.requireNonNull(viewerRank, "viewerRank");
		Objects.requireNonNull(overview, "overview");
		Objects.requireNonNull(stock, "stock");
		villagers = List.copyOf(villagers);
		jobChoices = List.copyOf(jobChoices);
		buildings = List.copyOf(buildings);
		members = List.copyOf(members);
	}
}
