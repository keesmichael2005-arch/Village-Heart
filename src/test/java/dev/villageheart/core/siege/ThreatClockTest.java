package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-25. A mark is crossed once, a skipped window rolls late, and a future saved day does not swallow the real one. */
class ThreatClockTest {
	@Test
	void crossingSleepingAndStandingStill() {
		ThreatClock.Step step = ThreatClock.at(999).advance(1000);
		assertTrue(step.raidRoll());
		assertFalse(step.siegeRoll());
		assertFalse(step.clock().advance(1001).raidRoll());

		step = step.clock().advance(13000);
		assertTrue(step.siegeRoll());
		assertFalse(step.raidRoll());
		assertFalse(step.clock().advance(13001).siegeRoll());

		assertFalse(ThreatClock.at(1500).advance(500).raidRoll());
		assertTrue(ThreatClock.at(500).advance(5000).raidRoll());
		assertFalse(ThreatClock.at(500).advance(5000).siegeRoll());
		assertTrue(ThreatClock.at(500).advance(24000 + 500).raidRoll());
		assertFalse(ThreatClock.at(1000).advance(1000).raidRoll());
		assertFalse(ThreatClock.at(1000).advance(1000).siegeRoll());
	}

	@Test
	void aFutureSavedDayResetsAndAnUnspawnedSiegeClearsAtDawn() {
		ThreatClock saved = new ThreatClock(49, 500, 50, ThreatState.NO_DAY, false);
		ThreatClock.Step arrived = saved.advance(50L * 24000 + 1000);
		assertTrue(arrived.raidRoll());
		assertEquals(50, arrived.clock().lastRaidRollDay());

		ThreatClock.Step rolled = ThreatClock.at(12999).advance(13000);
		assertTrue(rolled.siegeRoll());
		ThreatClock.Step dawn = rolled.clock().advance(24000);
		assertTrue(dawn.clearUnspawnedSiege());
		assertFalse(dawn.siegeRoll());
		assertEquals(ThreatState.NO_DAY, dawn.clock().lastSiegeRollDay());

		ThreatClock.Step kept = rolled.clock().withSiegeSpawned(true).advance(24000);
		assertFalse(kept.clearUnspawnedSiege());
		assertTrue(kept.clock().siegeSpawned());
	}
}
