package dev.villageheart.core.cook;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.cook.CookPlanner.Batch;
import dev.villageheart.core.cook.CookPlanner.CookDecision;
import dev.villageheart.core.cook.CookPlanner.CookDecision.Cook;
import dev.villageheart.core.cook.CookPlanner.CookDecision.Fetch;
import dev.villageheart.core.cook.CookPlanner.CookDecision.Idle;
import dev.villageheart.core.cook.CookPlanner.CookDecision.Yield;
import dev.villageheart.core.cook.CookPlanner.CookHint;
import dev.villageheart.core.cook.CookPlanner.Stock;
import dev.villageheart.core.cook.CookPlanner.TripState;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CookPlannerTest {
	private static final Map<String, Integer> HEAT = Map.of("minecraft:coal", 1600, "minecraft:charcoal", 1600, "minecraft:oak_log", 300);
	private static final List<String> ORDER = List.of("minecraft:charcoal", "minecraft:coal", "minecraft:oak_log");

	/** Mutable builder for a planner input. */
	private static final class B {
		Map<String, Long> available = new HashMap<>();
		Map<String, Integer> carried = new HashMap<>();
		int bank;
		long dayTime = 3000;
		double speed = 1.0;
		boolean valid = true;
		TripState trip = TripState.NONE;
		boolean returning;
		int freeSlots = 8;
		Optional<Batch> current = Optional.empty();

		B stock(String id, long n) { available.put(id, n); return this; }
		B carry(String id, int n) { carried.put(id, n); return this; }
		B bank(int b) { bank = b; return this; }
		B at(long t) { dayTime = t; return this; }

		Stock build() {
			return new Stock(available, carried, HEAT, ORDER, bank, dayTime, speed, valid, trip, returning, freeSlots, 200, 60, current);
		}

		CookDecision decide(boolean depositNow) { return CookPlanner.decide(build(), depositNow); }
	}

	private static CookRecipe recipe(String id) {
		return CookRecipes.ALL.stream().filter(r -> r.id().equals(id)).findFirst().orElseThrow();
	}

	@Test
	void row1InvalidKitchenHandsBack() {
		B b = new B().carry("minecraft:beef", 4);
		b.valid = false;
		assertEquals(new Idle(CookHint.OK, true), b.decide(false));
	}

	@Test
	void row2SpeedZeroIsTooHungryEvenWithSomethingToHandIn() {
		B b = new B().stock("minecraft:beef", 8).stock("minecraft:coal", 1);
		b.speed = 0;
		assertEquals(new Idle(CookHint.TOO_HUNGRY, false), b.decide(true), "row 2 wins over the deposit (the deposit trip is its own behaviour)");
	}

	@Test
	void row3DepositUnlessStorageIsBlocked() {
		B b = new B().carry("minecraft:cooked_beef", 8);
		assertEquals(new Yield(), b.decide(true));
		b.trip = TripState.FULL;
		assertNotEquals(new Yield(), b.decide(true));
		b.trip = TripState.UNREACHABLE;
		assertNotEquals(new Yield(), b.decide(true));
	}

	@Test
	void aBatchInProgressKeepsCookingNotYielding() {
		// 7 beef and 1 cooked beef of the current batch are carried: depositNow is false for kept items, so the planner resumes
		B b = new B().carry("minecraft:beef", 7).carry("minecraft:cooked_beef", 1).bank(200);
		b.current = Optional.of(new Batch(recipe("cooked_beef"), 7, "minecraft:coal"));
		assertEquals(new Cook(recipe("cooked_beef"), 1), b.decide(false), "heat for one unit in the bank");
		b.bank = 1400;
		assertEquals(new Cook(recipe("cooked_beef"), 7), b.decide(false));
	}

	@Test
	void row4ResumesFromCarriedInputsAfterAReload() {
		B b = new B().carry("minecraft:wheat", 3).bank(200);
		assertEquals(new Cook(recipe("bread"), 1), b.decide(false));
		B c = new B().carry("minecraft:wheat", 3).carry("minecraft:coal", 1);
		assertEquals(new Cook(recipe("bread"), 1), c.decide(false), "carried fuel counts as heat");
	}

	@Test
	void row4bAfterTheCutoffEverythingGoesBack() {
		B b = new B().carry("minecraft:beef", 4).bank(1600).at(10600);
		assertEquals(new Idle(CookHint.OK, true), b.decide(false));
		B c = new B().carry("minecraft:coal", 1).at(10600);
		assertEquals(new Idle(CookHint.OK, true), c.decide(false));
	}

	@Test
	void row5CarriedInputsNoHeatFetchesFuel() {
		B b = new B().carry("minecraft:beef", 4).stock("minecraft:coal", 3);
		assertEquals(new Fetch(recipe("cooked_beef"), 4, Map.of(), "minecraft:coal", 1), b.decide(false));
		B c = new B().carry("minecraft:beef", 4);
		assertEquals(new Idle(CookHint.NEEDS_FUEL, true), c.decide(false), "no fuel anywhere: hand the beef back");
	}

	@Test
	void row6StorageFullStartsNoNewBatch() {
		B b = new B().stock("minecraft:beef", 8).stock("minecraft:coal", 1);
		b.trip = TripState.FULL;
		assertEquals(new Idle(CookHint.OK, false), b.decide(false));
	}

	@Test
	void row7TooLateForAnyUnit() {
		B b = new B().stock("minecraft:beef", 8).stock("minecraft:coal", 1).at(10400);
		assertEquals(new Idle(CookHint.OK, false), b.decide(false), "done for today, fuel is there");
		B c = new B().stock("minecraft:beef", 8).at(10400);
		assertEquals(new Idle(CookHint.NEEDS_FUEL, false), c.decide(false), "the reason does not vanish late in the day");
		B d = new B().at(10400);
		assertEquals(new Idle(CookHint.NO_WORK, false), d.decide(false));
	}

	@Test
	void row8StartsABatchAndFetches() {
		B b = new B().stock("minecraft:beef", 10).stock("minecraft:wheat", 9).stock("minecraft:coal", 4);
		assertEquals(new Fetch(recipe("cooked_beef"), 8, Map.of("minecraft:beef", 8), "minecraft:coal", 1), b.decide(false));
		B c = new B().stock("minecraft:wheat", 9).stock("minecraft:coal", 4).bank(1200);
		assertEquals(new Fetch(recipe("bread"), 3, Map.of("minecraft:wheat", 9), "", 0), c.decide(false), "the bank covers three loaves");
	}

	@Test
	void row8NoFuelIsNeedsFuel() {
		B b = new B().stock("minecraft:beef", 10).stock("minecraft:wheat", 9);
		assertEquals(new Idle(CookHint.NEEDS_FUEL, false), b.decide(false));
	}

	@Test
	void row9NothingToCookIsNoWork() {
		assertEquals(new Idle(CookHint.NO_WORK, false), new B().stock("minecraft:coal", 4).decide(false));
		assertEquals(new Idle(CookHint.NO_WORK, false), new B().decide(false));
	}

	@Test
	void theBatchStopsWhereTheTimeAllows() {
		// day time 7800, walk 200: (8500 - 7800 - 200) / 125 = 4 units, with work ending at 9000
		B b = new B().stock("minecraft:beef", 8).stock("minecraft:coal", 1).at(7800);
		Fetch f = (Fetch) b.decide(false);
		assertEquals(4, f.units());
		assertEquals(Map.of("minecraft:beef", 4), f.inputs());
	}

	@Test
	void hungerHalvesTheTimeBudget() {
		B b = new B().stock("minecraft:beef", 8).stock("minecraft:coal", 1).at(7800);
		b.speed = 0.5;
		assertEquals(2, ((Fetch) b.decide(false)).units());
	}

	@Test
	void soupWithEightFreeSlotsIsABatchOfFive() {
		B b = new B().stock("minecraft:beetroot", 60).stock("minecraft:bowl", 10).stock("minecraft:coal", 2);
		Fetch f = (Fetch) b.decide(false);
		assertEquals("beetroot_soup", f.r().id());
		assertEquals(5, f.units());
		assertEquals(Map.of("minecraft:beetroot", 30, "minecraft:bowl", 5), f.inputs());
	}

	@Test
	void beetrootSoupNeedsSixAndABowl() {
		assertEquals(new Idle(CookHint.NO_WORK, false), new B().stock("minecraft:beetroot", 5).stock("minecraft:bowl", 1).stock("minecraft:coal", 1).decide(false));
		assertEquals(new Idle(CookHint.NO_WORK, false), new B().stock("minecraft:beetroot", 6).stock("minecraft:coal", 1).decide(false), "no bowl");
		assertEquals("beetroot_soup", ((Fetch) new B().stock("minecraft:beetroot", 6).stock("minecraft:bowl", 1).stock("minecraft:coal", 1).decide(false)).r().id());
	}

	@Test
	void whatOthersReservedIsNotAvailableToThePlanner() {
		// the glue hands the planner the stock minus the others' reservations, so reserved beef is simply not there
		assertEquals(new Idle(CookHint.NO_WORK, false), new B().stock("minecraft:beef", 0).stock("minecraft:coal", 4).decide(false));
	}

	@Test
	void theHighestGainComesFirst() {
		B b = new B().stock("minecraft:potato", 8).stock("minecraft:wheat", 9).stock("minecraft:cod", 2).stock("minecraft:coal", 4);
		assertEquals("bread", ((Fetch) b.decide(false)).r().id(), "gain 5 beats the potato's 4 and the cod's 3");
	}

	@Test
	void slotsShrinkTheBatch() {
		B b = new B().stock("minecraft:beetroot", 60).stock("minecraft:bowl", 10).stock("minecraft:coal", 2);
		b.freeSlots = 4;
		assertEquals(1, ((Fetch) b.decide(false)).units(), "4 slots - 2 inputs - 1 fuel = 1 soup");
	}
}
