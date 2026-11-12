package dev.villageheart.core.siege;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** T-L1-19. Omen and extras rise with the threat and stop at the caps. */
class RaidScaleTest {
	@Test
	void samplesAndMonotonic() {
		assertEquals(new RaidScale(1, 0, 0), RaidScale.of(0));
		assertEquals(new RaidScale(1, 1, 0), RaidScale.of(2));
		assertEquals(new RaidScale(2, 1, 0), RaidScale.of(3));
		assertEquals(new RaidScale(3, 4, 2), RaidScale.of(8));
		assertEquals(new RaidScale(5, 4, 2), RaidScale.of(100));
		RaidScale prev = RaidScale.of(0);
		for (int t = 1; t <= 100; t++) {
			RaidScale next = RaidScale.of(t);
			assertTrue(next.omen() >= prev.omen() && next.extraVindicators() >= prev.extraVindicators() && next.extraRavagers() >= prev.extraRavagers(), t + " " + next);
			prev = next;
		}
	}
}
