package dev.villageheart.core.sim;

/** Per-tick cost of the mod's village logic over some period, in milliseconds. */
public record TickStats(long samples, double meanMs, double p95Ms, double maxMs) {
	public static final TickStats EMPTY = new TickStats(0, 0, 0, 0);
}
