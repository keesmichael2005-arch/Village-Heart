package dev.villageheart.core.mine;

import dev.villageheart.core.Balance;
import java.util.Objects;
import java.util.Optional;

/**
 * The ordered checks of one underground step (M16 plan 3.4), pure. The first check that fails decides. Progress is kept on every
 * surface except SITE_INVALID (the shaft changed), where it is reset.
 */
public final class AwayCheck {
	private AwayCheck() {}

	public enum Lease { HELD, NONE, UNKNOWN }

	/**
	 * @param registeredMiner the villager is a registered miner of the current village and the trip is a miner trip
	 * @param phaseIsWork     the day phase is WORK
	 * @param siteValid       the trip's shaft record exists, is VALID and still links to the miner's Mine
	 * @param slotHeld        the miner still holds a Mine slot
	 * @param lease           the state of the pickaxe in the Mine chest
	 * @param multiplier      WorkSpeed for this step (0 = on strike, starving)
	 * @param depthKnown      the shaft's deepest point is known (it is runtime data, unknown after a restart)
	 * @param progress        the saved progress
	 * @param unknownSteps    steps in a row spent waiting for an unreadable chest or an unknown depth
	 */
	public record Inputs(boolean registeredMiner, boolean phaseIsWork, boolean siteValid, boolean slotHeld, Lease lease,
			double multiplier, boolean depthKnown, AwayProgress progress, int unknownSteps) {
		public Inputs {
			Objects.requireNonNull(lease, "lease");
			Objects.requireNonNull(progress, "progress");
		}
	}

	/**
	 * @param surface  the reason to come up, or empty to stay down
	 * @param haul     true when {@code surface} is HAUL: roll a haul now
	 * @param progress the progress after this step
	 */
	public record Outcome(Optional<SurfaceReason> surface, boolean haul, AwayProgress progress, int unknownSteps) {
		public boolean stays() { return surface.isEmpty(); }
	}

	private static Outcome up(SurfaceReason r, AwayProgress progress, int unknown) {
		return new Outcome(Optional.of(r), r == SurfaceReason.HAUL, progress, unknown);
	}

	public static Outcome check(Inputs in) {
		if (!in.registeredMiner()) return up(SurfaceReason.NOT_A_MINER, in.progress(), 0);
		if (!in.phaseIsWork()) return up(SurfaceReason.DAY_END, in.progress(), 0);
		if (!in.siteValid() || !in.slotHeld()) return up(SurfaceReason.SITE_INVALID, AwayProgress.ZERO, 0);
		int unknown = in.unknownSteps();
		switch (in.lease()) {
			case NONE:
				return up(SurfaceReason.NO_TOOL, in.progress(), 0);
			case UNKNOWN:
				unknown++;
				if (unknown >= Balance.MINER_UNKNOWN_LIMIT) return up(SurfaceReason.STOPPED_WORKING, in.progress(), 0);
				return new Outcome(Optional.empty(), false, in.progress(), unknown);
			case HELD:
				break;
		}
		if (in.multiplier() <= 0) return up(SurfaceReason.STOPPED_WORKING, in.progress(), 0);
		if (!in.depthKnown()) {
			AwayProgress held = in.progress().addHeld(Balance.MINER_STEP_TICKS, in.multiplier());
			if (!held.due()) return new Outcome(Optional.empty(), false, held, 0);
			unknown++; // a haul is waiting for the depth: bounded like an unreadable chest
			if (unknown >= Balance.MINER_UNKNOWN_LIMIT) return up(SurfaceReason.STOPPED_WORKING, held, 0);
			return new Outcome(Optional.empty(), false, held, unknown);
		}
		AwayProgress.Step step = in.progress().add(Balance.MINER_STEP_TICKS, in.multiplier());
		if (step.hauls() > 0) return up(SurfaceReason.HAUL, step.next(), 0);
		return new Outcome(Optional.empty(), false, step.next(), 0);
	}
}
