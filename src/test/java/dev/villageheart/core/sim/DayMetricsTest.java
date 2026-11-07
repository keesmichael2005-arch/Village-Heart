package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DayMetricsTest {
	@Test
	void flatten() {
		DayMetrics d = new DayMetrics(3, 100, true, 8, 7, 1, 2, 1, 3, Map.of("minecraft:oak_log", 10L, "minecraft:bread", 4L),
				0, 0, 0,
				List.of(new DeathRecord("minecraft:zombie", true, 1), new DeathRecord("minecraft:zombie", true, 2),
						new DeathRecord("minecraft:fall", false, 3)),
				new TickStats(24000, 0.5, 0.6, 0.7), 1, 2, 3, 4, 5);
		Map<String, Double> f = d.flatten();
		for (String k : f.keySet()) assertTrue(MetricNames.isKnown(k), "unknown flattened key " + k);
		for (String k : MetricNames.KNOWN) assertTrue(f.containsKey(k), "KNOWN key not flattened: " + k);
		assertEquals(1.0, f.get("claimed"));
		assertEquals(2.0, f.get("deaths.cause.minecraft:zombie"));
		assertEquals(1.0, f.get("deaths.cause.minecraft:fall"));
		assertEquals(3.0, f.get("deaths.total"));
		assertEquals(2.0, f.get("deaths.mobs"));
		assertEquals(14.0, f.get("stockpile.total"));
		assertEquals(2.0, f.get("stockpile.distinct"));
		assertEquals(10.0, f.get("stockpile.items.minecraft:oak_log"));
		assertEquals(0.5, f.get("village_ms.mean"));
		assertEquals(24000.0, f.get("village_ms.samples"));
	}

	@Test
	void unclaimedIsZero() {
		DayMetrics d = SimFixtures.day(1, 8);
		assertEquals(1.0, d.flatten().get("claimed"));
		DayMetrics u = new DayMetrics(0, 0, false, 0, 0, 0, 0, 0, 0, Map.of(), 0, 0, 0, List.of(), TickStats.EMPTY, 0, 0, 0, 0, 0);
		assertEquals(0.0, u.flatten().get("claimed"));
	}

	@Test
	void tableLookups() {
		MetricsTable t = SimFixtures.table(8, 8, 7);
		assertEquals(3, t.lastDay());
		assertEquals(8.0, t.value("population", 0).getAsDouble());
		assertEquals(7.0, t.value("population", 3).getAsDouble());
		assertTrue(t.value("population", 4).isEmpty());
		assertTrue(t.value("population", -1).isEmpty());
		assertEquals(0.0, t.value("stockpile.items.minecraft:oak_log", 1).getAsDouble());
		assertEquals(0.0, t.value("stockpile.tag.minecraft:logs", 1).getAsDouble());
		assertTrue(t.value("nonsense", 1).isEmpty());
	}
}
