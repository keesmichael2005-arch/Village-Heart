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
}
