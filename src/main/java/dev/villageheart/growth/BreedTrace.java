package dev.villageheart.growth;

import dev.villageheart.VillageHeart;
import java.util.Map;
import java.util.TreeMap;

/**
 * Why a make-love run ended without a baby (M23 plan 3.5): counts and, behind {@code -Dvillageheart.breed.trace=true}, one log line per run.
 * The counts are what the 100-pair probe reads (steps 1 and 9). Server thread only.
 */
public final class BreedTrace {
	public static final boolean TRACE = Boolean.getBoolean("villageheart.breed.trace");

	/** Why a run ended without a birth, in the order the stop hook tests them. */
	public enum Abort { TOO_FAR, NOT_VISIBLE, CANNOT_BREED, PARTNER_GONE, TIMED_OUT, NO_BED }

	private static final Map<String, Integer> COUNTS = new TreeMap<>();
	private static int births;
	/** Villagers whose make-love run (as the one running the behaviour) has ended, with or without a baby: what the probe waits for. */
	private static final java.util.Set<java.util.UUID> FINISHED = new java.util.HashSet<>();

	private BreedTrace() {}

	public static void reset() {
		COUNTS.clear();
		births = 0;
		FINISHED.clear();
	}

	public static void finish(java.util.UUID initiator) { FINISHED.add(initiator); }

	public static boolean finished(java.util.UUID initiator) { return FINISHED.contains(initiator); }

	public static void birth() { births++; }

	public static int births() { return births; }

	public static void abort(Abort reason, double distance, boolean licensed) {
		COUNTS.merge((licensed ? "licensed " : "plain ") + reason.name(), 1, Integer::sum);
		if (TRACE) VillageHeart.LOG.info("[breed] stop without a birth: {} (distance {}, {})", reason, String.format("%.2f", distance), licensed ? "licensed" : "plain");
	}

	/** Count of aborts of this reason (both kinds, or only the licensed ones). */
	public static int aborts(Abort reason, boolean licensed) { return COUNTS.getOrDefault((licensed ? "licensed " : "plain ") + reason.name(), 0); }

	public static int totalAborts() { return COUNTS.values().stream().mapToInt(Integer::intValue).sum(); }

	public static String summary() { return "births=" + births + " aborts=" + COUNTS; }
}
