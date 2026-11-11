package dev.villageheart.core.gear;

import dev.villageheart.core.Balance;
import java.util.Locale;

/**
 * Tool and armour materials. WOODEN and STONE came first (M19a, self-made tools); M19 appends LEATHER, COPPER, IRON and DIAMOND.
 * Codecs store {@code name()}, never the ordinal. Compare tiers with {@link #rank()}, never with {@code ordinal()} (leather sits between
 * stone and copper).
 */
public enum CraftTier {
	WOODEN("wooden"), STONE("stone"), LEATHER("leather"), COPPER("copper"), IRON("iron"), DIAMOND("diamond");

	private final String prefix;

	CraftTier(String prefix) { this.prefix = prefix; }

	/** Item id prefix ({@code minecraft:iron_axe}, {@code minecraft:leather_helmet}). */
	public String itemPrefix() { return prefix; }

	/** Work ticks at level 1 for a self-made tool of this tier. Only wooden and stone; any other tier throws. */
	public int selfTicks() {
		return switch (this) {
			case WOODEN -> Balance.SELF_WOODEN_TICKS;
			case STONE -> Balance.SELF_STONE_TICKS;
			default -> throw new IllegalStateException("not a self-made tier: " + this);
		};
	}

	/** Quality order shared with {@code GearInfo.rank}: wooden and leather 1, stone 2, copper 3, iron 4, diamond 5. */
	public int rank() {
		return switch (this) {
			case WOODEN, LEATHER -> 1;
			case STONE -> 2;
			case COPPER -> 3;
			case IRON -> 4;
			case DIAMOND -> 5;
		};
	}

	public boolean ironOrBetter() { return this == IRON || this == DIAMOND; }

	/** The stockpile role that pays for this tier's head (or the whole armour piece). */
	public MaterialRole headRole() {
		return switch (this) {
			case WOODEN -> MaterialRole.PLANKS;
			case STONE -> MaterialRole.STONE;
			case LEATHER -> MaterialRole.LEATHER;
			case COPPER -> MaterialRole.COPPER_INGOT;
			case IRON -> MaterialRole.IRON_INGOT;
			case DIAMOND -> MaterialRole.DIAMOND;
		};
	}

	public boolean armourOnly() { return this == LEATHER; }

	public boolean toolOnly() { return this == WOODEN || this == STONE; }

	public String id() { return name().toLowerCase(Locale.ROOT); }
}
