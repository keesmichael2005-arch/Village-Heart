package dev.villageheart.core.heart;

import dev.villageheart.core.Tier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @param foodDaysTenths -1 when there is nobody to feed
 * @param wageBill       what the next wage run costs; -1 only when there is no village data
 * @param moreAlerts     alerts beyond the {@link HeartLimits#MAX_ALERTS} shown
 */
public record Overview(String ownerName, Tier tier, Optional<Tier> nextTier, List<GoalLine> goals, int population, int adults,
		int babies, int beds, int workers, int workerSlots, int foodDaysTenths, int treasury, int wageBill,
		List<AlertLine> alerts, int moreAlerts) {
	public Overview {
		Objects.requireNonNull(ownerName, "ownerName");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(nextTier, "nextTier");
		goals = List.copyOf(goals);
		alerts = List.copyOf(alerts);
	}
}
