package dev.villageheart.core.guard;

import dev.villageheart.core.Balance;
import java.util.Collection;
import java.util.Optional;

/**
 * Which piece of gear is better (M18 3.3). Armour by (armor, toughness, durability fraction), a weapon by (attack damage, durability
 * fraction); ties: WORN, then BARRACKS, then STOCKPILE, then the lower order. Enchantments are not ranked in v1 (they are no attribute
 * modifiers), so a Sharpness V iron sword ranks as a plain iron sword.
 */
public final class GearRanking {
	private GearRanking() {}

	/** Positive when {@code a} is better than {@code b}, by the numbers only (no tie-break on where they lie). */
	public static int compareStats(GearItem a, GearItem b) {
		int c;
		if (a.slot() == GearSlot.WEAPON) {
			c = Double.compare(a.attackDamage(), b.attackDamage());
		} else {
			c = Double.compare(a.armor(), b.armor());
			if (c == 0) c = Double.compare(a.toughness(), b.toughness());
		}
		if (c == 0) c = Double.compare(round(a.durabilityFraction()), round(b.durabilityFraction()));
		return c;
	}

	/** Positive when {@code a} ranks above {@code b}: stats, then WORN > BARRACKS > STOCKPILE, then the lower order. */
	public static int compare(GearItem a, GearItem b) {
		int c = compareStats(a, b);
		if (c != 0) return c;
		c = Integer.compare(b.source().ordinal(), a.source().ordinal());
		if (c != 0) return c;
		return Integer.compare(b.order(), a.order());
	}

	/** The best usable piece among {@code items} of one slot (the caller filters the slot). */
	public static Optional<GearItem> best(Collection<GearItem> items) {
		GearItem best = null;
		for (GearItem g : items) {
			if (!g.usable(Balance.GEAR_MIN_DURABILITY)) continue;
			if (best == null || compare(g, best) > 0) best = g;
		}
		return Optional.ofNullable(best);
	}

	private static double round(double f) { return Math.round(f * 1000.0) / 1000.0; }
}
