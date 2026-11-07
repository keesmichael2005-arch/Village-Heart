package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssertionTest {
	private static AssertionResult run(String expr, MetricsTable t) {
		return Assertion.parse(expr).evaluate(t);
	}

	private static void passes(String expr, MetricsTable t) {
		AssertionResult r = run(expr, t);
		assertTrue(r.passed(), expr + " -> " + r.detail());
	}

	private static void fails(String expr, MetricsTable t) {
		AssertionResult r = run(expr, t);
		assertFalse(r.passed(), expr + " should fail but: " + r.detail());
	}

	private static void rejects(String expr) {
		assertThrows(AssertionSyntaxException.class, () -> Assertion.parse(expr), expr);
	}

	@Test
	void parse() {
		MetricsTable t = SimFixtures.table(8, 8, 7, 8, 8);
		passes("population + adults * 2 == 24 on day 1", t);   // 8 + 8*2
		passes("(population + adults) * 2 == 32 on day 1", t);
		passes("-population == -8 on day 1", t);
		passes("population - -2 == 10 on day 1", t);
		passes("1e1 == 10", t);
		passes("population/2 == 4 on day 1", t);
		for (String c : new String[] {">=", "<=", ">", "<", "==", "!="}) Assertion.parse("population " + c + " 3");
		passes("population >= 3 on every day", t);
		passes("population >= 3 on last day", t);
		passes("population == 7 on day 3", t);
		passes("population >= 7 on days 2..4", t);
		passes("population >= 7 from day 2", t);
		passes("population>=3", t);
		passes("population@day1-1 == 7 on day 2", t);
		passes("population@first == 8", t);
		passes("population@last == 8 on day 1", t);
		passes("population@prev == 8 on day 3", t);
		passes("sum(population) == 39 on every day", t);
		passes("population@day3 == 7 on day 1", t);
		rejects("population >>= 3");
		rejects("populaton >= 1");
		rejects("population >= 1 on day");
		rejects("sum(population");
		rejects("population@day 1 >= 1");
		rejects("sum(max(population)) > 1");
		rejects("sum(population@day1) > 1");
		rejects("population > 1 on from day 2");
		rejects("population");
		rejects("population >= 1 on days 2.4");
		rejects("population >= 1 on days 2..");
		rejects("population >= 1 extra");
		rejects("population >= 1 on days 2.5..4");
		rejects("population = 1");
		rejects("population >= .5");
		rejects("population@yesterday > 1");
	}

	@Test
	void perDay() {
		passes("population >= population@day1 - 1 on every day", SimFixtures.table(8, 8, 7, 8, 8));
		AssertionResult r = run("population >= population@day1 - 1 on every day", SimFixtures.table(8, 6, 8, 8, 8));
		assertFalse(r.passed());
		assertEquals("day 2: population=6.0 < population@day1 - 1=7.0", r.detail());
	}

	@Test
	void roadmapExample() {
		passes("population >= 4 on day 5", SimFixtures.table(8, 8, 8, 8, 5));
		fails("population >= 4 on day 5", SimFixtures.table(8, 8, 8, 8, 3));
		passes("population >= 4", SimFixtures.table(8, 8, 8, 8, 5));
	}

	@Test
	void aggregates() {
		List<DayMetrics> days = new ArrayList<>();
		days.add(SimFixtures.day(1, 8, List.of(new DeathRecord("minecraft:fall", false, 1)), 50.0)); // outlier day 1
		for (int d = 2; d <= 5; d++) days.add(SimFixtures.day(d, 8, List.of(), 0.1));
		MetricsTable t = new MetricsTable(SimFixtures.day(0, 8), days);
		passes("sum(deaths.total) <= 1 on every day", t);
		fails("sum(deaths.total) < 1 on every day", t);
		passes("mean(village_ms.mean) < 1 from day 2", t);
		fails("mean(village_ms.mean) < 1 on every day", t);
		passes("max(village_ms.mean) == 50 on every day", t);
		passes("min(village_ms.mean) == 0.1 on every day", t);
		MetricsTable pops = SimFixtures.table(8, 8, 8, 5, 6);
		passes("sum(babies) + population@prev == 5 on every day", pops);     // with a FUNC present: last day of scope (5) minus 1 = day 4
		passes("sum(babies) + population@prev == 8 on days 1..3", pops);     // last day of scope is 3, prev is day 2
		passes("sum(babies) + population@prev - population == -1 on day 5", pops); // bare ref outside: day 5
	}

	@Test
	void prevAndBaseline() {
		MetricsTable t = SimFixtures.table(8, 9);
		passes("population >= population@prev on day 1", t);
		passes("population@prev == 8 on day 1", t);
		AssertionResult r = run("population@day9 >= 0 on day 1", t);
		assertFalse(r.passed());
		assertTrue(r.detail().contains("day 9"), r.detail());
	}

	@Test
	void emptyScope() {
		MetricsTable t = SimFixtures.table(1, 1, 1, 1, 1);
		for (String e : new String[] {"population >= 0 on days 3..2", "population >= 0 from day 6", "population >= 0 on day 9",
				"population >= 0 on days 3..9", "population >= 0 on day 0"}) {
			AssertionResult r = run(e, t);
			assertFalse(r.passed(), e);
			assertTrue(r.detail().contains("empty scope"), r.detail());
		}
		AssertionResult none = run("population >= 0 on every day", new MetricsTable(null, List.of()));
		assertFalse(none.passed());
	}

	@Test
	void dynamicFamilies() {
		DayMetrics d = new DayMetrics(1, 24041, true, 8, 7, 1, 0, 0, 0, Map.of("minecraft:oak_log", 12L), 0, 0, 0,
				List.of(new DeathRecord("minecraft:zombie", true, 5)), TickStats.EMPTY, 0, 0, 0, 0, 0);
		MetricsTable t = new MetricsTable(d, List.of(d));
		passes("stockpile.items.minecraft:oak_log == 12", t);
		passes("stockpile.items.minecraft:stone >= 0", t);
		passes("stockpile.items.minecraft:stone == 0", t);
		passes("stockpile.tag.minecraft:logs >= 0", t);
		passes("deaths.cause.minecraft:zombie == 1", t);
		passes("deaths.cause.minecraft:fall == 0", t);
		rejects("stockpile.items. >= 0");
		rejects("stockpile.bogus >= 0");
	}

	@Test
	void workFamilies() {
		DayMetrics d = new DayMetrics(1, 24041, true, 8, 7, 1, 0, 0, 0, Map.of(), 0, 0, 0, List.of(), TickStats.EMPTY, 0, 0, 0, 0, 0,
				Map.of("lumberjack.logs", 12L), Map.of("needs_tool", 1L), Map.of("minecraft:logs", 30L));
		MetricsTable t = new MetricsTable(d, List.of(d));
		passes("work.lumberjack.logs == 12", t);
		passes("work.lumberjack.trees == 0", t);
		passes("workers.status.needs_tool == 1", t);
		passes("workers.status.no_work == 0", t);
		passes("stockpile.tag.minecraft:logs == 30", t);
		rejects("work. >= 0");
		rejects("workers.status. >= 0");
	}

	@Test
	void divisionByZero() {
		AssertionResult r = run("population / babies > 1", SimFixtures.table(8));
		assertFalse(r.passed());
		assertTrue(r.detail().contains("division by zero"), r.detail());
	}

	@Test
	void notEqualAndEqualityDetails() {
		MetricsTable t = SimFixtures.table(8);
		passes("population != 7", t);
		fails("population != 8", t);
		AssertionResult r = run("population == 3 on day 1", t);
		assertEquals("day 1: population=8.0 > 3=3.0", r.detail());
	}
}
