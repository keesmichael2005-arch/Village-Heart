package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;

/**
 * A guard's food ration and what eating does (M18 r3, owner design). The ration is a number of nutrition points taken from the village's food
 * storage; a bite uses one point and heals {@link Balance#GUARD_HEAL_PER_POINT} health, one bite per {@link Balance#GUARD_EAT_TICKS} ticks while
 * the guard is hurt and no hostile is near. There is no other healing: no food, no healing.
 */
public final class GuardRation {
	private GuardRation() {}

	/** The result of one bite: the new ration and the health it gives. */
	public record Bite(int ration, double heal) {}

	public static boolean wantsRefill(int ration) { return ration < Balance.GUARD_RATION_REFILL_BELOW; }

	/** The ration after taking food worth {@code nutrition} points, never above the cap. */
	public static int add(int ration, int nutrition) {
		if (nutrition < 0) throw new IllegalArgumentException("nutrition " + nutrition);
		return Math.min(Balance.GUARD_RATION_CAP, ration + nutrition);
	}

	/** One bite: nothing when the guard is not hurt or has no ration; never heals past the max. */
	public static Bite bite(int ration, double health, double maxHealth) {
		if (ration <= 0 || health >= maxHealth) return new Bite(ration, 0);
		return new Bite(ration - 1, Math.min(Balance.GUARD_HEAL_PER_POINT, maxHealth - health));
	}
}
