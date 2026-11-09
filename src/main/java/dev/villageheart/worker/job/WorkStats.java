package dev.villageheart.worker.job;

import dev.villageheart.core.job.WorkCounters;
import java.util.Map;

/** The server's work counters ({@code work.<job>.<counter>} in the L3 metrics). Server thread only. */
public final class WorkStats {
	private static final WorkCounters COUNTERS = new WorkCounters();

	private WorkStats() {}

	public static void add(String job, String counter, long n) { COUNTERS.add(job, counter, n); }

	public static void max(String job, String counter, long n) { COUNTERS.max(job, counter, n); }

	public static long get(String job, String counter) { return COUNTERS.get(job, counter); }

	/** Counts since the last call, then zero. The L3 collector calls this once per simulated day. */
	public static Map<String, Long> snapshotAndReset() { return COUNTERS.snapshotAndReset(); }

	/** Counts so far without resetting. */
	public static Map<String, Long> snapshot() { return COUNTERS.snapshot(); }
}
