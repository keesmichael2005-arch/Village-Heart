package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.sim.Json;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ShapedSpecTest {
	@Test
	void json() {
		String expected = Json.write(Json.parse("""
			{"type":"minecraft:crafting_shaped","category":"misc",
			 "key":{"C":"#minecraft:planks","E":"minecraft:white_wool","#":"villageheart:blank_marker"},
			 "pattern":["CEC","E#E","CEC"],
			 "result":{"count":1,"id":"villageheart:house_marker"}}"""), true);
		String actual = Json.write(BuildingType.HOUSE.ring().recipe("villageheart:house_marker", 1).toJson(), true);
		assertEquals(expected, actual);
		assertTrue(actual.indexOf("\"type\"") < actual.indexOf("\"category\""));
		assertTrue(actual.indexOf("\"category\"") < actual.indexOf("\"key\""));
		assertTrue(actual.indexOf("\"key\"") < actual.indexOf("\"pattern\""));
		assertTrue(actual.indexOf("\"pattern\"") < actual.indexOf("\"result\""));
	}

	@Test
	void validation() {
		Map<Character, String> k = Map.of('A', "minecraft:dirt");
		assertThrows(IllegalArgumentException.class, () -> new ShapedSpec(List.of("AA", "A"), k, "minecraft:stick", 1), "ragged");
		assertThrows(IllegalArgumentException.class, () -> new ShapedSpec(List.of("AB"), k, "minecraft:stick", 1), "unknown char");
		assertThrows(IllegalArgumentException.class, () -> new ShapedSpec(List.of("A"), Map.of('A', "minecraft:dirt", 'B', "minecraft:dirt"), "minecraft:stick", 1), "unused key");
		assertThrows(IllegalArgumentException.class, () -> new ShapedSpec(List.of("A"), k, "minecraft:stick", 0), "count");
		assertThrows(IllegalArgumentException.class, () -> new ShapedSpec(List.of(), k, "minecraft:stick", 1), "empty");
		assertThrows(IllegalArgumentException.class, () -> new ShapedSpec(List.of("AAAA"), k, "minecraft:stick", 1), "wide");
		assertDoesNotThrow(() -> new ShapedSpec(List.of("A "), k, "minecraft:stick", 1));
	}
}
