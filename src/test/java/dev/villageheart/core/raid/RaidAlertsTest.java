package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.heart.AlertCode;
import dev.villageheart.core.heart.HeartLimits;
import dev.villageheart.core.heart.Severity;
import dev.villageheart.core.heart.TextLine;
import org.junit.jupiter.api.Test;

/** T-L1-4. The raid line says coming, the wave, or between waves, and its args fit the snapshot. */
class RaidAlertsTest {
	private static RaidRecord raid(int spawned, int total, int alive, boolean between) {
		return new RaidRecord(1, RaidOrigin.TEST, RaidPlan.VANILLA, 0L, 1, 8, 4, 4, 0, 0, total, spawned, alive, between, 0, 0, 0, 0, -1L);
	}

	private static void legal(TextLine text) {
		assertTrue(text.args().size() <= HeartLimits.MAX_TEXT_ARGS, text.args().toString());
		for (String arg : text.args()) assertTrue(arg.length() <= HeartLimits.MAX_ARG, arg);
	}

	@Test void comingWaveAndBetween() {
		var coming = RaidAlerts.line(raid(0, 5, 0, false));
		assertEquals(AlertCode.RAID, coming.code());
		assertEquals(Severity.DANGER, coming.severity());
		assertEquals(RaidAlerts.COMING, coming.text().key());
		assertEquals("Raid coming! Guards are called to duty", coming.text().english());
		legal(coming.text());

		var wave = RaidAlerts.line(raid(2, 5, 4, false));
		assertEquals(ListOf("2/5", "4"), wave.text().args());
		assertEquals("Raid! Wave 2/5, 4 raiders left", wave.text().english());
		assertEquals(Severity.DANGER, wave.severity());
		legal(wave.text());

		var between = RaidAlerts.line(raid(2, 5, 0, true));
		assertEquals(RaidAlerts.BETWEEN, between.text().key());
		assertEquals("Raid: wave 2 beaten, the next one is coming", between.text().english());
		legal(between.text());

		var waiting = RaidAlerts.pending();
		assertEquals(AlertCode.RAID, waiting.code());
		assertEquals(RaidAlerts.PENDING, waiting.text().key());
		assertEquals("A raid is coming", waiting.text().english());
		legal(waiting.text());
	}

	@Test void omenBonusAndACappedRaid() {
		var omen = RaidAlerts.line(raid(2, RaidFacts.wavesTotal(5, 2), 4, false));
		assertEquals("2/6", omen.text().args().get(0));
		var capped = RaidAlerts.line(raid(1, RaidFacts.wavesTotal(2, 1), 3, false));
		assertEquals("1/2", capped.text().args().get(0));
		assertEquals("3", capped.text().args().get(1));
		legal(omen.text());
		legal(capped.text());
	}

	private static java.util.List<String> ListOf(String... args) { return java.util.List.of(args); }
}
