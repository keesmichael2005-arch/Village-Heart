package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class ScenarioEvaluatorTest {
	private static Scenario scenario(String... assertions) {
		return new Scenario("t", "d", 5, null, List.of(), List.of(assertions));
	}

	@Test
	void allPass() {
		ScenarioResult r = ScenarioEvaluator.evaluate(scenario("claimed == 1 on every day", "population >= 4"),
				SimFixtures.table(8, 8, 8), List.of());
		assertEquals(SimStatus.PASS, r.status());
		assertEquals(2, r.results().size());
	}

	@Test
	void oneFalseFailsAndOrderKept() {
		ScenarioResult r = ScenarioEvaluator.evaluate(scenario("population >= 4", "population >= 100"),
				SimFixtures.table(8, 8), List.of("claimed == 1", "claimed == 0"));
		assertEquals(SimStatus.FAIL, r.status());
		assertEquals(List.of("population >= 4", "population >= 100", "claimed == 1", "claimed == 0"),
				r.results().stream().map(AssertionResult::text).toList());
		assertEquals(List.of(true, false, true, false), r.results().stream().map(AssertionResult::passed).toList());
	}

	@Test
	void noDayRecordsFails() {
		ScenarioResult r = ScenarioEvaluator.evaluate(scenario("population >= 0"),
				new MetricsTable(SimFixtures.day(0, 8), List.of()), List.of());
		assertEquals(SimStatus.FAIL, r.status());
		assertTrue(r.results().get(0).detail().contains("no day records"));
	}

	@Test
	void badAssertionThrowsAtParse() {
		assertThrows(AssertionSyntaxException.class, () -> ScenarioEvaluator.parseAll(scenario("population >>= 3"), List.of()));
		assertThrows(AssertionSyntaxException.class, () -> ScenarioEvaluator.parseAll(scenario(), List.of("nope >= 3")));
	}
}
