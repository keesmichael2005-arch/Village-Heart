package dev.villageheart.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.defence.DefenceSite;

/**
 * Immutable snapshot of the one claimed village.
 *
 * <p>{@code known} is the village's name book (M24): every UUID in {@code ranks} has an entry, the owner never does
 * (the owner's name is {@code ownerName}), no duplicates, at most {@link Balance#MAX_KNOWN_PLAYERS} entries and
 * at most one fewer ranked players. Stored sorted by (lastSeen, id) so equality and the save are stable.
 */
public record VillageState(HeartLocation heart, UUID owner, String ownerName, long claimedAtGameTime,
		Map<UUID, Rank> ranks, List<KnownPlayer> known, int treasury, Tier tier,
		List<BuildingRecord> buildings, List<WorkerRecord> workers, List<TreeTask> treeTasks, List<BuildJob> builds, List<DefenceSite> defences,
		List<dev.villageheart.core.gear.SelfCraft> selfCrafts) {

	/** Without self-crafts (the shape before M19a). */
	public VillageState(HeartLocation heart, UUID owner, String ownerName, long claimedAtGameTime, Map<UUID, Rank> ranks,
			List<KnownPlayer> known, int treasury, Tier tier, List<BuildingRecord> buildings, List<WorkerRecord> workers,
			List<TreeTask> treeTasks, List<BuildJob> builds, List<DefenceSite> defences) {
		this(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, treeTasks, builds, defences, List.of());
	}

	/** Without defence sites (the shape before M21). */
	public VillageState(HeartLocation heart, UUID owner, String ownerName, long claimedAtGameTime, Map<UUID, Rank> ranks,
			List<KnownPlayer> known, int treasury, Tier tier, List<BuildingRecord> buildings, List<WorkerRecord> workers,
			List<TreeTask> treeTasks, List<BuildJob> builds) {
		this(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, treeTasks, builds, List.of());
	}

	/** Without tree tasks and builds (the shape before M9). */
	public VillageState(HeartLocation heart, UUID owner, String ownerName, long claimedAtGameTime, Map<UUID, Rank> ranks,
			List<KnownPlayer> known, int treasury, Tier tier, List<BuildingRecord> buildings, List<WorkerRecord> workers) {
		this(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, List.of(), List.of(), List.of());
	}

	/** Without builds (the shape before M20). */
	public VillageState(HeartLocation heart, UUID owner, String ownerName, long claimedAtGameTime, Map<UUID, Rank> ranks,
			List<KnownPlayer> known, int treasury, Tier tier, List<BuildingRecord> buildings, List<WorkerRecord> workers,
			List<TreeTask> treeTasks) {
		this(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, treeTasks, List.of(), List.of());
	}

	public VillageState {
		Objects.requireNonNull(heart, "heart");
		Objects.requireNonNull(owner, "owner");
		Objects.requireNonNull(ownerName, "ownerName");
		Objects.requireNonNull(ranks, "ranks");
		Objects.requireNonNull(known, "known");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(buildings, "buildings");
		Objects.requireNonNull(workers, "workers");
		Objects.requireNonNull(treeTasks, "treeTasks");
		Objects.requireNonNull(builds, "builds");
		Objects.requireNonNull(defences, "defences");
		if (treasury < 0) throw new IllegalArgumentException("treasury must be >= 0: " + treasury);
		ranks = Map.copyOf(ranks);
		known = sortedKnown(known);
		buildings = sortedBuildings(buildings);
		workers = List.copyOf(workers);
		treeTasks = List.copyOf(treeTasks);
		builds = List.copyOf(builds);
		defences = List.copyOf(defences);
		Objects.requireNonNull(selfCrafts, "selfCrafts");
		selfCrafts = List.copyOf(selfCrafts);
		Set<UUID> seenCrafters = new HashSet<>();
		for (dev.villageheart.core.gear.SelfCraft c : selfCrafts) {
			if (!seenCrafters.add(c.worker())) throw new IllegalArgumentException("two self-crafts for worker " + c.worker());
		}
		Set<UUID> seenDefences = new HashSet<>();
		for (DefenceSite d : defences) {
			if (!seenDefences.add(d.id())) throw new IllegalArgumentException("two defence sites share the id " + d.id());
		}
		if (defences.size() > Balance.MAX_DEFENCE_SITES) throw new IllegalArgumentException("too many defence sites: " + defences.size());
		Set<UUID> seenBuilds = new HashSet<>();
		for (BuildJob b : builds) {
			if (!seenBuilds.add(b.id())) throw new IllegalArgumentException("two builds share the id " + b.id());
		}
		Set<HeartLocation> seenCells = new HashSet<>();
		for (TreeTask t : treeTasks) {
			for (HeartLocation c : t.base()) {
				if (!seenCells.add(c)) throw new IllegalArgumentException("two tree tasks share the cell " + c);
			}
		}
		Set<UUID> seenVillagers = new HashSet<>();
		Set<HeartLocation> seenBlocks = new HashSet<>();
		for (WorkerRecord w : workers) {
			if (!seenVillagers.add(w.villager())) throw new IllegalArgumentException("two records for villager " + w.villager());
			if (!seenBlocks.add(w.jobBlock())) throw new IllegalArgumentException("two workers on job block " + w.jobBlock());
		}
		if (ranks.containsKey(owner)) throw new IllegalArgumentException("owner must not appear in ranks");
		for (Rank r : ranks.values()) {
			if (r == Rank.OWNER || r == Rank.VISITOR) throw new IllegalArgumentException("ranks may not hold " + r);
		}
		if (ranks.size() > Balance.MAX_KNOWN_PLAYERS - 1) throw new IllegalArgumentException("too many ranked players: " + ranks.size());
		Set<UUID> knownIds = new HashSet<>();
		for (KnownPlayer k : known) {
			if (k.id().equals(owner)) throw new IllegalArgumentException("owner must not appear in known");
			if (!knownIds.add(k.id())) throw new IllegalArgumentException("two known entries for " + k.id());
		}
		if (known.size() > Balance.MAX_KNOWN_PLAYERS) throw new IllegalArgumentException("too many known players: " + known.size());
		for (UUID ranked : ranks.keySet()) {
			if (!knownIds.contains(ranked)) throw new IllegalArgumentException("ranked player without a known entry: " + ranked);
		}
	}

	private static final Comparator<HeartLocation> LOCATION_ORDER = Comparator.comparing(HeartLocation::dimension)
			.thenComparingInt(HeartLocation::x).thenComparingInt(HeartLocation::y).thenComparingInt(HeartLocation::z);

	private static final Comparator<KnownPlayer> KNOWN_ORDER = Comparator.comparingLong(KnownPlayer::lastSeen)
			.thenComparing(KnownPlayer::id);

	/** Order of buildings and of the round-robin: (dimension, x, y, z). */
	public static Comparator<HeartLocation> locationOrder() { return LOCATION_ORDER; }

	private static List<KnownPlayer> sortedKnown(List<KnownPlayer> in) {
		List<KnownPlayer> sorted = new ArrayList<>(in);
		sorted.sort(KNOWN_ORDER);
		return List.copyOf(sorted);
	}

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
		return new VillageState(heart, owner, ownerName, gameTime, Map.of(), List.of(), 0, Tier.HAMLET, List.of(), List.of(), List.of(), List.of());
	}

	// One place that rebuilds the record: later milestones add a component here and nowhere else.
	private VillageState copy(UUID newOwner, String newOwnerName, Map<UUID, Rank> newRanks, List<KnownPlayer> newKnown,
			int newTreasury, Tier newTier, List<BuildingRecord> newBuildings, List<WorkerRecord> newWorkers) {
		return new VillageState(heart, newOwner, newOwnerName, claimedAtGameTime, newRanks, newKnown, newTreasury, newTier,
				newBuildings, newWorkers, treeTasks, builds, defences, selfCrafts);
	}

	private VillageState withRanksAndKnown(Map<UUID, Rank> newRanks, List<KnownPlayer> newKnown) {
		return copy(owner, ownerName, newRanks, newKnown, treasury, tier, buildings, workers);
	}

	public Rank rankOf(UUID player) {
		if (owner.equals(player)) return Rank.OWNER;
		return ranks.getOrDefault(player, Rank.VISITOR);
	}

	public Optional<KnownPlayer> knownPlayer(UUID id) {
		for (KnownPlayer k : known) if (k.id().equals(id)) return Optional.of(k);
		return Optional.empty();
	}

	/** The last known name: the owner's {@code ownerName}, else the name book, else {@code ""} (the glue shows the UUID prefix). */
	public String name(UUID id) {
		if (owner.equals(id)) return ownerName;
		return knownPlayer(id).map(KnownPlayer::name).orElse("");
	}

	/** Record that {@code id} was seen as {@code name} at {@code now}. A blank name keeps the stored one. */
	public VillageState seen(UUID id, String name, long now) {
		Objects.requireNonNull(id, "id");
		Objects.requireNonNull(name, "name");
		String clean = KnownPlayer.cleanName(name);
		if (owner.equals(id)) {
			if (clean.isEmpty() || clean.equals(ownerName)) return this;
			return copy(owner, clean, ranks, known, treasury, tier, buildings, workers);
		}
		List<KnownPlayer> next = new ArrayList<>(known);
		Optional<KnownPlayer> existing = knownPlayer(id);
		if (existing.isPresent()) {
			String keep = clean.isEmpty() ? existing.get().name() : clean;
			next.remove(existing.get());
			next.add(new KnownPlayer(id, keep, now));
			return withRanksAndKnown(ranks, next);
		}
		if (next.size() >= Balance.MAX_KNOWN_PLAYERS && !evictOldestUnranked(next)) return this;
		next.add(new KnownPlayer(id, clean, now));
		return withRanksAndKnown(ranks, next);
	}

	/** Remove the unranked entry with the oldest lastSeen; false when every entry is ranked. */
	private boolean evictOldestUnranked(List<KnownPlayer> list) {
		KnownPlayer oldest = null;
		for (KnownPlayer k : list) {
			if (ranks.containsKey(k.id())) continue;
			if (oldest == null || KNOWN_ORDER.compare(k, oldest) < 0) oldest = k;
		}
		if (oldest == null) return false;
		list.remove(oldest);
		return true;
	}

	/**
	 * Set a rank. VISITOR removes the rank but keeps the name. A player without a name-book entry gets a placeholder
	 * entry (name {@code ""}, lastSeen 0); prefer {@link #withRank(UUID, String, Rank, long)}, which records the name.
	 */
	public VillageState withRank(UUID player, Rank rank) {
		Objects.requireNonNull(player, "player");
		Objects.requireNonNull(rank, "rank");
		if (rank == Rank.OWNER) throw new IllegalArgumentException("cannot grant OWNER");
		if (owner.equals(player)) throw new IllegalArgumentException("cannot change the owner's rank");
		Map<UUID, Rank> copy = new HashMap<>(ranks);
		if (rank == Rank.VISITOR) {
			copy.remove(player);
			return withRanksAndKnown(copy, known);
		}
		if (!ranks.containsKey(player) && ranks.size() >= Balance.MAX_KNOWN_PLAYERS - 1) {
			throw new IllegalArgumentException("too many ranked players");
		}
		copy.put(player, rank);
		List<KnownPlayer> nextKnown = new ArrayList<>(known);
		if (knownPlayer(player).isEmpty()) {
			if (nextKnown.size() >= Balance.MAX_KNOWN_PLAYERS) evictOldestUnranked(nextKnown);
			nextKnown.add(new KnownPlayer(player, "", 0));
		}
		return withRanksAndKnown(copy, nextKnown);
	}

	/** {@code seen(...)} then {@code withRank(...)}: what the glue uses. */
	public VillageState withRank(UUID player, String name, Rank rank, long now) {
		if (owner.equals(player)) throw new IllegalArgumentException("cannot change the owner's rank");
		return seen(player, name, now).withRank(player, rank);
	}

	/**
	 * Give the village to a Steward or Resident. The old owner becomes a Steward; the new owner leaves the rank map and
	 * the name book. Everything else (claim time, treasury, buildings, workers) is unchanged.
	 */
	public VillageState transferOwnership(UUID newOwner, long now) {
		Objects.requireNonNull(newOwner, "newOwner");
		Rank current = rankOf(newOwner);
		if (current != Rank.STEWARD && current != Rank.RESIDENT) {
			throw new IllegalArgumentException("can only transfer to a Steward or Resident, not " + current);
		}
		String newOwnerName = name(newOwner);
		Map<UUID, Rank> nextRanks = new HashMap<>(ranks);
		nextRanks.remove(newOwner);
		nextRanks.put(owner, Rank.STEWARD);
		List<KnownPlayer> nextKnown = new ArrayList<>(known);
		nextKnown.removeIf(k -> k.id().equals(newOwner));
		nextKnown.add(new KnownPlayer(owner, ownerName, now));
		return copy(newOwner, newOwnerName, nextRanks, nextKnown, treasury, tier, buildings, workers);
	}

	public VillageState depositTreasury(int emeralds) {
		if (emeralds < 0) throw new IllegalArgumentException("deposit must be >= 0: " + emeralds);
		return copy(owner, ownerName, ranks, known, Math.addExact(treasury, emeralds), tier, buildings, workers);
	}

	/** Takes {@code emeralds} out of the treasury; throws when {@code emeralds} is negative or more than the treasury holds. */
	public VillageState withdrawTreasury(int emeralds) {
		if (emeralds < 0 || emeralds > treasury) throw new IllegalArgumentException("withdraw must be in 0.." + treasury + ": " + emeralds);
		return copy(owner, ownerName, ranks, known, treasury - emeralds, tier, buildings, workers);
	}

	/** The treasury set to a value (an admin write or a wage run). */
	public VillageState withTreasury(int emeralds) {
		if (emeralds < 0) throw new IllegalArgumentException("treasury must be >= 0: " + emeralds);
		return copy(owner, ownerName, ranks, known, emeralds, tier, buildings, workers);
	}

	/** The mirror level of one worker, keeping hiring order; a no-op when there is no such worker or the level is the same. */
	public VillageState withWorkerLevel(UUID villager, int level) {
		Optional<WorkerRecord> rec = worker(villager);
		if (rec.isEmpty() || rec.get().level() == level) return this;
		List<WorkerRecord> next = new ArrayList<>(workers);
		next.replaceAll(w -> w.villager().equals(villager) ? w.withLevel(level) : w);
		return copy(owner, ownerName, ranks, known, treasury, tier, buildings, next);
	}

	public VillageState withTier(Tier t) {
		Objects.requireNonNull(t, "tier");
		return copy(owner, ownerName, ranks, known, treasury, t, buildings, workers);
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
		return copy(owner, ownerName, ranks, known, treasury, tier, copy, workers);
	}

	/** Remove the record at {@code marker}; a no-op when there is none. */
	public VillageState withoutBuilding(HeartLocation marker) {
		if (building(marker).isEmpty()) return this;
		List<BuildingRecord> copy = new ArrayList<>(buildings);
		copy.removeIf(o -> o.marker().equals(marker));
		return copy(owner, ownerName, ranks, known, treasury, tier, copy, workers);
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
		return copy(owner, ownerName, ranks, known, treasury, tier, buildings, copy);
	}

	/** Remove the record of {@code villager}; a no-op when there is none. */
	public VillageState withoutWorker(UUID villager) {
		if (worker(villager).isEmpty()) return this;
		List<WorkerRecord> copy = new ArrayList<>(workers);
		copy.removeIf(o -> o.villager().equals(villager));
		return copy(owner, ownerName, ranks, known, treasury, tier, buildings, copy);
	}

	public List<WorkerRecord> workersAt(HeartLocation building) {
		return workers.stream().filter(w -> w.building().equals(building)).toList();
	}

	// ---- tree tasks (M9) ----

	/** Append a tree task. Throws when it shares a base cell with another task. */
	public VillageState withTreeTask(TreeTask t) {
		Objects.requireNonNull(t, "task");
		List<TreeTask> next = new ArrayList<>(treeTasks);
		next.add(t);
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, next, builds, defences, selfCrafts);
	}

	/** Remove the task whose first base cell is {@code key}; a no-op when there is none. */
	public VillageState withoutTreeTask(HeartLocation key) {
		if (treeTask(key).isEmpty()) return this;
		List<TreeTask> next = new ArrayList<>(treeTasks);
		next.removeIf(t -> t.key().equals(key));
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, next, builds, defences, selfCrafts);
	}

	public Optional<TreeTask> treeTask(HeartLocation key) {
		for (TreeTask t : treeTasks) if (t.key().equals(key)) return Optional.of(t);
		return Optional.empty();
	}

	public List<TreeTask> treeTasksAt(HeartLocation building) {
		return treeTasks.stream().filter(t -> t.building().equals(building)).toList();
	}

	// ---- builds (M20) ----

	public Optional<BuildJob> build(UUID id) {
		for (BuildJob b : builds) if (b.id().equals(id)) return Optional.of(b);
		return Optional.empty();
	}

	/** Append a build (list order is the order they were started), or replace the one with the same id in place. */
	public VillageState withBuild(BuildJob b) {
		Objects.requireNonNull(b, "build");
		List<BuildJob> next = new ArrayList<>(builds);
		boolean replaced = false;
		for (int i = 0; i < next.size(); i++) {
			if (next.get(i).id().equals(b.id())) {
				next.set(i, b);
				replaced = true;
			}
		}
		if (!replaced) next.add(b);
		return withBuilds(next);
	}

	/** Remove the build; a no-op when there is none. */
	public VillageState withoutBuild(UUID id) {
		if (build(id).isEmpty()) return this;
		List<BuildJob> next = new ArrayList<>(builds);
		next.removeIf(b -> b.id().equals(id));
		return withBuilds(next);
	}

	private VillageState withBuilds(List<BuildJob> next) {
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, treeTasks, next, defences, selfCrafts);
	}

	// ---- defence sites (M21) ----

	public Optional<DefenceSite> defence(UUID id) {
		for (DefenceSite d : defences) if (d.id().equals(id)) return Optional.of(d);
		return Optional.empty();
	}

	/** Append a lamp post or watchtower that was finished. At {@link Balance#MAX_DEFENCE_SITES}, or for a known id, the state is returned unchanged. */
	public VillageState withDefence(DefenceSite d) {
		Objects.requireNonNull(d, "defence");
		if (defences.size() >= Balance.MAX_DEFENCE_SITES || defence(d.id()).isPresent()) return this;
		List<DefenceSite> next = new ArrayList<>(defences);
		next.add(d);
		return withDefences(next);
	}

	/** Remove the site; a no-op when there is none. */
	public VillageState withoutDefence(UUID id) {
		if (defence(id).isEmpty()) return this;
		List<DefenceSite> next = new ArrayList<>(defences);
		next.removeIf(d -> d.id().equals(id));
		return withDefences(next);
	}

	private VillageState withDefences(List<DefenceSite> next) {
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, treeTasks, builds, next, selfCrafts);
	}

	// ---- self-made tools (M19a) --------------------------------------------------------------------------------------------

	public Optional<dev.villageheart.core.gear.SelfCraft> selfCraft(UUID worker) {
		for (dev.villageheart.core.gear.SelfCraft c : selfCrafts) if (c.worker().equals(worker)) return Optional.of(c);
		return Optional.empty();
	}

	/** Replaces the worker's record or adds it (at most one per worker). */
	public VillageState withSelfCraft(dev.villageheart.core.gear.SelfCraft craft) {
		List<dev.villageheart.core.gear.SelfCraft> next = new ArrayList<>(selfCrafts);
		next.removeIf(c -> c.worker().equals(craft.worker()));
		next.add(craft);
		return withSelfCrafts(next);
	}

	public VillageState withoutSelfCraft(UUID worker) {
		List<dev.villageheart.core.gear.SelfCraft> next = new ArrayList<>(selfCrafts);
		if (!next.removeIf(c -> c.worker().equals(worker))) return this;
		return withSelfCrafts(next);
	}

	private VillageState withSelfCrafts(List<dev.villageheart.core.gear.SelfCraft> next) {
		return new VillageState(heart, owner, ownerName, claimedAtGameTime, ranks, known, treasury, tier, buildings, workers, treeTasks, builds, defences, next);
	}

	public boolean isHeartAt(HeartLocation loc) {
		return heart.equals(loc);
	}
}
