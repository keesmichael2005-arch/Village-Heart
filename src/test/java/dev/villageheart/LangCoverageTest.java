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
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.workplace_invalid_one", "1 worker has no workstation"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.workplace_invalid", "%s workers have no workstation"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.raid_coming", "Raid coming! Guards are called to duty"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.raid_pending", "A raid is coming"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.siege", "Zombie siege! %s zombies left"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.raid_wave", "Raid! Wave %s, %s raiders left"),
				new dev.villageheart.core.heart.TextLine("gui.villageheart.heart.alert.raid_between", "Raid: wave %s beaten, the next one is coming")};
		for (dev.villageheart.core.heart.TextLine t : alerts) assertEquals(t.fallback(), lang.get(t.key()), t.key());
		for (String id : dev.villageheart.core.heart.DeathCauseText.NAMED) {
			assertTrue(lang.containsKey("death.villageheart.cause." + id), id);
		}
		// the wage alerts (M12): the very lines WageAlerts builds
		for (int strikers : new int[] {1, 3}) {
			for (var a : dev.villageheart.core.economy.WageAlerts.of(0, 5, strikers)) assertEquals(a.text().fallback(), lang.get(a.text().key()), a.text().key());
		}
		for (var a : new dev.villageheart.core.heart.AlertLine[] {
				dev.villageheart.core.siege.SiegeAlerts.alert(3, 7, false, true).orElseThrow(),
				dev.villageheart.core.siege.SiegeAlerts.alert(3, 7, false, false).orElseThrow(),
				dev.villageheart.core.siege.SiegeAlerts.alert(3, 7, true, true).orElseThrow() }) {
			assertEquals(a.text().fallback(), lang.get(a.text().key()), a.text().key());
		}
	}

	/** The raid and siege warnings, the two titles and the stuck-raider lines (owner, 2026-11-11): every line is in the lang file with this English. */
	@Test
	void threatWarningLines() throws Exception {
		Map<String, Object> lang = lang();
		for (dev.villageheart.core.siege.ThreatWarnings.Kind kind : dev.villageheart.core.siege.ThreatWarnings.Kind.values()) {
			for (var line : kind.lines()) assertEquals(line.text(), lang.get(line.key()), line.key());
			kind.title().ifPresent(title -> assertEquals(title.text(), lang.get(title.key()), title.key()));
			assertEquals(kind.start().text(), lang.get(kind.start().key()), kind.start().key());
		}
		assertFalse(lang.containsKey("message.villageheart.title.siege"), "a siege has no title (owner, 2026-11-12)");
		assertEquals(dev.villageheart.core.siege.ThreatWarnings.STUCK_GLOW.text(), lang.get(dev.villageheart.core.siege.ThreatWarnings.STUCK_GLOW.key()));
		assertEquals(dev.villageheart.core.siege.ThreatWarnings.STUCK_FLED.text(), lang.get(dev.villageheart.core.siege.ThreatWarnings.STUCK_FLED.key()));
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

	/** The level-up chat lines (M15): the keys LevelUpFeedback sends exist with the English the plan promises. */
	@Test
	void levelUpMessages() throws Exception {
		Map<String, Object> lang = lang();
		assertEquals("%s the %s reached level %s", lang.get("message.villageheart.worker_level_up"));
		assertEquals("...and %s more workers levelled up", lang.get("message.villageheart.workers_level_up_more"));
		assertEquals("%s has the experience for level %s. It needs a %s.", lang.get("message.villageheart.worker_level_capped"));
		assertTrue(lang.containsKey("gui.villageheart.heart.villagers.level_capped"), "the Heart screen's capped-bar note");
	}

	/** The treasury chat and action-bar lines (M12): every key the glue sends exists with the English the plan promises. */
	@Test
	void treasuryMessages() throws Exception {
		Map<String, Object> lang = lang();
		assertEquals("Deposited %s. Treasury: %s", lang.get("message.villageheart.treasury.deposited"));
		assertEquals("Deposited %s. Paid %s striking workers. Treasury: %s", lang.get("message.villageheart.treasury.deposited_settled"));
		assertEquals("Only Residents and above can deposit emeralds", lang.get("message.villageheart.treasury.not_allowed"));
		assertEquals("Withdrew %s. Treasury: %s", lang.get("message.villageheart.treasury.withdrawn"));
		for (String k : new String[] {"no_emeralds", "full", "empty", "strike_notice", "strike_notice_one", "get", "set", "add"}) {
			assertTrue(lang.containsKey("message.villageheart.treasury." + k), k);
		}
		assertEquals("On strike", lang.get("gui.villageheart.heart.activity.on_strike"));
		assertEquals("Treasury: %s", lang.get("gui.villageheart.heart.overview.treasury"));
		assertEquals("Wages tomorrow: %s", lang.get("gui.villageheart.heart.overview.wages"));
		for (String k : new String[] {"deposit_all", "withdraw_one", "withdraw_stack"}) assertTrue(lang.containsKey("gui.villageheart.heart." + k), k);
	}
}
