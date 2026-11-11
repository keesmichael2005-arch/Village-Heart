package dev.villageheart.core.smith;

/**
 * Material repair, the vanilla anvil's quarter of the max durability, floored. A partial last quarter costs nothing: the smith does not
 * spend an ingot to finish a scrap of damage (vanilla would). Wooden and stone tools are not repaired; they are replaced.
 */
public final class RepairRules {
	private RepairRules() {}

	/** Durability one ingot (or one leather) restores. Integer division, so a max of 250 restores 62. */
	public static int unitSize(int max) { return max <= 0 ? 0 : max / 4; }

	/** How many whole units {@code damage} is worth. Never a unit for a remainder smaller than {@link #unitSize}. */
	public static int units(int damage, int max) {
		int size = unitSize(max);
		if (size <= 0 || damage <= 0) return 0;
		return damage / size;
	}

	/** Damage left after {@code n} units. Never negative. */
	public static int after(int damage, int max, int n) {
		return Math.max(0, damage - n * unitSize(max));
	}
}
