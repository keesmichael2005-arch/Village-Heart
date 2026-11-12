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
		TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb, long worldSaveBytes,
		Map<String, Long> work, Map<String, Long> workerStatus, Map<String, Long> stockpileTags, NeedsDay needs, WageDay wages,
		RaidDay raid, long deathsLogged, GolemDay golems, SiegeDay siege, TierDay tier) {

	/**
	 * What dinner and housing did that day (M11): the dinner's totals, the hungriest villager at the end of the day, and how many villagers
	 * sleep in a valid House. A report without them (M2-M10) reads zeros.
	 */
	public record NeedsDay(long dinners, long diners, long fed, long hungry, long eatenNutrition, long eatenItems, long maxHungryDays,
			long housed, long houseBeds) {
		public static final NeedsDay ZERO = new NeedsDay(0, 0, 0, 0, 0, 0, 0, 0, 0);
	}

	/**
	 * What the wage runs of that day did (M12): how many ran, what they cost in all, how many workers were paid and how many went on strike.
	 * A report without them (M2-M11) reads zeros.
	 */
	public record WageDay(long runs, long bill, long paid, long spent, long strikers) {
		public static final WageDay ZERO = new WageDay(0, 0, 0, 0, 0);
	}

	/**
	 * Golems that day (M19): vanilla spawn attempts the gate refused, and iron golems standing in the claim at the sample.
	 * A report written before M19 reads zeros.
	 */
	public record GolemDay(long vanillaBlocked, long inClaim) {
		public static final GolemDay ZERO = new GolemDay(0, 0);
	}

	/**
	 * The village tier and the fed-days streak at the end of the day. {@code name} is the tier id ({@code hamlet}, {@code village},
	 * {@code town}). A report written before this field reads as hamlet with a streak of 0.
	 */
	public record TierDay(String name, long fedStreak) {
		public static final TierDay NONE = new TierDay("hamlet", 0);

		public TierDay {
			if (name == null || name.isBlank()) name = "hamlet";
			name = name.toLowerCase(java.util.Locale.ROOT);
			if (fedStreak < 0) throw new IllegalArgumentException("fedStreak must be >= 0: " + fedStreak);
		}

		/** 0 hamlet, 1 village, 2 town. Anything else reads as hamlet so the metric stays a number. */
		public int ordinal() {
			return switch (name) {
				case "village" -> 1;
				case "town" -> 2;
				default -> 0;
			};
		}
	}

	/**
	 * One day's raid counters (M25 3.7). A report without them reads zeros, except {@code spawnMinDistance}, which stays -1 until a wave
	 * spawn is measured. {@code callupTicks} is 0 when no raid started that day.
	 */
	public record RaidDay(int started, int active, int victories, int losses, int stopped, int gone, int waves, int wavesTotal,
			int villagersAtStart, int villagersAtEnd, int guardsAtStart, int guardsQualifiedAtStart, int guardsWoundedAtStart,
			int guardsUnloadedAtStart, int villagerDeaths, int raidersKilled, int originRandom, int early, int reward, int extraRaiders,
			int spawnMinDistance, int callupTicks) {
		public static final RaidDay ZERO = new RaidDay(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, -1, 0);

		public void flatten(Map<String, Double> m) {
			m.put("raid.started", (double) started);
			m.put("raid.active", (double) active);
			m.put("raid.victories", (double) victories);
			m.put("raid.losses", (double) losses);
			m.put("raid.stopped", (double) stopped);
			m.put("raid.gone", (double) gone);
			m.put("raid.waves", (double) waves);
			m.put("raid.waves_total", (double) wavesTotal);
			m.put("raid.villagers_at_start", (double) villagersAtStart);
			m.put("raid.villagers_at_end", (double) villagersAtEnd);
			m.put("raid.guards_at_start", (double) guardsAtStart);
			m.put("raid.guards_qualified_at_start", (double) guardsQualifiedAtStart);
			m.put("raid.guards_wounded_at_start", (double) guardsWoundedAtStart);
			m.put("raid.guards_unloaded_at_start", (double) guardsUnloadedAtStart);
			m.put("raid.villager_deaths", (double) villagerDeaths);
			m.put("raid.raiders_killed", (double) raidersKilled);
			m.put("raid.origin_random", (double) originRandom);
			m.put("raid.early", (double) early);
			m.put("raid.reward", (double) reward);
			m.put("raid.extra_raiders", (double) extraRaiders);
			m.put("raid.spawn_min_distance", (double) spawnMinDistance);
			m.put("raid.callup_ticks", (double) callupTicks);
		}
	}

	/**
	 * One day's defence and siege counters (M25 3.17). A report without them reads zeros. {@code threatScore} is the live
	 * score times 100. {@code entityMs} is the overworld entity tick, measured the same way as {@code village_ms}.
	 */
	public record SiegeDay(int cells, int unseen, int breached, int cracked, int breachedNoon, int blocksBroken,
			int brokenZombie, int brokenRavager, int brokenVindicator, int brokenCreeper, int brokenOther, int repairs,
			int repairItems, int started, int zombies, int killed, int villagerDeaths, int paths, int maxLured, int threatScore,
			TickStats entityMs) {
		public static final SiegeDay ZERO = new SiegeDay(0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, TickStats.EMPTY);

		public void flatten(Map<String, Double> m) {
			m.put("defence.cells", (double) cells);
			m.put("defence.unseen", (double) unseen);
			m.put("defence.breached", (double) breached);
			m.put("defence.cracked", (double) cracked);
			m.put("defence.breached_noon", (double) breachedNoon);
			m.put("defence.blocks_broken", (double) blocksBroken);
			m.put("defence.broken.zombie", (double) brokenZombie);
			m.put("defence.broken.ravager", (double) brokenRavager);
			m.put("defence.broken.vindicator", (double) brokenVindicator);
			m.put("defence.broken.creeper", (double) brokenCreeper);
			m.put("defence.broken.other", (double) brokenOther);
			m.put("defence.repairs", (double) repairs);
			m.put("defence.repair_items", (double) repairItems);
			m.put("siege.started", (double) started);
			m.put("siege.zombies", (double) zombies);
			m.put("siege.killed", (double) killed);
			m.put("siege.villager_deaths", (double) villagerDeaths);
			m.put("siege.paths", (double) paths);
			m.put("lure.max_lured", (double) maxLured);
			m.put("threat.score", (double) threatScore);
			m.put("entity_ms.mean", entityMs.meanMs());
			m.put("entity_ms.p95", entityMs.p95Ms());
			m.put("entity_ms.max", entityMs.maxMs());
			m.put("entity_ms.samples", (double) entityMs.samples());
		}
	}

	/** Without the tier record (reports and callers from before it). */
	public DayMetrics(int day, long gameTime, boolean claimed, int population, int adults, int babies, int employed,
			int buildingsValid, int buildingsInvalid, Map<String, Long> stockpile, int foodNutrition, double foodDaysLeft, int treasury,
			List<DeathRecord> deaths, TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb,
			long worldSaveBytes, Map<String, Long> work, Map<String, Long> workerStatus, Map<String, Long> stockpileTags, NeedsDay needs,
			WageDay wages, RaidDay raid, long deathsLogged, GolemDay golems, SiegeDay siege) {
		this(day, gameTime, claimed, population, adults, babies, employed, buildingsValid, buildingsInvalid, stockpile, foodNutrition,
				foodDaysLeft, treasury, deaths, villageMs, serverMspt, serverTps, wallSeconds, heapUsedMb, worldSaveBytes, work, workerStatus,
				stockpileTags, needs, wages, raid, deathsLogged, golems, siege, TierDay.NONE);
	}

	/** Without the M25 raid and siege records, with the golem sample (M19). */
	public DayMetrics(int day, long gameTime, boolean claimed, int population, int adults, int babies, int employed,
			int buildingsValid, int buildingsInvalid, Map<String, Long> stockpile, int foodNutrition, double foodDaysLeft, int treasury,
			List<DeathRecord> deaths, TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb,
			long worldSaveBytes, Map<String, Long> work, Map<String, Long> workerStatus, Map<String, Long> stockpileTags, NeedsDay needs,
			WageDay wages, GolemDay golems) {
		this(day, gameTime, claimed, population, adults, babies, employed, buildingsValid, buildingsInvalid, stockpile, foodNutrition,
				foodDaysLeft, treasury, deaths, villageMs, serverMspt, serverTps, wallSeconds, heapUsedMb, worldSaveBytes, work, workerStatus,
				stockpileTags, needs, wages, RaidDay.ZERO, 0L, golems, SiegeDay.ZERO, TierDay.NONE);
	}

	/** Without the M25 raid record, with the golem count (a bare number of golems in the claim). */
	public DayMetrics(int day, long gameTime, boolean claimed, int population, int adults, int babies, int employed,
			int buildingsValid, int buildingsInvalid, Map<String, Long> stockpile, int foodNutrition, double foodDaysLeft, int treasury,
			List<DeathRecord> deaths, TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb,
			long worldSaveBytes, Map<String, Long> work, Map<String, Long> workerStatus, Map<String, Long> stockpileTags, NeedsDay needs,
			WageDay wages, RaidDay raid, long deathsLogged, int golemsInClaim) {
		this(day, gameTime, claimed, population, adults, babies, employed, buildingsValid, buildingsInvalid, stockpile, foodNutrition,
				foodDaysLeft, treasury, deaths, villageMs, serverMspt, serverTps, wallSeconds, heapUsedMb, worldSaveBytes, work, workerStatus,
				stockpileTags, needs, wages, raid, deathsLogged, new GolemDay(0, golemsInClaim), SiegeDay.ZERO, TierDay.NONE);
	}

	/** Without the golem sample and the M25 records, with the tier. */
	public DayMetrics(int day, long gameTime, boolean claimed, int population, int adults, int babies, int employed,
			int buildingsValid, int buildingsInvalid, Map<String, Long> stockpile, int foodNutrition, double foodDaysLeft, int treasury,
			List<DeathRecord> deaths, TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb,
			long worldSaveBytes, Map<String, Long> work, Map<String, Long> workerStatus, Map<String, Long> stockpileTags, NeedsDay needs,
			WageDay wages, TierDay tier) {
		this(day, gameTime, claimed, population, adults, babies, employed, buildingsValid, buildingsInvalid, stockpile, foodNutrition,
				foodDaysLeft, treasury, deaths, villageMs, serverMspt, serverTps, wallSeconds, heapUsedMb, worldSaveBytes, work, workerStatus,
				stockpileTags, needs, wages, RaidDay.ZERO, 0L, GolemDay.ZERO, SiegeDay.ZERO, tier);
	}

	/** Without the golem sample, the M25 records and the tier. */
	public DayMetrics(int day, long gameTime, boolean claimed, int population, int adults, int babies, int employed,
			int buildingsValid, int buildingsInvalid, Map<String, Long> stockpile, int foodNutrition, double foodDaysLeft, int treasury,
			List<DeathRecord> deaths, TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb,
			long worldSaveBytes, Map<String, Long> work, Map<String, Long> workerStatus, Map<String, Long> stockpileTags, NeedsDay needs,
			WageDay wages) {
		this(day, gameTime, claimed, population, adults, babies, employed, buildingsValid, buildingsInvalid, stockpile, foodNutrition,
				foodDaysLeft, treasury, deaths, villageMs, serverMspt, serverTps, wallSeconds, heapUsedMb, worldSaveBytes, work, workerStatus,
				stockpileTags, needs, wages, RaidDay.ZERO, 0L, GolemDay.ZERO, SiegeDay.ZERO, TierDay.NONE);
	}

	/** Without the M12 wage record. */
	public DayMetrics(int day, long gameTime, boolean claimed, int population, int adults, int babies, int employed,
			int buildingsValid, int buildingsInvalid, Map<String, Long> stockpile, int foodNutrition, double foodDaysLeft, int treasury,
			List<DeathRecord> deaths, TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb,
			long worldSaveBytes, Map<String, Long> work, Map<String, Long> workerStatus, Map<String, Long> stockpileTags, NeedsDay needs) {
		this(day, gameTime, claimed, population, adults, babies, employed, buildingsValid, buildingsInvalid, stockpile, foodNutrition,
				foodDaysLeft, treasury, deaths, villageMs, serverMspt, serverTps, wallSeconds, heapUsedMb, worldSaveBytes, work, workerStatus,
				stockpileTags, needs, WageDay.ZERO);
	}

	/** Without the M11 needs record. */
	public DayMetrics(int day, long gameTime, boolean claimed, int population, int adults, int babies, int employed,
			int buildingsValid, int buildingsInvalid, Map<String, Long> stockpile, int foodNutrition, double foodDaysLeft, int treasury,
			List<DeathRecord> deaths, TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb,
			long worldSaveBytes, Map<String, Long> work, Map<String, Long> workerStatus, Map<String, Long> stockpileTags) {
		this(day, gameTime, claimed, population, adults, babies, employed, buildingsValid, buildingsInvalid, stockpile, foodNutrition,
				foodDaysLeft, treasury, deaths, villageMs, serverMspt, serverTps, wallSeconds, heapUsedMb, worldSaveBytes, work, workerStatus,
				stockpileTags, NeedsDay.ZERO);
	}

	public DayMetrics {
		java.util.Objects.requireNonNull(needs, "needs");
		java.util.Objects.requireNonNull(wages, "wages");
		java.util.Objects.requireNonNull(raid, "raid");
		java.util.Objects.requireNonNull(siege, "siege");
		java.util.Objects.requireNonNull(siege.entityMs(), "entityMs");
		if (deathsLogged < 0) throw new IllegalArgumentException("deathsLogged must be >= 0: " + deathsLogged);
		java.util.Objects.requireNonNull(golems, "golems");
		java.util.Objects.requireNonNull(tier, "tier");
		stockpile = Map.copyOf(stockpile);
		deaths = List.copyOf(deaths);
		work = Map.copyOf(work);
		workerStatus = Map.copyOf(workerStatus);
		stockpileTags = Map.copyOf(stockpileTags);
	}

	/** Without the M9 families (work counters, worker statuses, stockpile by tag): they read empty. */
	public DayMetrics(int day, long gameTime, boolean claimed, int population, int adults, int babies, int employed,
			int buildingsValid, int buildingsInvalid, Map<String, Long> stockpile, int foodNutrition, double foodDaysLeft, int treasury,
			List<DeathRecord> deaths, TickStats villageMs, double serverMspt, double serverTps, double wallSeconds, long heapUsedMb,
			long worldSaveBytes) {
		this(day, gameTime, claimed, population, adults, babies, employed, buildingsValid, buildingsInvalid, stockpile, foodNutrition,
				foodDaysLeft, treasury, deaths, villageMs, serverMspt, serverTps, wallSeconds, heapUsedMb, worldSaveBytes, Map.of(), Map.of(), Map.of(),
				NeedsDay.ZERO);
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
		for (Map.Entry<String, Long> e : stockpileTags.entrySet()) m.put("stockpile.tag." + e.getKey(), (double) e.getValue());
		for (Map.Entry<String, Long> e : work.entrySet()) m.put("work." + e.getKey(), (double) e.getValue());
		for (Map.Entry<String, Long> e : workerStatus.entrySet()) m.put("workers.status." + e.getKey(), (double) e.getValue());
		m.put("food.nutrition", (double) foodNutrition);
		m.put("food.days_left", foodDaysLeft);
		// M26: 1 when the stock cannot feed the village one more dinner (under one day left; nobody to feed also counts). days_left is a ratio that
		// decays towards 0 and is never exactly 0, so "food days left is 0" is read as "under 1" (assert with zerorun(1 - food.out)).
		m.put("food.out", Double.isNaN(foodDaysLeft) || foodDaysLeft < 1.0 ? 1.0 : 0.0);
		m.put("food.dinners", (double) needs.dinners());
		m.put("food.diners", (double) needs.diners());
		m.put("food.fed", (double) needs.fed());
		m.put("food.hungry", (double) needs.hungry());
		m.put("food.eaten_nutrition", (double) needs.eatenNutrition());
		m.put("food.eaten_items", (double) needs.eatenItems());
		m.put("food.eaten_per_item", needs.eatenItems() == 0 ? Double.NaN : (double) needs.eatenNutrition() / needs.eatenItems()); // for reading; assertions use the sums
		m.put("food.max_hungry_days", (double) needs.maxHungryDays());
		m.put("housing.housed", (double) needs.housed());
		m.put("housing.house_beds", (double) needs.houseBeds());
		m.put("treasury", (double) treasury);
		m.put("wages.runs", (double) wages.runs());
		m.put("wages.bill", (double) wages.bill());
		m.put("wages.paid", (double) wages.paid());
		m.put("wages.spent", (double) wages.spent());
		m.put("wages.strikers", (double) wages.strikers());
		m.put("golems.vanilla_blocked", (double) golems.vanillaBlocked());
		m.put("golems.in_claim", (double) golems.inClaim());
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
		raid.flatten(m);
		m.put("deaths.logged", (double) deathsLogged);
		siege.flatten(m);
		m.put("village.tier", (double) tier.ordinal());
		m.put("tier.fed_streak", (double) tier.fedStreak());
		return m;
	}
}
