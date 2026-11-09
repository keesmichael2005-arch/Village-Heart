package dev.villageheart.core.tier;

import dev.villageheart.core.Tier;

/** What a dawn does to the tier: record the treasury, evaluate the next tier's goals, go up at most one step. */
public final class TierDawn {
	private TierDawn() {}

	public static DawnOutcome run(Tier current, TierProgress progress, TierInputs liveInputs, int treasuryAfterWages) {
		TierProgress p = progress.atDawn(treasuryAfterWages);
		TierReport report = TierGoals.evaluate(current, liveInputs, p);
		if (report.allMet()) return new DawnOutcome(report.next().get(), p, report, true);
		return new DawnOutcome(current, p, report, false);
	}
}
