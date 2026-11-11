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
	public record VillagePositions(Pos structure, Pos bell, Pos heart, Pos site) {
		public static final VillagePositions NONE = new VillagePositions(null, null, null, null);

		/** Without the prefab site (M11). */
		public VillagePositions(Pos structure, Pos bell, Pos heart) { this(structure, bell, heart, null); }
	}

	public record Report(String scenario, SimStatus status, String error, boolean cleanShutdown, long heartbeat,
			int actionsDone, Integer stoppedAfterDay, long seed, String difficulty, int daysRequested, String minecraft,
			VillagePositions village, long g0, List<Integer> reloads, DayMetrics baseline, List<DayMetrics> days,
			List<AssertionResult> assertions, Provenance provenance) {
		public Report {
			reloads = List.copyOf(reloads);
			days = List.copyOf(days);
			assertions = List.copyOf(assertions);
		}

		/** Without provenance (the shape before M13). */
		public Report(String scenario, SimStatus status, String error, boolean cleanShutdown, long heartbeat, int actionsDone, Integer stoppedAfterDay,
				long seed, String difficulty, int daysRequested, String minecraft, VillagePositions village, long g0, List<Integer> reloads,
				DayMetrics baseline, List<DayMetrics> days, List<AssertionResult> assertions) {
			this(scenario, status, error, cleanShutdown, heartbeat, actionsDone, stoppedAfterDay, seed, difficulty, daysRequested, minecraft, village, g0,
					reloads, baseline, days, assertions, null);
		}
	}

	/**
	 * Where a report comes from (M13): the baseline file a comparison read, the SHA-256 of the scenario's canonical JSON, and the git commit of
	 * the run (informational). Any member may be null.
	 */
	public record Provenance(String baselineFile, String scenarioSha256, String gitCommit) {}

	/** The SHA-256 (hex) of the scenario text as canonical JSON: parsed, written with sorted keys and no whitespace, so formatting and line endings do not matter. */
	public static String scenarioSha256(String scenarioJson) {
		try {
			String canonical = Json.write(sortKeys(Json.parse(scenarioJson)), false);
			byte[] d = java.security.MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder sb = new StringBuilder();
			for (byte b : d) sb.append(String.format("%02x", b));
			return sb.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	@SuppressWarnings("unchecked")
	private static Object sortKeys(Object o) {
		if (o instanceof Map<?, ?> m) {
			Map<String, Object> out = new TreeMap<>();
			for (Map.Entry<?, ?> e : m.entrySet()) out.put((String) e.getKey(), sortKeys(e.getValue()));
			return out;
		}
		if (o instanceof List<?> l) {
			List<Object> out = new ArrayList<>();
			for (Object x : l) out.add(sortKeys(x));
			return out;
		}
		return o;
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
		if (r.village().site() != null) v.put("site", pos(r.village().site()));
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
		if (r.provenance() != null) {
			m.put("baseline_file", r.provenance().baselineFile());
			m.put("scenario_sha256", r.provenance().scenarioSha256());
			m.put("git_commit", r.provenance().gitCommit());
		}
		return m;
	}

	private static Map<String, Object> longs(Map<String, Long> in) {
		return new LinkedHashMap<String, Object>(new TreeMap<>(in));
	}

	private static Map<String, Long> readLongs(Object o) {
		Map<String, Long> out = new LinkedHashMap<>();
		if (o == null) return out;
		for (Map.Entry<String, Object> e : Json.obj(o, "counter map").entrySet()) out.put(e.getKey(), ((Number) e.getValue()).longValue());
		return out;
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
		f.put("dinners", d.needs().dinners());
		f.put("diners", d.needs().diners());
		f.put("fed", d.needs().fed());
		f.put("hungry", d.needs().hungry());
		f.put("eaten_nutrition", d.needs().eatenNutrition());
		f.put("eaten_items", d.needs().eatenItems());
		f.put("max_hungry_days", d.needs().maxHungryDays());
		m.put("food", f);
		Map<String, Object> hs = new LinkedHashMap<>();
		hs.put("housed", d.needs().housed());
		hs.put("house_beds", d.needs().houseBeds());
		m.put("housing", hs);
		m.put("treasury", (long) d.treasury());
		Map<String, Object> wg = new LinkedHashMap<>();
		wg.put("runs", d.wages().runs());
		wg.put("bill", d.wages().bill());
		wg.put("paid", d.wages().paid());
		wg.put("spent", d.wages().spent());
		wg.put("strikers", d.wages().strikers());
		m.put("wages", wg);
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
			if (x.detail() != null) {
				DeathRecord.Detail t = x.detail();
				dm.put("day_time", t.dayTime());
				dm.put("name", t.name());
				dm.put("job", t.job());
				dm.put("guard", x.guard());
				dm.put("damage", t.damage());
				dm.put("attacker", t.attacker());
				dm.put("pos", List.of((long) t.x(), (long) t.y(), (long) t.z()));
				dm.put("wall", t.wall());
				dm.put("attacker_origin", t.origin());
				dm.put("attacker_wall", t.attackerWall());
				dm.put("zombified", x.zombified());
			}
			list.add(dm);
		}
		de.put("list", list);
		de.put("logged", d.deathsLogged());
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
		m.put("work", longs(d.work()));
		m.put("worker_status", longs(d.workerStatus()));
		m.put("stockpile_tags", longs(d.stockpileTags()));
		m.put("raid", raidMap(d.raid()));
		Map<String, Object> gl = new LinkedHashMap<>();
		gl.put("in_claim", (long) d.golemsInClaim());
		m.put("golems", gl);
		return m;
	}

	private static Map<String, Object> raidMap(DayMetrics.RaidDay r) {
		Map<String, Object> m = new LinkedHashMap<>();
		m.put("started", (long) r.started());
		m.put("active", (long) r.active());
		m.put("victories", (long) r.victories());
		m.put("losses", (long) r.losses());
		m.put("stopped", (long) r.stopped());
		m.put("gone", (long) r.gone());
		m.put("waves", (long) r.waves());
		m.put("waves_total", (long) r.wavesTotal());
		m.put("villagers_at_start", (long) r.villagersAtStart());
		m.put("villagers_at_end", (long) r.villagersAtEnd());
		m.put("guards_at_start", (long) r.guardsAtStart());
		m.put("guards_qualified_at_start", (long) r.guardsQualifiedAtStart());
		m.put("guards_wounded_at_start", (long) r.guardsWoundedAtStart());
		m.put("guards_unloaded_at_start", (long) r.guardsUnloadedAtStart());
		m.put("villager_deaths", (long) r.villagerDeaths());
		m.put("raiders_killed", (long) r.raidersKilled());
		m.put("origin_random", (long) r.originRandom());
		m.put("early", (long) r.early());
		m.put("reward", (long) r.reward());
		m.put("extra_raiders", (long) r.extraRaiders());
		m.put("spawn_min_distance", (long) r.spawnMinDistance());
		m.put("callup_ticks", (long) r.callupTicks());
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
				new VillagePositions(readPos(v.get("structure")), readPos(v.get("bell")), readPos(v.get("heart")), readPos(v.get("site"))),
				lng(m, "g0"), reloads, m.get("baseline") == null ? null : readDay(Json.obj(m.get("baseline"), "baseline")),
				days, as, m.containsKey("scenario_sha256") || m.containsKey("baseline_file")
						? new Provenance((String) m.get("baseline_file"), (String) m.get("scenario_sha256"), (String) m.get("git_commit")) : null);
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

	/** A count that reports before M11 do not have: reads 0. */
	private static long optLng(Map<String, Object> m, String k) {
		Object o = m.get(k);
		return o instanceof Number n ? n.longValue() : 0L;
	}

	private static double dbl(Map<String, Object> m, String k) {
		Object o = m.get(k);
		return o == null ? Double.NaN : ((Number) o).doubleValue();
	}

	private static DayMetrics readDay(Map<String, Object> m) {
		Map<String, Object> b = Json.obj(m.get("buildings"), "buildings");
		Map<String, Object> s = Json.obj(m.get("stockpile"), "stockpile");
		Map<String, Object> f = Json.obj(m.get("food"), "food");
		Map<String, Object> hs = m.get("housing") == null ? Map.of() : Json.obj(m.get("housing"), "housing");
		Map<String, Object> wg = m.get("wages") == null ? Map.of() : Json.obj(m.get("wages"), "wages");
		Map<String, Object> de = Json.obj(m.get("deaths"), "deaths");
		Map<String, Object> vm = Json.obj(m.get("village_ms"), "village_ms");
		Map<String, Object> sv = Json.obj(m.get("server"), "server");
		Map<String, Object> w = Json.obj(m.get("world"), "world");
		Map<String, Long> items = new LinkedHashMap<>();
		for (Map.Entry<String, Object> e : Json.obj(s.get("items"), "items").entrySet()) items.put(e.getKey(), ((Number) e.getValue()).longValue());
		Map<String, Object> raid = m.get("raid") == null ? null : Json.obj(m.get("raid"), "raid");
		Map<String, Object> golems = m.get("golems") == null ? null : Json.obj(m.get("golems"), "golems");
		List<DeathRecord> deaths = new ArrayList<>();
		for (Object o : Json.arr(de.get("list"), "deaths.list")) {
			Map<String, Object> d = Json.obj(o, "death");
			DeathRecord.Detail t = null;
			if (d.containsKey("name")) {
				List<Object> pos = Json.arr(d.get("pos"), "death.pos");
				t = new DeathRecord.Detail(lng(d, "day_time"), (String) d.get("name"), (String) d.get("job"), (String) d.get("damage"),
						(String) d.get("attacker"), ((Number) pos.get(0)).intValue(), ((Number) pos.get(1)).intValue(), ((Number) pos.get(2)).intValue(),
						(String) d.get("wall"), (String) d.get("attacker_origin"), (String) d.get("attacker_wall"));
			}
			deaths.add(new DeathRecord((String) d.get("cause"), (Boolean) d.get("by_mob"), lng(d, "game_time"), t));
		}
		return new DayMetrics((int) lng(m, "day"), lng(m, "game_time"), (Boolean) m.get("claimed"),
				(int) lng(m, "population"), (int) lng(m, "adults"), (int) lng(m, "babies"), (int) lng(m, "employed"),
				(int) lng(b, "valid"), (int) lng(b, "invalid"), items,
				(int) lng(f, "nutrition"), dbl(f, "days_left"), (int) lng(m, "treasury"), deaths,
				new TickStats(lng(vm, "samples"), dbl(vm, "mean"), dbl(vm, "p95"), dbl(vm, "max")),
				dbl(sv, "mspt"), dbl(sv, "tps"), dbl(m, "wall_seconds"), lng(sv, "heap_used_mb"), lng(w, "save_bytes"),
				readLongs(m.get("work")), readLongs(m.get("worker_status")), readLongs(m.get("stockpile_tags")),
				new DayMetrics.NeedsDay(optLng(f, "dinners"), optLng(f, "diners"), optLng(f, "fed"), optLng(f, "hungry"), optLng(f, "eaten_nutrition"),
						optLng(f, "eaten_items"), optLng(f, "max_hungry_days"), optLng(hs, "housed"), optLng(hs, "house_beds")),
				new DayMetrics.WageDay(optLng(wg, "runs"), optLng(wg, "bill"), optLng(wg, "paid"), optLng(wg, "spent"), optLng(wg, "strikers")),
				readRaid(raid), optLng(de, "logged"), golems == null ? 0 : (int) optLng(golems, "in_claim"));
	}

	/** A report from before M25 has no raid object: every counter is 0 and the spawn distance stays -1. */
	private static DayMetrics.RaidDay readRaid(Map<String, Object> r) {
		if (r == null) return DayMetrics.RaidDay.ZERO;
		return new DayMetrics.RaidDay((int) optLng(r, "started"), (int) optLng(r, "active"), (int) optLng(r, "victories"),
				(int) optLng(r, "losses"), (int) optLng(r, "stopped"), (int) optLng(r, "gone"), (int) optLng(r, "waves"),
				(int) optLng(r, "waves_total"), (int) optLng(r, "villagers_at_start"), (int) optLng(r, "villagers_at_end"),
				(int) optLng(r, "guards_at_start"), (int) optLng(r, "guards_qualified_at_start"), (int) optLng(r, "guards_wounded_at_start"),
				(int) optLng(r, "guards_unloaded_at_start"), (int) optLng(r, "villager_deaths"), (int) optLng(r, "raiders_killed"),
				(int) optLng(r, "origin_random"), (int) optLng(r, "early"), (int) optLng(r, "reward"), (int) optLng(r, "extra_raiders"),
				r.containsKey("spawn_min_distance") ? (int) optLng(r, "spawn_min_distance") : -1, (int) optLng(r, "callup_ticks"));
	}
}
