package dev.villageheart.core.needs;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.worker.Needs;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class NeedsRulesTest {
	static final HeartLocation HOUSE = new HeartLocation("minecraft:overworld", 10, 64, 10);
	static final HeartLocation BARRACKS = new HeartLocation("minecraft:overworld", 30, 64, 10);

	// ---- Needs ----
	@Test void speedFollowsTheRoadmap() {
		assertEquals(1.0, Needs.DEFAULT.speed(), 1e-9);
		assertEquals(0.5, Needs.of(1, true, true).speed(), 1e-9);
		assertEquals(0.0, Needs.of(2, true, true).speed(), 1e-9);
		assertEquals(0.0, Needs.of(7, true, true).speed(), 1e-9);
		assertEquals(0.75, Needs.of(0, false, true).speed(), 1e-9);
		assertEquals(0.375, Needs.of(1, false, true).speed(), 1e-9);
		assertEquals(0.0, Needs.of(0, true, false).speed(), 1e-9);
		assertEquals(Balance.UNHOUSED_SPEED, Needs.of(0, false, true).speed(), 1e-9);
		assertEquals(Balance.HUNGER_SPEED[1], Needs.fedFactor(1), 1e-9);
	}

	@Test void fedMustMatchHungryDays() {
		assertThrows(IllegalArgumentException.class, () -> new Needs(true, 1, true, true));
		assertThrows(IllegalArgumentException.class, () -> new Needs(false, 0, true, true));
		assertThrows(IllegalArgumentException.class, () -> new Needs(true, -1, true, true));
		assertDoesNotThrow(() -> new Needs(false, 3, true, true));
		assertEquals(new Needs(false, 3, false, true), Needs.of(3, false, true));
	}

	// ---- Diet ----
	@Test void hungerAccumulatesAndResets() {
		Diet d = Diet.fresh(100);
		assertTrue(d.fed());
		d = d.afterDinner(false, 0, 1).afterDinner(false, 0, 2).afterDinner(false, 4, 3);
		assertEquals(3, d.hungryDays());
		assertEquals(3, d.lastDinnerDay());
		assertEquals(4, d.lastNutrition());
		assertFalse(d.fed());
		d = d.afterDinner(true, 10, 4);
		assertEquals(0, d.hungryDays());
		assertTrue(d.fed());
		assertEquals(10, d.lastNutrition());
	}

	@Test void readFreshOnAbsentOrOtherClaim() {
		Diet hungry = new Diet(100, 2, 5, 0);
		assertEquals(Diet.fresh(100), Diet.read(Optional.empty(), 100));
		assertEquals(Diet.fresh(200), Diet.read(Optional.of(hungry), 200));
		assertEquals(hungry, Diet.read(Optional.of(hungry), 100));
		assertThrows(IllegalArgumentException.class, () -> new Diet(1, -1, 0, 0));
	}

	// ---- HouseBeds ----
	static long p(int x, int y, int z) { return Pos.pack(x, y, z); }

	@Test void housedInAHouseOrOwnBuilding() {
		HouseBeds beds = new HouseBeds(Map.of(HOUSE, Set.of(p(1, 64, 1), p(1, 64, 3)), BARRACKS, Set.of(p(9, 64, 9))), Set.of(HOUSE));
		assertTrue(beds.housed(p(1, 64, 1), Optional.empty()));
		assertFalse(beds.housed(p(9, 64, 9), Optional.empty()));
		assertTrue(beds.housed(p(9, 64, 9), Optional.of(BARRACKS)));
		assertFalse(beds.housed(p(9, 64, 9), Optional.of(HOUSE)));
		assertFalse(beds.housed(p(5, 64, 5), Optional.of(BARRACKS)));
		assertEquals(List.of(p(1, 64, 1), p(1, 64, 3)), beds.houseBeds());
		assertTrue(HouseBeds.empty().houseBeds().isEmpty());
	}

	// ---- Rehome ----
	@Test void rehomePrefersFreeThenAwakeResident() {
		Pos w = new Pos(0, 64, 0);
		var near = new Rehome.BedState(new Pos(3, 64, 0), Rehome.Holder.RESIDENT_AWAKE);
		var far = new Rehome.BedState(new Pos(9, 64, 0), Rehome.Holder.NONE);
		var asleep = new Rehome.BedState(new Pos(1, 64, 0), Rehome.Holder.RESIDENT_ASLEEP);
		var worker = new Rehome.BedState(new Pos(2, 64, 0), Rehome.Holder.WORKER);
		assertEquals(new Rehome.Choice(far.pos(), false), Rehome.choose(w, List.of(near, far, asleep, worker)).orElseThrow());
		assertEquals(new Rehome.Choice(near.pos(), true), Rehome.choose(w, List.of(near, asleep, worker)).orElseThrow());
		assertTrue(Rehome.choose(w, List.of(asleep, worker)).isEmpty());
		assertTrue(Rehome.choose(w, List.of()).isEmpty());
	}

	@Test void rehomeNearestWithTiesByPosition() {
		Pos w = new Pos(0, 64, 0);
		var a = new Rehome.BedState(new Pos(0, 64, 4), Rehome.Holder.NONE);
		var b = new Rehome.BedState(new Pos(4, 64, 0), Rehome.Holder.NONE);
		var c = new Rehome.BedState(new Pos(2, 64, 0), Rehome.Holder.NONE);
		assertEquals(c.pos(), Rehome.choose(w, List.of(a, b, c)).orElseThrow().bed());
		assertEquals(a.pos(), Rehome.choose(w, List.of(b, a)).orElseThrow().bed());
	}
}
