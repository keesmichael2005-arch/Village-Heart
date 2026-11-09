package dev.villageheart.core.job;

import java.util.Map;
import java.util.TreeMap;

/** Per-day work counters for the L3 metrics: key {@code <job>.<counter>}. */
public final class WorkCounters {
	private final Map<String, Long> counts = new TreeMap<>();

	public void add(String job, String counter, long n) {
		counts.merge(job + "." + counter, n, Long::sum);
	}

	public long get(String job, String counter) { return counts.getOrDefault(job + "." + counter, 0L); }

	/** The counters since the last call; the counters restart at zero. */
	public Map<String, Long> snapshotAndReset() {
		Map<String, Long> out = new TreeMap<>(counts);
		counts.clear();
		return out;
	}

	/** The counters without resetting them. */
	public Map<String, Long> snapshot() { return new TreeMap<>(counts); }
}
