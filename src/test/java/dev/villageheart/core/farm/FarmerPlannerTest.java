package dev.villageheart.core.farm;

import static dev.villageheart.core.farm.FarmerPlanner.Action.*;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.farm.FarmerPlanner.Action;
import dev.villageheart.core.farm.FarmerPlanner.Decision;
import dev.villageheart.core.farm.FarmerPlanner.Snapshot;
import dev.villageheart.core.farm.FarmerPlanner.StatusChange;
import org.junit.jupiter.api.Test;

class FarmerPlannerTest {
	/** A builder over a snapshot with nothing going on. */
	private static final class S {
		boolean valid = true, deposit, blocked, progress, mature, collect, plant, fetch, ready, bone, swept = true, none;
		int free = 8, surplus;

		Snapshot snap() { return new Snapshot(valid, deposit, blocked, progress, free, surplus, mature, collect, plant, fetch, ready, bone, swept, none); }

		Decision d() { return FarmerPlanner.decide(snap()); }
	}

	private static Action act(S s) { return s.d().action(); }

	@Test
	void invalidWorkplaceIdles() {
		S s = new S();
		s.valid = false;
		s.mature = true;
		s.deposit = true;
		assertEquals(new Decision(IDLE_AT_FARMHOUSE, StatusChange.KEEP), s.d());
	}

	@Test
	void aWantedTripBeatsHarvesting() {
		S s = new S();
		s.deposit = true;
		s.mature = true;
		assertEquals(YIELD, act(s));
	}

	@Test
	void storageFullDoesNotLoopOnYieldAndStopsHarvest() {
		S s = new S();
		s.deposit = true;
		s.blocked = true;
		s.mature = true;
		s.bone = true;
		assertEquals(BONEMEAL, act(s), "no harvest while Storage is full; bone meal goes on");
		s.bone = false;
		assertEquals(IDLE_AT_FARMHOUSE, act(s));
	}

	@Test
	void anActionInProgressContinues() {
		S s = new S();
		s.progress = true;
		s.mature = true;
		assertEquals(CONTINUE, act(s));
	}

	@Test
	void slotPressureComposts() {
		S s = new S();
		s.free = 2;
		s.surplus = 40;
		s.mature = true;
		assertEquals(COMPOST, act(s), "compost before harvesting more");
		s.free = 3;
		assertEquals(HARVEST, act(s));
		s.free = 1;
		s.surplus = 0;
		assertEquals(HARVEST, act(s), "no surplus: nothing to compost");
	}

	@Test
	void harvestBeatsCollectAndPlant() {
		S s = new S();
		s.mature = true;
		s.collect = true;
		s.plant = true;
		s.surplus = 30;
		assertEquals(HARVEST, act(s));
		s.mature = false;
		assertEquals(COLLECT, act(s));
		s.collect = false;
		assertEquals(PLANT, act(s));
		s.fetch = true;
		assertEquals(FETCH, act(s));
	}

	@Test
	void readyComposterBeatsBoneMeal() {
		S s = new S();
		s.ready = true;
		s.bone = true;
		assertEquals(COMPOST, act(s));
	}

	@Test
	void smallSurplusWaitsBehindBoneMeal() {
		S s = new S();
		s.surplus = 5;
		s.bone = true;
		assertEquals(BONEMEAL, act(s));
		s.bone = false;
		assertEquals(COMPOST, act(s), "composts when nothing else is left");
		s.surplus = 8;
		s.bone = true;
		assertEquals(COMPOST, act(s), "a batch is composted before bone meal");
	}

	@Test
	void noFarmlandIsOnlyReportedAfterACompleteSweep() {
		S s = new S();
		s.none = true;
		assertEquals(new Decision(IDLE_AT_FARMHOUSE, StatusChange.NO_WORK), s.d());
		s.swept = false;
		assertEquals(new Decision(IDLE_AT_FARMHOUSE, StatusChange.KEEP), s.d());
	}

	@Test
	void waitingForCropsToGrowIsNotNoWork() {
		S s = new S();
		assertEquals(new Decision(IDLE_AT_FARMHOUSE, StatusChange.CLEAR), s.d());
	}
}
