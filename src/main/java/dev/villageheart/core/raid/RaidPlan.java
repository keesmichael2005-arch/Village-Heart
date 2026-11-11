package dev.villageheart.core.raid;

/**
 * What was decided when a raid started (M25). {@code waveCap} 0 means vanilla's own group count.
 * {@code early} is a young-village cap (3.13a). The reward of a won random raid reads this later.
 */
public record RaidPlan(int omen, int extraVindicators, int extraRavagers, int waveCap, boolean early) {
	/** A test or Bad Omen raid: omen 1, no extras, vanilla waves. */
	public static final RaidPlan VANILLA = new RaidPlan(1, 0, 0, 0, false);

	/** The same plan. The wall track named the unset plan {@code NONE}. */
	public static final RaidPlan NONE = VANILLA;

	public RaidPlan {
		if (omen < 1 || omen > 5) throw new IllegalArgumentException("omen must be 1..5: " + omen);
		if (extraVindicators < 0 || extraRavagers < 0 || waveCap < 0) {
			throw new IllegalArgumentException("raid plan counts must be >= 0");
		}
	}
}
