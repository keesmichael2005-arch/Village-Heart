package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.tier.TierGoals;
import dev.villageheart.core.tier.TierInputs;
import dev.villageheart.core.tier.TierProgress;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** M14: the tier checklist in the Overview. */
class HeartSnapshotGoalsTest {
	private static VillageFacts facts(Tier tier, int population, TierInputs in, TierProgress p) {
		VillageFacts base = HeartSnapshotBuilderTest.basic();
		return new VillageFacts(base.ownerName(), tier, base.treasury(), base.adults(), base.babies(), base.beds(), base.workerSlots(),
				base.villagers(), base.jobs(), base.buildings(), base.stock(), base.extraAlerts(), base.members(), base.builds(), 0, 0,
				population, TierGoals.evaluate(tier, in, p));
	}

	@Test void hamletChecklistInOrderWithArgsAndMetFlags() {
		VillageFacts f = facts(Tier.HAMLET, 4, new TierInputs(4, 3, 0, true, true, false), new TierProgress(1, 0, 0));
		Overview o = HeartSnapshotBuilder.build(f, Rank.OWNER).overview();
		assertEquals(Optional.of(Tier.VILLAGE), o.nextTier());
		assertEquals(4, o.population());
		List<GoalLine> g = o.goals();
		String p = "gui.villageheart.heart.goal.";
		assertEquals(List.of(p + "population", p + "workers", p + "storage", p + "farmhouse", p + "kitchen", p + "fed_days"),
				g.stream().map(x -> x.label().key()).toList());
		assertEquals(List.of("4", "6"), g.get(0).label().args());
		assertEquals(List.of("3", "3"), g.get(1).label().args());
		assertEquals(List.of(), g.get(2).label().args());
		assertEquals(List.of("1", "3"), g.get(5).label().args());
		assertEquals(List.of(false, true, true, true, false, false), g.stream().map(GoalLine::met).toList());
	}

	@Test void townHasNoGoalsAndNoNextTier() {
		Overview o = HeartSnapshotBuilder.build(facts(Tier.TOWN, 20, TierInputs.EMPTY, TierProgress.INITIAL), Rank.OWNER).overview();
		assertTrue(o.goals().isEmpty());
		assertEquals(Optional.empty(), o.nextTier());
	}

	@Test void aVisitorSeesTheSameChecklist() {
		VillageFacts f = facts(Tier.HAMLET, 4, new TierInputs(4, 3, 0, true, true, false), new TierProgress(1, 0, 0));
		assertEquals(HeartSnapshotBuilder.build(f, Rank.OWNER).overview().goals(), HeartSnapshotBuilder.build(f, Rank.VISITOR).overview().goals());
	}

	@Test void longStreakAndCappedTreasuryShowAsDesigned() {
		VillageFacts f = facts(Tier.VILLAGE, 12, new TierInputs(12, 8, 2, true, true, true), new TierProgress(9, 0, 50));
		List<GoalLine> g = HeartSnapshotBuilder.build(f, Rank.OWNER).overview().goals();
		assertEquals(List.of("9", "7"), g.get(3).label().args());
		assertEquals(List.of("32", "32"), g.get(4).label().args());
		assertTrue(g.stream().allMatch(GoalLine::met));
	}

	@Test void populationFactIsTheOverviewAndTheGoalNumber() {
		// an unloaded worker: adults + babies says 2, the collected population (loaded + unloaded workers) says 3
		VillageFacts f = facts(Tier.HAMLET, 3, new TierInputs(3, 1, 0, false, false, false), TierProgress.INITIAL);
		Overview o = HeartSnapshotBuilder.build(f, Rank.OWNER).overview();
		assertEquals(3, o.population());
		assertEquals(Integer.toString(o.population()), o.goals().get(0).label().args().get(0));
	}

	@Test void goalCountsFitTheWireAndTheScreen() {
		assertTrue(TierGoals.maxGoals() <= HeartLimits.MAX_GOALS);
		assertEquals(TierGoals.maxGoals(), HeartLimits.MAX_GOAL_ROWS);
	}

	@Test void goalLabelArgsFitTheTextLimits() {
		VillageFacts f = facts(Tier.VILLAGE, 99999, new TierInputs(99999, 99999, 99999, true, true, true), new TierProgress(9999, 0, Integer.MAX_VALUE));
		for (GoalLine l : HeartSnapshotBuilder.build(f, Rank.OWNER).overview().goals()) {
			assertTrue(l.label().args().size() <= HeartLimits.MAX_TEXT_ARGS);
			for (String a : l.label().args()) assertTrue(a.length() <= HeartLimits.MAX_ARG, a);
		}
	}
}
