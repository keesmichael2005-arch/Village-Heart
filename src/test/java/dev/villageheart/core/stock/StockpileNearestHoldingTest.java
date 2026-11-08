package dev.villageheart.core.stock;

import static dev.villageheart.core.stock.FakeInventory.site;
import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import java.util.List;
import org.junit.jupiter.api.Test;

/** M20 (A7): the Storage a builder walks to is the nearest one that holds the item. */
class StockpileNearestHoldingTest {
	private static final Pos ORIGIN = new Pos(0, 0, 0);

	@Test
	void nearestSiteThatHoldsTheItem() {
		FakeInventory near = new FakeInventory().set(0, "bread", 5);
		FakeInventory far = new FakeInventory().set(0, "oak_planks", 40);
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 2, near), site(2, 30, far)));
		assertEquals(new Pos(2, 0, 0), s.nearestHolding(ORIGIN, k -> k.equals("bread")).orElseThrow().anchor());
		assertEquals(new Pos(30, 0, 0), s.nearestHolding(ORIGIN, k -> k.endsWith("_planks")).orElseThrow().anchor());
		assertEquals(new Pos(2, 0, 0), s.nearestHolding(new Pos(100, 0, 0), k -> k.equals("bread")).orElseThrow().anchor(),
				"the only site that holds bread, however far the worker is");
		assertTrue(s.nearestHolding(ORIGIN, k -> k.equals("diamond")).isEmpty());
	}

	@Test
	void nearestWinsWhenBothHold() {
		FakeInventory a = new FakeInventory().set(0, "oak_planks", 1);
		FakeInventory b = new FakeInventory().set(3, "oak_planks", 64);
		Stockpile<String> s = new Stockpile<>(List.of(site(1, 40, a), site(2, 6, b)));
		assertEquals(new Pos(6, 0, 0), s.nearestHolding(ORIGIN, k -> true).orElseThrow().anchor());
		assertEquals(new Pos(40, 0, 0), s.nearestHolding(new Pos(50, 0, 0), k -> true).orElseThrow().anchor());
	}

	@Test
	void emptyStockpileHoldsNothing() {
		assertTrue(new Stockpile<String>(List.of()).nearestHolding(ORIGIN, k -> true).isEmpty());
		assertTrue(new Stockpile<>(List.of(site(1, 1, new FakeInventory()))).nearestHolding(ORIGIN, k -> true).isEmpty());
	}
}
