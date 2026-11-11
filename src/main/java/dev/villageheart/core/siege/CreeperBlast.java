package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;

/**
 * The hole a breaching creeper opens (docs/plans/M25.md 3.9). The target cell and the defence cell above or below it take
 * {@link Balance#CREEPER_CORE_DAMAGE}. Other defence cells at centre distance {@code d} take {@code round(8 × (1 − d/r))},
 * with {@code r} 3, or 6 when the creeper is powered. Power widens the cracks. The hole stays the two core cells.
 */
public final class CreeperBlast {
	private CreeperBlast() {}

	public static int damage(double distance, boolean coreColumn, boolean powered) {
		if (coreColumn) return Balance.CREEPER_CORE_DAMAGE;
		int radius = powered ? Balance.CREEPER_RADIUS_POWERED : Balance.CREEPER_RADIUS;
		if (distance > radius) return 0;
		return (int) Math.round(8.0 * (1.0 - distance / radius));
	}
}
