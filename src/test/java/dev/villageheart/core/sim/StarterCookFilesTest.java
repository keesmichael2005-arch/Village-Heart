package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** M13 T-L1-11: {@code starter_cook} is {@code starter} plus a wheat field, coal and a cook; its baseline is the stored {@code starter} run and is not stale. */
class StarterCookFilesTest {
	private static Scenario scenario(String name) throws Exception {
		return ScenarioParser.parse(name, Files.readString(Path.of("sim/scenarios/" + name + ".json")));
	}

	@Test
	void theCookScenarioIsStarterPlusAFieldAndACook() throws Exception {
		Scenario starter = scenario("starter"), cook = scenario("starter_cook");
		assertEquals("../baselines/starter.json", cook.baseline());
		assertEquals(10, cook.defaultDays());
		assertEquals(starter.actions().size() + 2, cook.actions().size());
		List<ScenarioAction> rest = new ArrayList<>(cook.actions());
		ScenarioAction field = rest.stream().filter(a -> a.type().equals("field") && "wheat".equals(a.fields().get("crop"))).findFirst().orElseThrow();
		assertEquals(9L, ((Number) field.fields().get("size")).longValue());
		ScenarioAction hire = rest.stream().filter(a -> a.type().equals("hire") && "cook".equals(a.fields().get("job"))).findFirst().orElseThrow();
		assertEquals("kitchen", hire.fields().get("building"));
		assertEquals(1, hire.day());
		assertEquals(200, hire.tick());
		rest.remove(field);
		rest.remove(hire);
		assertEquals(starter.actions().size(), rest.size());
		for (int i = 0; i < rest.size(); i++) {
			ScenarioAction a = starter.actions().get(i), b = rest.get(i);
			assertEquals(a.type(), b.type(), "action " + i);
			if (a.type().equals("command")) {
				assertEquals("villageheart treasury add 32", a.fields().get("command"));
				assertEquals("villageheart treasury add 64", b.fields().get("command"), "three wages need more emeralds than two");
			} else if (a.type().equals("prefab") && "storage".equals(a.fields().get("building"))) {
				Map<String, Object> fa = new java.util.TreeMap<>(a.fields()), fb = new java.util.TreeMap<>(b.fields());
				@SuppressWarnings("unchecked") Map<String, Object> ia = new java.util.TreeMap<>((Map<String, Object>) fa.remove("chest_items"));
				@SuppressWarnings("unchecked") Map<String, Object> ib = new java.util.TreeMap<>((Map<String, Object>) fb.remove("chest_items"));
				assertEquals(fa, fb, "the Storage prefab differs only in its items");
				assertEquals(32L, ((Number) ib.remove("minecraft:coal")).longValue(), "32 coal for the cook");
				assertEquals(ia, ib);
			} else {
				assertEquals(a, b, "action " + i + " is starter's");
			}
		}
	}

	@Test
	void theAssertionsAreStartersPlusTheCookOnes() throws Exception {
		List<String> starter = new ArrayList<>(scenario("starter").assertions());
		List<String> cook = new ArrayList<>(scenario("starter_cook").assertions());
		assertTrue(starter.remove("employed == 2 on day 1"));
		assertTrue(cook.remove("employed == 3 on day 1"));
		assertTrue(cook.remove("sum(work.cook.items) >= 40 from day 2"), "the guard: the cook really worked");
		assertTrue(cook.stream().anyMatch(a -> a.contains("@base") && a.contains("food.eaten_nutrition") && a.contains("food.eaten_items") && a.endsWith("from day 3")));
		cook.removeIf(a -> a.contains("@base"));
		assertEquals(new java.util.TreeSet<>(starter), new java.util.TreeSet<>(cook), "every other assertion is starter's, unchanged");
	}

	@Test
	void theBaselineIsAFreshStarterRun() throws Exception {
		String text = Files.readString(Path.of("sim/baselines/starter.json"));
		MetricsJson.Report r = MetricsJson.read(text);
		assertEquals("starter", r.scenario());
		assertEquals(SimStatus.PASS, r.status());
		assertTrue(r.days().size() >= 10);
		long items = 0;
		for (DayMetrics d : r.days()) if (d.day() >= 3) items += d.needs().eatenItems();
		assertTrue(items > 0, "something was eaten on days 3..10");
		assertNotNull(r.provenance(), "the baseline report carries its scenario hash");
		assertEquals(MetricsJson.scenarioSha256(Files.readString(Path.of("sim/scenarios/starter.json"))), r.provenance().scenarioSha256(),
				"sim/scenarios/starter.json changed: capture sim/baselines/starter.json again (scripts/simulate.sh starter), docs/TESTING.md");
		assertNotNull(BaselineLoader.load(text));
	}
}
