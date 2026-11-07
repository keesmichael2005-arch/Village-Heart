package dev.villageheart.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Immutable snapshot of the one claimed village. */
public record VillageState(HeartLocation heart, UUID owner, String ownerName, long claimedAtGameTime,
		Map<UUID, Rank> ranks, int treasury, Tier tier,
		List<BuildingRecord> buildings, List<WorkerRecord> workers) {

	public VillageState {
		Objects.requireNonNull(heart, "heart");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(ownerName, "ownerName");
		Objects.requireNonNull(ranks, "ranks");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(buildings, "buildings");
		Objects.requireNonNull(workers, "workers");
		if (treasury < 0) throw new IllegalArgumentException("treasury must be >= 0: " + treasury);
		ranks = Map.copyOf(ranks);
		buildings = List.copyOf(buildings);
		workers = List.copyOf(workers);
		if (ranks.containsKey(owner)) throw new IllegalArgumentException("owner must not appear in ranks");
		for (Rank r : ranks.values()) {
			if (r == Rank.OWNER || r == Rank.VISITOR) throw new IllegalArgumentException("ranks may not hold " + r);
		}
	}

	public static VillageState founded(HeartLocation heart, UUID owner, String ownerName, long gameTime) {
		return new VillageState(heart, owner, ownerName, gameTime, Map.of(), 0, Tier.HAMLET, List.of(), List.of());
	}

	public Rank rankOf(UUID player) {
		if (owner.equals(player)) return Rank.OWNER;
		return ranks.getOrDefault(player, Rank.VISITOR);
	}

	public VillageState withRank(UUID player, Rank rank) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(rank, "rank");
		if (rank == Rank.OWNER) throw new IllegalArgumentException("cannot grant OWNER");
		if (owner.equals(player)) throw new IllegalArgumentException("cannot change the owner's rank");
		java.util.HashMap<UUID, Rank> copy = new java.util.HashMap<>(ranks);
		if (rank == Rank.VISITOR) copy.remove(player); else copy.put(player, rank);
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, copy, treasury, tier, buildings, workers);
	}

	public VillageState depositTreasury(int emeralds) {
		if (emeralds < 0) throw new IllegalArgumentException("deposit must be >= 0: " + emeralds);
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks,
				Math.addExact(treasury, emeralds), tier, buildings, workers);
	}

	public boolean isHeartAt(HeartLocation loc) {
		return heart.equals(loc);
	}
}
