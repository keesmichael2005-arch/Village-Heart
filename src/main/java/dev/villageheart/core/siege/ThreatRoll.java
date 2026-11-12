package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;

/** A seeded roll (docs/plans/M25.md 3.2 #12). SplitMix64, no shared random. The same world and day roll the same way. */
public final class ThreatRoll {
	private ThreatRoll() {}

	public enum Kind { RAID, SIEGE }

	public static int day(long gameTime) { return (int) (gameTime / 24000L); }

	public static int dayTime(long gameTime) { return (int) Math.floorMod(gameTime, 24000L); }

	public static boolean roll(long worldSeed, int day, Kind kind, double p) {
		return unit(worldSeed, day, kind) < p;
	}

	/** Inclusive {@code 2000..11000}, from the raid roll of that day. */
	public static int startTick(long worldSeed, int day) {
		long h = hash(worldSeed, day, Kind.RAID);
		int span = Balance.PENDING_START_MAX - Balance.PENDING_START_MIN + 1;
		return Balance.PENDING_START_MIN + (int) Long.remainderUnsigned(h, span);
	}

	static double unit(long worldSeed, int day, Kind kind) {
		return (hash(worldSeed, day, kind) >>> 11) * 0x1.0p-53;
	}

	static long hash(long worldSeed, int day, Kind kind) {
		long x = worldSeed ^ (kind == Kind.RAID ? 0x9E3779B97F4A7C15L : 0xC2B2AE3D27D4EB4FL);
		x ^= Integer.toUnsignedLong(day);
		return mix(x);
	}

	/** Stafford variant of SplitMix64. */
	static long mix(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}
}
