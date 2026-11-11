package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Random;
import org.junit.jupiter.api.Test;

/** The scripted threat of a walled scenario stands just outside the ring, not in the disc around the Heart. */
class OutsideBandTest {
	@Test
	void everyPickIsOutsideTheRingAndInTheBand() {
		Random r = new Random(1);
		boolean west = false, east = false, north = false, south = false;
		int x0 = 100, z0 = 200, w = 44, d = 32;
		for (int i = 0; i < 400; i++) {
			long p = OutsideBand.pick(r::nextInt, x0, z0, w, d);
			int x = OutsideBand.x(p), z = OutsideBand.z(p);
			assertEquals(WallRing.OUTSIDE, new WallRing(x0, z0, w, d).side(x, z), "outside the wall: " + x + "," + z);
			assertTrue(OutsideBand.inBand(x0, z0, w, d, x, z), "in the 3..8 band: " + x + "," + z);
			if (x < x0) west = true;
			else if (x > x0 + w - 1) east = true;
			else if (z < z0) north = true;
			else south = true;
		}
		assertTrue(west && east && north && south, "all four sides, including the south gate side");
	}

	@Test
	void aHeartDiscDoesNotDescribeTheBand() {
		// Heart well north of the site, as on seed 1 (the site starts about 27 blocks south). A point of the old
		// 12..20 disc around the Heart is not "just outside the wall".
		int hx = 641, hz = 821, sx = 638, sz = 848, w = 44, d = 32;
		Random r = new Random(2);
		int nearHeart = 0;
		for (int i = 0; i < 300; i++) {
			long p = OutsideBand.pick(r::nextInt, sx, sz, w, d);
			double dist = Math.hypot(OutsideBand.x(p) - hx, OutsideBand.z(p) - hz);
			if (dist >= 12 && dist <= 20) nearHeart++;
		}
		assertTrue(nearHeart < 30, "the band is the wall, not the old village disc: " + nearHeart + " of 300 fell in 12..20 of the Heart");
	}
}
