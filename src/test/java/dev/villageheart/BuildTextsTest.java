package dev.villageheart;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.blueprint.BuildStatus;
import dev.villageheart.core.blueprint.BuildTexts;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.Material;
import dev.villageheart.core.blueprint.SiteRules;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.HeartTexts;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.sim.Json;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerStatus;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** T-L1-11: the words of the build system exist, in the lang file, with the same English the server sends. */
class BuildTextsTest {
	@SuppressWarnings("unchecked")
	private static Map<String, Object> lang() throws Exception {
		try (InputStream in = BuildTextsTest.class.getResourceAsStream("/assets/villageheart/lang/en_us.json")) {
			assertNotNull(in);
			return (Map<String, Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	private static final Material PLANKS = new Material("#minecraft:planks", "planks", "planks", null);
	private static final Material DOOR = new Material("#minecraft:wooden_doors", "door", "doors", null);

	@Test
	void theRoadmapText() {
		TextLine t = BuildTexts.missing(PLANKS, 12);
		assertEquals("Needs 12 more planks", t.english());
		assertEquals("build.villageheart.needs", t.key());
		assertEquals(List.of("12", "planks"), t.args());
		assertEquals("Needs 1 more door", BuildTexts.missing(DOOR, 1).english());
		assertEquals("Needs 2 more doors", BuildTexts.missing(DOOR, 2).english());
	}

	@Test
	void statusTexts() {
		assertEquals("Building: 50%", BuildTexts.building(50).english());
		assertEquals("Waiting for a builder", BuildTexts.status(BuildStatus.WAITING_FOR_BUILDER, List.of()).english());
		assertEquals("glass in the way at (1, 2, 3)", BuildTexts.status(BuildStatus.BLOCKED, List.of("glass", "(1, 2, 3)")).english());
		assertEquals("Something is in the way at (1, 2, 3)", BuildTexts.status(BuildStatus.IN_THE_WAY, List.of("(1, 2, 3)")).english());
		assertEquals("Can't reach (1, 2, 3)", BuildTexts.status(BuildStatus.UNREACHABLE, List.of("(1, 2, 3)")).english());
		assertEquals("Site not loaded", BuildTexts.status(BuildStatus.NOT_LOADED, List.of()).english());
		assertEquals("Checking the work", BuildTexts.status(BuildStatus.CHECKING, List.of()).english());
		assertEquals("Clearing the site", BuildTexts.status(BuildStatus.CLEARING, List.of()).english());
		assertEquals("oak_leaves", BuildTexts.blockName("minecraft:oak_leaves"));
	}

	@Test
	void everyKeyIsInTheLangFileWithTheSameEnglish() throws Exception {
		Map<String, Object> lang = lang();
		for (BuildStatus s : BuildStatus.values()) {
			assertEquals(BuildTexts.fallback(s), lang.get(BuildTexts.key(s)), s.name());
		}
		for (var bp : Blueprints.all()) {
			TextLine t = BuildTexts.blueprintName(bp);
			assertEquals(t.fallback(), lang.get(t.key()), bp.id());
		}
		for (SiteRules.Code c : SiteRules.Code.values()) {
			String key = new SiteRules.SiteProblem(c, "", List.of(), null).key();
			assertTrue(lang.containsKey(key), key);
		}
		// the fallbacks the rules send equal the lang text
		SiteRules.SiteProblem unchecked = SiteRules.unchecked("House", new dev.villageheart.core.room.Pos(1, 2, 3));
		assertEquals(unchecked.fallback(), lang.get(unchecked.key()));
	}

	@Test
	void theBuildersTableTextsAreInTheLangFile() throws Exception {
		Map<String, Object> lang = lang();
		for (Map.Entry<String, String> e : dev.villageheart.core.blueprint.TableTexts.EN.entrySet()) {
			assertEquals(e.getValue(), lang.get(dev.villageheart.core.blueprint.TableTexts.key(e.getKey())), e.getKey());
		}
		assertEquals("Builder's Table", lang.get("container.villageheart.builders_table"));
		assertEquals("Not inside a village", lang.get("build.villageheart.msg.not_in_village"));
	}

	@Test
	void theBuilderHasWordsForEveryStatus() throws Exception {
		Map<String, Object> lang = lang();
		for (WorkerStatus st : WorkerStatus.values()) {
			String key = dev.villageheart.worker.job.WorkerStatusText.key(st, JobType.BUILDER);
			assertTrue(lang.containsKey(key), st + " needs " + key);
		}
		assertEquals("No blueprint to build", lang.get(HeartTexts.activityKey(ActivityKind.NO_WORK, "builder")));
		assertEquals("Needs materials", lang.get(HeartTexts.activityKey(ActivityKind.NEEDS_MATERIALS, "builder")));
		assertEquals("Build blocked", lang.get(HeartTexts.activityKey(ActivityKind.BLOCKED, "builder")));
	}
}
