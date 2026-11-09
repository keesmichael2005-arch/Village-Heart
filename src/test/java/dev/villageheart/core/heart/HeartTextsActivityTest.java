package dev.villageheart.core.heart;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/** M16: the words for a miner inside the Mineshaft and for a shaft it cannot reach (job-specific, with a generic fallback). */
class HeartTextsActivityTest {
	private static String text(ActivityKind a, String job) { return HeartTexts.EN.get(HeartTexts.activityKey(a, job)); }

	@Test
	void makingYourOwnToolNamesTheToolOfTheJob() {
		assertEquals("Making a stone pickaxe", text(ActivityKind.MAKING_STONE_TOOL, "miner"));
		assertEquals("Making a stone sword", text(ActivityKind.MAKING_STONE_TOOL, "hunter"));
		assertEquals("Making a wooden axe", text(ActivityKind.MAKING_WOODEN_TOOL, "lumberjack"));
		assertEquals("Making a wooden hoe", text(ActivityKind.MAKING_WOODEN_TOOL, "farmer"));
		assertEquals("Making a wooden tool", text(ActivityKind.MAKING_WOODEN_TOOL, "unlisted_job"));
		assertEquals("Needs axe (no wood)", text(ActivityKind.TOOL_NO_MATERIALS, "lumberjack"));
		assertEquals("Needs sword (no wood)", text(ActivityKind.TOOL_NO_MATERIALS, "hunter"));
		assertEquals("Needs axe (no room)", text(ActivityKind.TOOL_NO_SPACE, "lumberjack"));
		assertEquals("Needs a tool (no room)", text(ActivityKind.TOOL_NO_SPACE, "unlisted_job"));
	}

	@Test
	void minerAwayWorkingSaysAwayMining() {
		assertEquals(HeartTexts.key("activity.away_working.miner"), HeartTexts.activityKey(ActivityKind.AWAY_WORKING, "miner"));
		assertEquals("Away (mining)", text(ActivityKind.AWAY_WORKING, "miner"));
	}

	@Test
	void otherJobsFallBackToTheGenericWords() {
		assertEquals(HeartTexts.key("activity.away_working"), HeartTexts.activityKey(ActivityKind.AWAY_WORKING, "unlisted_job"));
		assertEquals("Away (working)", text(ActivityKind.AWAY_WORKING, "unlisted_job"));
		assertEquals("Can't reach its site", text(ActivityKind.SITE_UNREACHABLE, "unlisted_job"));
	}

	@Test
	void theHunterHasItsOwnWords() {
		assertEquals("Away (hunting)", text(ActivityKind.AWAY_WORKING, "hunter"));
		assertEquals("Can't reach the Hunting Ground", text(ActivityKind.SITE_UNREACHABLE, "hunter"));
		assertEquals("No valid Hunting Ground", text(ActivityKind.NO_WORK, "hunter"));
		assertEquals("Needs bow or sword", text(ActivityKind.NEEDS_TOOL, "hunter"));
	}

	@Test
	void siteUnreachableAndNoWorkForTheMiner() {
		assertEquals("Can't reach the Mineshaft", text(ActivityKind.SITE_UNREACHABLE, "miner"));
		assertEquals("No valid Mineshaft", text(ActivityKind.NO_WORK, "miner"));
		assertEquals("Nothing to do", text(ActivityKind.NO_WORK, "unlisted_job"));
		assertEquals("Needs pickaxe", text(ActivityKind.NEEDS_TOOL, "miner"));
	}

	@Test
	void newKindsAreAppendedSoOldCodecIndicesHold() {
		ActivityKind[] v = ActivityKind.values();
		assertEquals(ActivityKind.NO_WORK, v[13]);
		// M20 (merged to dev first) appended NEEDS_MATERIALS and BLOCKED; M16's kinds come after them
		assertEquals(ActivityKind.NEEDS_MATERIALS, v[14]);
		assertEquals(ActivityKind.BLOCKED, v[15]);
		assertEquals(ActivityKind.AWAY_WORKING, v[16]);
		assertEquals(ActivityKind.SITE_UNREACHABLE, v[17]);
	}
}
