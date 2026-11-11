package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-23, the lines. The alert-code order is asserted in step 15, when the codes are appended. */
class SiegeAlertsTest {
	@Test
	void lines() {
		assertEquals("Walls damaged: 3 broken, 7 cracked", SiegeAlerts.line(3, 7, false, true).orElseThrow().text());
		assertFalse(SiegeAlerts.line(3, 7, false, true).orElseThrow().danger());
		var breach = SiegeAlerts.line(3, 7, true, true).orElseThrow();
		assertEquals("Wall breached! 3 holes", breach.text());
		assertTrue(breach.danger());
		assertTrue(SiegeAlerts.line(0, 0, false, true).isEmpty());
		assertEquals("Walls damaged: 3 broken, 7 cracked (no builder)", SiegeAlerts.line(3, 7, false, false).orElseThrow().text());
	}
}
