package dev.villageheart.core.smith;

import dev.villageheart.core.Balance;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.tier.Population;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** How many ingots a hurt golem is worth, and how many golems the village may have. */
public final class GolemRules {
	private GolemRules() {}

	/** Below half health. Exactly half does not. */
	public static boolean wantsRepair(float health, float max) {
		return max > 0 && health < max * Balance.GOLEM_REPAIR_BELOW;
	}

	/**
	 * Ingots to spend while the missing health is at least {@link Balance#GOLEM_REPAIR_MIN_MISSING}. 25 health per ingot, and the last 9
	 * missing points are left (an ingot would overshoot them): {@code m < 10 ? 0 : ceil((m - 9) / 25)}.
	 */
	public static int ingotsFor(float health, float max) {
		float missing = max - health;
		if (missing < Balance.GOLEM_REPAIR_MIN_MISSING) return 0;
		return (int) Math.ceil((missing - (Balance.GOLEM_REPAIR_MIN_MISSING - 1)) / (double) Balance.GOLEM_HEAL_PER_INGOT - 1e-9);
	}

	/** {@code golems + builds < population / 5}. A build in progress counts, so two smiths cannot both start one. */
	public static boolean allowed(int population, int golems, int builds) {
		if (population < 0 || golems < 0 || builds < 0) return false;
		return golems + builds < population / Balance.GOLEM_VILLAGERS_PER;
	}

	/** Loaded villagers plus registered workers who are not in that census. */
	public static int population(Set<UUID> census, List<WorkerRecord> workers) {
		return Population.count(census, workers);
	}
}
