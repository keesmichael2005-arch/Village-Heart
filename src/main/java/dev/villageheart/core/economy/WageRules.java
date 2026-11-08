package dev.villageheart.core.economy;

import dev.villageheart.core.Balance;
import java.util.List;

/** What a worker costs (DESIGN section 6): 1 / 1 / 2 emeralds a day at level 1 / 2 / 3. */
public final class WageRules {
	private WageRules() {}

	public static int wage(int level) { return Balance.wageForLevel(level); }

	/** The sum of the wages of workers of these levels. Throws on overflow. */
	public static int bill(List<Integer> levels) {
		int sum = 0;
		for (int level : levels) sum = Math.addExact(sum, wage(level));
		return sum;
	}
}
