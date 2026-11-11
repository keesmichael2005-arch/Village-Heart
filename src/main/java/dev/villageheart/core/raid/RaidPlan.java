package dev.villageheart.core.raid;

/**
 * The size of a raid, fixed at the moment it starts (docs/plans/M25.md 3.3, 3.13a).
 *
 * @param omen             vanilla omen level
 * @param extraVindicators added from wave 2 on a full random raid
 * @param extraRavagers    added from wave 3 on a full random raid
 * @param waveCap          0 means vanilla's own wave count; a positive cap is a young-village raid
 * @param early            true when the wave cap is the young-village rule
 */
public record RaidPlan(int omen, int extraVindicators, int extraRavagers, int waveCap, boolean early) {
	public static final RaidPlan NONE = new RaidPlan(1, 0, 0, 0, false);
}
