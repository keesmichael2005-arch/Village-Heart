package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.sim.ScenarioParser.ScenarioParseException;
import org.junit.jupiter.api.Test;

class ActionSpecsTest {
	private static String doc(String actions) {
		return "{\"schema\":1,\"name\":\"x\",\"default_days\":1,\"actions\":[" + actions + "],\"assertions\":[]}";
	}

	private static void bad(String action, String contains) {
		ScenarioParseException e = assertThrows(ScenarioParseException.class, () -> ScenarioParser.parse("x", doc(action)));
		assertTrue(e.getMessage().contains(contains), e.getMessage());
	}

	private static final String PREFAB = "\"action\":\"prefab\",\"building\":\"house\",\"at\":[2,2],\"size\":[5,4],\"door\":\"south\",\"contents\":[\"bed\",\"light\"]";

	@Test void validActionsParse() {
		ScenarioParser.parse("x", doc("{\"action\":\"site\",\"size\":[44,32],\"min_distance\":24,\"max_distance\":72},{" + PREFAB + "},"
				+ "{\"action\":\"field\",\"at\":[10,14],\"size\":9,\"crop\":\"carrots\",\"age\":7},"
				+ "{\"action\":\"grove\",\"area\":[22,21,20,10],\"trees\":8,\"spacing\":5,\"seed\":11},"
				+ "{\"action\":\"hire\",\"day\":1,\"tick\":200,\"job\":\"farmer\",\"building\":\"farmhouse\"},"
				+ "{\"action\":\"raid\",\"day\":1,\"tick\":100,\"omen\":3,\"scaled\":false},"
				+ "{\"action\":\"golems\",\"count\":1},"
				+ "{\"action\":\"top_up_villagers\",\"adults\":4,\"at\":[2,2]},"
				+ "{\"action\":\"worker_level\",\"job\":\"guard\",\"level\":2},"
				+ "{\"action\":\"siege\",\"day\":2,\"zombies\":8},"
				+ "{\"action\":\"threats\",\"random_raids\":true,\"sieges\":false},"
				+ "{\"action\":\"build\",\"blueprint\":\"wall_segment\",\"from\":[0,0],\"to\":[9,0]}"));
	}

	@Test void typosNameTheField() {
		bad("{\"action\":\"prefab\",\"building\":\"house\",\"at\":[2,2],\"door\":\"south\",\"contents\":[]}", "size");
		bad("{" + PREFAB.replace("\"bed\"", "\"sofa\"") + "}", "unknown word sofa");
		bad("{" + PREFAB.replace("south", "up") + "}", "door");
		bad("{" + PREFAB.replace("house", "castle") + "}", "unknown building castle");
		bad("{\"action\":\"field\",\"at\":[1,1],\"size\":10,\"crop\":\"carrots\"}", "size");
		bad("{\"action\":\"field\",\"at\":[1,1],\"size\":9,\"crop\":\"kale\"}", "crop");
		bad("{\"action\":\"hire\",\"job\":\"wizard\",\"building\":\"farmhouse\"}", "unknown job wizard");
		bad("{\"action\":\"site\",\"size\":[44],\"max_distance\":72}", "size");
		bad("{\"action\":\"site\",\"size\":[4,4],\"min_distance\":50,\"max_distance\":20}", "min_distance");
		bad("{\"action\":\"grove\",\"area\":[1,1,5,5],\"trees\":0,\"spacing\":5}", "trees");
		bad("{\"action\":\"raid\",\"omen\":3}", "day");
		bad("{\"action\":\"raid\",\"day\":1,\"omen\":0}", "omen");
		bad("{\"action\":\"raid\",\"day\":1,\"omen\":6}", "omen");
		bad("{\"action\":\"raid\",\"day\":1,\"omen\":1,\"scaled\":\"yes\"}", "scaled");
		bad("{\"action\":\"golems\",\"count\":5}", "count");
		bad("{\"action\":\"golems\"}", "count");
		bad("{\"action\":\"top_up_villagers\",\"adults\":0,\"at\":[1,1]}", "adults");
		bad("{\"action\":\"top_up_villagers\",\"adults\":4}", "at");
		bad("{\"action\":\"worker_level\",\"job\":\"wizard\",\"level\":2}", "unknown job wizard");
		bad("{\"action\":\"worker_level\",\"job\":\"guard\",\"level\":4}", "level");
		bad("{\"action\":\"siege\",\"zombies\":8}", "day");
		bad("{\"action\":\"siege\",\"day\":1}", "zombies");
		bad("{\"action\":\"siege\",\"day\":1,\"zombies\":0}", "zombies");
		bad("{\"action\":\"siege\",\"day\":1,\"zombies\":21}", "zombies");
		bad("{\"action\":\"threats\",\"random_raids\":true}", "sieges");
		bad("{\"action\":\"threats\",\"random_raids\":\"yes\",\"sieges\":true}", "random_raids");
		bad("{\"action\":\"build\",\"from\":[0,0],\"to\":[9,0]}", "blueprint");
		bad("{\"action\":\"build\",\"blueprint\":\"castle\",\"from\":[0,0],\"to\":[9,0]}", "unknown castle");
		bad("{\"action\":\"build\",\"blueprint\":\"wall_segment\",\"from\":[0],\"to\":[9,0]}", "from");
	}
}
