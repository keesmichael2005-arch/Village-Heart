package dev.villageheart.core.worker;

/** A worker's needs (filled by M11/M12/M15; M7 only stores and round-trips them). */
public record Needs(boolean fed, int hungryDays, boolean housed, boolean paid) {
	public static final Needs DEFAULT = new Needs(true, 0, true, true);

	public Needs {
		if (hungryDays < 0) throw new IllegalArgumentException("hungryDays must be >= 0: " + hungryDays);
	}

	/** Work speed factor of these needs. M9 reads it; M11 fills in the rule (hungry x0.5 / x0.75 / 0). Every need met: 1.0. */
	public double speed() { return 1.0; }
}
