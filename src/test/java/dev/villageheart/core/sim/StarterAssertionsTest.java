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

	private static DayMetrics wageDay(int d, int treasury, long runs, long strikers) {
		DayMetrics b = day(d, 1, 10, 9, 0, d, List.of());
		return new DayMetrics(b.day(), b.gameTime(), true, 8, 8, 0, 2, 6, 0, Map.of(), 100, 2.0, treasury, List.of(), b.villageMs(),
				1.5, 470.0, 50.0, 900, 41_000_000L, Map.of(), Map.of(), Map.of("minecraft:logs", (long) d), b.needs(),
				new DayMetrics.WageDay(runs, 2 * runs, 2 * runs - strikers, 2 * runs - strikers, strikers));
	}

	private static MetricsTable wages(int[] treasury, long[] runs, long[] strikers) {
		List<DayMetrics> days = new ArrayList<>();
		for (int i = 0; i < treasury.length; i++) days.add(wageDay(i + 1, treasury[i], runs[i], strikers[i]));
		return new MetricsTable(wageDay(0, 30, 0, 0), days);
	}

	/** M12 (T-L3-1): the starter scenario's wage assertions are live: an unfunded village fails them. */
	@Test void wageAssertionsOfTheStarterScenario() {
		String none = "max(wages.strikers) == 0 from day 1";
		String ran = "sum(wages.runs) >= 8 from day 1";
		String falling = "treasury <= treasury@prev - 2 from day 3";
		// day 1 has no wage run (the clock is reset at day 0), the dawns of days 2..5 pay 2 each
		MetricsTable ok = wages(new int[] {28, 26, 24, 22, 20}, new long[] {0, 1, 1, 1, 1}, new long[] {0, 0, 0, 0, 0});
		assertTrue(run(none, ok).passed());
		assertTrue(run(falling, ok).passed());
		assertTrue(run("sum(wages.runs) >= 4 from day 1", ok).passed());
		assertFalse(run(ran, ok).passed(), "only 4 runs in 5 days");
		// an unfunded village: the treasury stays at 0, so both workers strike from day 2
		MetricsTable poor = wages(new int[] {0, 0, 0, 0, 0}, new long[] {0, 1, 1, 1, 1}, new long[] {0, 2, 2, 2, 2});
		assertFalse(run(none, poor).passed(), "the metric is live: strikers fail the assertion");
		assertFalse(run(falling, poor).passed(), "a treasury that never falls fails: wages are not being charged");
	}

	private static DayMetrics workDay(int d, Map<String, Long> work) {
		DayMetrics b = day(d, 1, 10, 9, 0, d, List.of());
		return new DayMetrics(b.day(), b.gameTime(), true, 8, 8, 0, 2, 6, 0, Map.of(), 100, 2.0, 0, List.of(), b.villageMs(),
				1.5, 470.0, 50.0, 900, 41_000_000L, work, Map.of(), Map.of("minecraft:logs", (long) d), b.needs(), new DayMetrics.WageDay(0, 0, 0, 0, 0));
	}

	/**
	 * sim-area: the follow tickets keep a wandering worker ticking, so a worker that walks away for real must still fail the run. The daily
	 * farthest distance from the Heart is a metric and the starter asserts it stays under the vanilla walk-home limit (150 blocks).
	 */
	@Test void wanderingWorkersAreSeenAndFrozenOnesFail() {
		String far = "max(work.workers.max_heart_distance) < 150 from day 1";
		String frozen = "sum(work.workers.not_ticking) == 0 from day 1";
		List<DayMetrics> days = new ArrayList<>();
		days.add(workDay(1, Map.of("workers.max_heart_distance", 60L)));
		days.add(workDay(2, Map.of("workers.max_heart_distance", 111L)));
		MetricsTable ok = new MetricsTable(workDay(0, Map.of()), days);
		assertTrue(run(far, ok).passed(), "111 blocks (the r5 farmer's flight) is a stroll the worker comes back from");
		assertTrue(run(frozen, ok).passed(), "a missing not_ticking key reads 0");
		days.add(workDay(3, Map.of("workers.max_heart_distance", 150L, "workers.not_ticking", 5L)));
		MetricsTable lost = new MetricsTable(workDay(0, Map.of()), days);
		assertFalse(run(far, lost).passed(), "a worker 150 blocks out cannot walk home: a real loss");
		assertFalse(run(frozen, lost).passed());
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
