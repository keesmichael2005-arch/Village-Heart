package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.siege.ThreatRoll.Kind;
import org.junit.jupiter.api.Test;

/** T-L1-20. The roll is a pure function of the world seed, the day and the kind. */
class ThreatRollTest {
	@Test
	void sameSeedAgreesAndTheKindsDiffer() {
		long seed = 0x123456789ABCDEFL;
		assertEquals(ThreatRoll.roll(seed, 4, Kind.RAID, 0.5), ThreatRoll.roll(seed, 4, Kind.RAID, 0.5));
		assertNotEquals(ThreatRoll.unit(seed, 0, Kind.RAID), ThreatRoll.unit(seed, 0, Kind.SIEGE));
		for (int day = 0; day < 1000; day++) {
			int tick = ThreatRoll.startTick(seed, day);
			assertTrue(tick >= Balance.PENDING_START_MIN && tick <= Balance.PENDING_START_MAX, tick + " on day " + day);
		}
		assertEquals(0, ThreatRoll.day(23999));
		assertEquals(1, ThreatRoll.day(24000));
		assertEquals(1000, ThreatRoll.dayTime(1000));
		assertEquals(0, ThreatRoll.dayTime(24000));
	}

	@Test
	void aHundredThousandDaysAtEightPercent() {
		long seed = 42;
		int hits = 0;
		for (int day = 0; day < 100_000; day++) if (ThreatRoll.roll(seed, day, Kind.RAID, 0.08)) hits++;
		assertTrue(hits >= 7_500 && hits <= 8_500, hits + " hits");
	}
}
