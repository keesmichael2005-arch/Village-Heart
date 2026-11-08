package dev.villageheart;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.JobBlockType;
import dev.villageheart.core.sim.Json;
import dev.villageheart.core.worker.HireRefusal;
import dev.villageheart.core.worker.JobType;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LangCoverageTest {
	@SuppressWarnings("unchecked")
	private static Map<String, Object> lang() throws Exception {
		try (InputStream in = LangCoverageTest.class.getResourceAsStream("/assets/villageheart/lang/en_us.json")) {
			assertNotNull(in, "en_us.json on the classpath");
			return (Map<String, Object>) Json.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		}
	}

	/** Heart screen texts the server builds: each key exists and its English matches the fallback the server sends. */
	@Test
	void heartScreenServerTexts() throws Exception {
		Map<String, Object> lang = lang();
		for (dev.villageheart.core.heart.Rejection r : dev.villageheart.core.heart.Rejection.values()) {
			assertEquals(r.message(), lang.get(r.langKey()), r.langKey());
		}
		assertTrue(lang.containsKey("message.villageheart.heart.not_this_village"));
		// Every alert text the builder can produce.
		dev.villageheart.core.heart.TextLine[] alerts = {
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.no_storage", "No Storage: items have nowhere to go"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.storage_full",
						"Storage full: no empty slot in any readable Storage"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.storage_full_unchecked",
						"Storage full: no empty slot in any readable Storage (%s Storage not checked yet)"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.invalid_building", "1 building needs attention"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.invalid_buildings", "%s buildings need attention"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.workplace_invalid_one", "1 worker has no valid workplace"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.workplace_invalid", "%s workers have no valid workplace")};
		for (dev.villageheart.core.heart.TextLine t : alerts) assertEquals(t.fallback(), lang.get(t.key()), t.key());
	}

	/** Every client text of the Heart screen is in en_us.json with the same English, and every enum the screen shows has one. */
	@Test
	void heartScreenClientTexts() throws Exception {
		Map<String, Object> lang = lang();
		for (Map.Entry<String, String> e : dev.villageheart.core.heart.HeartTexts.EN.entrySet()) {
			assertEquals(e.getValue(), lang.get(e.getKey()), e.getKey());
		}
		for (dev.villageheart.core.heart.ActivityKind a : dev.villageheart.core.heart.ActivityKind.values()) {
			dev.villageheart.core.heart.HeartTexts.key("activity." + a.name().toLowerCase(java.util.Locale.ROOT));
		}
		dev.villageheart.core.heart.HeartTexts.key("activity.workplace_invalid");
		for (BuildingType t : BuildingType.values()) {
			assertEquals(t.displayName(), dev.villageheart.core.heart.HeartTexts.english("building." + t.id()), t.id());
		}
		for (dev.villageheart.core.heart.RowStatus s : dev.villageheart.core.heart.RowStatus.values()) {
			dev.villageheart.core.heart.HeartTexts.key("status." + s.name().toLowerCase(java.util.Locale.ROOT));
		}
		for (dev.villageheart.core.heart.DetailKind d : dev.villageheart.core.heart.DetailKind.values()) {
			dev.villageheart.core.heart.HeartTexts.key("detail." + (d == dev.villageheart.core.heart.DetailKind.WORKER_SLOTS ? "slots"
					: d.name().toLowerCase(java.util.Locale.ROOT)));
		}
		for (dev.villageheart.core.worker.JobType j : dev.villageheart.core.worker.JobType.values()) {
			assertEquals(j.displayName(), lang.get("entity.villageheart.villager." + j.id()), j.id());
		}
	}

	@Test
	void everyNameIsPresent() throws Exception {
		Map<String, Object> lang = lang();
		for (BuildingType t : BuildingType.values()) {
			assertEquals(t.displayName() + " Marker", lang.get("block.villageheart." + t.markerId()), t.markerId());
		}
		for (JobBlockType t : JobBlockType.values()) {
			assertEquals(t.displayName(), lang.get("block.villageheart." + t.id()), t.id());
		}
		for (JobType j : JobType.values()) {
			assertEquals(j.displayName(), lang.get("entity.villageheart.villager." + j.id()), j.id());
		}
		for (HireRefusal r : HireRefusal.values()) assertTrue(lang.containsKey(r.langKey()), r.langKey());
		for (String k : new String[] {"message.villageheart.hire.hired", "message.villageheart.hire.fired", "message.villageheart.hire.not_a_worker",
				"message.villageheart.hire.unknown_job", "message.villageheart.hire.workers_header", "message.villageheart.hire.workers_none",
				"message.villageheart.hire.worker_line"}) {
			assertTrue(lang.containsKey(k), k);
		}
		assertEquals("Blank Marker", lang.get("item.villageheart.blank_marker"));
		assertEquals("Village Heart", lang.get("itemGroup.villageheart.main"));
		for (String k : new String[] {"block.villageheart.village_heart", "message.villageheart.claim.claimed",
				"message.villageheart.claim.no_bell_nearby", "message.villageheart.claim.already_have_village",
				"message.villageheart.claim.already_claimed_other", "message.villageheart.heart.not_owner",
				"message.villageheart.heart.released"}) {
			assertTrue(lang.containsKey(k), k);
		}
	}

	@Test
	void everyWorkerStatusHasWordsForEveryJob() throws Exception {
		Map<String, Object> lang = lang();
		for (dev.villageheart.core.worker.WorkerStatus st : dev.villageheart.core.worker.WorkerStatus.values()) {
			for (JobType j : JobType.values()) {
				String key = dev.villageheart.worker.job.WorkerStatusText.key(st, j);
				assertTrue(lang.containsKey(key), st + " for " + j.id() + " needs " + key);
				assertTrue(key.startsWith("gui.villageheart.heart.activity."), "one set of words: " + key);
			}
		}
		for (dev.villageheart.core.job.ToolKind k : dev.villageheart.core.job.ToolKind.values()) {
			assertTrue(lang.containsKey("gui.villageheart.heart.activity.needs_tool." + k.id()), k.id());
		}
		assertEquals("Needs axe", lang.get("gui.villageheart.heart.activity.needs_tool.axe"));
		assertEquals("No trees within 32 blocks", lang.get("gui.villageheart.heart.activity.no_work.lumberjack"));
		assertEquals("No farmland within 24 blocks", lang.get("gui.villageheart.heart.activity.no_work.farmer"));
		assertTrue(lang.keySet().stream().noneMatch(k -> k.startsWith("status.villageheart.")), "M9's separate status keys are gone");
	}
}
