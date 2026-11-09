package dev.villageheart.food;

/**
 * Runtime dinner totals for the L3 day record (M11 plan 3.8). Not saved: the harness halts right after a day record, when the counters were
 * just reset, so no dinner is ever in flight across a restart. Server thread only.
 */
public final class FoodStats {
	public record Day(long dinners, long diners, long fed, long hungry, long eatenNutrition, long eatenItems) {
		public static final Day ZERO = new Day(0, 0, 0, 0, 0, 0);
	}

	private static long dinners, diners, fed, hungry, eatenNutrition, eatenItems;

	private FoodStats() {}

	public static void record(int dinersN, int fedN, int hungryN, long nutrition, long items) {
		dinners++;
		diners += dinersN;
		fed += fedN;
		hungry += hungryN;
		eatenNutrition += nutrition;
		eatenItems += items;
	}

	public static Day snapshot() { return new Day(dinners, diners, fed, hungry, eatenNutrition, eatenItems); }

	public static Day snapshotAndReset() {
		Day d = snapshot();
		reset();
		return d;
	}

	public static void reset() { dinners = diners = fed = hungry = eatenNutrition = eatenItems = 0; }
}
