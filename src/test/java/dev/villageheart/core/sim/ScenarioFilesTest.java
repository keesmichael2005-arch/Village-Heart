package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/** Every shipped scenario must parse, so a broken file fails L1 and not minute 5 of a run. */
class ScenarioFilesTest {
	@Test
	void everyScenarioFileParses() throws Exception {
		List<Path> files;
		try (Stream<Path> s = Files.list(Path.of("sim/scenarios"))) {
			files = s.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList();
		}
		assertFalse(files.isEmpty(), "no scenario files found in sim/scenarios");
		assertTrue(files.stream().anyMatch(p -> p.getFileName().toString().equals("bare.json")));
		for (Path p : files) {
			String name = p.getFileName().toString().replaceAll("\\.json$", "");
			Scenario s = ScenarioParser.parse(name, Files.readString(p));
			assertEquals(name, s.name());
		}
	}

	@Test
	void starterHasItsActionsAndAssertions() throws Exception {
		Scenario s = ScenarioParser.parse("starter", Files.readString(Path.of("sim/scenarios/starter.json")));
		assertEquals(12, s.actions().size());
		assertEquals(10, s.defaultDays());
		assertEquals(7 + 1, s.assertions().size());
		var hires = s.actions().stream().filter(a -> a.type().equals("hire")).toList();
		assertEquals(2, hires.size());
		for (var h : hires) {
			assertEquals(1, h.day());
			assertEquals(200, h.tick());
		}
		assertEquals(10, s.actions().stream().filter(a -> a.day() == 0).count());
		assertEquals("claim", s.actions().get(0).type());
		assertEquals("site", s.actions().get(1).type());
	}
}
