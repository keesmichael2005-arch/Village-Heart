package dev.villageheart.core.tier;

import dev.villageheart.core.Balance;

/**
 * Progress that is not a live fact (M14): the fed-days streak, the last dinner day counted and the best treasury seen at a dawn.
 * A day without a dinner leaves the streak alone; a hungry dinner resets it; a day index counts once.
 */
public record TierProgress(int fedStreak, long lastDinnerDay, int bestDawnTreasury) {
	public static final long NO_DAY = Long.MIN_VALUE;
	public static final TierProgress INITIAL = new TierProgress(0, NO_DAY, 0);

	public TierProgress {
		if (fedStreak < 0) throw new IllegalArgumentException("fedStreak must be >= 0: " + fedStreak);
		if (bestDawnTreasury < 0) throw new IllegalArgumentException("bestDawnTreasury must be >= 0: " + bestDawnTreasury);
	}

	/** A dinner was served on day index {@code day} for {@code diners} villagers, {@code fed} of whom ate enough. */
	public TierProgress afterDinner(long day, int diners, int fed) {
		if (diners < 0 || fed < 0) throw new IllegalArgumentException("negative dinner counts");
		if (day == lastDinnerDay) return this;
		boolean fedDay = diners > 0 && fed >= diners;
		int streak = fedDay ? Math.min(Balance.MAX_FED_STREAK, fedStreak + 1) : 0;
		return new TierProgress(streak, day, bestDawnTreasury);
	}

	/** The treasury after wages at a dawn. */
	public TierProgress atDawn(int treasury) {
		if (treasury < 0) throw new IllegalArgumentException("treasury must be >= 0: " + treasury);
		return treasury > bestDawnTreasury ? new TierProgress(fedStreak, lastDinnerDay, treasury) : this;
	}
}
