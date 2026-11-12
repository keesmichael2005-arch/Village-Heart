package dev.villageheart.core.sim;

import java.util.List;

/** The day table {@code scripts/simulate.sh} prints (the {@code .summary.md} body). */
public final class SimSummary {
	private SimSummary() {}

	public static String table(List<DayMetrics> days) {
		StringBuilder sb = new StringBuilder();
		sb.append("| day | tier | fed streak | population | deaths | village ms mean | village ms p95 | server tps | heap MB |\n");
		sb.append("|---|---|---|---|---|---|---|---|---|\n");
		for (DayMetrics d : days) {
			sb.append(String.format("| %d | %s | %d | %d | %d | %.4f | %.4f | %.0f | %d |%n", d.day(), d.tier().name(), d.tier().fedStreak(),
					d.population(), d.deaths().size(), d.villageMs().meanMs(), d.villageMs().p95Ms(), d.serverTps(), d.heapUsedMb()));
		}
		return sb.toString();
	}
}
