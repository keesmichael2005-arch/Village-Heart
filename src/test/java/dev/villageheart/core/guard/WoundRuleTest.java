package dev.villageheart.core.guard;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WoundRuleTest {
	@Test
	void hysteresis() {
		assertTrue(WoundRule.wounded(false, 5, 20));
		assertTrue(WoundRule.wounded(true, 7, 20), "35 % stays wounded");
		assertFalse(WoundRule.wounded(true, 16, 20), "80 % is healed");
		assertFalse(WoundRule.wounded(false, 6.1, 20), "30.5 % from healthy is not wounded");
		assertFalse(WoundRule.wounded(false, 7, 20));
	}

	@Test
	void usesFractionsOfMax() {
		assertTrue(WoundRule.wounded(false, 11, 40));
		assertFalse(WoundRule.wounded(false, 12.5, 40));
		assertTrue(WoundRule.wounded(true, 31, 40));
		assertFalse(WoundRule.wounded(true, 32, 40));
	}
}
