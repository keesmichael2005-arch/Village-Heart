package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TickTimerTest {
	private static final long MS = 1_000_000L;

	@Test
	void hundredTicks() {
		TickTimer t = new TickTimer();
		for (int k = 1; k <= 100; k++) { t.add(k * MS); t.commitTick(); }
		TickStats s = t.snapshot();
		assertEquals(100, s.samples());
		assertEquals(50.5, s.meanMs(), 1e-9);
		assertEquals(95.0, s.p95Ms(), 1e-9);
		assertEquals(100.0, s.maxMs(), 1e-9);
	}

	@Test
	void twoAddsOneSample() {
		TickTimer t = new TickTimer();
		t.add(2 * MS);
		t.add(3 * MS);
		t.commitTick();
		TickStats s = t.snapshot();
		assertEquals(1, s.samples());
		assertEquals(5.0, s.meanMs(), 1e-9);
		assertEquals(5.0, s.p95Ms(), 1e-9);
		assertEquals(5.0, s.maxMs(), 1e-9);
	}

	@Test
	void emptyCommitIsZeroSample() {
		TickTimer t = new TickTimer();
		t.commitTick();
		assertEquals(new TickStats(1, 0, 0, 0), t.snapshot());
	}

	@Test
	void resetClearsAndDropsUncommitted() {
		TickTimer t = new TickTimer();
		t.add(MS);
		t.commitTick();
		t.add(7 * MS); // uncommitted
		assertEquals(1, t.snapshotAndReset().samples());
		assertEquals(TickStats.EMPTY, t.snapshot());
		t.commitTick(); // the dropped sum must not come back
		assertEquals(0.0, t.snapshot().maxMs(), 1e-12);
	}

	@Test
	void negativeRejected() {
		assertThrows(IllegalArgumentException.class, () -> new TickTimer().add(-1));
	}

	@Test
	void growsPastInitialCapacity() {
		TickTimer t = new TickTimer();
		for (int i = 0; i < 24000; i++) { t.add(MS); t.commitTick(); }
		assertEquals(24000, t.snapshot().samples());
	}
}
