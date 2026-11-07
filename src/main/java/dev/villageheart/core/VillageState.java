package dev.villageheart.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Immutable snapshot of the one claimed village. */
public record VillageState(HeartLocation heart, UUID owner, String ownerName, long claimedAtGameTime,
		Map<UUID, Rank> ranks, int treasury, Tier tier,
		List<BuildingRecord> buildings, List<WorkerRecord> workers, List<TreeTask> treeTasks) {

	/** Without tree tasks (the shape before M9). */
	public VillageState(HeartLocation heart, UUID owner, String ownerName, long claimedAtGameTime, Map<UUID, Rank> ranks, int treasury,
			Tier tier, List<BuildingRecord> buildings, List<WorkerRecord> workers) {
		this(heart, owner, ownerName, claimedAtGameTime, ranks, treasury, tier, buildings, workers, List.of());
	}

	public VillageState {
		Objects.requireNonNull(heart, "heart");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(ownerName, "ownerName");
		Objects.requireNonNull(ranks, "ranks");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(buildings, "buildings");
		Objects.requireNonNull(workers, "workers");
		Objects.requireNonNull(treeTasks, "treeTasks");
		if (treasury < 0) throw new IllegalArgumentException("treasury must be >= 0: " + treasury);
		ranks = Map.copyOf(ranks);
		buildings = sortedBuildings(buildings);
		workers = List.copyOf(workers);
		treeTasks = List.copyOf(treeTasks);
		java.util.Set<HeartLocation> seenCells = new java.util.HashSet<>();
		for (TreeTask t : treeTasks) {
			for (HeartLocation c : t.base()) {
				if (!seenCells.add(c)) throw new IllegalArgumentException("two tree tasks share the cell " + c);
			}
		}
		java.util.Set<UUID> seenVillagers = new java.util.HashSet<>();
		java.util.Set<HeartLocation> seenBlocks = new java.util.HashSet<>();
		for (WorkerRecord w : workers) {
			if (!seenVillagers.add(w.villager())) throw new IllegalArgumentException("two records for villager " + w.villager());
			if (!seenBlocks.add(w.jobBlock())) throw new IllegalArgumentException("two workers on job block " + w.jobBlock());
		}
		if (ranks.containsKey(owner)) throw new IllegalArgumentException("owner must not appear in ranks");
		for (Rank r : ranks.values()) {
			if (r == Rank.OWNER || r == Rank.VISITOR) throw new IllegalArgumentException("ranks may not hold " + r);
		}
	}

	private static final Comparator<HeartLocation> LOCATION_ORDER = Comparator.comparing(HeartLocation::dimension)
			.thenComparingInt(HeartLocation::x).thenComparingInt(HeartLocation::y).thenComparingInt(HeartLocation::z);

	/** Order of buildings and of the round-robin: (dimension, x, y, z). */
	public static Comparator<HeartLocation> locationOrder() { return LOCATION_ORDER; }

	private static List<BuildingRecord> sortedBuildings(List<BuildingRecord> in) {
		List<BuildingRecord> sorted = new ArrayList<>(in);
		sorted.sort(Comparator.comparing(BuildingRecord::marker, LOCATION_ORDER));
		for (int i = 1; i < sorted.size(); i++) {
			if (sorted.get(i).marker().equals(sorted.get(i - 1).marker())) {
				throw new IllegalArgumentException("two buildings at " + sorted.get(i).marker());
			}
		}
		return List.copyOf(sorted);
	}

	public static VillageState founded(HeartLocation heart, UUID owner, String ownerName, long gameTime) {
		return new VillageState(heart, owner, ownerName, gameTime, Map.of(), 0, Tier.HAMLET, List.of(), List.of(), List.of());
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
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, copy, treasury, tier, buildings, workers, treeTasks);
	}

	public VillageState depositTreasury(int emeralds) {
		if (emeralds < 0) throw new IllegalArgumentException("deposit must be >= 0: " + emeralds);
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks,
				Math.addExact(treasury, emeralds), tier, buildings, workers, treeTasks);
	}

	public VillageState withTier(Tier t) {
		Objects.requireNonNull(t, "tier");
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, treasury, t, buildings, workers, treeTasks);
	}

	public Optional<BuildingRecord> building(HeartLocation marker) {
		for (BuildingRecord b : buildings) if (b.marker().equals(marker)) return Optional.of(b);
		return Optional.empty();
	}

	/** Insert, or replace the record with the same marker. */
	public VillageState withBuilding(BuildingRecord b) {
		Objects.requireNonNull(b, "building");
		List<BuildingRecord> copy = new ArrayList<>(buildings);
		copy.removeIf(o -> o.marker().equals(b.marker()));
		copy.add(b);
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, treasury, tier, copy, workers, treeTasks);
	}

	/** Remove the record at {@code marker}; a no-op when there is none. */
	public VillageState withoutBuilding(HeartLocation marker) {
		if (building(marker).isEmpty()) return this;
		List<BuildingRecord> copy = new ArrayList<>(buildings);
		copy.removeIf(o -> o.marker().equals(marker));
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, treasury, tier, copy, workers, treeTasks);
	}

	public Optional<WorkerRecord> worker(UUID villager) {
		for (WorkerRecord w : workers) if (w.villager().equals(villager)) return Optional.of(w);
		return Optional.empty();
	}

	/** Append (list order is hiring order). Throws when the villager or the job block already has a record. */
	public VillageState withWorker(WorkerRecord w) {
		Objects.requireNonNull(w, "worker");
		List<WorkerRecord> copy = new ArrayList<>(workers);
		copy.add(w);
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, treasury, tier, buildings, copy, treeTasks);
	}

	/** Remove the record of {@code villager}; a no-op when there is none. */
	public VillageState withoutWorker(UUID villager) {
		if (worker(villager).isEmpty()) return this;
		List<WorkerRecord> copy = new ArrayList<>(workers);
		copy.removeIf(o -> o.villager().equals(villager));
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, treasury, tier, buildings, copy, treeTasks);
	}

	public List<WorkerRecord> workersAt(HeartLocation building) {
		return workers.stream().filter(w -> w.building().equals(building)).toList();
	}

	// ---- tree tasks (M9) ----

	/** Append a tree task. Throws when it shares a base cell with another task. */
	public VillageState withTreeTask(TreeTask t) {
		Objects.requireNonNull(t, "task");
		List<TreeTask> copy = new ArrayList<>(treeTasks);
		copy.add(t);
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, treasury, tier, buildings, workers, copy);
	}

	/** Remove the task whose first base cell is {@code key}; a no-op when there is none. */
	public VillageState withoutTreeTask(HeartLocation key) {
		if (treeTask(key).isEmpty()) return this;
		List<TreeTask> copy = new ArrayList<>(treeTasks);
		copy.removeIf(t -> t.key().equals(key));
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, treasury, tier, buildings, workers, copy);
	}

	public Optional<TreeTask> treeTask(HeartLocation key) {
		for (TreeTask t : treeTasks) if (t.key().equals(key)) return Optional.of(t);
		return Optional.empty();
	}

	public List<TreeTask> treeTasksAt(HeartLocation building) {
		return treeTasks.stream().filter(t -> t.building().equals(building)).toList();
	}

	public boolean isHeartAt(HeartLocation loc) {
		return heart.equals(loc);
	}
}
