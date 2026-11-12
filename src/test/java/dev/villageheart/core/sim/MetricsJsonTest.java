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
		assertEquals(List.of("day", "game_time", "claimed", "population", "adults", "babies", "employed", "tier", "buildings",
				"stockpile", "food", "housing", "treasury", "wages", "deaths", "village_ms", "server", "world", "wall_seconds", "work", "worker_status",
				"stockpile_tags", "raid", "golems", "defence", "siege", "lure", "threat", "entity_ms"), List.copyOf(day.keySet()));
		assertEquals(List.of("valid", "invalid"), List.copyOf(((Map<String, Object>) day.get("buildings")).keySet()));
		assertEquals(List.of("total", "distinct", "items"), List.copyOf(((Map<String, Object>) day.get("stockpile")).keySet()));
		assertEquals(List.of("nutrition", "days_left", "dinners", "diners", "fed", "hungry", "eaten_nutrition", "eaten_items", "max_hungry_days"),
				List.copyOf(((Map<String, Object>) day.get("food")).keySet()));
		assertEquals(List.of("housed", "house_beds"), List.copyOf(((Map<String, Object>) day.get("housing")).keySet()));
		assertEquals(List.of("total", "mobs", "by_cause", "list", "logged"), List.copyOf(((Map<String, Object>) day.get("deaths")).keySet()));
		assertEquals(List.of("started", "active", "victories", "losses", "stopped", "gone", "waves", "waves_total", "villagers_at_start",
				"villagers_at_end", "guards_at_start", "guards_qualified_at_start", "guards_wounded_at_start", "guards_unloaded_at_start",
				"villager_deaths", "raiders_killed", "origin_random", "early", "reward", "extra_raiders", "spawn_min_distance", "callup_ticks"),
				List.copyOf(((Map<String, Object>) day.get("raid")).keySet()));
		assertEquals(List.of("in_claim"), List.copyOf(((Map<String, Object>) day.get("golems")).keySet()));
		assertEquals(List.of("cells", "unseen", "breached", "cracked", "breached_noon", "blocks_broken", "broken", "repairs", "repair_items"),
				List.copyOf(((Map<String, Object>) day.get("defence")).keySet()));
		assertEquals(List.of("zombie", "ravager", "vindicator", "creeper", "other"),
				List.copyOf(((Map<String, Object>) ((Map<String, Object>) day.get("defence")).get("broken")).keySet()));
		assertEquals(List.of("started", "zombies", "killed", "villager_deaths", "paths"),
				List.copyOf(((Map<String, Object>) day.get("siege")).keySet()));
		assertEquals(List.of("max_lured"), List.copyOf(((Map<String, Object>) day.get("lure")).keySet()));
		assertEquals(List.of("score"), List.copyOf(((Map<String, Object>) day.get("threat")).keySet()));
		assertEquals(List.of("mean", "p95", "max", "samples"), List.copyOf(((Map<String, Object>) day.get("entity_ms")).keySet()));
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

	@Test
	void needsRoundTripAndOldReportsReadZeros() {
		DayMetrics d = rich(1);
		DayMetrics.NeedsDay nd = new DayMetrics.NeedsDay(1, 9, 8, 1, 90, 30, 2, 6, 8);
		DayMetrics with = new DayMetrics(d.day(), d.gameTime(), d.claimed(), d.population(), d.adults(), d.babies(), d.employed(),
				1, 3, d.stockpile(), 12, 3.5, 4, d.deaths(), d.villageMs(), 1.5, 470.25, 51.2, 900, 41_000_000L,
				Map.of(), Map.of(), Map.of(), nd);
		Report r = new Report("x", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", 1, "1.21.11", VillagePositions.NONE,
				0, List.of(), null, List.of(with), List.of());
		String json = MetricsJson.write(r);
		assertEquals(r, MetricsJson.read(json));
		Map<String, Double> flat = with.flatten();
		assertEquals(9.0, flat.get("food.diners"));
		assertEquals(90.0, flat.get("food.eaten_nutrition"));
		assertEquals(2.0, flat.get("food.max_hungry_days"));
		assertEquals(8.0, flat.get("housing.house_beds"));
		for (String k : List.of("food.dinners", "food.diners", "food.fed", "food.hungry", "food.eaten_nutrition", "food.eaten_items",
				"food.max_hungry_days", "housing.housed", "housing.house_beds")) {
			assertTrue(MetricNames.isKnown(k), k);
			assertTrue(flat.containsKey(k), k);
		}
		// a report written before M11 has none of these keys: they read as zeros
		@SuppressWarnings("unchecked")
		Map<String, Object> m = (Map<String, Object>) Json.parse(json);
		@SuppressWarnings("unchecked")
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) m.get("days")).get(0);
		day.remove("housing");
		@SuppressWarnings("unchecked")
		Map<String, Object> food = (Map<String, Object>) day.get("food");
		for (String k : List.of("dinners", "diners", "fed", "hungry", "eaten_nutrition", "eaten_items", "max_hungry_days")) food.remove(k);
		assertEquals(DayMetrics.NeedsDay.ZERO, MetricsJson.read(Json.write(m, true)).days().get(0).needs());
	}

	@Test
	void wagesRoundTripPinnedKeysAndOldReportsReadZeros() {
		DayMetrics d = rich(1);
		DayMetrics.WageDay wd = new DayMetrics.WageDay(1, 3, 2, 2, 1);
		DayMetrics with = new DayMetrics(d.day(), d.gameTime(), d.claimed(), d.population(), d.adults(), d.babies(), d.employed(),
				1, 3, d.stockpile(), 12, 3.5, 4, d.deaths(), d.villageMs(), 1.5, 470.25, 51.2, 900, 41_000_000L,
				Map.of(), Map.of(), Map.of(), DayMetrics.NeedsDay.ZERO, wd);
		Report r = new Report("x", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", 1, "1.21.11", VillagePositions.NONE,
				0, List.of(), null, List.of(with), List.of());
		String json = MetricsJson.write(r);
		assertEquals(r, MetricsJson.read(json));
		@SuppressWarnings("unchecked")
		Map<String, Object> m = (Map<String, Object>) Json.parse(json);
		@SuppressWarnings("unchecked")
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) m.get("days")).get(0);
		@SuppressWarnings("unchecked")
		Map<String, Object> wages = (Map<String, Object>) day.get("wages");
		assertEquals(List.of("runs", "bill", "paid", "spent", "strikers"), List.copyOf(wages.keySet()));
		Map<String, Double> flat = with.flatten();
		assertEquals(1.0, flat.get("wages.runs"));
		assertEquals(3.0, flat.get("wages.bill"));
		assertEquals(2.0, flat.get("wages.paid"));
		assertEquals(2.0, flat.get("wages.spent"));
		assertEquals(1.0, flat.get("wages.strikers"));
		for (String k : List.of("wages.runs", "wages.bill", "wages.paid", "wages.spent", "wages.strikers")) assertTrue(MetricNames.isKnown(k), k);
		day.remove("wages");
		assertEquals(DayMetrics.WageDay.ZERO, MetricsJson.read(Json.write(m, true)).days().get(0).wages());
	}

	@Test
	void raidRoundTripPinnedKeysAndOldReportsReadZeros() {
		DayMetrics d = rich(1);
		DayMetrics.RaidDay raid = new DayMetrics.RaidDay(1, 1, 0, 0, 0, 0, 3, 5, 12, 11, 4, 3, 1, 0, 1, 6, 1, 0, 0, 2, 48, 80);
		DayMetrics with = new DayMetrics(d.day(), d.gameTime(), d.claimed(), d.population(), d.adults(), d.babies(), d.employed(),
				1, 3, d.stockpile(), 12, 3.5, 4, d.deaths(), d.villageMs(), 1.5, 470.25, 51.2, 900, 41_000_000L,
				Map.of(), Map.of(), Map.of(), DayMetrics.NeedsDay.ZERO, DayMetrics.WageDay.ZERO, raid, 2L, 1);
		Report r = new Report("x", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", 1, "1.21.11", VillagePositions.NONE,
				0, List.of(), null, List.of(with), List.of());
		String json = MetricsJson.write(r);
		assertEquals(r, MetricsJson.read(json));
		Map<String, Double> flat = with.flatten();
		assertEquals(1.0, flat.get("raid.started"));
		assertEquals(1.0, flat.get("raid.origin_random"));
		assertEquals(48.0, flat.get("raid.spawn_min_distance"));
		assertEquals(80.0, flat.get("raid.callup_ticks"));
		assertEquals(2.0, flat.get("deaths.logged"));
		assertEquals(1.0, flat.get("golems.in_claim"));
		for (String k : List.of("raid.started", "raid.active", "raid.victories", "raid.losses", "raid.stopped", "raid.gone", "raid.waves",
				"raid.waves_total", "raid.villagers_at_start", "raid.villagers_at_end", "raid.guards_at_start", "raid.guards_qualified_at_start",
				"raid.guards_wounded_at_start", "raid.guards_unloaded_at_start", "raid.villager_deaths", "raid.raiders_killed", "raid.origin_random",
				"raid.early", "raid.reward", "raid.extra_raiders", "raid.spawn_min_distance", "raid.callup_ticks", "deaths.logged", "golems.in_claim")) {
			assertTrue(MetricNames.isKnown(k), k);
			assertTrue(flat.containsKey(k), k);
		}
		assertFalse(MetricNames.isKnown("raid.defence_hp"));
		assertFalse(MetricNames.isKnown("siege.active"));
		DayMetrics zero = rich(1);
		assertEquals(DayMetrics.RaidDay.ZERO, zero.raid());
		assertEquals(-1.0, zero.flatten().get("raid.spawn_min_distance"));
		assertEquals(0.0, zero.flatten().get("raid.callup_ticks"));
		@SuppressWarnings("unchecked")
		Map<String, Object> m = (Map<String, Object>) Json.parse(json);
		@SuppressWarnings("unchecked")
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) m.get("days")).get(0);
		day.remove("raid");
		day.remove("golems");
		@SuppressWarnings("unchecked")
		Map<String, Object> deaths = (Map<String, Object>) day.get("deaths");
		deaths.remove("logged");
		DayMetrics old = MetricsJson.read(Json.write(m, true)).days().get(0);
		assertEquals(DayMetrics.RaidDay.ZERO, old.raid());
		assertEquals(0L, old.deathsLogged());
		assertEquals(0, old.golemsInClaim());
	}

	@Test
	void siegeRoundTripAndOldReportsReadZeros() {
		DayMetrics d = rich(1);
		DayMetrics.SiegeDay siege = new DayMetrics.SiegeDay(300, 0, 1, 4, 1, 2, 1, 0, 0, 1, 0, 3, 2, 1, 8, 5, 1, 6, 12, 250,
				new TickStats(100, 0.2, 0.4, 0.9));
		DayMetrics with = new DayMetrics(d.day(), d.gameTime(), d.claimed(), d.population(), d.adults(), d.babies(), d.employed(),
				1, 3, d.stockpile(), 12, 3.5, 4, d.deaths(), d.villageMs(), 1.5, 470.25, 51.2, 900, 41_000_000L,
				Map.of(), Map.of(), Map.of(), DayMetrics.NeedsDay.ZERO, DayMetrics.WageDay.ZERO, DayMetrics.RaidDay.ZERO, 0L, 0, siege);
		Report r = new Report("x", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", 1, "1.21.11", VillagePositions.NONE,
				0, List.of(), null, List.of(with), List.of());
		assertEquals(r, MetricsJson.read(MetricsJson.write(r)));
		Map<String, Double> flat = with.flatten();
		assertEquals(300.0, flat.get("defence.cells"));
		assertEquals(1.0, flat.get("defence.broken.zombie"));
		assertEquals(1.0, flat.get("siege.started"));
		assertEquals(8.0, flat.get("siege.zombies"));
		assertEquals(12.0, flat.get("lure.max_lured"));
		assertEquals(250.0, flat.get("threat.score"));
		assertEquals(0.2, flat.get("entity_ms.mean"));
		for (String k : List.of("defence.cells", "defence.unseen", "defence.breached", "defence.cracked", "defence.breached_noon",
				"defence.blocks_broken", "defence.broken.zombie", "defence.broken.ravager", "defence.broken.vindicator",
				"defence.broken.creeper", "defence.broken.other", "defence.repairs", "defence.repair_items",
				"siege.started", "siege.zombies", "siege.killed", "siege.villager_deaths", "siege.paths",
				"lure.max_lured", "threat.score", "entity_ms.mean", "entity_ms.p95", "entity_ms.max", "entity_ms.samples")) {
			assertTrue(MetricNames.isKnown(k), k);
		}
		@SuppressWarnings("unchecked")
		Map<String, Object> m = (Map<String, Object>) Json.parse(MetricsJson.write(r));
		@SuppressWarnings("unchecked")
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) m.get("days")).get(0);
		day.remove("defence");
		day.remove("siege");
		day.remove("lure");
		day.remove("threat");
		day.remove("entity_ms");
		assertEquals(DayMetrics.SiegeDay.ZERO, MetricsJson.read(Json.write(m, true)).days().get(0).siege());
	}
}
