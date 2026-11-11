package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.sim.MetricsJson.Pos;
import dev.villageheart.core.sim.MetricsJson.Report;
import dev.villageheart.core.sim.MetricsJson.VillagePositions;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The M26-prep death log: detailed death records in the report JSON, the wall ring side, and the summary's death and hire tables. */
class DeathLogTest {
	private static final DeathRecord GUARD_BY_ZOMBIE = new DeathRecord("minecraft:zombie", true, 24000L + 14500,
			new DeathRecord.Detail(14500, "Ada", "guard", "minecraft:mob_attack", "minecraft:zombie", 700, 64, 830, WallRing.INSIDE,
					DeathRecord.NATURAL, WallRing.INSIDE));
	private static final DeathRecord FARMER_ZOMBIFIED = new DeathRecord(DeathRecord.ZOMBIFIED, true, 48000L + 15000,
			new DeathRecord.Detail(15000, "Bo", "farmer", "minecraft:mob_attack", "minecraft:zombie", 680, 64, 860, WallRing.OUTSIDE,
					DeathRecord.SCRIPTED, WallRing.OUTSIDE));

	private static DayMetrics day(int d, List<DeathRecord> deaths, Map<String, Long> work) {
		DayMetrics x = SimFixtures.day(d, 8, deaths, 0.0003);
		return new DayMetrics(x.day(), x.gameTime(), x.claimed(), x.population(), x.adults(), x.babies(), x.employed(), 0, 0, Map.of(), 0, 0.0, 0,
				deaths, x.villageMs(), 1, 1, 1, 1, 1, work, Map.of(), Map.of());
	}

	private static Report report(List<DayMetrics> days) {
		return new Report("defended", SimStatus.PASS, null, true, 1, 0, null, 1, "normal", days.size(), "1.21.11",
				new VillagePositions(new Pos(0, 0, 0), new Pos(1, 64, 1), new Pos(2, 64, 2)), 41, List.of(), day(0, List.of(), Map.of()), days, List.of());
	}

	@Test
	void detailedDeathsRoundTrip() {
		Report r = report(List.of(day(1, List.of(GUARD_BY_ZOMBIE, new DeathRecord("minecraft:fall", false, 3)), Map.of()),
				day(2, List.of(FARMER_ZOMBIFIED), Map.of())));
		assertEquals(r, MetricsJson.read(MetricsJson.write(r)));
	}

	@SuppressWarnings("unchecked")
	@Test
	void pinnedDetailKeys() {
		Map<String, Object> m = (Map<String, Object>) Json.parse(MetricsJson.write(report(List.of(day(1, List.of(FARMER_ZOMBIFIED), Map.of())))));
		Map<String, Object> day = (Map<String, Object>) ((List<Object>) m.get("days")).get(0);
		Map<String, Object> death = (Map<String, Object>) ((List<Object>) ((Map<String, Object>) day.get("deaths")).get("list")).get(0);
		assertEquals(List.of("cause", "by_mob", "game_time", "day_time", "name", "job", "guard", "damage", "attacker", "pos", "wall", "attacker_origin",
				"attacker_wall", "zombified"),
				List.copyOf(death.keySet()));
		assertEquals(true, death.get("zombified"));
		assertEquals(false, death.get("guard"));
		assertEquals(List.of(680L, 64L, 860L), death.get("pos"));
	}

	@Test
	void flagsFollowTheRecord() {
		assertTrue(GUARD_BY_ZOMBIE.guard());
		assertFalse(GUARD_BY_ZOMBIE.zombified());
		assertTrue(FARMER_ZOMBIFIED.zombified());
		assertFalse(FARMER_ZOMBIFIED.guard());
		assertFalse(new DeathRecord("zombified", true, 1).guard()); // an old record has no detail
		assertEquals(GUARD_BY_ZOMBIE.detail(), GUARD_BY_ZOMBIE.withCause("zombified", true).detail());
	}

	@Test
	void wallRingSides() {
		WallRing ring = new WallRing(100, 200, 44, 32); // wall cells x 100..143, z 200..231
		assertEquals(WallRing.INSIDE, ring.side(101, 201));
		assertEquals(WallRing.INSIDE, ring.side(142, 230));
		assertEquals(WallRing.ON_WALL, ring.side(100, 210));
		assertEquals(WallRing.ON_WALL, ring.side(122, 231)); // the gate cell (x offset 22 on the south edge)
		assertEquals(WallRing.ON_WALL, ring.side(143, 200));
		assertEquals(WallRing.OUTSIDE, ring.side(99, 210));
		assertEquals(WallRing.OUTSIDE, ring.side(122, 232));
		assertEquals(WallRing.OUTSIDE, ring.side(144, 215));
	}

	@Test
	void summaryHasOneRowPerDeathAndTheHireCounts() {
		String md = DeathLog.markdown(List.of(
				day(1, List.of(GUARD_BY_ZOMBIE), Map.of(DeathLog.HIRES, 4L)),
				day(2, List.of(FARMER_ZOMBIFIED, new DeathRecord("minecraft:fall", false, 5)),
						Map.of(DeathLog.HIRES, 1L, DeathLog.REHIRES, 1L, DeathLog.WAIT_PREFIX + "guard.night", 30L,
								DeathLog.WAIT_PREFIX + "guard.no_free_villager", 7L, "guard.kills", 2L,
								DeathLog.SPAWN_PREFIX + WallRing.INSIDE, 3L, DeathLog.SPAWN_PREFIX + WallRing.OUTSIDE, 9L,
								DeathLog.INSIDE_PREFIX + DeathRecord.SCRIPTED, 2L, DeathLog.PRESENT_PREFIX + DeathRecord.NATURAL, 40L))));
		assertTrue(md.contains("| 1 | 14500 | Ada | guard | yes | minecraft:zombie | minecraft:mob_attack | minecraft:zombie | natural | 700 64 830 | inside | inside | no |"), md);
		assertTrue(md.contains("| 2 | 15000 | Bo | farmer | no | zombified | minecraft:mob_attack | minecraft:zombie | scripted | 680 64 860 | outside | outside | yes |"), md);
		assertTrue(md.contains("| 2 | ? | ? | ? | ? | minecraft:fall | ? | ? | ? | ? | ? | ? | no |"), md);
		assertTrue(md.contains("| 1 | 4 | 0 | - |"), md);
		assertTrue(md.contains("| 2 | 1 | 1 | guard.night 30, guard.no_free_villager 7 |"), md);
		assertTrue(md.contains("| 1 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 | 0 |"), md);
		assertTrue(md.contains("| 2 | 3 | 0 | 9 | 0 | 2 | 0 | 0 | 0 | 0 | 40 |"), md);
	}

	@Test
	void noDeathsSaysSo() {
		assertTrue(DeathLog.markdown(List.of(day(1, List.of(), Map.of()))).contains("## Deaths\n\nNone."));
	}
}
