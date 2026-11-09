package dev.villageheart.core.tier;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.heart.JobLevels;

/**
 * Tier rules that levelling needs (DESIGN sections 4 and 9). M15 created the three level methods; M14 adds tier progression to
 * this class.
 */
public final class TierRules {
	private TierRules() {}

	/** The tier after this one; empty at Town. */
	public static java.util.Optional<Tier> next(Tier tier) {
		Tier[] all = Tier.values();
		return tier.ordinal() + 1 < all.length ? java.util.Optional.of(all[tier.ordinal() + 1]) : java.util.Optional.empty();
	}

	/** The highest job level an earned level may reach at this tier: Hamlet 1, Village 2, Town 3. */
	public static int levelCap(Tier tier) { return Balance.LEVEL_CAPS[tier.ordinal()]; }

	/** The level a worker with {@code xp} total XP has earned, ignoring the tier (1..3). */
	public static int levelFromXp(int xp) {
		int level = 1;
		while (level < Balance.LEVEL_CAPS[Balance.LEVEL_CAPS.length - 1] && xp >= JobLevels.xpForNext(level)) level++;
		return level;
	}

	/** The level a worker with {@code xp} holds at this tier: the earned level, held down by the tier's cap. */
	public static int levelFor(int xp, Tier tier) { return Math.min(levelFromXp(xp), levelCap(tier)); }
}
