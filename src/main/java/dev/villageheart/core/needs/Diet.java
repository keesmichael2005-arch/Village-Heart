package dev.villageheart.core.needs;

import java.util.Optional;

/**
 * A villager's hunger (M11 plan 3.2 #5), kept on every villager. {@code claim} identifies the claim it was written under (the claim's
 * game time): after the Heart is broken and placed again the old diets read as fresh.
 */
public record Diet(long claim, int hungryDays, long lastDinnerDay, int lastNutrition) {
	public Diet {
		if (hungryDays < 0) throw new IllegalArgumentException("hungryDays must be >= 0: " + hungryDays);
		if (lastNutrition < 0) throw new IllegalArgumentException("lastNutrition must be >= 0: " + lastNutrition);
	}

	public static Diet fresh(long claim) { return new Diet(claim, 0, -1, 0); }

	public boolean fed() { return hungryDays == 0; }

	public Diet afterDinner(boolean fed, int nutrition, long day) {
		return new Diet(claim, fed ? 0 : hungryDays + 1, day, Math.max(0, nutrition));
	}

	/** A missing diet, or one written under another claim, is fresh (fed). */
	public static Diet read(Optional<Diet> stored, long claim) {
		return stored.filter(d -> d.claim() == claim).orElseGet(() -> fresh(claim));
	}
}
