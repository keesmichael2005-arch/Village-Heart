package dev.villageheart.core.smith;

import java.util.Set;

/**
 * What the smith does not hand to Storage mid-job. At the end of the day nothing is kept except a finished tool on its way to a chest
 * and gear on its way back to the Barracks ({@code overnight}). While handing everything back, nothing is kept.
 */
public final class SmithKeep {
	private SmithKeep() {}

	public static int keep(String itemId, int have, Set<String> jobItems, boolean returning, boolean homeTime, boolean overnight) {
		if (have <= 0) return 0;
		if (returning) return 0;
		if (homeTime) return overnight && jobItems.contains(itemId) ? have : 0;
		return jobItems.contains(itemId) ? have : 0;
	}
}
