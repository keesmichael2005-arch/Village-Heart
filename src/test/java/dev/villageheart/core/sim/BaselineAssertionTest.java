package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M13 T-L1-9: {@code x@base} reads a stored baseline run inside a function; the nutrition-per-item comparison of {@code starter_cook}. */
class BaselineAssertionTest {
	private static final String RATIO = "sum(food.eaten_nutrition) / sum(food.eaten_items) > sum(food.eaten_nutrition@base) / sum(food.eaten_items@base) from day 3";

	/** Ten days; every day {@code items} items worth {@code nutrition} points were eaten. */
	private static MetricsTable run(int days, long items, long nutrition) {
		List<DayMetrics> list = new ArrayList<>();
		for (int d = 1; d <= days; d++) list.add(day(d, items, nutrition));
		return new MetricsTable(day(0, 0, 0), list);
	}

	private static DayMetrics day(int d, long items, long nutrition) {
		return new DayMetrics(d, 24000L * d, true, 8, 7, 1, 2, 0, 0, Map.of(), 0, 0, 0, List.of(), TickStats.EMPTY, 0, 0, 0, 0, 0,
				Map.of(), Map.of(), Map.of(), new DayMetrics.NeedsDay(1, 8, 8, 0, nutrition, items, 0, 0, 0));
	}

	private static AssertionResult eval(String text, MetricsTable t, MetricsTable base) { return Assertion.parse(text).evaluate(t, base); }

	@Test
	void higherNutritionPerItemPasses() {
		MetricsTable cook = run(10, 10, 40); // 4.0 per item
		MetricsTable base = run(10, 10, 32); // 3.2 per item
		AssertionResult r = eval(RATIO, cook, base);
		assertTrue(r.passed(), r.detail());
		assertTrue(r.detail().contains("4.0") && r.detail().contains("3.2"), "both numbers are in the report: " + r.detail());
	}

	@Test
	void equalOrLowerFails() {
		assertFalse(eval(RATIO, run(10, 10, 32), run(10, 10, 32)).passed());
		assertFalse(eval(RATIO, run(10, 10, 30), run(10, 10, 32)).passed());
	}

	@Test
	void theSumsAreWeightedNotAveragedPerDay() {
		List<DayMetrics> days = new ArrayList<>();
		for (int d = 1; d <= 4; d++) days.add(d == 3 ? day(d, 1, 8) : day(d, 100, 300)); // day 3: 8.0 per item but one item
		MetricsTable cook = new MetricsTable(day(0, 0, 0), days);
		AssertionResult r = eval(RATIO, cook, run(4, 100, 310));
		assertFalse(r.passed(), "(8 + 300) / 101 = 3.05 < 3.1: " + r.detail());
	}

	@Test
	void withoutABaselineItFailsWithAReason() {
		AssertionResult r = eval(RATIO, run(10, 10, 40), null);
		assertFalse(r.passed());
		assertTrue(r.detail().contains("baseline"), r.detail());
	}

	@Test
	void aShortBaselineFailsWithItsDays() {
		AssertionResult r = eval(RATIO, run(10, 10, 40), run(5, 10, 32));
		assertFalse(r.passed());
		assertTrue(r.detail().contains("baseline has days 0..5"), r.detail());
	}

	@Test
	void divisionByZeroInTheBaselineFails() {
		AssertionResult r = eval(RATIO, run(10, 10, 40), run(10, 0, 0));
		assertFalse(r.passed());
		assertTrue(r.detail().contains("division by zero"), r.detail());
	}

	@Test
	void baseIsOnlyAllowedInsideAFunction() {
		assertThrows(AssertionSyntaxException.class, () -> Assertion.parse("food.eaten_items@base > 1"));
		assertThrows(AssertionSyntaxException.class, () -> Assertion.parse("sum(food.eaten_items@day1) > 1"));
		assertDoesNotThrow(() -> Assertion.parse("sum(food.eaten_items@base) > 1"));
		assertDoesNotThrow(() -> Assertion.parse("food.eaten_items@prev >= 0"), "the other references are unchanged");
	}
}
