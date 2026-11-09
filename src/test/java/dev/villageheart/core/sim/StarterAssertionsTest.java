package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The assertions of sim/scenarios/starter.json (M11) evaluate correctly on hand-made tables, and cannot pass vacuously. */
class StarterAssertionsTest {
	private static DayMetrics day(int d, long dinners, long diners, long fed, long maxHungry, long logs, List<DeathRecord> deaths) {
		return new DayMetrics(d, 41L + 24000L * d, true, 8, 8, 0, 2, 6, 0, Map.of(), 100, 2.0, 0, deaths, new TickStats(24000, 3.0E-4, 4.0E-4, 0.7),
				1.5, 470.0, 50.0, 900, 41_000_000L, Map.of(), Map.of(), Map.of("minecraft:logs", logs),
				new DayMetrics.NeedsDay(dinners, diners, fed, diners - fed, fed * 10, fed * 2, maxHungry, 4, 4));
	}

	private static MetricsTable table(long[] fed, long[] logs, long dinners) {
		List<DayMetrics> days = new ArrayList<>();
		for (int i = 0; i < fed.length; i++) days.add(day(i + 1, dinners, 10, fed[i], 0, logs[i], List.of()));
		return new MetricsTable(day(0, 0, 0, 0, 0, 0, List.of()), days);
	}

	private static AssertionResult run(String expr, MetricsTable t) { return Assertion.parse(expr).evaluate(t); }

	@Test void logsMustRiseEveryDayAgainstTheBaseline() {
		String a = "stockpile.tag.minecraft:logs > stockpile.tag.minecraft:logs@prev on every day";
		assertTrue(run(a, table(new long[] {10, 10, 10}, new long[] {3, 5, 9}, 1)).passed());
		AssertionResult flat = run(a, table(new long[] {10, 10, 10}, new long[] {3, 3, 9}, 1));
		assertFalse(flat.passed());
		assertTrue(flat.detail().contains("2"), flat.detail()); // names the day
		assertFalse(run(a, table(new long[] {10, 10}, new long[] {0, 4}, 1)).passed(), "day 1 equal to the baseline (0) fails");
	}

	@Test void fedRatioFromDayThree() {
		String a = "sum(food.fed) >= 0.9 * sum(food.diners) from day 3";
		long[] logs = {1, 2, 3, 4};
		assertTrue(run(a, table(new long[] {0, 0, 9, 9}, logs, 1)).passed(), "9 of 10 on days 3 and 4 passes, days 1-2 are ignored");
		assertFalse(run(a, table(new long[] {10, 10, 8, 8}, logs, 1)).passed(), "8 of 10 fails");
		AssertionResult tooShort = run(a, table(new long[] {10, 10}, new long[] {1, 2}, 1));
		assertFalse(tooShort.passed());
		assertTrue(tooShort.detail().contains("empty scope"), tooShort.detail());
	}

	@Test void oneDinnerEveryDayIsLoud() {
		String a = "food.dinners == 1 on every day";
		assertTrue(run(a, table(new long[] {9, 9}, new long[] {1, 2}, 1)).passed());
		assertFalse(run(a, table(new long[] {9, 9}, new long[] {1, 2}, 0)).passed());
		assertFalse(run(a, table(new long[] {9, 9}, new long[] {1, 2}, 2)).passed());
	}

	@Test void noStarvationDeathsReadsZeroWhenNobodyDied() {
		MetricsTable t = table(new long[] {9, 9}, new long[] {1, 2}, 1);
		assertTrue(run("sum(deaths.cause.minecraft:starve) == 0 from day 1", t).passed());
		List<DayMetrics> days = List.of(day(1, 1, 10, 9, 0, 1, List.of()), day(2, 1, 10, 9, 0, 2, List.of(new DeathRecord("minecraft:starve", false, 5))));
		assertFalse(run("sum(deaths.cause.minecraft:starve) == 0 from day 1", new MetricsTable(day(0, 0, 0, 0, 0, 0, List.of()), days)).passed());
	}

	@Test void maxHungryDaysBelowThree() {
		String a = "max(food.max_hungry_days) < 3 from day 3";
		List<DayMetrics> days = new ArrayList<>();
		for (int i = 1; i <= 4; i++) days.add(day(i, 1, 10, 9, i == 2 ? 5 : 2, i, List.of()));
		assertTrue(run(a, new MetricsTable(day(0, 0, 0, 0, 0, 0, List.of()), days)).passed(), "day 2 is outside the scope");
		days.set(3, day(4, 1, 10, 9, 3, 4, List.of()));
		assertFalse(run(a, new MetricsTable(day(0, 0, 0, 0, 0, 0, List.of()), days)).passed());
	}
}
