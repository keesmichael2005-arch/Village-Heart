package dev.villageheart.core.gear;

import dev.villageheart.core.Balance;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.worker.JobType;

/**
 * How a job wears its tool: {@code wearPerUnit} durability per unit of work, {@code unitTicks} ticks per unit at stone pace and level 1.
 * Lumberjack and miner pace follows the tool tier ({@link #unitTicksAt}); hunter and farmer do not.
 */
public record WearProfile(int wearPerUnit, int unitTicks, boolean paceDependsOnTier) {
	public WearProfile {
		if (wearPerUnit < 1 || unitTicks < 1) throw new IllegalArgumentException("wear profile must be positive");
	}

	/** A profile whose pace does not depend on the tier (the two-argument shape M19a used). */
	public WearProfile(int wearPerUnit, int unitTicks) { this(wearPerUnit, unitTicks, false); }

	/** Lumberjack 1 per log (40 ticks at stone), farmer 1 per harvest (20), miner 10 per haul, hunter 5 per trip. */
	public static WearProfile of(JobType job) {
		return switch (job) {
			case LUMBERJACK -> new WearProfile(1, 40, true);
			case FARMER -> new WearProfile(1, 20, false);
			case MINER -> new WearProfile(Balance.MINER_WEAR_PER_HAUL, 2000, true);
			case HUNTER -> new WearProfile(Balance.HUNTER_WEAR_PER_TRIP, 3000, false);
			default -> new WearProfile(1, 100, false);
		};
	}

	/**
	 * Ticks one unit takes with a tool of {@code tier}. Stone pace for a tier-paced job, scaled by {@code TOOL_SPEED[stone] / TOOL_SPEED[tier]};
	 * unchanged for the hunter and the farmer.
	 */
	public int unitTicksAt(ToolTier tier) {
		if (!paceDependsOnTier) return unitTicks;
		double stone = Balance.TOOL_SPEED[ToolTier.STONE.ordinal()];
		double speed = Balance.TOOL_SPEED[tier.ordinal()];
		if (speed <= 0) return unitTicks;
		return (int) Math.ceil(unitTicks * stone / speed - 1e-9);
	}
}
