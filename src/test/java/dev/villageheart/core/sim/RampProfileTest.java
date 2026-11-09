package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.sim.RampProfile.Ground;
import dev.villageheart.core.sim.RampProfile.Ramp;
import java.util.Random;
import org.junit.jupiter.api.Test;

class RampProfileTest {
	private static final int W = 12, D = 8, RAMP = 9, FLOOR = 66;

	/** Checks the patch, the cone, "never above the land" and every edge-sharing pair of columns for a step of at most 1; returns the worst step. */
	private static int check(Ramp r, Ground nat) {
		int m = r.margin(), worst = 0;
		int[][] h = r.h();
		for (int i = 0; i < h.length; i++) for (int j = 0; j < h[i].length; j++) {
			int x = i - m, z = j - m;
			int k = Math.max(Math.max(-x, x - (W - 1)), Math.max(-z, z - (D - 1)));
			if (k <= 0) assertEquals(FLOOR, h[i][j], "patch at " + x + "," + z);
			else {
				assertTrue(Math.abs(h[i][j] - FLOOR) <= k, "within k of the floor at " + x + "," + z + ": " + h[i][j]);
				assertTrue(h[i][j] <= Math.max(nat.height(x, z), FLOOR - k), "not above the land at " + x + "," + z);
			}
			if (i + 1 < h.length) worst = Math.max(worst, Math.abs(h[i][j] - h[i + 1][j]));
			if (j + 1 < h[i].length) worst = Math.max(worst, Math.abs(h[i][j] - h[i][j + 1]));
		}
		return worst;
	}

	@Test void roughRandomTerrainStillHasOneBlockSteps() {
		for (long seed = 1; seed <= 25; seed++) {
			Random rnd = new Random(seed);
			int[][] nat = new int[W + 60][D + 60];
			for (int[] row : nat) for (int j = 0; j < row.length; j++) row[j] = 55 + rnd.nextInt(25);
			Ground g = (x, z) -> nat[x + 30][z + 30];
			assertEquals(1, check(RampProfile.solve(g, W, D, FLOOR, RAMP), g), "seed " + seed);
		}
	}

	@Test void cliffsPillarsAndFloatingCanopyAreSloped() {
		// a 7-block cliff right at the patch edge, a 24-block pillar (a canopy read as ground) and a 12-block pit in the ring
		Ground g = (x, z) -> x < 0 ? FLOOR - 7 : x == 14 && z == 3 ? 90 : x == 6 && z == 12 ? 50 : FLOOR;
		assertEquals(1, check(RampProfile.solve(g, W, D, FLOOR, RAMP), g));
	}

	@Test void smoothHillMeetsTheLandWithinOne() {
		Ground g = (x, z) -> FLOOR + Math.max(0, x - 5) / 2; // rises one block per two columns east of the patch
		Ramp r = RampProfile.solve(g, W, D, FLOOR, RAMP);
		assertEquals(1, check(r, g));
		assertTrue(r.meets());
		assertEquals(RAMP + 3, r.margin());
	}

	@Test void aHillTooSteepToMeetExtendsTheMarginThenStops() {
		Ground g = (x, z) -> FLOOR + 2 * Math.max(0, x); // two blocks per column: it never meets the 1-per-step ramp
		Ramp r = RampProfile.solve(g, W, D, FLOOR, RAMP);
		assertFalse(r.meets());
		assertEquals(RAMP + 12, r.margin());
		assertEquals(1, check(r, g));
	}

	@Test void flatLandIsUnchangedAndUnknownColumnsAreSkipped() {
		Ramp r = RampProfile.solve((x, z) -> x == 5 && z == -4 ? RampProfile.UNKNOWN : FLOOR, W, D, FLOOR, RAMP);
		int m = r.margin();
		assertEquals(RampProfile.UNKNOWN, r.h()[5 + m][-4 + m]);
		assertEquals(FLOOR, r.h()[3][3]);
		assertEquals(FLOOR, r.h()[m - 2][m + 2]);
	}
}
