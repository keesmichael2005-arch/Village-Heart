package dev.villageheart.core.sim;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One metrics record (a baseline or the end of a simulated day). */
public record DayMetrics(int day, long gameTime, boolean claimed,
		int population, int adults, int babies, int employed,
		int buildingsValid, int buildingsInvalid,
		Map<String, Long> stockpile,
		int foodNutrition, double foodDaysLeft, int treasury,
		List<DeathRecord> deaths,
		TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb, long worldSaveBytes) {

	public DayMetrics {
		stockpile = Map.copyOf(stockpile);
		deaths = List.copyOf(deaths);
	}

	public long stockpileTotal() {
		long t = 0;
		for (long c : stockpile.values()) t += c;
		return t;
	}

	public int mobDeaths() {
		int n = 0;
		for (DeathRecord d : deaths) if (d.byMob()) n++;
		return n;
	}

	/** The numbers the assertion language reads, by path. Dynamic families appear only for keys that exist. */
	public Map<String, Double> flatten() {
		Map<String, Double> m = new LinkedHashMap<>();
		m.put("day", (double) day);
		m.put("claimed", claimed ? 1.0 : 0.0);
		m.put("population", (double) population);
		m.put("adults", (double) adults);
		m.put("babies", (double) babies);
		m.put("employed", (double) employed);
		m.put("buildings.valid", (double) buildingsValid);
		m.put("buildings.invalid", (double) buildingsInvalid);
		m.put("stockpile.total", (double) stockpileTotal());
		m.put("stockpile.distinct", (double) stockpile.size());
		for (Map.Entry<String, Long> e : stockpile.entrySet()) m.put("stockpile.items." + e.getKey(), (double) e.getValue());
		m.put("food.nutrition", (double) foodNutrition);
		m.put("food.days_left", foodDaysLeft);
		m.put("treasury", (double) treasury);
		m.put("deaths.total", (double) deaths.size());
		m.put("deaths.mobs", (double) mobDeaths());
		Map<String, Integer> byCause = new LinkedHashMap<>();
		for (DeathRecord d : deaths) byCause.merge(d.cause(), 1, Integer::sum);
		for (Map.Entry<String, Integer> e : byCause.entrySet()) m.put("deaths.cause." + e.getKey(), (double) e.getValue());
		m.put("village_ms.mean", villageMs.meanMs());
		m.put("village_ms.p95", villageMs.p95Ms());
		m.put("village_ms.max", villageMs.maxMs());
		m.put("village_ms.samples", (double) villageMs.samples());
		m.put("server.mspt", serverMspt);
		m.put("server.tps", serverTps);
		m.put("server.heap_used_mb", (double) heapUsedMb);
		m.put("world.save_bytes", (double) worldSaveBytes);
		m.put("wall_seconds", wallSeconds);
		return m;
	}
}
