package dev.villageheart.core.sim;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** Baseline (day 0) plus the day records 1..n: the evaluator's view of a run. */
public final class MetricsTable {
	private final DayMetrics baseline;
	private final List<DayMetrics> days;

	public MetricsTable(DayMetrics baseline, List<DayMetrics> days) {
		this.baseline = baseline;
		this.days = List.copyOf(days);
	}

	public int lastDay() {
		return days.size();
	}

	/** Empty if the day is outside 0..lastDay (or there is no baseline). Missing dynamic-family keys read 0. */
	public OptionalDouble value(String path, int day) {
		DayMetrics m;
		if (day == 0) m = baseline;
		else if (day >= 1 && day <= days.size()) m = days.get(day - 1);
		else return OptionalDouble.empty();
		if (m == null) return OptionalDouble.empty();
		Map<String, Double> flat = m.flatten();
		Double v = flat.get(path);
		if (v != null) return OptionalDouble.of(v);
		return MetricNames.isFamily(path) ? OptionalDouble.of(0.0) : OptionalDouble.empty();
	}
}
