package dev.villageheart.core.guard;

/**
 * One piece of gear a guard could wear or wield, with the numbers read from the item's own components (M18 3.3): the glue fills them, so
 * leather < copper < chainmail = gold < iron < diamond < netherite falls out of the armour values instead of a material list.
 *
 * @param order position among equals (chest order, stockpile order): the last tie-break
 */
public record GearItem(String id, GearSlot slot, double armor, double toughness, double attackDamage, int damage, int maxDamage, Source source,
		int order) {
	/** Where the piece is now. */
	public enum Source { WORN, BARRACKS, STOCKPILE }

	/** 1.0 for an undamaged (or undamageable) piece, 0.0 for one about to break. */
	public double durabilityFraction() {
		return maxDamage <= 0 ? 1.0 : Math.max(0.0, 1.0 - (double) damage / maxDamage);
	}

	/** Armour needs armour points (pumpkins, heads and elytra have none), a weapon needs attack damage; and enough durability left. */
	public boolean usable(double minDurability) {
		boolean stats = slot == GearSlot.WEAPON ? attackDamage > 0 : armor > 0;
		return stats && durabilityFraction() >= minDurability - 1e-9;
	}
}
