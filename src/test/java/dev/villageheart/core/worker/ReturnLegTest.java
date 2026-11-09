package dev.villageheart.core.worker;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ReturnLegTest {
	@Test void withinOneLegGoesStraightToTheTarget() {
		double[] p = ReturnLeg.next(0, 0, 10, -10);
		assertArrayEquals(new double[] {10, -10}, p, 1e-9);
	}

	@Test void aFarWorkerGetsALegTowardsTheTarget() {
		double[] p = ReturnLeg.next(0, 0, 90, 0);
		assertArrayEquals(new double[] {ReturnLeg.LEG, 0}, p, 1e-9);
		double[] q = ReturnLeg.next(100, 100, 100, 40);
		assertArrayEquals(new double[] {100, 100 - ReturnLeg.LEG}, q, 1e-9);
	}

	@Test void legsReachTheTargetInFewSteps() {
		double x = -45, z = 31;
		int steps = 0;
		while (Math.hypot(x - 10, z - 5) > 1e-9 && steps < 20) {
			double[] n = ReturnLeg.next(x, z, 10, 5);
			assertTrue(Math.hypot(n[0] - x, n[1] - z) <= ReturnLeg.LEG + 1e-9);
			x = n[0]; z = n[1]; steps++;
		}
		assertTrue(steps <= 4, "steps " + steps);
		assertEquals(10, x, 1e-9);
		assertEquals(5, z, 1e-9);
	}
}
