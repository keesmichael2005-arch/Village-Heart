package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.blueprint.BuildPlan.Kind;
import dev.villageheart.core.blueprint.BuilderPlanner.Action;
import dev.villageheart.core.blueprint.BuilderPlanner.CellState;
import dev.villageheart.core.blueprint.BuilderPlanner.Facts;
import dev.villageheart.core.blueprint.BuilderPlanner.Type;
import dev.villageheart.core.blueprint.BuilderPlanner.WaitReason;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.worker.Needs;
import org.junit.jupiter.api.Test;

/** T-L1-8 */
class BuilderPlannerTest {
	/** A PLACE step on a free cell, material in hand, in reach, standing outside: the base case. */
	private static Facts place() {
		return new Facts(true, false, Kind.PLACE, CellState.FREE, true, true, false, false, true, false);
	}

	private static Facts with(Facts f, java.util.function.UnaryOperator<Facts> change) { return change.apply(f); }

	private static Action next(Facts f) { return BuilderPlanner.next(f); }

	@Test
	void noBuildMeansIdle() {
		assertEquals(Type.IDLE, next(new Facts(false, false, null, CellState.FREE, false, false, false, false, false, false)).type());
	}

	@Test
	void finishedMeansFinish() {
		assertEquals(Type.FINISH, next(new Facts(true, true, null, CellState.FREE, false, false, false, false, false, false)).type());
	}

	@Test
	void theBaseCasePlaces() {
		assertEquals(Type.PLACE, next(place()).type());
		Facts clear = new Facts(true, false, Kind.CLEAR, CellState.CLEARABLE, false, false, false, false, true, false);
		assertEquals(Type.CLEAR, next(clear).type());
		// a natural block in a PLACE cell is cleared first
		Facts over = new Facts(true, false, Kind.PLACE, CellState.CLEARABLE, true, true, false, false, true, false);
		assertEquals(Type.CLEAR, next(over).type());
	}

	@Test
	void aNaturalBlockUnderAPlaceStepIsOnlyClearedWithTheMaterialInHand() {
		// clear and place are one action, so without the material the builder fetches it (or waits), and never clears first
		for (Kind kind : new Kind[] {Kind.PLACE, Kind.CHECK}) {
			assertEquals(Type.FETCH, next(new Facts(true, false, kind, CellState.CLEARABLE, false, true, false, false, true, false)).type(), kind.name());
			assertEquals(Type.YIELD, next(new Facts(true, false, kind, CellState.CLEARABLE, false, true, true, false, true, false)).type(), kind.name());
			assertEquals(new Action(Type.WAIT, WaitReason.NEEDS_MATERIALS),
					next(new Facts(true, false, kind, CellState.CLEARABLE, false, false, false, false, true, false)), kind.name());
			assertEquals(Type.CLEAR, next(new Facts(true, false, kind, CellState.CLEARABLE, true, true, false, false, true, false)).type(), kind.name());
		}
		// a CLEAR step needs no material
		assertEquals(Type.CLEAR, next(new Facts(true, false, Kind.CLEAR, CellState.CLEARABLE, false, false, false, false, true, false)).type());
	}

	@Test
	void freeCellsCostNothing() {
		assertEquals(Type.SKIP, next(new Facts(true, false, Kind.CLEAR, CellState.ALREADY_RIGHT, false, false, false, false, false, false)).type());
		assertEquals(Type.SKIP, next(new Facts(true, false, Kind.CHECK, CellState.ALREADY_RIGHT, false, false, false, false, false, false)).type());
	}

	@Test
	void unloadedAndBlockedWait() {
		Action a = next(new Facts(true, false, Kind.PLACE, CellState.UNLOADED, true, true, false, false, true, false));
		assertEquals(new Action(Type.WAIT, WaitReason.NOT_LOADED), a);
		Action b = next(new Facts(true, false, Kind.PLACE, CellState.BLOCKED, true, true, false, false, true, false));
		assertEquals(new Action(Type.WAIT, WaitReason.BLOCKED), b);
		Action c = next(new Facts(true, false, Kind.CLEAR, CellState.BLOCKED, false, false, false, false, true, false));
		assertEquals(new Action(Type.WAIT, WaitReason.BLOCKED), c);
	}

	@Test
	void nothingCarriedNothingReservedPauses() {
		Action a = next(new Facts(true, false, Kind.PLACE, CellState.FREE, false, false, false, false, true, false));
		assertEquals(new Action(Type.WAIT, WaitReason.NEEDS_MATERIALS), a);
	}

	@Test
	void reservedButNotCarriedFetches() {
		assertEquals(Type.FETCH, next(new Facts(true, false, Kind.PLACE, CellState.FREE, false, true, false, false, true, false)).type());
		// a full inventory hands in first
		assertEquals(Type.YIELD, next(new Facts(true, false, Kind.PLACE, CellState.FREE, false, true, true, false, true, false)).type());
	}

	@Test
	void clearingYieldsToADepositTrip() {
		assertEquals(Type.YIELD, next(new Facts(true, false, Kind.CLEAR, CellState.CLEARABLE, false, false, true, false, true, false)).type());
		// placing with the material in hand does not need a trip
		assertEquals(Type.PLACE, next(new Facts(true, false, Kind.PLACE, CellState.FREE, true, true, true, false, true, false)).type());
	}

	@Test
	void outOfReachOrInsideWalks() {
		assertEquals(Type.WALK, next(with(place(), f -> new Facts(true, false, Kind.PLACE, CellState.FREE, true, true, false, false, false, false))).type());
		assertEquals(Type.WALK, next(with(place(), f -> new Facts(true, false, Kind.PLACE, CellState.FREE, true, true, false, true, true, false))).type());
	}

	@Test
	void unreachableWaits() {
		Action a = next(new Facts(true, false, Kind.PLACE, CellState.FREE, true, true, false, false, false, true));
		assertEquals(new Action(Type.WAIT, WaitReason.UNREACHABLE), a);
	}

	@Test
	void anEntityInTheCellWaitsOnceInReach() {
		Action a = next(new Facts(true, false, Kind.PLACE, CellState.ENTITY, true, true, false, false, true, false));
		assertEquals(new Action(Type.WAIT, WaitReason.IN_THE_WAY), a);
		// not in reach yet: walk there first
		assertEquals(Type.WALK, next(new Facts(true, false, Kind.PLACE, CellState.ENTITY, true, true, false, false, false, false)).type());
	}

	@Test
	void paceIsOneBlockPerBuildTicksAtLevelOne() { // M26: 70 ticks (3.5 s) a block, was 20 (one a second)
		double speed = WorkSpeed.untooled(1, Needs.DEFAULT);
		assertEquals(1.0, speed);
		WorkClock clock = new WorkClock(Balance.BUILD_TICKS_PER_BLOCK);
		int units = 0;
		for (int t = 0; t < 2000; t++) units += clock.tick(speed);
		assertEquals((int) (2000 / Balance.BUILD_TICKS_PER_BLOCK), units);
		assertTrue(Balance.BUILD_TICKS_PER_BLOCK >= 60 && Balance.BUILD_TICKS_PER_BLOCK <= 80, "owner: about one block per 3-4 seconds at level 1");
		assertEquals(1.25, WorkSpeed.untooled(2, Needs.DEFAULT));
	}
}
