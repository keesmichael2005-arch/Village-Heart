package dev.villageheart.core.sim;

import java.util.Arrays;

/**
 * Collects one nanosecond total per server tick. Work is added in sections during the tick and
 * {@link #commitTick()} records the sum as one sample. Server thread only.
 */
public final class TickTimer {
	private long[] samples = new long[1024];
	private int count;
	private long current;

	public void add(long nanos) {
		if (nanos < 0) throw new IllegalArgumentException("nanos must be >= 0: " + nanos);
		current += nanos;
	}

	public void commitTick() {
		if (count == samples.length) samples = Arrays.copyOf(samples, count * 2);
		samples[count++] = current;
		current = 0;
	}

	public TickStats snapshot() {
		if (count == 0) return TickStats.EMPTY;
		long[] sorted = Arrays.copyOf(samples, count);
		Arrays.sort(sorted);
		double sum = 0;
		for (long s : sorted) sum += s;
		int rank = (int) Math.ceil(0.95 * count); // nearest rank, 1-based
		return new TickStats(count, sum / count / 1e6, sorted[rank - 1] / 1e6, sorted[count - 1] / 1e6);
	}

	public TickStats snapshotAndReset() {
		TickStats s = snapshot();
		count = 0;
		current = 0;
		return s;
	}
}
