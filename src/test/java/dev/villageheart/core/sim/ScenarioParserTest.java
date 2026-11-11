package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.sim.ScenarioParser.ScenarioParseException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ScenarioParserTest {
	@Test
	void bareParses() throws Exception {
		Scenario s = ScenarioParser.parse("bare", Files.readString(Path.of("sim/scenarios/bare.json")));
		assertEquals("bare", s.name());
		assertEquals(5, s.defaultDays());
		assertEquals(1, s.actions().size());
		assertEquals("claim", s.actions().get(0).type());
		assertEquals(0, s.actions().get(0).day());
		assertEquals(3, s.assertions().size());
		assertNull(s.seed());
		assertFalse(s.threats());
	}

	private static String doc(String name, String days, String action, String assertion) {
		return "{\"schema\":1,\"name\":\"" + name + "\",\"default_days\":" + days + ",\"actions\":[" + action + "],\"assertions\":[" + assertion + "]}";
	}

	private static void bad(String file, String json, String contains) {
		ScenarioParseException e = assertThrows(ScenarioParseException.class, () -> ScenarioParser.parse(file, json));
		assertTrue(e.getMessage().contains(contains), e.getMessage());
	}

	@Test
	void errors() {
		String ok = "{\"action\":\"claim\"}";
		bad("x", doc("y", "1", ok, ""), "must equal the file name");
		bad("x", doc("x", "1", "{\"action\":\"teleport\"}", ""), "unknown action");
		bad("x", doc("x", "1", "{\"action\":\"claim\",\"tick\":24000}", ""), "tick");
		bad("x", doc("x", "0", ok, ""), "default_days");
		bad("x", doc("x", "1", ok, "\"population >>= 3\""), "assertion");
		bad("x", doc("x", "1", "{\"action\":\"command\"}", ""), "command");
		bad("x", doc("x", "1", "{}", ""), "missing");
		bad("x", "{not json", "x:");
	}

	@Test
	void villageSiegeFlag() {
		String body = "{\"schema\":1,\"name\":\"x\",\"default_days\":2,\"actions\":[{\"action\":\"claim\"}],\"assertions\":[]";
		assertTrue(ScenarioParser.parse("x", body + "}").villageSiege(), "omitted means the siege stays on");
		assertTrue(ScenarioParser.parse("x", body + ",\"village_siege\":true}").villageSiege());
		assertFalse(ScenarioParser.parse("x", body + ",\"village_siege\":false}").villageSiege());
		bad("x", body + ",\"village_siege\":\"no\"}", "village_siege");
	}

	@Test
	void fieldsAndSeed() {
		Scenario s = ScenarioParser.parse("x", "{\"schema\":1,\"name\":\"x\",\"default_days\":2,\"seed\":7,\"actions\":["
				+ "{\"action\":\"command\",\"command\":\"time set day\",\"day\":2,\"tick\":5}],\"assertions\":[]}");
		assertEquals(7L, s.seed());
		assertEquals("time set day", s.actions().get(0).fields().get("command"));
		assertEquals(24005L, s.actions().get(0).dueTick());
		assertFalse(s.threats());
	}

	@Test
	void threatsFlag() {
		Scenario on = ScenarioParser.parse("x", "{\"schema\":1,\"name\":\"x\",\"default_days\":1,\"threats\":true,\"actions\":[],\"assertions\":[]}");
		assertTrue(on.threats());
		bad("x", "{\"schema\":1,\"name\":\"x\",\"default_days\":1,\"threats\":\"yes\",\"actions\":[],\"assertions\":[]}", "threats");
	}
}
