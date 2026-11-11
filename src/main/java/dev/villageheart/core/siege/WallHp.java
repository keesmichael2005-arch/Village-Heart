package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;
import dev.villageheart.core.defence.DefenceKind;

/** Hit points and crack stages of a defence cell (docs/plans/M25.md 3.9). */
public final class WallHp {
	private WallHp() {}

	public static int hp(DefenceKind kind) { return kind == DefenceKind.GATE ? Balance.HP_GATE : Balance.HP_STONE; }

	/**
	 * Destruction-crack stage, 0 to 9. {@code 11/12} is 9. The stage never reaches 10: a cell at its hit points is a breach, not a crack.
	 */
	public static int stage(int damage, int hp) {
		if (hp <= 0 || damage <= 0) return 0;
		return Math.min(9, (10 * damage) / hp);
	}

	/**
	 * A negative crack id, injective for dx, dz inside the claim (radius 80) at one height.
	 * {@code dx} and {@code dz} are the cell minus the Heart.
	 */
	public static long crackId(int dx, int dz, int y, int minY) {
		return -1L - (dx + 128) - 256L * (dz + 128) - 65536L * (y - minY);
	}
}
