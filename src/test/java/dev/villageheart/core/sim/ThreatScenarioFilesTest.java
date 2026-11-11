package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/**
 * T-L3-2 and T-L3-3 files parse before a multi-day run. {@code siege.started} and the lure peak are per-day deltas, so an
 * unscoped sum or max would read only the last day; the scopes are {@code from day 1}.
 */
class ThreatScenarioFilesTest {
	private static Scenario load(String name) throws Exception {
		return ScenarioParser.parse(name, Files.readString(Path.of("sim/scenarios/" + name + ".json")));
	}

	@Test
	void raidReloadStartsLateOnDayThreeAndKeepsTheRaidAssertions() throws Exception {
		Scenario raid = load("raid");
		Scenario reload = load("raid_reload");
		assertEquals(6, reload.defaultDays());
		assertFalse(reload.threats());
		var raidAction = reload.actions().stream().filter(a -> a.type().equals("raid")).toList();
		assertEquals(1, raidAction.size());
		assertEquals(3, raidAction.get(0).day());
		assertEquals(20000, raidAction.get(0).tick());
		assertEquals(1, ((Number) raidAction.get(0).fields().get("omen")).intValue());
		assertEquals(Boolean.FALSE, raidAction.get(0).fields().get("scaled"));
		for (String a : raid.assertions()) assertTrue(reload.assertions().contains(a), a);
		assertTrue(reload.assertions().contains("raid.active == 1 on day 3"));
		assertTrue(reload.assertions().contains("sum(raid.started) == 1 from day 1"));
	}

	@Test
	void siegeIsTheRaidTownWithOneSiegeAndNoRaid() throws Exception {
		Scenario raid = load("raid");
		Scenario siege = load("siege");
		assertEquals(4, siege.defaultDays());
		assertFalse(siege.threats());
		assertTrue(siege.actions().stream().noneMatch(a -> a.type().equals("raid")));
		assertEquals(raid.actions().size(), siege.actions().size(), "the raid action is replaced by the siege action");
		var action = siege.actions().stream().filter(a -> a.type().equals("siege")).toList();
		assertEquals(1, action.size());
		assertEquals(2, action.get(0).day());
		assertEquals(8, ((Number) action.get(0).fields().get("zombies")).intValue());
		assertEquals(java.util.List.of(
				"claimed == 1 on every day",
				"sum(siege.started) == 1 from day 1",
				"defence.breached == 0 on last day",
				"sum(siege.villager_deaths) <= 2 from day 1",
				"max(lure.max_lured) <= 24 from day 1",
				"village_ms.mean < 1 on every day"), siege.assertions());
	}
}
