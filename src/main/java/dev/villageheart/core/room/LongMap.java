package dev.villageheart.core.room;

/**
 * Minimal open-addressing long-to-long hash map (also used as a set). It exists so a scan allocates no boxed keys while it
 * fills: the scanner is the hot path of the village tick. Not thread-safe, no removal.
 */
final class LongMap {
	private long[] keys;
	private long[] vals;
	private boolean[] used;
	private int size;
	private int mask;

	LongMap(int expected) {
		int cap = 16;
		while (cap < expected * 2) cap <<= 1;
		alloc(cap);
	}

	private void alloc(int cap) {
		keys = new long[cap];
		vals = new long[cap];
		used = new boolean[cap];
		mask = cap - 1;
	}

	private static int hash(long k) {
		k *= 0x9E3779B97F4A7C15L;
		return (int) (k ^ (k >>> 32));
	}

	private int slot(long key) {
		int i = hash(key) & mask;
		while (used[i] && keys[i] != key) i = (i + 1) & mask;
		return i;
	}

	boolean containsKey(long key) { return used[slot(key)]; }

	/** The value for {@code key}; the key must be present. */
	long get(long key) { return vals[slot(key)]; }

	/** Adds the key; returns false (and leaves the value alone) when it was already there. */
	boolean putIfAbsent(long key, long val) {
		int i = slot(key);
		if (used[i]) return false;
		used[i] = true;
		keys[i] = key;
		vals[i] = val;
		if (++size * 2 > keys.length) grow();
		return true;
	}

	/** Sets the value, adding the key when needed. */
	void put(long key, long val) {
		if (!putIfAbsent(key, val)) vals[slot(key)] = val;
	}

	/** The value, or {@code def} when the key is absent. */
	long getOrDefault(long key, long def) {
		int i = slot(key);
		return used[i] ? vals[i] : def;
	}

	int size() { return size; }

	private void grow() {
		long[] ok = keys, ov = vals;
		boolean[] ou = used;
		alloc(ok.length * 2);
		for (int i = 0; i < ok.length; i++) {
			if (!ou[i]) continue;
			int j = slot(ok[i]);
			used[j] = true;
			keys[j] = ok[i];
			vals[j] = ov[i];
		}
	}

	/** All keys, boxed, for building the immutable result sets. */
	Long[] boxedKeys() {
		Long[] out = new Long[size];
		int n = 0;
		for (int i = 0; i < keys.length; i++) if (used[i]) out[n++] = keys[i];
		return out;
	}

	long minKey() {
		long min = Long.MAX_VALUE;
		for (int i = 0; i < keys.length; i++) if (used[i] && keys[i] < min) min = keys[i];
		return min;
	}

	/** Visit every key (iteration order is unspecified, callers must not depend on it). */
	void forEachKey(java.util.function.LongConsumer c) {
		for (int i = 0; i < keys.length; i++) if (used[i]) c.accept(keys[i]);
	}
}
