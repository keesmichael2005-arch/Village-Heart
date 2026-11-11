package dev.villageheart.core.siege;

/** What the slow scan does with one cell (docs/plans/M25.md 3.9). Only a seen cell that is missing can become breached. */
public final class DefenceScan {
	private DefenceScan() {}

	public enum Occupancy { MISSING, HOLDING, OTHER }

	public static boolean breached(boolean seenBefore, boolean opened, Occupancy now) {
		if (opened || now == Occupancy.HOLDING || now == Occupancy.OTHER) return false;
		if (!seenBefore) return false;
		return true;
	}
}
