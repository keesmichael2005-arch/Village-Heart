package dev.villageheart.core.tier;

/** The live facts the goals read. Booleans mean "a valid one exists". */
public record TierInputs(int population, int workers, int guards, boolean validStorage, boolean validFarmhouse, boolean validKitchen) {
	public static final TierInputs EMPTY = new TierInputs(0, 0, 0, false, false, false);

	public TierInputs {
		if (population < 0 || workers < 0 || guards < 0) throw new IllegalArgumentException("counts must be >= 0");
	}
}
