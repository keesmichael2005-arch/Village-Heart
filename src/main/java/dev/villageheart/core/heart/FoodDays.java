package dev.villageheart.core.heart;

/** How long the food in Storage lasts (DESIGN section 6): an adult eats 10 nutrition a day, a baby half. */
public final class FoodDays {
	private FoodDays() {}

	public static final int NO_VILLAGERS = -1;

	/** Tenths of a day, rounded down, saturating at Integer.MAX_VALUE; -1 when there is nobody to feed. */
	public static int tenths(long nutrition, int adults, int babies) {
		if (adults < 0 || babies < 0 || nutrition < 0) throw new IllegalArgumentException("negative input");
		long demand = adults * 10L + babies * 5L;
		if (demand == 0) return NO_VILLAGERS;
		long n = Math.min(nutrition, Long.MAX_VALUE / 10);
		return (int) Math.min(Integer.MAX_VALUE, n * 10 / demand);
	}
}
