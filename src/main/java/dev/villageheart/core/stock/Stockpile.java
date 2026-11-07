package dev.villageheart.core.stock;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * The village's shared pool, as a <b>view</b> over live inventories: it holds no counts of its own, so it can never drift
 * from the chests. Sites are ordered by marker, containers by position, slots by index; "nearest" is the squared distance
 * to a site's anchor with ties broken by that order.
 */
public final class Stockpile<K> {
	private final List<StorageSite<K>> sites;
	private final Reservations<K> reservations;

	public Stockpile(List<StorageSite<K>> sites, Reservations<K> reservations) {
		List<StorageSite<K>> sorted = new ArrayList<>(sites);
		sorted.sort(Comparator.comparing(StorageSite::marker));
		this.sites = List.copyOf(sorted);
		this.reservations = reservations;
	}

	public Stockpile(List<StorageSite<K>> sites) { this(sites, new Reservations<>()); }

	public List<StorageSite<K>> sites() { return sites; }

	// ---- reading ---------------------------------------------------------------------------------

	public long count(K key) {
		long total = 0;
		for (StorageSite<K> s : sites) {
			for (ContainerRef<K> c : s.containers()) {
				Inventory<K> inv = c.inventory();
				for (int i = 0, n = inv.size(); i < n; i++) if (key.equals(inv.key(i))) total += inv.count(i);
			}
		}
		return total;
	}

	public long count(Predicate<? super K> match) {
		long total = 0;
		for (StorageSite<K> s : sites) {
			for (ContainerRef<K> c : s.containers()) {
				Inventory<K> inv = c.inventory();
				for (int i = 0, n = inv.size(); i < n; i++) {
					K k = inv.key(i);
					if (k != null && match.test(k)) total += inv.count(i);
				}
			}
		}
		return total;
	}

	/** What a holder may take: the stock minus what others reserved (never negative). */
	public long available(K key, @Nullable UUID holder) {
		int others = reservations.reserved(key) - (holder == null ? 0 : reservations.reservedBy(holder, key));
		return Math.max(0, count(key) - others);
	}

	public boolean hasSpaceFor(K key) {
		for (StorageSite<K> s : sites) if (hasSpace(s, key)) return true;
		return false;
	}

	public Optional<StorageSite<K>> nearestWithSpace(Pos from, K key) {
		StorageSite<K> best = null;
		long bestD = Long.MAX_VALUE;
		for (StorageSite<K> s : sites) {
			long d = dist2(from, s.anchor());
			if (d < bestD && hasSpace(s, key)) {
				best = s;
				bestD = d;
			}
		}
		return Optional.ofNullable(best);
	}

	// ---- deposit ---------------------------------------------------------------------------------

	/** Stores up to {@code n}, nearest site first, spilling into the next. Returns how many were stored. */
	public int deposit(Pos from, K key, int n) {
		checkAmount(n);
		if (n == 0) return 0;
		Set<Inventory<K>> touched = newTouched();
		int left = n;
		for (StorageSite<K> s : byDistance(from)) {
			if (left == 0) break;
			left -= fill(s, key, left, touched);
		}
		finish(touched);
		return n - left;
	}

	/** Same, into one site only. */
	public int depositInto(StorageSite<K> site, K key, int n) {
		checkAmount(n);
		if (n == 0) return 0;
		Set<Inventory<K>> touched = newTouched();
		int stored = fill(site, key, n, touched);
		finish(touched);
		return stored;
	}

	// ---- withdraw --------------------------------------------------------------------------------

	/** Takes up to {@code n}, nearest first; never takes what others reserved. Returns how many were taken. */
	public int withdraw(@Nullable Pos from, K key, int n, @Nullable UUID holder) {
		checkAmount(n);
		if (n == 0) return 0;
		long allowed = available(key, holder);
		int want = (int) Math.min(n, allowed);
		if (want == 0) return 0;
		Set<Inventory<K>> touched = newTouched();
		int left = want;
		for (StorageSite<K> s : from == null ? sites : byDistance(from)) {
			if (left == 0) break;
			left -= take(s, key, left, touched);
		}
		finish(touched);
		int taken = want - left;
		if (holder != null && taken > 0) reservations.consume(holder, key, taken);
		return taken;
	}

	public record Taken<K>(K key, int count) {}

	/** Takes up to {@code n} of the first matching key (in withdraw order) that can be taken at all. */
	public Optional<Taken<K>> withdrawFirst(@Nullable Pos from, Predicate<? super K> match, int n, @Nullable UUID holder) {
		checkAmount(n);
		if (n == 0) return Optional.empty();
		Set<K> tried = new LinkedHashSet<>();
		for (StorageSite<K> s : from == null ? sites : byDistance(from)) {
			for (ContainerRef<K> c : s.containers()) {
				Inventory<K> inv = c.inventory();
				for (int i = 0, size = inv.size(); i < size; i++) {
					K k = inv.key(i);
					if (k == null || !match.test(k) || !tried.add(k)) continue;
					int taken = withdraw(from, k, n, holder);
					if (taken > 0) return Optional.of(new Taken<>(k, taken));
				}
			}
		}
		return Optional.empty();
	}

