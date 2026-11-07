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
}
