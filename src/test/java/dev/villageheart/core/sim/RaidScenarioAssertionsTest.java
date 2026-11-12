package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-L1-12. The raid scenario's assertions accept a won raid and reject each way the plan says a run fails. */
class RaidScenarioAssertionsTest {
	private static Scenario scenario() throws Exception {
		return ScenarioParser.parse("raid", Files.readString(Path.of("sim/scenarios/raid.json")));
	}

	private static ScenarioResult eval(MetricsTable table) throws Exception {
		return ScenarioEvaluator.evaluate(scenario(), table, List.of());
	}

	@Test void twelveAtTheStartThreeDeathsAndAVictoryPasses() throws Exception {
		ScenarioResult r = eval(table(raidPass(), siegePass(486, 0, 0, 2, 1, 500)));
		assertEquals(SimStatus.PASS, r.status(), String.valueOf(r.results()));
	}

	@Test void fourDeathsFails() throws Exception {
		DayMetrics.RaidDay[] raid = raidPass();
		raid[4] = raid(0, 0, 1, 0, 0, 0, 0, 9, 0, 0, 4, 0, 0);
		assertEquals(SimStatus.FAIL, eval(table(raid, siegePass(486, 0, 0, 2, 1, 500))).status());
	}

	@Test void stoppedRaidFails() throws Exception {
		DayMetrics.RaidDay[] raid = raidPass();
		raid[4] = raid(0, 0, 0, 0, 1, 0, 12, 12, 4, 4, 0, 0, 0);
		assertEquals(SimStatus.FAIL, eval(table(raid, siegePass(486, 0, 0, 2, 1, 500))).status());
	}

