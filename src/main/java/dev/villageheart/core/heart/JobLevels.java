package dev.villageheart.core.heart;

import dev.villageheart.core.Balance;

/** Display side of worker levels (DESIGN section 4). M15 owns gaining XP. */
public final class JobLevels {
	private JobLevels() {}

	/** Total XP at which {@code level} turns into the next one; 0 at the top level. */
	public static int xpForNext(int level) {
		if (level < 1) throw new IllegalArgumentException("level must be >= 1: " + level);
		return switch (level) {
			case 1 -> Balance.LEVEL_2_XP;
			case 2 -> Balance.LEVEL_3_XP;
			default -> 0;
		};
	}
}
