package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Report file format (reports/sim/NAME.json). Keys are snake_case and part of the format. */
public final class MetricsJson {
	private MetricsJson() {}

	public record Pos(int x, int y, int z) {}

	/** Any member may be null before it is known. */
	public record VillagePositions(Pos structure, Pos bell, Pos heart) {
		public static final VillagePositions NONE = new VillagePositions(null, null, null);
	}

	public record Report(String scenario, SimStatus status, String error, boolean cleanShutdown, long heartbeat,
			int actionsDone, Integer stoppedAfterDay, long seed, String difficulty, int daysRequested, String minecraft,
			VillagePositions village, long g0, List<Integer> reloads, DayMetrics baseline, List<DayMetrics> days,
			List<AssertionResult> assertions) {
		public Report {
			reloads = List.copyOf(reloads);
			days = List.copyOf(days);
			assertions = List.copyOf(assertions);
		}
	}

	public static final int SCHEMA = 1;

	// ---- write ----

	public static String write(Report r) {
		return Json.write(toJson(r), true);
	}

	static Map<String, Object> toJson(Report r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("schema", (long) SCHEMA);
		m.put("scenario", r.scenario());
		m.put("status", r.status().name());
		m.put("error", r.error());
		m.put("clean_shutdown", r.cleanShutdown());
		m.put("heartbeat", r.heartbeat());
		m.put("actions_done", (long) r.actionsDone());
		m.put("stopped_after_day", r.stoppedAfterDay() == null ? null : (Object) (long) r.stoppedAfterDay());
		m.put("seed", r.seed());
		m.put("difficulty", r.difficulty());
		m.put("days_requested", (long) r.daysRequested());
		m.put("minecraft", r.minecraft());
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("structure", pos(r.village().structure()));
		v.put("bell", pos(r.village().bell()));
		v.put("heart", pos(r.village().heart()));
		m.put("village", v);
		m.put("g0", r.g0());
		List<Object> reloads = new ArrayList<>();
		for (int d : r.reloads()) reloads.add((long) d);
		m.put("reloads", reloads);
		m.put("baseline", r.baseline() == null ? null : day(r.baseline()));
		List<Object> days = new ArrayList<>();
		for (DayMetrics d : r.days()) days.add(day(d));
		m.put("days", days);
		List<Object> as = new ArrayList<>();
		for (AssertionResult a : r.assertions()) {
			Map<String, Object> am = new LinkedHashMap<>();
			am.put("text", a.text());
			am.put("passed", a.passed());
			am.put("detail", a.detail());
			as.add(am);
		}
		m.put("assertions", as);
		return m;
	}

	private static Object pos(Pos p) {
		return p == null ? null : new ArrayList<Object>(List.of((long) p.x(), (long) p.y(), (long) p.z()));
	}

