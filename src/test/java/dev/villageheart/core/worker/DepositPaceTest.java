package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class DepositPaceTest {
	@Test
	void aQuarterSecondPerItem() {
		assertEquals(0, DepositPace.ticks(0));
		assertEquals(0, DepositPace.ticks(-3));
		assertEquals(5, DepositPace.ticks(1));
		assertEquals(320, DepositPace.ticks(64));
	}
}
