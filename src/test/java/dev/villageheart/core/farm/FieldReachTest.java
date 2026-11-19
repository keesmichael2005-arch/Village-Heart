package dev.villageheart.core.farm;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The connected field: seed disc, side-by-side steps of one block, cap 4096. */
class FieldReachTest {
	private static final Pos COMPOSTER = new Pos(0, 64, 0);

	private static Set<Pos> cells(Pos... ps) {
		Set<Pos> s = new HashSet<>();
		for (Pos p : ps) s.add(p);
		return s;
	}

	private static boolean has(List<Pos> field, int x, int y, int z) {
		return field.contains(new Pos(x, y, z));
	}

	@Test
	void theSeedDiscIsTheOldRange() {
		Set<Pos> farm = cells(new Pos(24, 64, 0), new Pos(25, 64, 0), new Pos(0, 72, 0), new Pos(0, 73, 0));
		// 25 and dy +9 are outside the disc and not connected to a seed here (the edge cells are the only farmland,
		// and 25 touches 24, 73 touches 72 — those pairs ARE connected). Isolate them:
		List<Pos> onlyEdge = FieldReach.select(List.of(COMPOSTER), cells(new Pos(24, 64, 0)));
		assertTrue(has(onlyEdge, 24, 64, 0));
		List<Pos> onlyHigh = FieldReach.select(List.of(COMPOSTER), cells(new Pos(0, 72, 0)));
		assertTrue(has(onlyHigh, 0, 72, 0));
		List<Pos> outsideAlone = FieldReach.select(List.of(COMPOSTER), cells(new Pos(25, 64, 0), new Pos(0, 73, 0)));
		assertTrue(outsideAlone.isEmpty(), "farmland outside the disc with no seed is not a field: " + outsideAlone);
		assertFalse(farm.isEmpty());
	}

	@Test
	void aSideStepOutsideTheDiscIsIncluded() {
		List<Pos> field = FieldReach.select(List.of(COMPOSTER), cells(new Pos(24, 64, 0), new Pos(25, 64, 0)));
		assertTrue(has(field, 24, 64, 0));
		assertTrue(has(field, 25, 64, 0), "the cell touching the disc is part of the field");
	}

	@Test
	void aStepOfOneBlockUpOrDownIsIncluded() {
		List<Pos> up = FieldReach.select(List.of(COMPOSTER), cells(new Pos(24, 64, 0), new Pos(25, 65, 0)));
		assertTrue(has(up, 25, 65, 0));
		List<Pos> down = FieldReach.select(List.of(COMPOSTER), cells(new Pos(24, 64, 0), new Pos(25, 63, 0)));
		assertTrue(has(down, 25, 63, 0));
		List<Pos> stacked = FieldReach.select(List.of(COMPOSTER), cells(new Pos(0, 72, 0), new Pos(0, 73, 0)));
		assertFalse(has(stacked, 0, 73, 0), "the cell directly above shares a top, not a side");
	}

	@Test
	void aTwoBlockRiseStopsTheField() {
		List<Pos> field = FieldReach.select(List.of(COMPOSTER), cells(new Pos(0, 72, 0), new Pos(0, 74, 0)));
		assertTrue(has(field, 0, 72, 0));
		assertFalse(has(field, 0, 74, 0), "two blocks up is not a step");
	}

	@Test
	void aDiagonalTouchStopsTheField() {
		List<Pos> field = FieldReach.select(List.of(COMPOSTER), cells(new Pos(24, 64, 0), new Pos(25, 64, 1)));
		assertTrue(has(field, 24, 64, 0));
		assertFalse(has(field, 25, 64, 1));
	}

	@Test
	void aOneBlockGapStopsTheField() {
		List<Pos> field = FieldReach.select(List.of(COMPOSTER), cells(new Pos(24, 64, 0), new Pos(26, 64, 0)));
		assertTrue(has(field, 24, 64, 0));
		assertFalse(has(field, 26, 64, 0), "path, fence or water in the gap is simply not farmland");
	}

	@Test
	void everyComposterOfTheFarmhouseIsOneField() {
		Pos other = new Pos(100, 64, 0);
		Set<Pos> farm = cells(new Pos(24, 64, 0), new Pos(25, 64, 0), new Pos(124, 64, 0));
		List<Pos> field = FieldReach.select(List.of(COMPOSTER, other), farm);
		assertTrue(has(field, 25, 64, 0));
		assertTrue(has(field, 124, 64, 0), "the second composter's disc is the same field");
	}

	@Test
	void theCapKeepsTheNearestAndLeavesTheRest() {
		assertEquals(4096, Balance.MAX_FIELD_CELLS);
		Set<Pos> farm = new HashSet<>();
		for (int x = 0; x <= 5000; x++) farm.add(new Pos(x, 64, 0));
		List<Pos> field = FieldReach.select(List.of(COMPOSTER), farm);
		assertEquals(4096, field.size());
		assertTrue(has(field, 0, 64, 0));
		assertTrue(has(field, 24, 64, 0));
		assertTrue(has(field, 4095, 64, 0));
		assertFalse(has(field, 4096, 64, 0));
	}
}
