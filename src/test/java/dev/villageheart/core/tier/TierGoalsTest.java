package dev.villageheart.core.tier;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import dev.villageheart.core.Tier;

class TierGoalsTest {
	private static final TierInputs VILLAGE_OK = new TierInputs(6, 3, 0, true, true, true);
	private static final TierProgress VILLAGE_P = new TierProgress(3, 0, 0);
	private static final TierInputs TOWN_OK = new TierInputs(12, 8, 2, true, true, true);
	private static final TierProgress TOWN_P = new TierProgress(7, 0, 32);

	private static Goal goal(TierReport r, GoalId id) {
		return r.goals().stream().filter(g -> g.id() == id).findFirst().orElseThrow();
	}

	private static void onlyUnmet(TierReport r, GoalId id) {
		assertFalse(r.allMet());
		for (Goal g : r.goals()) assertEquals(g.id() != id, g.met(), g.id().toString());
	}

	@Test void atThresholdEveryGoalIsMet() {
		assertTrue(TierGoals.evaluate(Tier.HAMLET, VILLAGE_OK, VILLAGE_P).allMet());
		assertTrue(TierGoals.evaluate(Tier.VILLAGE, TOWN_OK, TOWN_P).allMet());
	}

	@Test void eachVillageGoalAloneBlocks() {
		onlyUnmet(TierGoals.evaluate(Tier.HAMLET, new TierInputs(5, 3, 0, true, true, true), VILLAGE_P), GoalId.POPULATION);
		onlyUnmet(TierGoals.evaluate(Tier.HAMLET, new TierInputs(6, 2, 0, true, true, true), VILLAGE_P), GoalId.WORKERS);
		onlyUnmet(TierGoals.evaluate(Tier.HAMLET, new TierInputs(6, 3, 0, false, true, true), VILLAGE_P), GoalId.STORAGE);
		onlyUnmet(TierGoals.evaluate(Tier.HAMLET, new TierInputs(6, 3, 0, true, false, true), VILLAGE_P), GoalId.FARMHOUSE);
		onlyUnmet(TierGoals.evaluate(Tier.HAMLET, new TierInputs(6, 3, 0, true, true, false), VILLAGE_P), GoalId.KITCHEN);
		TierReport r = TierGoals.evaluate(Tier.HAMLET, VILLAGE_OK, new TierProgress(2, 0, 0));
		onlyUnmet(r, GoalId.FED_DAYS);
		assertEquals(2, goal(r, GoalId.FED_DAYS).have());
		assertEquals(3, goal(r, GoalId.FED_DAYS).need());
	}

	@Test void eachTownGoalAloneBlocks() {
		onlyUnmet(TierGoals.evaluate(Tier.VILLAGE, new TierInputs(11, 8, 2, true, true, true), TOWN_P), GoalId.POPULATION);
		onlyUnmet(TierGoals.evaluate(Tier.VILLAGE, new TierInputs(12, 7, 2, true, true, true), TOWN_P), GoalId.WORKERS);
		onlyUnmet(TierGoals.evaluate(Tier.VILLAGE, new TierInputs(12, 8, 1, true, true, true), TOWN_P), GoalId.GUARDS);
		onlyUnmet(TierGoals.evaluate(Tier.VILLAGE, TOWN_OK, new TierProgress(6, 0, 32)), GoalId.FED_DAYS);
		onlyUnmet(TierGoals.evaluate(Tier.VILLAGE, TOWN_OK, new TierProgress(7, 0, 31)), GoalId.DAWN_TREASURY);
	}

	@Test void aboveThresholdIsMet() {
		assertTrue(goal(TierGoals.evaluate(Tier.HAMLET, new TierInputs(40, 3, 0, true, true, true), VILLAGE_P), GoalId.POPULATION).met());
	}

	@Test void orderAndMembership() {
		assertEquals(List.of(GoalId.POPULATION, GoalId.WORKERS, GoalId.STORAGE, GoalId.FARMHOUSE, GoalId.KITCHEN, GoalId.FED_DAYS),
				TierGoals.evaluate(Tier.HAMLET, TierInputs.EMPTY, TierProgress.INITIAL).goals().stream().map(Goal::id).toList());
		assertEquals(List.of(GoalId.POPULATION, GoalId.WORKERS, GoalId.GUARDS, GoalId.FED_DAYS, GoalId.DAWN_TREASURY),
				TierGoals.evaluate(Tier.VILLAGE, TierInputs.EMPTY, TierProgress.INITIAL).goals().stream().map(Goal::id).toList());
	}

	@Test void townHasNoNextTier() {
		TierReport r = TierGoals.evaluate(Tier.TOWN, TOWN_OK, TOWN_P);
		assertEquals(Optional.empty(), r.next());
		assertTrue(r.goals().isEmpty());
		assertFalse(r.allMet());
	}

	@Test void maxGoalsIsSix() {
		assertEquals(6, TierGoals.maxGoals());
	}

	@Test void inputsRejectNegatives() {
		assertThrows(IllegalArgumentException.class, () -> new TierInputs(-1, 0, 0, false, false, false));
	}
}
