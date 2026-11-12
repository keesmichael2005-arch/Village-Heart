package dev.villageheart.core.siege;

import dev.villageheart.core.defence.DefenceKind;

/** Damage and swing interval of a mob hitting a defence cell (docs/plans/M25.md 3.9). A baby uses its grown row. */
public final class SiegeHits {
	private SiegeHits() {}

	public enum Attacker { ZOMBIE, BABY_ZOMBIE, HUSK, ZOMBIE_VILLAGER, VINDICATOR, RAVAGER }

	public static int damage(Attacker attacker, DefenceKind kind) {
		boolean gate = kind == DefenceKind.GATE;
		return switch (attacker) {
			case ZOMBIE, BABY_ZOMBIE, HUSK, ZOMBIE_VILLAGER -> gate ? 2 : 1;
			case VINDICATOR -> gate ? 4 : 2;
			case RAVAGER -> gate ? 4 : 6;
		};
	}

	public static int interval(Attacker attacker) { return attacker == Attacker.RAVAGER ? 40 : 20; }

	public static int hitsToBreak(Attacker attacker, DefenceKind kind) {
		int each = damage(attacker, kind);
		return (WallHp.hp(kind) + each - 1) / each;
	}
}
