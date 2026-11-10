package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T-L1-14: the {@code defended} scenario and the three actions M18 adds name their bad fields at parse time. */
class DefendedScenarioTest {
	private static void bad(String type, Map<String, Object> f, String field) {
		IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> ActionSpecs.validate(type, f), type + " " + f);
		assertTrue(e.getMessage().contains(field), "the error names " + field + ": " + e.getMessage());
	}

	@Test
	void defendedParsesWithItsThreatAndGuards() throws Exception {
		Scenario s = ScenarioParser.parse("defended", Files.readString(Path.of("sim/scenarios/defended.json")));
		assertEquals(10, s.defaultDays());
		long spawns = s.actions().stream().filter(a -> a.type().equals("night_spawn")).count();
		assertEquals(9, spawns, "one scripted night for each of days 1..9");
		assertEquals(2, s.actions().stream().filter(a -> a.type().equals("hire") && "guard".equals(a.fields().get("job"))).count());
		assertTrue(s.actions().stream().anyMatch(a -> a.type().equals("tier")));
		assertTrue(s.actions().stream().anyMatch(a -> a.type().equals("training_yard")));
		assertTrue(s.assertions().stream().anyMatch(x -> x.contains("work.guard.kills") && x.contains("15")));
		assertTrue(s.assertions().stream().anyMatch(x -> x.contains("deaths.mobs") && x.contains("<= 1")));
	}

	@Test
	void tierNeedsAKnownTier() {
		ActionSpecs.validate("tier", Map.of("tier", "village"));
		bad("tier", Map.of("tier", "city"), "tier");
		bad("tier", Map.of(), "tier");
	}

	@Test
	void trainingYardNeedsAt() {
		ActionSpecs.validate("training_yard", Map.of("at", List.of(38L, 12L)));
		bad("training_yard", Map.of(), "at");
		bad("training_yard", Map.of("at", List.of(1L)), "at");
	}

	@Test
	void nightSpawnFieldErrors() {
		ActionSpecs.validate("night_spawn", Map.of("mob", "minecraft:zombie", "count", 3L, "radius", 20L));
		bad("night_spawn", Map.of("count", 3L), "mob");
		bad("night_spawn", Map.of("mob", "zombie", "count", 3L), "mob");
		bad("night_spawn", Map.of("mob", "minecraft:zombie"), "count");
		bad("night_spawn", Map.of("mob", "minecraft:zombie", "count", 0L), "count");
		bad("night_spawn", Map.of("mob", "minecraft:zombie", "count", 3L, "radius", 5L), "radius");
	}

	@Test
	void prefabKnowsGrindstoneAndTarget() {
		Map<String, Object> f = new java.util.HashMap<>(Map.of("building", "barracks", "at", List.of(1L, 1L), "size", List.of(5L, 4L), "door", "south",
				"contents", List.of("bed", "grindstone", "target", "chest")));
		ActionSpecs.validate("prefab", f);
		f.put("contents", List.of("bed", "anvil"));
		bad("prefab", f, "anvil");
	}

	@Test
	void defendedWalledIsDefendedPlusAWallLampsAndZombiesOutside() throws Exception {
		Scenario plain = ScenarioParser.parse("defended", Files.readString(Path.of("sim/scenarios/defended.json")));
		Scenario walled = ScenarioParser.parse("defended_walled", Files.readString(Path.of("sim/scenarios/defended_walled.json")));
		assertEquals(plain.assertions(), walled.assertions(), "the same assertions, unchanged");
		assertEquals(plain.defaultDays(), walled.defaultDays());
		assertEquals(1, walled.actions().stream().filter(a -> a.type().equals("wall_ring")).count(), "one wall ring (with one gate)");
		assertEquals(1, walled.actions().stream().filter(a -> a.type().equals("lamps")).count());
		var spawns = walled.actions().stream().filter(a -> a.type().equals("night_spawn")).toList();
		assertEquals(9, spawns.size());
		for (var sp : spawns) assertTrue(sp.fields().containsKey("outside"), "every zombie is placed outside the wall");
		// everything else is defended's: dropping the two additions leaves the same action types in the same order
		var types = walled.actions().stream().map(a -> a.type()).filter(t -> !t.equals("wall_ring") && !t.equals("lamps")).toList();
		assertEquals(plain.actions().stream().map(a -> a.type()).toList(), types);
		assertTrue(plain.actions().stream().noneMatch(a -> a.type().equals("wall_ring")), "defended stays the harsh case: no walls");
	}

	@Test
	void wallRingAndLampsFieldErrors() {
		ActionSpecs.validate("wall_ring", Map.of("size", List.of(44L, 32L), "gate", 22L));
		bad("wall_ring", Map.of(), "size");
		bad("wall_ring", Map.of("size", List.of(44L)), "size");
		bad("wall_ring", Map.of("size", List.of(44L, 32L), "gate", 500L), "gate");
		ActionSpecs.validate("lamps", Map.of("size", List.of(44L, 32L)));
		bad("lamps", Map.of("size", List.of(44L, 32L), "spacing", 1L), "spacing");
		ActionSpecs.validate("night_spawn", Map.of("mob", "minecraft:zombie", "count", 3L, "outside", List.of(44L, 32L)));
		bad("night_spawn", Map.of("mob", "minecraft:zombie", "count", 3L, "outside", List.of(44L)), "outside");
	}
}
