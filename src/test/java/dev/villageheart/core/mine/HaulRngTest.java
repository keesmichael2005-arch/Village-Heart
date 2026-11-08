package dev.villageheart.core.mine;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.UUID;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;

class HaulRngTest {
	private static final UUID A = UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e");
	private static final UUID B = UUID.fromString("7c9e6679-7425-40de-944b-e07fc1f90ae7");

	private static double[] first(RandomGenerator r, int n) {
		double[] d = new double[n];
		for (int i = 0; i < n; i++) d[i] = r.nextDouble();
		return d;
	}

	@Test
	void sameInputsGiveTheSameStream() {
		assertArrayEquals(first(HaulRng.forHaul(42, A, 3), 8), first(HaulRng.forHaul(42, A, 3), 8));
	}

	@Test
	void differentHaulsWorkersAndSeedsGiveDifferentStreams() {
		double[] base = first(HaulRng.forHaul(42, A, 3), 8);
		assertFalse(Arrays.equals(base, first(HaulRng.forHaul(42, A, 4), 8)));
		assertFalse(Arrays.equals(base, first(HaulRng.forHaul(42, B, 3), 8)));
		assertFalse(Arrays.equals(base, first(HaulRng.forHaul(43, A, 3), 8)));
	}

	/** Pins the mix formula: these literals were computed independently (SplitMix64 in Python), so a refactor cannot move them silently. */
	@Test
	void pinnedLiteralStreams() {
		assertArrayEquals(new double[] {0.6476990880126788, 0.5650948109298276, 0.9263071630214337}, first(HaulRng.forHaul(0L, A, 0), 3), 0.0);
		assertArrayEquals(new double[] {0.521419030498905, 0.31616052850437404, 0.4616144071814049}, first(HaulRng.forHaul(42L, B, 7), 3), 0.0);
		assertArrayEquals(new double[] {0.5731610884421702, 0.5622064774388611, 0.3694937833320553},
				first(HaulRng.forHaul(-1234567890123L, A, 123456), 3), 0.0);
	}

	@Test
	void theTableTakesItsRandomnessAsAParameter() {
		// a generator that always returns 0.0 hits every chance entry; one that returns 0.99 hits none: nothing else decides a roll
		RandomGenerator low = () -> 0L;
		RandomGenerator high = () -> -1L;
		assertEquals(4, HaulTable.roll(DepthBand.HIGH, dev.villageheart.core.job.ToolTier.STONE, low).size());
		assertEquals(3, HaulTable.roll(DepthBand.HIGH, dev.villageheart.core.job.ToolTier.STONE, high).size());
	}
}
