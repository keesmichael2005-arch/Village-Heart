package dev.villageheart.core.raid;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.siege.RaidScale;

/**
 * The raid a village is handed at the start moment (docs/plans/M25.md 3.13a). A Hamlet is treated as a young village,
 * so it is not handed a full raid. Bad Omen and TEST are never capped and get no extras.
 */
public final class RaidPlans {
	private RaidPlans() {}

	public static RaidPlan of(Tier tier, int qualifiedGuards, double threat, RaidOrigin origin) {
		if (origin != RaidOrigin.RANDOM) return new RaidPlan(1, 0, 0, 0, false);
		if (tier == Tier.TOWN || qualifiedGuards >= Balance.RAID_FULL_GUARDS) {
			RaidScale scale = RaidScale.of(threat);
			return new RaidPlan(scale.omen(), scale.extraVindicators(), scale.extraRavagers(), 0, false);
		}
		int waves = qualifiedGuards >= 2 ? Balance.EARLY_RAID_WAVES : 1;
		return new RaidPlan(1, 0, 0, waves, true);
	}
}
