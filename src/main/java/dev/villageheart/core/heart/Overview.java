package dev.villageheart.core.heart;

import dev.villageheart.core.Tier;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * @param foodDaysTenths -1 when there is nobody to feed
 * @param wageBill       what the next wage run costs; -1 only when there is no village data
 * @param moreAlerts     alerts beyond the {@link HeartLimits#MAX_ALERTS} shown
 * @param recentDeaths   the newest deaths, newest first, at most {@link HeartLimits#MAX_DEATH_ROWS} (M25)
 * @param deathsLogged   how many deaths the log has assigned a sequence to; not capped when the list drops old rows
 */
public record Overview(String ownerName, Tier tier, Optional<Tier> nextTier, List<GoalLine> goals, int population, int adults,
		int babies, int beds, int workers, int workerSlots, int foodDaysTenths, int treasury, int wageBill,
		List<AlertLine> alerts, int moreAlerts, List<DeathRow> recentDeaths, long deathsLogged) {
	/** Without the death rows (the shape before M25). */
	public Overview(String ownerName, Tier tier, Optional<Tier> nextTier, List<GoalLine> goals, int population, int adults,
			int babies, int beds, int workers, int workerSlots, int foodDaysTenths, int treasury, int wageBill,
			List<AlertLine> alerts, int moreAlerts) {
		this(ownerName, tier, nextTier, goals, population, adults, babies, beds, workers, workerSlots, foodDaysTenths, treasury,
				wageBill, alerts, moreAlerts, List.of(), 0L);
	}

	public Overview {
		Objects.requireNonNull(ownerName, "ownerName");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(nextTier, "nextTier");
		goals = List.copyOf(goals);
		alerts = List.copyOf(alerts);
		recentDeaths = List.copyOf(recentDeaths);
		if (deathsLogged < 0) deathsLogged = 0;
	}
}
