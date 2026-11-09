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
}
