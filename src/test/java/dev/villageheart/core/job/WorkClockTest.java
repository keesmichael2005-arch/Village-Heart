package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class WorkClockTest {
	@Test
	void remainderCarriesSoLogsFallOnExactTicks() {
		WorkClock c = new WorkClock(50);
		java.util.List<Integer> at = new java.util.ArrayList<>();
		for (int t = 1; t <= 130; t++) if (c.tick(1.25) > 0) at.add(t);
		assertEquals(java.util.List.of(40, 80, 120), at);
	}

	@Test
	void resetDropsProgress() {
		WorkClock c = new WorkClock(50);
		for (int i = 0; i < 20; i++) assertEquals(0, c.tick(1.0));
		assertTrue(c.progress() > 0.3);
		c.reset();
		assertEquals(0.0, c.progress());
		for (int i = 0; i < 49; i++) assertEquals(0, c.tick(1.0));
		assertEquals(1, c.tick(1.0));
	}

	@Test
	void progressStaysInZeroToOne() {
		WorkClock c = new WorkClock(50);
		for (int i = 0; i < 500; i++) {
			c.tick(1.37);
			assertTrue(c.progress() >= 0.0 && c.progress() < 1.0, "progress " + c.progress());
		}
	}

	@Test
	void highSpeedNeverLosesUnits() {
		WorkClock c = new WorkClock(2);
		int units = 0;
		for (int i = 0; i < 1000; i++) {
			int u = c.tick(3.0);
			assertTrue(u == 1 || u == 2, "units " + u);
			units += u;
		}
		assertEquals(1500, units);
	}

	@Test
	void zeroSpeedDoesNothing() {
		WorkClock c = new WorkClock(50);
		for (int i = 0; i < 100; i++) assertEquals(0, c.tick(0.0));
		assertEquals(0.0, c.progress());
	}
}
