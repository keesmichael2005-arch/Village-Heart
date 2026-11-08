package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.sim.MetricsJson.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricsJsonTest {
	private static DayMetrics rich(int day) {
		Map<String, Long> items = new LinkedHashMap<>();
		items.put("minecraft:wheat", 5L);
		items.put("minecraft:oak_log", 64L);
		items.put("minecraft:bread", 5L);
		return new DayMetrics(day, 41L + 24000L * day, true, 8, 7, 1, 2, 1, 3, items, 12, 3.5, 4,
				List.of(new DeathRecord("minecraft:fall", false, 100), new DeathRecord("zombified", true, 200)),
				new TickStats(24000, 3.0E-4, 4.0E-4, 0.7), 1.5, 470.25, 51.2, 900, 41_000_000L);
	}

	private static Report report() {
		List<DayMetrics> days = new ArrayList<>();
		for (int d = 1; d <= 5; d++) days.add(rich(d));
		return new Report("bare", SimStatus.PASS, null, true, 120041, 3, null, 1, "normal", 5, "1.21.11",
				new VillagePositions(new Pos(640, 0, 816), new Pos(639, 66, 821), new Pos(642, 64, 821)), 41,
				List.of(2), rich(0), days,
				List.of(new AssertionResult("claimed == 1 on every day", true, "5/5 days")));
	}

	@Test
	void roundTrip() {
		Report r = report();
		assertEquals(r, MetricsJson.read(MetricsJson.write(r)));
		Report withStop = new Report("bare", SimStatus.RUNNING, "boom\nstack", false, 1, 0, 2, 1, "normal", 5, "1.21.11",
				VillagePositions.NONE, 0, List.of(), null, List.of(), List.of());
		assertEquals(withStop, MetricsJson.read(MetricsJson.write(withStop)));
	}

	@Test
	void nanBecomesNullAndReadsAsNan() {
		DayMetrics d = rich(1);
		DayMetrics nan = new DayMetrics(d.day(), d.gameTime(), d.claimed(), d.population(), d.adults(), d.babies(), d.employed(),
				0, 0, Map.of(), 0, Double.NaN, 0, List.of(), d.villageMs(), 1, 1, 1, 1, 1);
		Report r = new Report("x", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", 1, "1.21.11", VillagePositions.NONE,
				0, List.of(), null, List.of(nan), List.of());
		String json = MetricsJson.write(r);
		assertTrue(json.contains("\"days_left\": null"), json);
		assertTrue(Double.isNaN(MetricsJson.read(json).days().get(0).foodDaysLeft()));
		assertEquals(r, MetricsJson.read(json));
	}

	@SuppressWarnings("unchecked")
	@Test
	void pinnedKeys() {
		Map<String, Object> m = (Map<String, Object>) Json.parse(MetricsJson.write(report()));
		assertEquals(List.of("schema", "scenario", "status", "error", "clean_shutdown", "heartbeat", "actions_done",
				"stopped_after_day", "seed", "difficulty", "days_requested", "minecraft", "village", "g0", "reloads",
				"baseline", "days", "assertions"), List.copyOf(m.keySet()));
		assertEquals(List.of("structure", "bell", "heart"), List.copyOf(((Map<String, Object>) m.get("village")).keySet()));
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) m.get("days")).get(0);
		assertEquals(List.of("day", "game_time", "claimed", "population", "adults", "babies", "employed", "buildings",
				"stockpile", "food", "treasury", "deaths", "village_ms", "server", "world", "wall_seconds", "work", "worker_status",
				"stockpile_tags"), List.copyOf(day.keySet()));
		assertEquals(List.of("valid", "invalid"), List.copyOf(((Map<String, Object>) day.get("buildings")).keySet()));
		assertEquals(List.of("total", "distinct", "items"), List.copyOf(((Map<String, Object>) day.get("stockpile")).keySet()));
		assertEquals(List.of("nutrition", "days_left"), List.copyOf(((Map<String, Object>) day.get("food")).keySet()));
		assertEquals(List.of("total", "mobs", "by_cause", "list"), List.copyOf(((Map<String, Object>) day.get("deaths")).keySet()));
		assertEquals(List.of("mean", "p95", "max", "samples"), List.copyOf(((Map<String, Object>) day.get("village_ms")).keySet()));
		assertEquals(List.of("mspt", "tps", "heap_used_mb"), List.copyOf(((Map<String, Object>) day.get("server")).keySet()));
		assertEquals(List.of("save_bytes"), List.copyOf(((Map<String, Object>) day.get("world")).keySet()));
		Map<String, Object> death = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) day.get("deaths")).get("list")).get(0);
		assertEquals(List.of("cause", "by_mob", "game_time"), List.copyOf(death.keySet()));
		assertEquals(List.of("text", "passed", "detail"),
				List.copyOf(((Map<String, Object>) ((List<Object>) m.get("assertions")).get(0)).keySet()));
		assertEquals(1L, m.get("schema"));
		assertNull(m.get("stopped_after_day"));
	}

	@Test
	void workFamiliesRoundTripAndOldReportsReadEmpty() {
		DayMetrics d = rich(1);
		DayMetrics withWork = new DayMetrics(d.day(), d.gameTime(), d.claimed(), d.population(), d.adults(), d.babies(), d.employed(),
				1, 3, d.stockpile(), 12, 3.5, 4, d.deaths(), d.villageMs(), 1.5, 470.25, 51.2, 900, 41_000_000L,
				Map.of("lumberjack.logs", 24L, "lumberjack.trees", 4L), Map.of("needs_tool", 1L), Map.of("minecraft:logs", 30L));
		Report r = new Report("x", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", 1, "1.21.11", VillagePositions.NONE,
				0, List.of(), null, List.of(withWork), List.of());
		String json = MetricsJson.write(r);
		assertTrue(json.contains("\"lumberjack.logs\": 24"), json);
		assertEquals(r, MetricsJson.read(json));
		// a report written before M9 has none of the three keys: they read as empty maps
		@SuppressWarnings("unchecked")
		Map<String, Object> m = (Map<String, Object>) Json.parse(json);
		@SuppressWarnings("unchecked")
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) m.get("days")).get(0);
		day.remove("work");
		day.remove("worker_status");
		day.remove("stockpile_tags");
		DayMetrics old = MetricsJson.read(Json.write(m, true)).days().get(0);
		assertEquals(Map.of(), old.work());
		assertEquals(Map.of(), old.workerStatus());
		assertEquals(Map.of(), old.stockpileTags());
	}

	@SuppressWarnings("unchecked")
	@Test
	void itemsSorted() {
		Map<String, Object> m = (Map<String, Object>) Json.parse(MetricsJson.write(report()));
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) m.get("days")).get(0);
		Map<String, Object> st = (Map<String, Object>) day.get("stockpile");
		assertEquals(List.of("minecraft:oak_log", "minecraft:bread", "minecraft:wheat"),
				List.copyOf(((Map<String, Object>) st.get("items")).keySet()));
		assertEquals(74L, st.get("total"));
		assertEquals(3L, st.get("distinct"));
	}
}
