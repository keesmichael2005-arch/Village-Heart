package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Test helpers to build metrics tables. */
final class SimFixtures {
	private SimFixtures() {}

	static DayMetrics day(int day, int population) {
		return day(day, population, List.of(), 0.0003);
	}

	static DayMetrics day(int day, int population, List<DeathRecord> deaths, double villageMean) {
		return new DayMetrics(day, 41L + 24000L * day, true, population, population, 0, 0, 0, 0, Map.of(),
				0, 0.0, 0, deaths, new TickStats(24000, villageMean, villageMean * 2, villageMean * 100),
				1.5, 470.0, 50.0, 900, 41_000_000L);
	}

	static MetricsTable table(int... pops) {
		List<DayMetrics> days = new ArrayList<>();
		for (int i = 0; i < pops.length; i++) days.add(day(i + 1, pops[i]));
		return new MetricsTable(day(0, pops.length > 0 ? pops[0] : 0), days);
	}
}
