package dev.villageheart.core.hunt;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.hunt.HunterPlanner.Action;
import dev.villageheart.core.hunt.HunterPlanner.Decision;
import dev.villageheart.core.hunt.HunterPlanner.PlanInputs;
import dev.villageheart.core.hunt.HunterPlanner.StatusEffect;
import dev.villageheart.core.mine.AwayCheck.Lease;
import java.util.OptionalLong;
import org.junit.jupiter.api.Test;

/** T-L1-9: one case per planner row, and precedence. */
class HunterPlannerTest {
	/** Everything fine at day time 1000: goes out. */
	private static PlanInputs ok() { return new PlanInputs(true, false, false, Lease.HELD, true, 1000, 0, 1.0, 1, 0, OptionalLong.empty(), 10); }

	private static PlanInputs at(long dayTime, double progress) {
		return new PlanInputs(true, false, false, Lease.HELD, true, dayTime, progress, 1.0, 1, 0, OptionalLong.empty(), 10);
	}

	private static Decision d(PlanInputs in) { return HunterPlanner.decide(in); }

	private static Decision dec(Action a, StatusEffect s) { return new Decision(a, s); }

	@Test
	void goesOutWhenEverythingIsFine() {
		assertEquals(dec(Action.GO_OUT, StatusEffect.CLEAR), d(ok()));
	}

	@Test
	void row1NoSlot() {
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.CLEAR), d(new PlanInputs(false, true, true, Lease.NONE, false, 1000, 0, 0, 1, 9, OptionalLong.empty(), 99)));
	}

	@Test
	void row2YieldBeatsEverythingBelow() {
		assertEquals(dec(Action.YIELD, StatusEffect.KEEP), d(new PlanInputs(true, true, false, Lease.NONE, false, 1000, 0, 0, 1, 9, OptionalLong.empty(), 99)));
		assertEquals(dec(Action.YIELD, StatusEffect.KEEP), d(new PlanInputs(true, false, true, Lease.UNKNOWN, true, 1000, 0, 1, 1, 0, OptionalLong.empty(), 1)));
	}

	@Test
	void rows3And4LeaseAndRow5Ground() {
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.KEEP), d(new PlanInputs(true, false, false, Lease.UNKNOWN, true, 1000, 0, 0, 1, 0, OptionalLong.empty(), 1)));
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.NEEDS_TOOL), d(new PlanInputs(true, false, false, Lease.NONE, true, 1000, 0, 0, 1, 0, OptionalLong.empty(), 1)));
		// no ground and no weapon: the weapon is asked for first
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.NEEDS_TOOL), d(new PlanInputs(true, false, false, Lease.NONE, false, 1000, 0, 0, 1, 0, OptionalLong.empty(), 1)));
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.NO_WORK), d(new PlanInputs(true, false, false, Lease.HELD, false, 1000, 0, 1, 1, 0, OptionalLong.empty(), 1)));
	}

	@Test
	void row6Unreachable() {
		PlanInputs a = new PlanInputs(true, false, false, Lease.HELD, true, 1000, 0, 1, 1, 0, OptionalLong.of(599), 10);
		PlanInputs b = new PlanInputs(true, false, false, Lease.HELD, true, 1000, 0, 1, 1, 0, OptionalLong.of(600), 10);
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.SITE_UNREACHABLE), d(a));
		assertEquals(dec(Action.GO_OUT, StatusEffect.CLEAR), d(b));
	}

	@Test
	void row7DailyCap() {
		assertEquals(dec(Action.GO_OUT, StatusEffect.CLEAR), d(withDone(1, 1)));
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.CLEAR), d(withDone(1, 2)));
		assertEquals(dec(Action.GO_OUT, StatusEffect.CLEAR), d(withDone(2, 2)));
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.CLEAR), d(withDone(2, 3)));
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.CLEAR), d(withDone(3, 3)));
		assertEquals(2, HunterPlanner.tripsPerDay(1));
		assertEquals(3, HunterPlanner.tripsPerDay(2));
		assertEquals(3, HunterPlanner.tripsPerDay(9));
		assertEquals(2, HunterPlanner.tripsPerDay(0));
	}

	private static PlanInputs withDone(int level, int done) {
		return new PlanInputs(true, false, false, Lease.HELD, true, 1000, 0, 1.0, level, done, OptionalLong.empty(), 10);
	}

	@Test
	void row8TheTripMustFitTheWorkDay() {
		// WORK_END - trip - margin. A full trip is 3000 units; 1500 done leaves 1500; speed 1.25 needs 2400.
		long full = Balance.WORK_END - 3000 - Balance.HUNTER_DEPART_MARGIN;
		long half = Balance.WORK_END - 1500 - Balance.HUNTER_DEPART_MARGIN;
		long fast = Balance.WORK_END - 2400 - Balance.HUNTER_DEPART_MARGIN;
		assertEquals(dec(Action.GO_OUT, StatusEffect.CLEAR), d(at(full, 0)));
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.CLEAR), d(at(full + 1, 0)));
		assertEquals(dec(Action.GO_OUT, StatusEffect.CLEAR), d(at(half, 1500)));
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.CLEAR), d(at(half + 1, 1500)));
		assertTrue(HunterPlanner.fitsNow(full, 0, 1.0));
		assertFalse(HunterPlanner.fitsNow(full + 1, 0, 1.0));
		assertTrue(HunterPlanner.fitsNow(half, 1500, 1.0));
		assertFalse(HunterPlanner.fitsNow(half + 1, 1500, 1.0));
		assertFalse(HunterPlanner.fitsNow(1000, 0, 0.0));
		assertFalse(HunterPlanner.fitsNow(999, 0, 1.0)); // not before the work phase
		assertTrue(HunterPlanner.fitsNow(1000, 0, 1.0));
		// the day time wraps: the same time one day later
		assertTrue(HunterPlanner.fitsNow(24000 + full, 0, 1.0));
		// a faster hunter leaves later
		assertTrue(HunterPlanner.fitsNow(fast, 0, 1.25));
		assertFalse(HunterPlanner.fitsNow(fast + 1, 0, 1.25));
		assertEquals(dec(Action.IDLE_AT_LODGE, StatusEffect.CLEAR), d(new PlanInputs(true, false, false, Lease.HELD, true, 1000, 0, 0, 1, 0, OptionalLong.empty(), 10)));
	}

	@Test
	void rows9And10Distance() {
		assertEquals(dec(Action.GO_OUT, StatusEffect.CLEAR), d(dist(40)));
		assertEquals(dec(Action.WALK_TO_LODGE, StatusEffect.CLEAR), d(dist(41)));
	}

	private static PlanInputs dist(double blocks) {
		return new PlanInputs(true, false, false, Lease.HELD, true, 1000, 0, 1.0, 1, 0, OptionalLong.empty(), blocks);
	}
}
