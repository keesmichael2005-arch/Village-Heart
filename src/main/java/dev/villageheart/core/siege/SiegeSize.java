package dev.villageheart.core.siege;

/** How many zombies a siege brings (docs/plans/M25.md 3.12). Fewer than four qualified guards face a small group. */
public final class SiegeSize {
	private SiegeSize() {}

	public static int zombies(double threat, int qualifiedGuards) {
		int raw = (int) Math.floor(4 + 2 * threat);
		raw = Math.max(4, Math.min(20, raw));
		if (qualifiedGuards >= 4) return raw;
		return Math.min(raw, Math.max(2, qualifiedGuards + 1));
	}
}
