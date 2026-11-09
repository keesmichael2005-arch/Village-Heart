package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M13 T-L1-9 / T-L1-10: the scenario's {@code baseline}, the baseline loader, the report's provenance and the derived food metric. */
class BaselineFilesTest {
	private static final String SCENARIO = "{\"schema\":1,\"name\":\"x\",\"default_days\":3,\"baseline\":\"../baselines/starter.json\","
			+ "\"assertions\":[\"sum(food.eaten_items@base) > 0 from day 1\"]}";

	@Test
	void theScenarioNamesItsBaseline() {
		Scenario s = ScenarioParser.parse("x", SCENARIO);
		assertEquals("../baselines/starter.json", s.baseline());
		assertNull(ScenarioParser.parse("y", "{\"schema\":1,\"name\":\"y\",\"default_days\":3}").baseline());
	}

	@Test
	void aBaseReferenceWithoutABaselineIsAParseError() {
		var e = assertThrows(ScenarioParser.ScenarioParseException.class, () -> ScenarioParser.parse("x",
				"{\"schema\":1,\"name\":\"x\",\"default_days\":3,\"assertions\":[\"sum(food.eaten_items@base) > 0\"]}"));
		assertTrue(e.getMessage().contains("baseline"), e.getMessage());
		assertThrows(ScenarioParser.ScenarioParseException.class, () -> ScenarioParser.parse("x",
				"{\"schema\":1,\"name\":\"x\",\"default_days\":3,\"baseline\":7}"));
	}

	private static MetricsJson.Report report() {
		DayMetrics d0 = new DayMetrics(0, 41, true, 8, 7, 1, 0, 0, 0, Map.of(), 0, 0, 0, List.of(), TickStats.EMPTY, 0, 0, 0, 0, 0);
		DayMetrics d1 = new DayMetrics(1, 24041, true, 8, 7, 1, 0, 0, 0, Map.of(), 0, 0, 0, List.of(), TickStats.EMPTY, 0, 0, 0, 0, 0,
				Map.of(), Map.of(), Map.of(), new DayMetrics.NeedsDay(1, 8, 8, 0, 31, 10, 0, 0, 0));
		return new MetricsJson.Report("starter", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", 1, "1.21.11", MetricsJson.VillagePositions.NONE,
				0, List.of(), d0, List.of(d1), List.of());
	}

	@Test
	void theLoaderReadsAStoredReport() {
		MetricsTable t = BaselineLoader.load(MetricsJson.write(report()));
		assertEquals(1, t.lastDay());
		assertEquals(10.0, t.value("food.eaten_items", 1).orElseThrow());
		assertEquals(3.1, t.value("food.eaten_per_item", 1).orElseThrow(), 1e-9);
		assertThrows(IllegalArgumentException.class, () -> BaselineLoader.load("not json"));
		assertThrows(IllegalArgumentException.class, () -> BaselineLoader.load("{\"schema\":1}"));
	}

	@Test
	void eatenPerItemIsNanWithoutMeals() {
		MetricsTable t = BaselineLoader.load(MetricsJson.write(report()));
		DayMetrics none = new DayMetrics(1, 1, true, 8, 7, 1, 0, 0, 0, Map.of(), 0, 0, 0, List.of(), TickStats.EMPTY, 0, 0, 0, 0, 0);
		assertTrue(Double.isNaN(none.flatten().get("food.eaten_per_item")));
		assertNotNull(t);
	}

	@Test
	void provenanceRoundTripsAndOldReportsReadNull() {
		MetricsJson.Report r = report();
		assertNull(MetricsJson.read(MetricsJson.write(r)).provenance(), "an old-shape report has none");
		MetricsJson.Report with = new MetricsJson.Report(r.scenario(), r.status(), r.error(), r.cleanShutdown(), r.heartbeat(), r.actionsDone(), r.stoppedAfterDay(),
				r.seed(), r.difficulty(), r.daysRequested(), r.minecraft(), r.village(), r.g0(), r.reloads(), r.baseline(), r.days(), r.assertions(),
				new MetricsJson.Provenance("../baselines/starter.json", "abc", "1234567"));
		String json = MetricsJson.write(with);
		assertTrue(json.contains("\"baseline_file\"") && json.contains("\"scenario_sha256\"") && json.contains("\"git_commit\""), json);
		assertEquals(with, MetricsJson.read(json));
	}

	@Test
	void theScenarioHashIgnoresFormattingKeyOrderAndLineEndings() {
		String a = "{\"schema\":1,\"name\":\"x\",\"actions\":[{\"action\":\"claim\",\"day\":0}]}";
		String b = "{\r\n  \"name\": \"x\",\r\n  \"actions\": [ { \"day\": 0, \"action\": \"claim\" } ],\r\n  \"schema\": 1\r\n}\r\n";
		assertEquals(MetricsJson.scenarioSha256(a), MetricsJson.scenarioSha256(b));
		assertNotEquals(MetricsJson.scenarioSha256(a), MetricsJson.scenarioSha256(a.replace("\"x\"", "\"y\"")));
		assertEquals(64, MetricsJson.scenarioSha256(a).length());
	}
}
