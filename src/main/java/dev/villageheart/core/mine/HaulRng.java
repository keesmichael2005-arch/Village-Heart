package dev.villageheart.core.mine;

import java.util.SplittableRandom;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * The seeded random source of one haul: a function of the world seed, the worker and the worker's haul counter, so a reload never
 * re-rolls a haul differently and tests can predict every haul. The mix is the SplitMix64 finaliser, pinned (see HaulRngTest).
 */
public final class HaulRng {
	private HaulRng() {}

	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	public static RandomGenerator forHaul(long worldSeed, UUID worker, long haulIndex) {
		long s = mix(mix(mix(worldSeed ^ worker.getMostSignificantBits()) ^ worker.getLeastSignificantBits()) ^ haulIndex);
		return new SplittableRandom(s);
	}
}
