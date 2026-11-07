package dev.villageheart.core.sim;

import static org.junit.jupiter.api.Assertions.*;

import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SimClockTest {
	@Test
	void dayBoundaries() {
		SimClock c = new SimClock(41, 5);
		assertEquals(OptionalInt.empty(), c.dayEnded(24040));
		assertEquals(OptionalInt.of(1), c.dayEnded(24041));
		assertEquals(OptionalInt.empty(), c.dayEnded(24041));
		assertFalse(c.finished());
	}

	@Test
	void skippedTicksLoseNothing() {
		SimClock c = new SimClock(41, 5);
		c.dayEnded(24041);
		assertEquals(OptionalInt.of(2), c.dayEnded(41 + 72000));
		assertEquals(OptionalInt.of(3), c.dayEnded(41 + 72000));
		assertEquals(OptionalInt.empty(), c.dayEnded(41 + 72000));
		assertEquals(3, c.completedDays());
		c.dayEnded(41 + 5 * 24000);
		c.dayEnded(41 + 5 * 24000);
		assertTrue(c.finished());
		assertEquals(OptionalInt.empty(), c.dayEnded(41 + 9 * 24000));
	}

	@Test
	void resume() {
		SimClock c = new SimClock(41, 5, 2);
		assertEquals(72000, c.remainingTicks(41 + 48000));
		assertEquals(OptionalInt.empty(), c.dayEnded(41 + 48000));
		assertEquals(OptionalInt.of(3), c.dayEnded(41 + 72000));
		assertEquals(24000, c.elapsed(41 + 24000));
	}
}
