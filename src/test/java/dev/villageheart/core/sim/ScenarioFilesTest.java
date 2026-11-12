package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every shipped scenario must parse, so a broken file fails L1 and not minute 5 of a run. */
class ScenarioFilesTest {
	@Test
	void everyScenarioFileParses() throws Exception {
		List<Path> files;
		try (Stream<Path> s = Files.list(Path.of("sim/scenarios"))) {
			files = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
		}
		assertFalse(files.isEmpty(), "no scenario files found in sim/scenarios");
		assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("bare.json")));
		for (Path p : files) {
			String name = p.getFileName().toString().replaceAll("\\.json$", "");
			Scenario s = ScenarioParser.parse(name, Files.readString(p));
			assertEquals(name, s.name());
		}
	}

	@Test
	void starterHasItsActionsAndAssertions() throws Exception {
		Scenario s = ScenarioParser.parse("starter", Files.readString(Path.of("sim/scenarios/starter.json")));
		assertEquals(13, s.actions().size()); // M12 added `villageheart treasury add 32` (a real scenario change)
		assertEquals(10, s.defaultDays());
		assertEquals(14, s.assertions().size()); // M11 10 (incl. farmer harvest + not_ticking), M12 + 3 wage assertions, sim-area + max_heart_distance
		var hires = s.actions().stream().filter(a -> a.type().equals("hire")).toList();
		assertEquals(2, hires.size());
		for (var h : hires) {
			assertEquals(1, h.day());
			assertEquals(200, h.tick());
		}
		assertEquals(11, s.actions().stream().filter(a -> a.day() == 0).count());
		assertEquals("claim", s.actions().get(0).type());
		assertEquals("command", s.actions().get(1).type());
		assertEquals("site", s.actions().get(2).type());
	}

	/** The day report carries the village tier and the fed-days streak, and both are assertion metrics. */
	@Test
	void reportCarriesVillageTierAndFedStreak() {
		DayMetrics d = new DayMetrics(2, 48041, true, 8, 8, 0, 3, 2, 0, Map.of(), 10, 1.5, 4, List.of(), TickStats.EMPTY,
				1, 20, 1, 10, 1, Map.of(), Map.of(), Map.of(), DayMetrics.NeedsDay.ZERO, DayMetrics.WageDay.ZERO,
				new DayMetrics.TierDay("village", 4));
		MetricsJson.Report r = new MetricsJson.Report("x", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", 1, "1.21.11",
				MetricsJson.VillagePositions.NONE, 0, List.of(), null, List.of(d), List.of());
		String json = MetricsJson.write(r);
		DayMetrics back = MetricsJson.read(json).days().get(0);
		assertEquals("village", back.tier().name());
		assertEquals(4L, back.tier().fedStreak());
		assertEquals(1.0, back.flatten().get("village.tier"));
		assertEquals(4.0, back.flatten().get("tier.fed_streak"));
		assertTrue(MetricNames.isKnown("village.tier"));
		assertTrue(MetricNames.isKnown("tier.fed_streak"));
		MetricsTable table = new MetricsTable(null, List.of(back));
		AssertionResult tier = Assertion.parse("village.tier == 1 on last day").evaluate(table);
		assertTrue(tier.passed(), tier.detail());
		AssertionResult streak = Assertion.parse("tier.fed_streak == 4 on last day").evaluate(table);
		assertTrue(streak.passed(), streak.detail());
		String md = SimSummary.table(List.of(back));
		assertTrue(md.contains("fed streak"), md);
		assertTrue(md.contains("| village |"), md);
		assertTrue(md.contains("| 4 |"), md);
		// a report from before the field reads hamlet / 0, and the metrics stay known
		@SuppressWarnings("unchecked")
		Map<String, Object> raw = (Map<String, Object>) Json.parse(json);
		@SuppressWarnings("unchecked")
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) raw.get("days")).get(0);
		day.remove("tier");
		DayMetrics old = MetricsJson.read(Json.write(raw, true)).days().get(0);
		assertEquals(DayMetrics.TierDay.NONE, old.tier());
		assertEquals(0.0, old.flatten().get("village.tier"));
		assertEquals(0.0, old.flatten().get("tier.fed_streak"));
	}
}
