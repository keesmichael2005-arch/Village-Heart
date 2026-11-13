package dev.villageheart.core.guide;

import dev.villageheart.core.Tier;
import java.util.Set;

/**
 * Facts the guide can see. The glue fills this from the live village. A true flag latches the matching task,
 * so a later false (emeralds spent, a build finished and gone) does not uncheck it.
 */
public record GuideSignals(boolean heartOpen, boolean markerPlaced, Set<String> valid, Set<String> jobs, boolean emeralds,
		boolean field, boolean fedDinner, boolean wagesPaid, boolean houseStocked, boolean buildStarted, boolean buildFinished,
		boolean golem, Set<String> defences, Tier tier, int wageShort) {
	public GuideSignals {
		valid = Set.copyOf(valid);
		jobs = Set.copyOf(jobs);
		defences = Set.copyOf(defences);
		if (tier == null) throw new IllegalArgumentException("tier");
		if (wageShort < 0) throw new IllegalArgumentException("wageShort must be >= 0: " + wageShort);
	}

	public static GuideSignals none() {
		return new GuideSignals(false, false, Set.of(), Set.of(), false, false, false, false, false, false, false, false, Set.of(),
				Tier.HAMLET, 0);
	}

	public boolean valid(String typeId) { return valid.contains(typeId); }
	public boolean job(String jobId) { return jobs.contains(jobId); }
	public boolean defence(String blueprintId) { return defences.contains(blueprintId); }
	public boolean tierAtLeast(Tier t) { return tier.ordinal() >= t.ordinal(); }
}
