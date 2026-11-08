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
		double[] r = firstHaul(2.0); // stone pickaxe, x2.0 (vanilla 4 / 2)
		assertEquals(100, r[0]);
		assertEquals(0.0, r[1], 1e-9);
	}

	@Test
	void woodTakesLongerAndIronIsFasterWithTheRemainderCarried() {
		assertEquals(200, firstHaul(1.0)[0]); // wood: 4000 ticks (stone 2000)
		double[] iron = firstHaul(3.0);
		assertEquals(67, iron[0]); // 1340 ticks: 4000 / 60 per step = 66.7
		assertEquals(20.0, iron[1], 1e-9);
	}

	@Test
	void remaindersAreNeverLostOverManyHauls() {
		AwayProgress p = AwayProgress.ZERO;
		int hauls = 0;
		for (int i = 0; i < 25_000; i++) { // 500 000 ticks at 3.0 = 1 500 000 units = 375 hauls
			AwayProgress.Step s = p.add(20, 3.0);
			hauls += s.hauls();
			p = s.next();
		}
		assertEquals(375, hauls);
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
	void hunterTripCostsExactly150StepsAtPaceOne() {
		AwayProgress p = AwayProgress.ZERO;
		for (int i = 1; i <= 150; i++) {
			AwayProgress.Step s = p.add(20, 1.0, Balance.HUNTER_TRIP_UNITS);
			if (i < 150) assertEquals(0, s.hauls(), "step " + i);
			else {
				assertEquals(1, s.hauls());
				assertEquals(0.0, s.next().units(), 1e-9);
			}
			p = s.next();
		}
	}

	@Test
	void hunterTripAtPace125IsDueAtStep120() {
		AwayProgress p = AwayProgress.ZERO;
		int trip = 0;
		for (int i = 1; i <= 120; i++) {
			AwayProgress.Step s = p.add(20, 1.25, Balance.HUNTER_TRIP_UNITS);
			if (s.hauls() > 0) trip = i;
			p = s.next();
		}
		assertEquals(120, trip);
		assertTrue(new AwayProgress(3000).due(3000));
		assertFalse(new AwayProgress(2999).due(3000));
		assertEquals(3000, new AwayProgress(2990).addHeld(20, 1.0, 3000).units());
	}

	@Test
	void theTwoArgumentMethodsStillUseTheMinersNumbers() {
		AwayProgress.Step s = new AwayProgress(3990).add(20, 1.0);
		assertEquals(1, s.hauls());
		assertEquals(10.0, s.next().units(), 1e-9);
		assertEquals(0, new AwayProgress(3900).add(20, 1.0).hauls());
	}

	@Test
	void negativeProgressIsRejected() {
		assertThrows(IllegalArgumentException.class, () -> new AwayProgress(-1));
	}
}