	// ---- snapshot --------------------------------------------------------------------------------

	/** {@code unknownSites} and {@code unreadContainers} come from the glue (only it knows about scans and chunks). */
	public StockSnapshot<K> snapshot(long gameTime, int unknownSites, int unreadContainers, StorageAlert alert) {
		Map<K, long[]> totals = new LinkedHashMap<>();
		int siteCount = 0, containers = 0, slots = 0, free = 0;
		for (StorageSite<K> s : sites) {
			if (!s.containers().isEmpty()) siteCount++;
			for (ContainerRef<K> c : s.containers()) {
				Inventory<K> inv = c.inventory();
				containers++;
				for (int i = 0, n = inv.size(); i < n; i++) {
					slots++;
					K k = inv.key(i);
					int cnt = k == null ? 0 : inv.count(i);
					if (k == null || cnt <= 0) {
						free++;
						continue;
					}
					long[] t = totals.get(k);
					if (t == null) totals.put(k, new long[] {cnt}); else t[0] += cnt;
				}
			}
		}
		List<StockSnapshot.Total<K>> list = new ArrayList<>(totals.size());
		for (Map.Entry<K, long[]> e : totals.entrySet()) list.add(new StockSnapshot.Total<>(e.getKey(), e.getValue()[0]));
		list.sort(Comparator.comparingLong((StockSnapshot.Total<K> t) -> t.count()).reversed()); // stable: ties keep first-seen order
		return new StockSnapshot<>(gameTime, list, siteCount, unknownSites, containers, unreadContainers, slots, free, alert);
	}

	// ---- internals -------------------------------------------------------------------------------

	private static void checkAmount(int n) {
		if (n < 0) throw new IllegalArgumentException("amount must be >= 0: " + n);
	}

	private static long dist2(Pos a, Pos b) {
		long dx = a.x() - b.x(), dy = a.y() - b.y(), dz = a.z() - b.z();
		return dx * dx + dy * dy + dz * dz;
	}

	private List<StorageSite<K>> byDistance(Pos from) {
		List<StorageSite<K>> out = new ArrayList<>(sites);
		out.sort(Comparator.comparingLong(s -> dist2(from, s.anchor()))); // stable: ties keep site order
		return out;
	}

	private Set<Inventory<K>> newTouched() { return Collections.newSetFromMap(new IdentityHashMap<>()); }

	private void finish(Set<Inventory<K>> touched) {
		for (Inventory<K> inv : touched) inv.changed();
	}

	private boolean hasSpace(StorageSite<K> s, K key) {
		for (ContainerRef<K> c : s.containers()) {
			Inventory<K> inv = c.inventory();
			for (int i = 0, n = inv.size(); i < n; i++) {
				K k = inv.key(i);
				if (k == null ? inv.limit(i, key) > 0 : k.equals(key) && inv.count(i) < inv.limit(i, key)) return true;
			}
		}
		return false;
	}

	/** Partial stacks of {@code key} first (container, slot order), then empty slots in the same order. */
	private int fill(StorageSite<K> s, K key, int n, Set<Inventory<K>> touched) {
		int left = n;
		for (int pass = 0; pass < 2 && left > 0; pass++) {
			for (ContainerRef<K> c : s.containers()) {
				Inventory<K> inv = c.inventory();
				for (int i = 0, size = inv.size(); i < size && left > 0; i++) {
					K k = inv.key(i);
					boolean fits = pass == 0 ? k != null && k.equals(key) : k == null;
					if (!fits) continue;
					int room = inv.limit(i, key) - (k == null ? 0 : inv.count(i));
					if (room <= 0) continue;
					int put = Math.min(room, left);
					inv.add(i, key, put);
					touched.add(inv);
					left -= put;
				}
			}
		}
		return n - left;
	}

	private int take(StorageSite<K> s, K key, int n, Set<Inventory<K>> touched) {
		int left = n;
		for (ContainerRef<K> c : s.containers()) {
			Inventory<K> inv = c.inventory();
			for (int i = 0, size = inv.size(); i < size && left > 0; i++) {
				if (!key.equals(inv.key(i))) continue;
				int took = Math.min(inv.count(i), left);
				if (took <= 0) continue;
				inv.remove(i, took);
				touched.add(inv);
				left -= took;
			}
		}
		return n - left;
	}
}
