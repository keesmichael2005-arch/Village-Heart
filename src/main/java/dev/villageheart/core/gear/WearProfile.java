package dev.villageheart.core.gear;

import dev.villageheart.core.worker.JobType;

/** How a job wears its tool: {@code wearPerUnit} durability per unit of work, {@code unitTicks} ticks per unit at level 1. */
public record WearProfile(int wearPerUnit, int unitTicks) {
	public WearProfile {
		if (wearPerUnit < 1 || unitTicks < 1) throw new IllegalArgumentException("wear profile must be positive");
	}

	/** Lumberjack 1 per log (40 ticks), farmer 1 per harvest (20), miner 10 per haul, hunter 5 per trip. */
	public static WearProfile of(JobType job) {
		return switch (job) {
			case LUMBERJACK -> new WearProfile(1, 40);
			case FARMER -> new WearProfile(1, 20);
			case MINER -> new WearProfile(dev.villageheart.core.Balance.MINER_WEAR_PER_HAUL, 2000);
			case HUNTER -> new WearProfile(dev.villageheart.core.Balance.HUNTER_WEAR_PER_TRIP, 3000);
			default -> new WearProfile(1, 100);
		};
	}
}
