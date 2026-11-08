package dev.villageheart.core.economy;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

class TreasuryMathTest {
	@Test void depositableClampsAtTheCap() {
		assertEquals(10, TreasuryMath.depositable(0, 10));
		assertEquals(3, TreasuryMath.depositable(Balance.MAX_TREASURY - 3, 10));
		assertEquals(0, TreasuryMath.depositable(Balance.MAX_TREASURY, 10));
		assertEquals(0, TreasuryMath.depositable(5, 0));
		assertEquals(0, TreasuryMath.depositable(5, -4), "never negative");
		assertEquals(Balance.MAX_TREASURY, TreasuryMath.depositable(0, Integer.MAX_VALUE));
	}

	@Test void withdrawableIsWhatTheTreasuryHolds() {
		assertEquals(5, TreasuryMath.withdrawable(5, 64));
		assertEquals(0, TreasuryMath.withdrawable(0, 1));
		assertEquals(3, TreasuryMath.withdrawable(10, 3));
		assertEquals(0, TreasuryMath.withdrawable(10, -3));
	}
}
