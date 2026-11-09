package dev.villageheart.core.tier;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;

/** The checklist per target tier (DESIGN section 9). */
public final class TierGoals {
	private TierGoals() {}

	/** The goals for reaching {@code target}, in checklist order. Hamlet has none. */
	public static List<GoalId> goalsFor(Tier target) {
		return switch (target) {
			case HAMLET -> List.of();
			case VILLAGE -> List.of(GoalId.POPULATION, GoalId.WORKERS, GoalId.STORAGE, GoalId.FARMHOUSE, GoalId.KITCHEN, GoalId.FED_DAYS);
			case TOWN -> List.of(GoalId.POPULATION, GoalId.WORKERS, GoalId.GUARDS, GoalId.FED_DAYS, GoalId.DAWN_TREASURY);
		};
	}

	public static int maxGoals() {
		int max = 0;
		for (Tier t : Tier.values()) max = Math.max(max, goalsFor(t).size());
		return max;
	}

	private static int need(Tier target, GoalId id) {
		boolean town = target == Tier.TOWN;
		return switch (id) {
			case POPULATION -> town ? Balance.TOWN_POPULATION : Balance.VILLAGE_POPULATION;
			case WORKERS -> town ? Balance.TOWN_WORKERS : Balance.VILLAGE_WORKERS;
			case GUARDS -> Balance.TOWN_GUARDS;
			case FED_DAYS -> town ? Balance.TOWN_FED_DAYS : Balance.VILLAGE_FED_DAYS;
			case DAWN_TREASURY -> Balance.TOWN_DAWN_TREASURY;
			case STORAGE, FARMHOUSE, KITCHEN -> 1;
		};
	}

	public static TierReport evaluate(Tier current, TierInputs in, TierProgress p) {
		Optional<Tier> next = TierRules.next(current);
		if (next.isEmpty()) return new TierReport(current, next, List.of());
		Tier target = next.get();
		List<Goal> goals = new ArrayList<>();
		for (GoalId id : goalsFor(target)) {
			int have = switch (id) {
				case POPULATION -> in.population();
				case WORKERS -> in.workers();
				case GUARDS -> in.guards();
				case STORAGE -> in.validStorage() ? 1 : 0;
				case FARMHOUSE -> in.validFarmhouse() ? 1 : 0;
				case KITCHEN -> in.validKitchen() ? 1 : 0;
				case FED_DAYS -> p.fedStreak();
				case DAWN_TREASURY -> p.bestDawnTreasury();
			};
			goals.add(new Goal(id, have, need(target, id)));
		}
		return new TierReport(current, next, goals);
	}
}