	private static Map<String, Object> day(DayMetrics d) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("day", (long) d.day());
		m.put("game_time", d.gameTime());
		m.put("claimed", d.claimed());
		m.put("population", (long) d.population());
		m.put("adults", (long) d.adults());
		m.put("babies", (long) d.babies());
		m.put("employed", (long) d.employed());
		Map<String, Object> b = new LinkedHashMap<>();
		b.put("valid", (long) d.buildingsValid());
		b.put("invalid", (long) d.buildingsInvalid());
		m.put("buildings", b);
		Map<String, Object> s = new LinkedHashMap<>();
		s.put("total", d.stockpileTotal());
		s.put("distinct", (long) d.stockpile().size());
		List<Map.Entry<String, Long>> items = new ArrayList<>(d.stockpile().entrySet());
		items.sort((a, c) -> {
			int cmp = Long.compare(c.getValue(), a.getValue());
			return cmp != 0 ? cmp : a.getKey().compareTo(c.getKey());
		});
		Map<String, Object> im = new LinkedHashMap<>();
		for (Map.Entry<String, Long> e : items) im.put(e.getKey(), e.getValue());
		s.put("items", im);
		m.put("stockpile", s);
		Map<String, Object> f = new LinkedHashMap<>();
		f.put("nutrition", (long) d.foodNutrition());
		f.put("days_left", d.foodDaysLeft());
		m.put("food", f);
		m.put("treasury", (long) d.treasury());
		Map<String, Object> de = new LinkedHashMap<>();
		de.put("total", (long) d.deaths().size());
		de.put("mobs", (long) d.mobDeaths());
		Map<String, Long> byCause = new TreeMap<>();
		for (DeathRecord x : d.deaths()) byCause.merge(x.cause(), 1L, Long::sum);
		de.put("by_cause", new LinkedHashMap<String, Object>(byCause));
		List<Object> list = new ArrayList<>();
		for (DeathRecord x : d.deaths()) {
			Map<String, Object> dm = new LinkedHashMap<>();
			dm.put("cause", x.cause());
			dm.put("by_mob", x.byMob());
			dm.put("game_time", x.gameTime());
			list.add(dm);
		}
		de.put("list", list);
		m.put("deaths", de);
		Map<String, Object> vm = new LinkedHashMap<>();
		vm.put("mean", d.villageMs().meanMs());
		vm.put("p95", d.villageMs().p95Ms());
		vm.put("max", d.villageMs().maxMs());
		vm.put("samples", d.villageMs().samples());
		m.put("village_ms", vm);
		Map<String, Object> sv = new LinkedHashMap<>();
		sv.put("mspt", d.serverMspt());
		sv.put("tps", d.serverTps());
		sv.put("heap_used_mb", d.heapUsedMb());
		m.put("server", sv);
		Map<String, Object> w = new LinkedHashMap<>();
		w.put("save_bytes", d.worldSaveBytes());
		m.put("world", w);
		m.put("wall_seconds", d.wallSeconds());
		return m;
	}

	// ---- read ----

	public static Report read(String text) {
		Map<String, Object> m = Json.obj(Json.parse(text), "report");
		long schema = lng(m, "schema");
		if (schema != SCHEMA) throw new Json.JsonException("unsupported report schema " + schema);
		Map<String, Object> v = Json.obj(m.get("village"), "village");
		List<Integer> reloads = new ArrayList<>();
		for (Object o : Json.arr(m.get("reloads"), "reloads")) reloads.add(((Number) o).intValue());
		List<DayMetrics> days = new ArrayList<>();
		for (Object o : Json.arr(m.get("days"), "days")) days.add(readDay(Json.obj(o, "day record")));
		List<AssertionResult> as = new ArrayList<>();
		for (Object o : Json.arr(m.get("assertions"), "assertions")) {
			Map<String, Object> a = Json.obj(o, "assertion");
			as.add(new AssertionResult((String) a.get("text"), (Boolean) a.get("passed"), (String) a.get("detail")));
		}
		Object stopped = m.get("stopped_after_day");
		return new Report((String) m.get("scenario"), SimStatus.valueOf((String) m.get("status")), (String) m.get("error"),
				(Boolean) m.get("clean_shutdown"), lng(m, "heartbeat"), (int) lng(m, "actions_done"),
				stopped == null ? null : ((Number) stopped).intValue(), lng(m, "seed"), (String) m.get("difficulty"),
				(int) lng(m, "days_requested"), (String) m.get("minecraft"),
				new VillagePositions(readPos(v.get("structure")), readPos(v.get("bell")), readPos(v.get("heart"))),
				lng(m, "g0"), reloads, m.get("baseline") == null ? null : readDay(Json.obj(m.get("baseline"), "baseline")),
				days, as);
	}

	private static Pos readPos(Object o) {
		if (o == null) return null;
		List<Object> l = Json.arr(o, "position");
		return new Pos(((Number) l.get(0)).intValue(), ((Number) l.get(1)).intValue(), ((Number) l.get(2)).intValue());
	}

	private static long lng(Map<String, Object> m, String k) {
		Object o = m.get(k);
		if (!(o instanceof Number n)) throw new Json.JsonException("missing number '" + k + "'");
		return n.longValue();
	}

	private static double dbl(Map<String, Object> m, String k) {
		Object o = m.get(k);
		return o == null ? Double.NaN : ((Number) o).doubleValue();
	}

	private static DayMetrics readDay(Map<String, Object> m) {
		Map<String, Object> b = Json.obj(m.get("buildings"), "buildings");
		Map<String, Object> s = Json.obj(m.get("stockpile"), "stockpile");
		Map<String, Object> f = Json.obj(m.get("food"), "food");
		Map<String, Object> de = Json.obj(m.get("deaths"), "deaths");
		Map<String, Object> vm = Json.obj(m.get("village_ms"), "village_ms");
		Map<String, Object> sv = Json.obj(m.get("server"), "server");
		Map<String, Object> w = Json.obj(m.get("world"), "world");
		Map<String, Long> items = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : Json.obj(s.get("items"), "items").entrySet()) items.put(e.getKey(), ((Number) e.getValue()).longValue());
		List<DeathRecord> deaths = new ArrayList<>();
		for (Object o : Json.arr(de.get("list"), "deaths.list")) {
			Map<String, Object> d = Json.obj(o, "death");
			deaths.add(new DeathRecord((String) d.get("cause"), (Boolean) d.get("by_mob"), lng(d, "game_time")));
		}
		return new DayMetrics((int) lng(m, "day"), lng(m, "game_time"), (Boolean) m.get("claimed"),
				(int) lng(m, "population"), (int) lng(m, "adults"), (int) lng(m, "babies"), (int) lng(m, "employed"),
				(int) lng(b, "valid"), (int) lng(b, "invalid"), items,
				(int) lng(f, "nutrition"), dbl(f, "days_left"), (int) lng(m, "treasury"), deaths,
				new TickStats(lng(vm, "samples"), dbl(vm, "mean"), dbl(vm, "p95"), dbl(vm, "max")),
				dbl(sv, "mspt"), dbl(sv, "tps"), dbl(m, "wall_seconds"), lng(sv, "heap_used_mb"), lng(w, "save_bytes"));
	}
}
