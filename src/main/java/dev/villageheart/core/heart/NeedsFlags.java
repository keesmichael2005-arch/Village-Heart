package dev.villageheart.core.heart;

/** A worker's needs as shown by icons. {@code known} is false for villagers without a job (icons hidden). */
public record NeedsFlags(boolean known, boolean fed, boolean housed, boolean paid) {
	public static final NeedsFlags UNKNOWN = new NeedsFlags(false, false, false, false);

	public static NeedsFlags of(boolean fed, boolean housed, boolean paid) { return new NeedsFlags(true, fed, housed, paid); }
}
