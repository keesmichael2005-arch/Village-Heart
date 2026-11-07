package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SimRequestTest {
	@Test
	void parsesWhatTheScriptWrites() {
		String json = Json.write(Map.of("scenario_file", "/a/b/bare.json", "scenario", "bare", "days", 5L, "out", "/r/bare.json",
				"extra_assertions", List.of("population >= \"1\" on day 1", "a\\b"), "resume", false), false);
		SimRequest r = SimRequest.parse(json);
		assertEquals("bare", r.scenario());
		assertEquals(5, r.days());
		assertEquals("/r/bare.json", r.out());
		assertEquals(List.of("population >= \"1\" on day 1", "a\\b"), r.extraAssertions());
		assertNull(r.stopAfterDay());
		assertFalse(r.resume());
	}

	@Test
	void stopAndResume() {
		SimRequest r = SimRequest.parse("{\"scenario_file\":\"f\",\"scenario\":\"s\",\"days\":4,\"out\":\"o\","
				+ "\"extra_assertions\":[],\"stop_after_day\":2,\"resume\":true}");
		assertEquals(2, r.stopAfterDay());
		assertTrue(r.resume());
		SimRequest n = SimRequest.parse("{\"scenario_file\":\"f\",\"scenario\":\"s\",\"days\":4,\"out\":\"o\","
				+ "\"extra_assertions\":[],\"stop_after_day\":null,\"resume\":false}");
		assertNull(n.stopAfterDay());
	}

	@Test
	void rejectsBadRequests() {
		assertThrows(Json.JsonException.class, () -> SimRequest.parse("{\"scenario\":\"s\"}"));
		assertThrows(Json.JsonException.class, () -> SimRequest.parse("nope"));
	}
}
