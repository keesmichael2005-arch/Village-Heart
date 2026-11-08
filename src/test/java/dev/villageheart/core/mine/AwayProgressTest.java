package dev.villageheart.core.mine;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import org.junit.jupiter.api.Test;

class AwayProgressTest {
	/** Steps of 20 ticks until the first haul; returns {steps, remainder x 1000}. */
	private static double[] firstHaul(double multiplier) {
		AwayProgress p = AwayProgress.ZERO;
		for (int step = 1; step < 10_000; step++) {
			AwayProgress.Step s = p.add(Balance.MINER_STEP_TICKS, multiplier);
			if (s.hauls() > 0) return new double[] {step, s.next().units()};
			p = s.next();
		}
		throw new AssertionError("no haul");
	}

	@Test
	void stoneAtLevelOneIsTwoThousandTicks() {
		double[] r = firstHaul(1.25);
		assertEquals(100, r[0]);
		assertEquals(0.0, r[1], 1e-9);
	}

	@Test
	void woodTakesLongerAndIronIsFasterWithTheRemainderCarried() {
		assertEquals(125, firstHaul(1.0)[0]);
		double[] iron = firstHaul(1.5);
		assertEquals(84, iron[0]); // 1680 ticks
		assertEquals(20.0, iron[1], 1e-9);
	}

	@Test
	void remaindersAreNeverLostOverManyHauls() {
		AwayProgress p = AwayProgress.ZERO;
		int hauls = 0;
		for (int i = 0; i < 25_000; i++) { // 500 000 ticks at 1.5 = 750 000 units = 300 hauls
			AwayProgress.Step s = p.add(20, 1.5);
			hauls += s.hauls();
			p = s.next();
		}
		assertEquals(300, hauls);
		assertEquals(0.0, p.units(), 1e-6);
	}

	@Test
	void zeroMultiplierAddsNothing() {
		AwayProgress p = new AwayProgress(100);
		AwayProgress.Step s = p.add(20, 0);
		assertEquals(0, s.hauls());
		assertEquals(100.0, s.next().units());
		assertEquals(100.0, p.addHeld(20, 0).units());
	}

	@Test
	void aHeldHaulStaysCappedAtOneHaul() {
		AwayProgress p = new AwayProgress(Balance.MINER_HAUL_UNITS - 10);
		AwayProgress held = p.addHeld(20, 1.25);
		assertEquals(Balance.MINER_HAUL_UNITS, held.units());
		assertTrue(held.due());
		assertEquals(Balance.MINER_HAUL_UNITS, held.addHeld(20, 1.25).units());
		AwayProgress.Step s = held.add(20, 1.25);
		assertEquals(1, s.hauls());
		assertEquals(25.0, s.next().units(), 1e-9);
	}

	@Test
	void atMostOneHaulPerStepAndTheRestIsCapped() {
		AwayProgress.Step s = new AwayProgress(10_000).add(20, 1.25);
		assertEquals(1, s.hauls());
		assertEquals(Balance.MINER_HAUL_UNITS, s.next().units());
	}

	@Test
	void negativeProgressIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new AwayProgress(-1));
	}
}
