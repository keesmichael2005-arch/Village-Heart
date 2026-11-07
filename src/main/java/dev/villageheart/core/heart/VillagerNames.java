package dev.villageheart.core.heart;

import java.util.UUID;

/** Deterministic names for villagers without a name tag, so the rows do not change between refreshes. */
public final class VillagerNames {
	private VillagerNames() {}

	private static final String[] START = {"Bram", "Til", "Os", "Wil", "Ha", "Ma", "Pe", "Jo", "Ed", "Ro", "Ka", "Lu", "Ner", "Cor",
			"Dun", "Fen", "Gil", "Hob", "Ivo", "Mor"};
	private static final String[] END = {"da", "ric", "wyn", "mund", "la", "ton", "bert", "sen", "ley", "a", "o", "ith", "ford",
			"ine", "ard", "ow", "ina", "ald", "ey", "ric"};

	public static String of(UUID id) {
		long h = id.getMostSignificantBits() * 0x9E3779B97F4A7C15L ^ Long.rotateLeft(id.getLeastSignificantBits(), 31) * 0xC2B2AE3D27D4EB4FL;
		h ^= h >>> 29;
		h *= 0xBF58476D1CE4E5B9L;
		h ^= h >>> 32;
		int a = (int) Long.remainderUnsigned(h, START.length);
		int b = (int) Long.remainderUnsigned(h >>> 17, END.length);
		return START[a] + END[b];
	}

	/** Length of the longest name {@link #of} can return. */
	public static int maxLength() {
		int s = 0, e = 0;
		for (String x : START) s = Math.max(s, x.length());
		for (String x : END) e = Math.max(e, x.length());
		return s + e;
	}
}
