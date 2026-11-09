package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.sim.SiteFinder.Column;
import dev.villageheart.core.sim.SiteFinder.Site;
import java.util.Optional;
import java.util.function.BiFunction;
import org.junit.jupiter.api.Test;

class SiteFinderTest {
	private static final Column FLAT = new Column(64, true, true);

	private static Optional<Site> find(BiFunction<Integer, Integer, Column> f, int w, int d, int min, int max) {
		return SiteFinder.find(f::apply, 0, 0, w, d, min, max, 72, 3);
	}

	@Test void findsAFlatPatchBesideAVillagePatch() {
		// a "village" (not natural) within 10 blocks of the Heart, flat natural land elsewhere
		Optional<Site> s = find((x, z) -> Math.abs(x) <= 10 && Math.abs(z) <= 10 ? new Column(64, false, true) : FLAT, 20, 10, 0, 60);
		assertTrue(s.isPresent());
		Site site = s.get();
		for (int x = site.x(); x < site.x() + 20; x++) for (int z = site.z(); z < site.z() + 10; z++) {
			assertFalse(Math.abs(x) <= 10 && Math.abs(z) <= 10, "overlaps the village at " + x + "," + z);
		}
		assertEquals(64, site.floorY());
		assertEquals(0, site.cost());
	}

	@Test void waterOrUnnaturalColumnRejectsCandidates() {
		Optional<Site> s = find((x, z) -> x == 5 && z == 5 ? new Column(64, false, true) : FLAT, 10, 10, 0, 40);
		Site site = s.orElseThrow();
		assertFalse(5 >= site.x() && 5 < site.x() + 10 && 5 >= site.z() && 5 < site.z() + 10);
	}

	@Test void deviationFourIsRejectedThreeIsNot() {
		assertTrue(find((x, z) -> new Column(Math.floorMod(x, 2) == 0 ? 64 : 68, true, true), 10, 10, 0, 40).isEmpty(), "deviation 4 from the median");
		assertTrue(find((x, z) -> new Column(Math.floorMod(x, 2) == 0 ? 64 : 67, true, true), 10, 10, 0, 40).isPresent(), "deviation 3 is allowed");
	}

	@Test void equalCandidatesPreferNearerThenLowerXz() {
		Site s = find((x, z) -> FLAT, 10, 10, 0, 40).orElseThrow();
		// the patch centred as near to the Heart as the lattice allows (corner at -4 or -6 by 2-block lattice), lowest (x, z) on a tie
		assertEquals(-6, s.x());
		assertEquals(-6, s.z());
	}

	@Test void minAndMaxDistance() {
		Site s = find((x, z) -> FLAT, 10, 10, 24, 60).orElseThrow();
		double nx = Math.max(Math.max(s.x(), -(s.x() + 9)), 0), nz = Math.max(Math.max(s.z(), -(s.z() + 9)), 0);
		assertTrue(Math.hypot(nx, nz) >= 24, "nearest point " + Math.hypot(nx, nz));
		assertTrue(find((x, z) -> FLAT, 100, 100, 0, 40).isEmpty(), "too big for the max distance");
	}

	@Test void nothingFitsAndUnloadedAndNotTicking() {
		assertTrue(find((x, z) -> new Column(64 + Math.floorMod(x + z, 11), true, true), 10, 10, 0, 40).isEmpty());
		assertTrue(find((x, z) -> null, 10, 10, 0, 40).isEmpty());
		assertTrue(find((x, z) -> new Column(64, true, false), 10, 10, 0, 40).isEmpty());
	}

	@Test void claimMarginLimitsTheSite() {
		assertTrue(SiteFinder.find((x, z) -> FLAT, 0, 0, 10, 10, 0, 100, 30, 3).orElseThrow().x() >= -30);
		assertTrue(SiteFinder.find((x, z) -> Math.abs(x) < 60 && Math.abs(z) < 60 ? new Column(64, false, true) : FLAT, 0, 0, 10, 10, 0, 100, 40, 3).isEmpty());
	}
}
