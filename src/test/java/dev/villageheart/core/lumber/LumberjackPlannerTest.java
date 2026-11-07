package dev.villageheart.core.lumber;

import static dev.villageheart.core.lumber.LumberjackPlanner.Action.*;
import static dev.villageheart.core.lumber.LumberjackPlanner.StatusChange.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.lumber.LumberjackPlanner.Decision;
import dev.villageheart.core.lumber.LumberjackPlanner.FinderState;
import dev.villageheart.core.lumber.LumberjackPlanner.Snapshot;
import dev.villageheart.core.lumber.LumberjackPlanner.ToolState;
import org.junit.jupiter.api.Test;

class LumberjackPlannerTest {
	/** A calm start: valid camp, axe, morning, a tree is ready, nothing else to do. */
	private static final Snapshot BASE = new Snapshot(true, false, false, ToolState.PRESENT, false, false, false, 1000, FinderState.HAS_TREE);

	private static Snapshot with(Snapshot s, java.util.function.UnaryOperator<Builder> f) { return f.apply(new Builder(s)).build(); }

	private static final class Builder {
		boolean campValid, deposit, fell, replant, collect, task;
		ToolState tool;
		long time;
		FinderState finder;

		Builder(Snapshot s) {
			campValid = s.campValid();
			deposit = s.depositWanted();
			fell = s.fellInProgress();
			tool = s.tool();
			replant = s.replantReady();
			collect = s.collectAvailable();
			task = s.fellingTaskAvailable();
			time = s.dayTime();
			finder = s.finder();
		}

		Builder invalid() { campValid = false; return this; }
		Builder deposit() { deposit = true; return this; }
		Builder fell() { fell = true; return this; }
		Builder tool(ToolState t) { tool = t; return this; }
		Builder replant() { replant = true; return this; }
		Builder collect() { collect = true; return this; }
		Builder task() { task = true; return this; }
		Builder time(long t) { time = t; return this; }
		Builder finder(FinderState f) { finder = f; return this; }

		Snapshot build() { return new Snapshot(campValid, deposit, fell, tool, replant, collect, task, time, finder); }
	}

	private static void expect(Snapshot s, LumberjackPlanner.Action a, LumberjackPlanner.StatusChange st) {
		Decision d = LumberjackPlanner.plan(s);
		assertEquals(a, d.action(), s.toString());
		assertEquals(st, d.status(), s.toString());
	}

	@Test
	void row1_invalidCampIdles() {
		expect(with(BASE, b -> b.invalid().deposit().fell()), IDLE_AT_CAMP, KEEP);
	}

	@Test
	void row2_aWantedDepositYieldsEvenMidFell() {
		expect(with(BASE, b -> b.deposit().fell()), YIELD, KEEP);
		expect(with(BASE, b -> b.deposit().tool(ToolState.NONE)), YIELD, KEEP);
	}

	@Test
	void row3_aFellInProgressContinuesAndIsNeverPreemptedByDropsOrReplants() {
		expect(with(BASE, b -> b.fell().collect().replant()), CONTINUE_FELL, CLEAR_ALL);
	}

	@Test
	void row3_aLostAxeInterruptsTheFell() {
		expect(with(BASE, b -> b.fell().tool(ToolState.NONE)), IDLE_AT_CAMP, NEEDS_TOOL);
	}

	@Test
	void row4_noAxeNothingToDoIsNeedsAxe() {
		expect(with(BASE, b -> b.tool(ToolState.NONE)), IDLE_AT_CAMP, NEEDS_TOOL);
	}

	@Test
	void row4_noAxeButAPendingReplantStillPlants() {
		expect(with(BASE, b -> b.tool(ToolState.NONE).replant()), REPLANT, NEEDS_TOOL);
		expect(with(BASE, b -> b.tool(ToolState.NONE).collect()), COLLECT, NEEDS_TOOL);
	}

	@Test
	void row4_anUnknownToolDoesNotChangeTheStatus() {
		expect(with(BASE, b -> b.tool(ToolState.UNKNOWN)), IDLE_AT_CAMP, KEEP);
		expect(with(BASE, b -> b.tool(ToolState.UNKNOWN).replant()), REPLANT, KEEP);
	}

	@Test
	void rows5and6_replantBeforeCollectBeforeANewFell() {
		expect(with(BASE, b -> b.replant().collect()), REPLANT, CLEAR_NEEDS_TOOL);
		expect(with(BASE, b -> b.collect()), COLLECT, CLEAR_NEEDS_TOOL);
		expect(with(BASE, b -> b.task().collect()), COLLECT, CLEAR_NEEDS_TOOL);
	}

	@Test
	void row7_anUnclaimedTaskIsResumedBeforeANewTree() {
		expect(with(BASE, b -> b.task()), FELL_TASK, CLEAR_ALL);
	}

	@Test
	void row8_aNewTreeWhileItIsEarlyEnough() {
		expect(BASE, FELL_NEW, CLEAR_ALL);
		expect(with(BASE, b -> b.time(4999)), FELL_NEW, CLEAR_ALL);
	}

	@Test
	void row8_noNewTreeFromDayTime5000ButAnInterruptedFellIsFinished() {
		expect(with(BASE, b -> b.time(5000)), IDLE_AT_CAMP, CLEAR_NEEDS_TOOL);
		expect(with(BASE, b -> b.time(8000).task()), FELL_TASK, CLEAR_ALL);
		expect(with(BASE, b -> b.time(8000).fell()), CONTINUE_FELL, CLEAR_ALL);
	}

	@Test
	void row9_aPausedFinderSearches() {
		expect(with(BASE, b -> b.finder(FinderState.PAUSED)), SEARCH, CLEAR_NEEDS_TOOL);
		expect(with(BASE, b -> b.finder(FinderState.PAUSED).time(6000)), IDLE_AT_CAMP, CLEAR_NEEDS_TOOL);
	}

	@Test
	void row10_anExhaustedFinderMeansNoWork() {
		expect(with(BASE, b -> b.finder(FinderState.EXHAUSTED)), IDLE_AT_CAMP, NO_WORK);
	}
}
