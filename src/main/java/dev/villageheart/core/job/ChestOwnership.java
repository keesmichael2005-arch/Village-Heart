package dev.villageheart.core.job;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/**
 * Which chest belongs to which job block. One chest per worker: the nearest chest nobody else owns. A double chest (two halves that
 * name each other) counts once. A Barracks is also limited by its beds.
 */
public final class ChestOwnership {
	private ChestOwnership() {}

	/** A block position inside one room. Order is x, then y, then z. */
	public record Spot(int x, int y, int z) implements Comparable<Spot> {
		@Override
		public int compareTo(Spot o) {
			int c = Integer.compare(x, o.x);
			if (c != 0) return c;
			c = Integer.compare(y, o.y);
			return c != 0 ? c : Integer.compare(z, o.z);
		}
	}

	/**
	 * One chest. {@code partner} is the other half of a double chest, or null. Two entries that name each other are one chest, identified
	 * by the lower position.
	 */
	public record Chest(Spot at, @Nullable Spot partner) {
		public Chest {
			if (partner != null && partner.equals(at)) partner = null;
		}

		public Spot key() {
			if (partner == null || at.compareTo(partner) <= 0) return at;
			return partner;
		}

		long near(Spot job) {
			long d = dist(job, at);
			if (partner != null) d = Math.min(d, dist(job, partner));
			return d;
		}
	}

	/** A worker and the chest they currently claim, or null when an old save never stored one. */
	public record Held(UUID id, Spot jobBlock, @Nullable Spot chest) {}

	/** {@code beds} is null for every building except a Barracks. */
	public static int slots(int jobBlocks, int chests, @Nullable Integer beds) {
		int n = Math.min(Math.max(jobBlocks, 0), Math.max(chests, 0));
		if (beds != null) n = Math.min(n, Math.max(beds, 0));
		return n;
	}

	/** One entry per chest. A double chest contributes its lower half. */
	public static List<Chest> distinct(List<Chest> chests) {
		Map<Spot, Chest> byKey = new LinkedHashMap<>();
		for (Chest c : chests) byKey.putIfAbsent(c.key(), c.key().equals(c.at()) ? c : new Chest(c.key(), c.at()));
		return List.copyOf(byKey.values());
	}

	/** The nearest chest whose key is not taken. Equal distance: the lower key wins. */
	public static Optional<Chest> nearestFree(Spot job, List<Chest> chests, Set<Spot> taken) {
		Chest best = null;
		for (Chest c : distinct(chests)) {
			if (taken.contains(c.key())) continue;
			if (best == null || better(job, c, best)) best = c;
		}
		return Optional.ofNullable(best);
	}

	/**
	 * Each job block, nearer to a remaining chest first, takes the nearest free chest. A room with more job blocks than chests pairs only
	 * as many as there are chests.
	 */
	public static Map<Spot, Spot> pair(List<Spot> jobBlocks, List<Chest> chests, Set<Spot> alreadyOwned) {
		Set<Spot> taken = new java.util.HashSet<>(alreadyOwned);
		Map<Spot, Spot> out = new LinkedHashMap<>();
		while (out.size() < jobBlocks.size()) {
			Spot bestJob = null;
			Chest bestChest = null;
			long bestDist = Long.MAX_VALUE;
			for (Spot job : jobBlocks) {
				if (out.containsKey(job)) continue;
				Optional<Chest> n = nearestFree(job, chests, taken);
				if (n.isEmpty()) continue;
				long d = n.get().near(job);
				if (bestJob == null || d < bestDist || (d == bestDist && job.compareTo(bestJob) < 0)) {
					bestJob = job;
					bestChest = n.get();
					bestDist = d;
				}
			}
			if (bestJob == null) break;
			out.put(bestJob, bestChest.key());
			taken.add(bestChest.key());
		}
		return Map.copyOf(out);
	}

	/**
	 * An old save. A chest claimed by several workers stays with the nearest job block. A saved chest that is not in the room is kept
	 * (the block is gone). Workers with no chest take the nearest chest still free.
	 *
	 * @return villager to the chest they own, or null when they are waiting on a chest
	 */
	public static Map<UUID, Spot> resolve(List<Held> workers, List<Chest> roomChests) {
		Set<Spot> room = new java.util.HashSet<>();
		for (Chest c : distinct(roomChests)) room.add(c.key());
		Map<UUID, Spot> out = new LinkedHashMap<>();
		Map<Spot, List<Held>> sharing = new LinkedHashMap<>();
		List<Held> open = new ArrayList<>();
		for (Held h : workers) {
			if (h.chest() != null && !room.contains(h.chest())) {
				out.put(h.id(), h.chest());
				continue;
			}
			if (h.chest() != null) sharing.computeIfAbsent(h.chest(), k -> new ArrayList<>()).add(h);
			else open.add(h);
		}
		Set<Spot> taken = new java.util.HashSet<>();
		for (var e : sharing.entrySet()) {
			List<Held> claim = new ArrayList<>(e.getValue());
			claim.sort(Comparator.comparingLong((Held h) -> dist(h.jobBlock(), e.getKey())).thenComparing(h -> h.jobBlock()));
			out.put(claim.get(0).id(), e.getKey());
			taken.add(e.getKey());
			for (int i = 1; i < claim.size(); i++) open.add(new Held(claim.get(i).id(), claim.get(i).jobBlock(), null));
		}
		open.sort(Comparator.comparing(Held::jobBlock));
		for (Held h : open) {
			Optional<Chest> n = nearestFree(h.jobBlock(), roomChests, taken);
			if (n.isEmpty()) out.put(h.id(), null);
			else {
				out.put(h.id(), n.get().key());
				taken.add(n.get().key());
			}
		}
		return out;
	}

	private static boolean better(Spot job, Chest c, Chest best) {
		long d = c.near(job), b = best.near(job);
		if (d != b) return d < b;
		return c.key().compareTo(best.key()) < 0;
	}

	private static long dist(Spot a, Spot b) {
		long dx = (long) a.x() - b.x(), dy = (long) a.y() - b.y(), dz = (long) a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}
}
