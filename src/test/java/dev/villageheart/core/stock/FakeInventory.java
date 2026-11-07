package dev.villageheart.core.stock;

import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Nullable;

/** Test inventory over strings: per-key limits (default 64, "sword" 1, "egg" 16), optional refused keys, call counters. */
final class FakeInventory implements Inventory<String> {
	static final Map<String, Integer> LIMITS = Map.of("sword", 1, "egg", 16);

	final String[] keys;
	final int[] counts;
	final Set<String> refuses;
	int adds, removes, changes;

	FakeInventory() { this(27, Set.of()); }

	FakeInventory(int size, Set<String> refuses) {
		keys = new String[size];
		counts = new int[size];
		this.refuses = refuses;
	}

	FakeInventory set(int slot, String key, int n) {
		keys[slot] = key;
		counts[slot] = n;
		return this;
	}

	/** Fill every slot with {@code key} at its limit. */
	FakeInventory full(String key) {
		for (int i = 0; i < keys.length; i++) set(i, key, LIMITS.getOrDefault(key, 64));
		return this;
	}

	@Override public int size() { return keys.length; }
	@Override public @Nullable String key(int slot) { return counts[slot] == 0 ? null : keys[slot]; }
	@Override public int count(int slot) { return counts[slot]; }
	@Override public int limit(int slot, String key) { return refuses.contains(key) ? 0 : LIMITS.getOrDefault(key, 64); }

	@Override public void add(int slot, String key, int n) {
		if (n <= 0 || (counts[slot] != 0 && !keys[slot].equals(key)) || counts[slot] + n > limit(slot, key)) {
			throw new IllegalStateException("bad add " + n + " " + key + " to slot " + slot);
		}
		keys[slot] = key;
		counts[slot] += n;
		adds++;
	}

	@Override public void remove(int slot, int n) {
		if (n <= 0 || n > counts[slot]) throw new IllegalStateException("bad remove " + n + " from slot " + slot);
		counts[slot] -= n;
		if (counts[slot] == 0) keys[slot] = null;
		removes++;
	}

	@Override public void changed() { changes++; }

	int total(String key) {
		int t = 0;
		for (int i = 0; i < keys.length; i++) if (key.equals(key(i))) t += counts[i];
		return t;
	}

	// ---- builders --------------------------------------------------------------------------------

	static ContainerRef<String> at(int x, FakeInventory inv) { return new ContainerRef<>(new Pos(x, 0, 0), inv); }

	static StorageSite<String> site(int markerX, int anchorX, FakeInventory... invs) {
		List<ContainerRef<String>> cs = new ArrayList<>();
		for (int i = 0; i < invs.length; i++) cs.add(at(markerX * 100 + i, invs[i]));
		return new StorageSite<>(new Pos(markerX, 0, 0), new Pos(anchorX, 0, 0), cs);
	}
}
