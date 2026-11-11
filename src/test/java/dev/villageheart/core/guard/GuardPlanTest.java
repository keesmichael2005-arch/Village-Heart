package dev.villageheart.core.guard;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.guard.GuardPlanner.DutyFacts;
import dev.villageheart.core.guard.GuardPlanner.DutyMode;
import dev.villageheart.core.guard.GuardPlanner.OffFacts;
import dev.villageheart.core.guard.GuardPlanner.OffMode;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** T-L1-8 (planner), T-L1-9 (patrol route), T-L1-10 (beds). */
class GuardPlanTest {
	@Test
	void dutyPrecedence() {
		assertEquals(DutyMode.FIGHT, GuardPlanner.duty(new DutyFacts(true, true, true)));
		assertEquals(DutyMode.EQUIP, GuardPlanner.duty(new DutyFacts(false, true, true)));
		assertEquals(DutyMode.PATROL, GuardPlanner.duty(new DutyFacts(false, true, false)), "source on cooldown");
		assertEquals(DutyMode.PATROL, GuardPlanner.duty(new DutyFacts(false, false, true)), "nothing to upgrade");
		assertEquals(DutyMode.FIGHT, GuardPlanner.duty(new DutyFacts(true, false, false)));
	}

	@Test
	void offPrecedence() {
		assertEquals(OffMode.DEFEND, GuardPlanner.off(new OffFacts(true, true, true, true)));
		assertEquals(OffMode.RETURN_GEAR, GuardPlanner.off(new OffFacts(false, true, true, true)));
		assertEquals(OffMode.SPAR, GuardPlanner.off(new OffFacts(false, false, true, true)));
		assertEquals(OffMode.IDLE, GuardPlanner.off(new OffFacts(false, false, false, true)), "no yard");
		assertEquals(OffMode.IDLE, GuardPlanner.off(new OffFacts(false, false, true, false)), "cannot work");
	}

	private static final List<Pos> POINTS = List.of(new Pos(0, 0, 0), new Pos(10, 0, 0), new Pos(20, 0, 0), new Pos(30, 0, 0));

	@Test
	void startIndexSpreadsGuards() {
		assertEquals(0, PatrolRoute.start(4, 0, 2));
		assertEquals(2, PatrolRoute.start(4, 1, 2));
		assertEquals(0, PatrolRoute.start(1, 0, 3));
	}

	@Test
	void nextGoesRoundAndSingleStays() {
		PatrolRoute r = new PatrolRoute();
		assertEquals(POINTS.get(1), r.next(POINTS, POINTS.get(0), 0));
		assertEquals(POINTS.get(0), r.next(POINTS, POINTS.get(3), 0));
		assertEquals(POINTS.get(0), r.next(List.of(POINTS.get(0)), POINTS.get(0), 0));
	}

	@Test
	void unreachableIsSkippedThenRetried() {
		PatrolRoute r = new PatrolRoute();
		r.markUnreachable(POINTS.get(1), 100);
		assertEquals(POINTS.get(2), r.next(POINTS, POINTS.get(0), 200));
		assertEquals(POINTS.get(1), r.next(POINTS, POINTS.get(0), 100 + Balance.PATROL_SKIP_TICKS));
	}

	@Test
	void allUnreachableStaysPut() {
		PatrolRoute r = new PatrolRoute();
		for (Pos p : POINTS) if (!p.equals(POINTS.get(0))) r.markUnreachable(p, 0);
		assertEquals(POINTS.get(0), r.next(POINTS, POINTS.get(0), 10));
	}

	private static UUID id(int i) { return new UUID(0, i); }

	@Test
	void bedsAreStableAndRemovedBedMovesTheGuard() {
		List<Pos> beds = List.of(new Pos(5, 0, 0), new Pos(3, 0, 0), new Pos(4, 0, 0));
		List<UUID> guards = List.of(id(1), id(2));
		Map<UUID, Pos> first = GuardBeds.assign(beds, guards, Map.of());
		assertEquals(new Pos(3, 0, 0), first.get(id(1)));
		assertEquals(new Pos(4, 0, 0), first.get(id(2)));
		assertEquals(first, GuardBeds.assign(beds, guards, first), "stable");
		Map<UUID, Pos> after = GuardBeds.assign(List.of(new Pos(4, 0, 0), new Pos(5, 0, 0)), guards, first);
		assertEquals(new Pos(4, 0, 0), after.get(id(2)), "keeps its bed");
		assertEquals(new Pos(5, 0, 0), after.get(id(1)), "moved to a free one");
	}

	@Test
	void moreGuardsThanBedsLeavesTheLastWithout() {
		Map<UUID, Pos> m = GuardBeds.assign(List.of(new Pos(1, 0, 0)), List.of(id(1), id(2)), Map.of());
		assertEquals(1, m.size());
		assertTrue(m.containsKey(id(1)));
		assertFalse(m.containsKey(id(2)));
	}
}
