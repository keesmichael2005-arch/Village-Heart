package dev.villageheart.core.lumber;

import dev.villageheart.core.Balance;
import dev.villageheart.core.tree.Species;

/** Pure rules about replanting and about the saplings a lumberjack keeps. */
public final class ReplantRules {
	private ReplantRules() {}

	/** The sapling item that regrows the species. */
	public static String saplingFor(Species species) { return species.saplingId(); }

	/** One sapling per base cell: 1 for an ordinary tree, 4 for a 2x2 trunk (dark oak, pale oak, mega spruce and jungle). */
	public static int saplingsNeeded(Species species, int baseCells) {
		if (baseCells < 1) throw new IllegalArgumentException("a tree has at least one base cell");
		return species.needsTwoByTwo() ? Math.max(baseCells, 4) : baseCells;
	}

	/** After this long without the right sapling any sapling that can grow on a single cell is planted (never the 2x2 species). */
	public static boolean fallbackAllowed(long pendingTicks, Species species) {
		return pendingTicks >= Balance.REPLANT_FALLBACK_TICKS && !species.needsTwoByTwo();
	}

	/** A spot nobody planted for this many days is dropped. */
	public static boolean expired(long since, long now) {
		return now - since >= (long) Balance.REPLANT_EXPIRY_DAYS * Balance.DAY_TICKS;
	}

	/** How many of an item the worker keeps (and does not deposit): what open spots need plus the reserve, at most what it has. */
	public static int keepCount(int have, int neededByOpenSpots) {
		return Math.min(have, Math.max(0, neededByOpenSpots) + Balance.SAPLING_RESERVE);
	}
}
