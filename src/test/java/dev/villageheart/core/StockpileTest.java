package dev.villageheart.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StockpileTest {
	@Test void depositAndWithdraw() {
		Stockpile s = new Stockpile();
		s.deposit("oak_log", 10);
		assertEquals(4, s.withdraw("oak_log", 4));
		assertEquals(6, s.count("oak_log"));
	}

	@Test void cannotWithdrawMoreThanStored() {
		Stockpile s = new Stockpile();
		s.deposit("bread", 2);
		assertEquals(2, s.withdraw("bread", 5));
		assertEquals(0, s.count("bread"));
		assertTrue(s.snapshot().isEmpty());
	}

	@Test void rejectsNegativeDeposit() {
		assertThrows(IllegalArgumentException.class, () -> new Stockpile().deposit("x", -1));
	}
}
