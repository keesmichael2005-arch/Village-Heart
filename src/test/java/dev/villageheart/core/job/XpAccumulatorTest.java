package dev.villageheart.core.job;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class XpAccumulatorTest {
	private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);

	@Test
	void fifthsAddUpExactly() {
		XpAccumulator x = new XpAccumulator();
		for (int i = 0; i < 4; i++) assertEquals(0, x.add(A, WorkUnit.BLOCK_PLACED, 1), "unit " + i);
		assertEquals(1, x.add(A, WorkUnit.BLOCK_PLACED, 1));
		assertEquals(0, x.add(A, WorkUnit.BLOCK_PLACED, 4));
		assertEquals(1, x.add(A, WorkUnit.BLOCK_PLACED, 1));
	}

	@Test
	void manyAtOnceCarryTheRest() {
		XpAccumulator x = new XpAccumulator();
		assertEquals(2, x.add(A, WorkUnit.BLOCK_PLACED, 13)); // 2.6 XP
		assertEquals(0, x.add(A, WorkUnit.BLOCK_PLACED, 1)); // 2.8 carried: 0.8
		assertEquals(1, x.add(A, WorkUnit.BLOCK_PLACED, 1)); // 1.0 now
	}

	@Test
	void millionsDoNotDrift() {
		XpAccumulator x = new XpAccumulator();
		long total = 0;
		for (int i = 0; i < 5_000_000; i++) total += x.add(A, WorkUnit.BLOCK_PLACED, 1);
		assertEquals(1_000_000, total);
		assertEquals(1_000_000, new XpAccumulator().add(A, WorkUnit.BLOCK_PLACED, 5_000_000));
		assertEquals(200_000_000, new XpAccumulator().add(A, WorkUnit.LOG_FELLED, 200_000_000));
		assertEquals(Integer.MAX_VALUE, new XpAccumulator().add(A, WorkUnit.GAME_HUNTED, Integer.MAX_VALUE));
	}

	@Test
	void wholeUnitsAreOneXp() {
		XpAccumulator x = new XpAccumulator();
		assertEquals(1, x.add(A, WorkUnit.LOG_FELLED, 1));
		assertEquals(3, x.add(A, WorkUnit.CROP_HARVESTED, 3));
		assertEquals(0, x.add(A, WorkUnit.LOG_FELLED, 0));
		assertThrows(IllegalArgumentException.class, () -> x.add(A, WorkUnit.LOG_FELLED, -1));
	}

	@Test
	void workersAreSeparateAndForgetDrops() {
		XpAccumulator x = new XpAccumulator();
		x.add(A, WorkUnit.BLOCK_PLACED, 3);
		x.add(B, WorkUnit.BLOCK_PLACED, 4);
		assertEquals(1, x.add(A, WorkUnit.BLOCK_PLACED, 2));
		assertEquals(1, x.add(B, WorkUnit.BLOCK_PLACED, 1));
		x.add(A, WorkUnit.BLOCK_PLACED, 4);
		x.forget(A);
		assertEquals(0, x.add(A, WorkUnit.BLOCK_PLACED, 1));
	}
}
