package dev.villageheart.core.job;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.JobLevels;
import dev.villageheart.core.tier.TierRules;
import java.util.Optional;

/** Job levels (DESIGN section 4): XP is banked up to level 3's threshold; the tier only caps the level, never the XP. Never lowers a level. */
public final class Leveling {
	private Leveling() {}

	/** before + amount (amount >= 0, else throws), XP clamped to {@code MAX_BANKED_XP}, level = max(before.level, levelFor(xp, tier)). */
	public static XpChange gain(Progress before, int amount, Tier tier) {
		if (amount < 0) throw new IllegalArgumentException("amount must be >= 0: " + amount);
		long sum = (long) before.xp() + amount;
		int xp = (int) Math.min(sum, Balance.MAX_BANKED_XP);
		Progress after = new Progress(Math.max(before.level(), TierRules.levelFor(xp, tier)), xp);
		boolean up = after.level() > before.level();
		boolean cap = false;
		if (after.level() < 3) {
			int threshold = JobLevels.xpForNext(after.level());
			cap = after.level() >= TierRules.levelCap(tier) && before.xp() < threshold && xp >= threshold;
		}
		return new XpChange(before, after, up, cap);
	}

	/** The level the pass should hold now: max(level, levelFor(xp, tier)). Never lower. */
	public static int levelNow(Progress p, Tier tier) { return Math.max(p.level(), TierRules.levelFor(p.xp(), tier)); }

	/** True when the worker has the XP for the next level but the tier caps it (the Heart bar turns gold). */
	public static boolean capped(Progress p, Tier tier) {
		return p.level() < 3 && p.level() >= TierRules.levelCap(tier) && p.xp() >= JobLevels.xpForNext(p.level());
	}

	/** The tier a worker needs for its next level ("needs a Village"), or empty at level 3. */
	public static Optional<Tier> tierForNext(Progress p) {
		if (p.level() >= 3) return Optional.empty();
		for (Tier t : Tier.values()) if (TierRules.levelCap(t) > p.level()) return Optional.of(t);
		return Optional.empty();
	}

	/** Clamps stored values from a save: level to 1..3, XP to 0..MAX_BANKED_XP. */
	public static Progress sanitize(int level, int xp) {
		return new Progress(Math.max(1, Math.min(3, level)), Math.max(0, Math.min(Balance.MAX_BANKED_XP, xp)));
	}
}