	@Test void stillActiveOnTheLastDayFails() throws Exception {
		DayMetrics.RaidDay[] raid = raidPass();
		raid[5] = raid(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
		assertEquals(SimStatus.FAIL, eval(table(raid, siegePass(486, 0, 0, 2, 1, 500))).status());
	}

	@Test void slowCallupFails() throws Exception {
		DayMetrics.RaidDay[] raid = raidPass();
		raid[2] = raid(1, 1, 0, 0, 0, 0, 12, 12, 4, 4, 0, 0, 99999);
		assertEquals(SimStatus.FAIL, eval(table(raid, siegePass(486, 0, 0, 2, 1, 500))).status());
	}

	@Test void loggedDeathsMustEqualTheList() throws Exception {
		assertEquals(SimStatus.FAIL, eval(table(raidPass(), siegePass(486, 0, 0, 2, 1, 500), 1)).status());
	}

	@Test void eightOfTwelveLeftFails() throws Exception {
		DayMetrics.RaidDay[] raid = raidPass();
		raid[4] = raid(0, 0, 1, 0, 0, 0, 12, 8, 4, 4, 3, 0, 0);
		assertEquals(SimStatus.FAIL, eval(table(raid, siegePass(486, 0, 0, 2, 1, 500))).status());
	}

	@Test void twoGolemsFails() throws Exception {
		assertEquals(SimStatus.FAIL, eval(table(raidPass(), siegePass(486, 0, 0, 2, 1, 500), 0, 2)).status());
	}

	@Test void threeGuardsAtTheStartFails() throws Exception {
		DayMetrics.RaidDay[] raid = raidPass();
		raid[2] = raid(1, 1, 0, 0, 0, 0, 12, 12, 3, 4, 0, 0, 40);
		assertEquals(SimStatus.FAIL, eval(table(raid, siegePass(486, 0, 0, 2, 1, 500))).status());
	}

	@Test void threeQualifiedOfFourFails() throws Exception {
		DayMetrics.RaidDay[] raid = raidPass();
		raid[2] = raid(1, 1, 0, 0, 0, 0, 12, 12, 4, 3, 0, 0, 40);
		assertEquals(SimStatus.FAIL, eval(table(raid, siegePass(486, 0, 0, 2, 1, 500))).status());
	}

	@Test void repairsShortOfTheBreachFails() throws Exception {
		assertEquals(SimStatus.FAIL, eval(table(raidPass(), siegePass(486, 0, 0, 0, 2, 500))).status());
	}

	@Test void aRewardOnTheTestRaidFails() throws Exception {
		DayMetrics.RaidDay[] raid = raidPass();
		raid[4] = raid(0, 0, 1, 0, 0, 0, 12, 10, 4, 4, 3, 8, 0);
		assertEquals(SimStatus.FAIL, eval(table(raid, siegePass(486, 0, 0, 2, 1, 500))).status());
	}

	@Test void aNoonBreachOnTheLastDayFails() throws Exception {
		assertEquals(SimStatus.FAIL, eval(table(raidPass(), siegePass(486, 1, 1, 2, 1, 500))).status());
	}

	@Test void aBreachAfterNoonPasses() throws Exception {
		ScenarioResult r = eval(table(raidPass(), siegePass(486, 0, 1, 2, 1, 500)));
		assertEquals(SimStatus.PASS, r.status(), String.valueOf(r.results()));
	}

	@Test void twoHundredAndFiftyCellsFails() throws Exception {
		assertEquals(SimStatus.FAIL, eval(table(raidPass(), siegePass(250, 0, 0, 2, 1, 500))).status());
	}

	@Test void aThreatScoreOf390Fails() throws Exception {
		assertEquals(SimStatus.FAIL, eval(table(raidPass(), siegePass(486, 0, 0, 2, 1, 390))).status());
	}

	/** Days 1..6. The raid is fought on days 3..5 (indexes 2..4) and is over on day 6. */
	private static DayMetrics.RaidDay[] raidPass() {
		DayMetrics.RaidDay z = DayMetrics.RaidDay.ZERO;
		return new DayMetrics.RaidDay[] {
				z, z,
				raid(1, 1, 0, 0, 0, 0, 12, 0, 4, 4, 0, 0, 40),
				raid(0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0),
				raid(0, 0, 1, 0, 0, 0, 0, 10, 0, 0, 3, 0, 0),
				z};
	}

	private static DayMetrics.RaidDay raid(int started, int active, int victories, int losses, int stopped, int gone,
			int atStart, int atEnd, int guards, int qualified, int deaths, int reward, int callup) {
		return new DayMetrics.RaidDay(started, active, victories, losses, stopped, gone, 0, 0, atStart, atEnd, guards, qualified,
				0, 0, deaths, 0, 0, 0, reward, 0, -1, callup);
	}

	/** cells, noon breaches, breaches, repairs, blocks broken, threat score. Day 1 carries the cells; day 2 the score; day 6 the noon. */
	private static DayMetrics.SiegeDay[] siegePass(int cells, int noon, int breached, int repairs, int broken, int score) {
		DayMetrics.SiegeDay[] days = new DayMetrics.SiegeDay[6];
		for (int i = 0; i < 6; i++) days[i] = siege(cells, 0, 0, 0, 0, score);
		days[0] = siege(cells, 0, 0, 0, 0, 0);
		days[1] = siege(cells, 0, 0, 0, 0, score);
		days[5] = siege(cells, noon, breached, repairs, broken, score);
		days[4] = siege(cells, 0, 0, repairs, broken, score);
		return days;
	}

	private static DayMetrics.SiegeDay siege(int cells, int noon, int breached, int repairs, int broken, int score) {
		return new DayMetrics.SiegeDay(cells, 0, breached, 0, noon, broken, 0, 0, 0, 0, 0, repairs, 0, 0, 0, 0, 0, 0, 0, score, TickStats.EMPTY);
	}

	private static MetricsTable table(DayMetrics.RaidDay[] raid, DayMetrics.SiegeDay[] siege) {
		return table(raid, siege, 0, 1);
	}

	private static MetricsTable table(DayMetrics.RaidDay[] raid, DayMetrics.SiegeDay[] siege, long loggedOff) {
		return table(raid, siege, loggedOff, 1);
	}

	private static MetricsTable table(DayMetrics.RaidDay[] raid, DayMetrics.SiegeDay[] siege, long loggedOff, int golems) {
		List<DayMetrics> days = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			List<DeathRecord> deaths = i == 4 ? List.of(
					new DeathRecord("minecraft:zombie", true, 1),
					new DeathRecord("minecraft:zombie", true, 2),
					new DeathRecord("minecraft:zombie", true, 3)) : List.of();
			int pop = i == 0 ? 12 : 12;
			int employed = i == 0 ? 9 : 9;
			long logged = deaths.size() - (i == 4 ? loggedOff : 0);
			days.add(new DayMetrics(i + 1, 41L + 24000L * (i + 1), true, pop, pop, 0, employed, 12, 0, java.util.Map.of(),
					100, 2.0, 64, deaths, new TickStats(24000, 0.001, 0.002, 0.01), 1.5, 20, 0, 0, 0,
					java.util.Map.of(), java.util.Map.of(), java.util.Map.of(), DayMetrics.NeedsDay.ZERO, DayMetrics.WageDay.ZERO,
					raid[i], logged, new DayMetrics.GolemDay(0, golems), siege[i]));
		}
		DayMetrics baseline = new DayMetrics(0, 41L, true, 1, 1, 0, 0, 0, 0, java.util.Map.of(), 0, 0, 64, List.of(),
				TickStats.EMPTY, 0, 20, 0, 0, 0, java.util.Map.of(), java.util.Map.of(), java.util.Map.of(),
				DayMetrics.NeedsDay.ZERO, DayMetrics.WageDay.ZERO, DayMetrics.RaidDay.ZERO, 0, new DayMetrics.GolemDay(0, golems), DayMetrics.SiegeDay.ZERO);
		return new MetricsTable(baseline, days);
	}
}
