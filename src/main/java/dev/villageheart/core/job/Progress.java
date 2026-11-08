package dev.villageheart.core.job;

/** A worker's level and total XP (cumulative, banked up to {@code Balance.MAX_BANKED_XP}). */
public record Progress(int level, int xp) {
	public Progress {
		if (level < 1 || level > 3) throw new IllegalArgumentException("level must be 1..3: " + level);
		if (xp < 0) throw new IllegalArgumentException("xp must be >= 0: " + xp);
	}
}
