package dev.villageheart.core.worker;

/** A worker's needs (filled by M11/M12/M15; M7 only stores and round-trips them). */
public record Needs(boolean fed, int hungryDays, boolean housed, boolean paid) {
	public static final Needs DEFAULT = new Needs(true, 0, true, true);

	public Needs {
		if (hungryDays < 0) throw new IllegalArgumentException("hungryDays must be >= 0: " + hungryDays);
	}
}
