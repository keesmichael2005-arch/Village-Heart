package dev.villageheart.core.worker;

import dev.villageheart.core.Balance;

/**
 * A worker's needs, a derived copy written by the needs system (M11): fed, hunger, housing and wages (paid is stubbed true until M12).
 * Invariant: {@code fed == (hungryDays == 0)}.
 */
public record Needs(boolean fed, int hungryDays, boolean housed, boolean paid) {
	public static final Needs DEFAULT = new Needs(true, 0, true, true);

	public Needs {
		if (hungryDays < 0) throw new IllegalArgumentException("hungryDays must be >= 0: " + hungryDays);
		if (fed != (hungryDays == 0)) throw new IllegalArgumentException("fed must equal (hungryDays == 0): fed=" + fed + ", hungryDays=" + hungryDays);
	}

	/** Needs from the number of missed dinners. */
	public static Needs of(int hungryDays, boolean housed, boolean paid) { return new Needs(hungryDays == 0, hungryDays, housed, paid); }

	/** Speed factor of hunger alone: x1 fed, x0.5 after one missed dinner, 0 after two or more. */
	public static double fedFactor(int hungryDays) {
		return Balance.HUNGER_SPEED[Math.max(0, Math.min(hungryDays, Balance.HUNGER_SPEED.length - 1))];
	}

	/** Work speed factor of these needs (DESIGN section 4). M9/M10 read it through WorkSpeed. Every need met: 1.0. */
	public double speed() {
		return fedFactor(hungryDays) * (housed ? 1.0 : Balance.UNHOUSED_SPEED) * (paid ? 1.0 : 0.0);
	}
}
