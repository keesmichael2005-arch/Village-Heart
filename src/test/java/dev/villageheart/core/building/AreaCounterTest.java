package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.building.AreaCounter.AreaCount;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.FakeGrid;
import dev.villageheart.core.room.Pos;
import org.junit.jupiter.api.Test;

class AreaCounterTest {
	static final Cell LEAVES = Cell.solid("minecraft:oak_leaves", "#natural");
	static final Pos CENTRE = new Pos(0, 0, 0);

	/** 40 leaf cells: x 1..8, y 0..4, z 0 (the farthest is 8.9 away). */
	static FakeGrid forty() {
		FakeGrid g = new FakeGrid();
		for (int x = 1; x <= 8; x++) for (int y = 0; y <= 4; y++) g.set(x, y, 0, LEAVES);
		return g;
	}

	static int count(FakeGrid g) { return AreaCounter.count(g, CENTRE, Balance.HUNTING_GROUND_RADIUS, "#natural").n(); }

	@Test void countsInsideTheSphere() {
		assertEquals(40, count(forty()));
	}

	@Test void edgeIsInclusive() {
		FakeGrid g = forty();
		g.set(12, 0, 0, LEAVES);
		assertEquals(41, count(g));
		g.set(13, 0, 0, LEAVES);
		assertEquals(41, count(g), "13 is outside");
	}

	@Test void sphereNotCube() {
		FakeGrid g = forty();
		g.set(9, 9, 0, LEAVES); // 12.7
		assertEquals(40, count(g));
	}

	@Test void unloadedInsideTheSphere() {
		AreaCount c = AreaCounter.count(forty().unloadedBeyondX(5), CENTRE, 12, "#natural");
		assertNotNull(c.notLoaded());
	}

	@Test void unloadedOutsideTheSphereIsIgnored() {
		AreaCount c = AreaCounter.count(forty().unloadedBeyondX(12), CENTRE, 12, "#natural");
		assertNull(c.notLoaded());
		assertEquals(40, c.n());
	}

	@Test void targetsInTheYard() {
		FakeGrid g = new FakeGrid();
		g.set(3, 0, 0, Cell.solid("minecraft:target"));
		g.set(0, 6, 0, Cell.solid("minecraft:target"));
		g.set(0, 7, 0, Cell.solid("minecraft:target"));
		assertEquals(2, AreaCounter.count(g, CENTRE, Balance.TRAINING_YARD_RADIUS, "minecraft:target").n());
	}
}
