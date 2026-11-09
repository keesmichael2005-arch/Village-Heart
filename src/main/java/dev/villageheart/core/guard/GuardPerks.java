package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;

/** What a guard's level gives (M18 3.2 #7): max health 20/30/40, melee damage +0/+1/+2. Level above 3 counts as 3. */
public final class GuardPerks {
	private GuardPerks() {}

	public static double maxHealth(int level) { return Balance.GUARD_LEVEL_HEALTH[index(level)]; }

	public static double bonusDamage(int level) { return Balance.GUARD_LEVEL_DAMAGE[index(level)]; }

	private static int index(int level) {
		if (level < 1) throw new IllegalArgumentException("level " + level);
		return Math.min(level, 3) - 1;
	}
}
