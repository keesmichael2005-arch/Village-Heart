package dev.villageheart.core.tier;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import dev.villageheart.core.Tier;

/** The goals of the next tier with progress; {@code goals} is empty exactly when {@code next} is empty (Town). */
public record TierReport(Tier current, Optional<Tier> next, List<Goal> goals) {
	public TierReport {
		Objects.requireNonNull(current, "current");
		Objects.requireNonNull(next, "next");
		goals = List.copyOf(goals);
	}

	public boolean allMet() {
		if (goals.isEmpty()) return false;
		for (Goal g : goals) if (!g.met()) return false;
		return true;
	}
}
