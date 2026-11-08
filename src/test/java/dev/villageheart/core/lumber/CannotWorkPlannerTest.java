package dev.villageheart.core.lumber;

import static dev.villageheart.core.lumber.LumberjackPlanner.Action.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.farm.FarmerPlanner;
import dev.villageheart.core.lumber.LumberjackPlanner.FinderState;
import dev.villageheart.core.lumber.LumberjackPlanner.Snapshot;
import dev.villageheart.core.lumber.LumberjackPlanner.ToolState;
import org.junit.jupiter.api.Test;

/** M11: a worker with work speed 0 (two missed dinners, or unpaid) idles; deposit trips still run. */
class CannotWorkPlannerTest {
	private static Snapshot lumber(boolean deposit, boolean fell, boolean replant, boolean cannotWork) {
		return new Snapshot(true, deposit, fell, ToolState.PRESENT, replant, true, true, 1000, FinderState.HAS_TREE, cannotWork);
	}

	@Test void lumberjackIdlesWhateverItWasDoing() {
		assertEquals(FELL_TASK, LumberjackPlanner.plan(lumber(false, false, false, false)).action());
		assertEquals(CONTINUE_FELL, LumberjackPlanner.plan(lumber(false, true, false, false)).action());
		assertEquals(REPLANT, LumberjackPlanner.plan(lumber(false, false, true, false)).action());
		assertEquals(IDLE_AT_CAMP, LumberjackPlanner.plan(lumber(false, false, false, true)).action());
		assertEquals(IDLE_AT_CAMP, LumberjackPlanner.plan(lumber(false, true, false, true)).action());
		assertEquals(IDLE_AT_CAMP, LumberjackPlanner.plan(lumber(false, false, true, true)).action());
	}

	@Test void lumberjackStillDeposits() {
		assertEquals(YIELD, LumberjackPlanner.plan(lumber(true, true, false, true)).action());
	}

	@Test void invalidCampStillWinsFirst() {
		Snapshot s = new Snapshot(false, true, true, ToolState.PRESENT, true, true, true, 1000, FinderState.HAS_TREE, true);
		assertEquals(IDLE_AT_CAMP, LumberjackPlanner.plan(s).action());
	}

	private static FarmerPlanner.Snapshot farm(boolean deposit, boolean progress, boolean mature, boolean cannotWork) {
		return new FarmerPlanner.Snapshot(true, deposit, false, progress, 8, 0, mature, true, true, false, false, false, true, false, cannotWork);
	}

	@Test void farmerIdlesWhateverItWasDoing() {
		assertEquals(FarmerPlanner.Action.HARVEST, FarmerPlanner.decide(farm(false, false, true, false)).action());
		assertEquals(FarmerPlanner.Action.CONTINUE, FarmerPlanner.decide(farm(false, true, true, false)).action());
		assertEquals(FarmerPlanner.Action.IDLE_AT_FARMHOUSE, FarmerPlanner.decide(farm(false, false, true, true)).action());
		assertEquals(FarmerPlanner.Action.IDLE_AT_FARMHOUSE, FarmerPlanner.decide(farm(false, true, true, true)).action());
		assertEquals(FarmerPlanner.Action.YIELD, FarmerPlanner.decide(farm(true, true, true, true)).action());
	}
}
