package dev.villageheart.core.mine;

import dev.villageheart.core.Balance;
import java.util.OptionalLong;

/** The miner's decision on the surface (M16 plan 3.2 #10): the first row that matches wins. Pure; the glue gathers the inputs. */
public final class MinerPlanner {
	private MinerPlanner() {}

	public enum Action {
		/** Stand at the stonecutter. */
		IDLE_AT_MINE,
		/** A deposit trip takes the load first. */
		YIELD,
		/** Too far from the shaft for one walk: go to the stonecutter first. */
		WALK_TO_MINE,
		/** Walk to the shaft entrance and go down. */
		GO_DOWN
	}

	/** What the planner does to the job status the miner reports. */
	public enum StatusEffect { KEEP, CLEAR, NEEDS_TOOL, NO_WORK, SITE_UNREACHABLE }

	public record Decision(Action action, StatusEffect status) {}

	/**
	 * @param hasSlot              the miner still holds a Mine slot
	 * @param carriesNonFood       it carries something that is not food (a haul to hand in)
	 * @param pendingNotEmpty      part of a haul is still waiting for room in its inventory
	 * @param lease                the pickaxe state
	 * @param shaftValid           a VALID Mineshaft is linked to the Mine
	 * @param depthKnown           that shaft's deepest point is known
	 * @param dayTime              day time in [0, 24000)
	 * @param sinceUnreachable     ticks since the shaft last turned out unreachable, if it did
	 * @param distanceToMarker     blocks from the miner to the shaft marker
	 */
	public record PlanInputs(boolean hasSlot, boolean carriesNonFood, boolean pendingNotEmpty, AwayCheck.Lease lease, boolean shaftValid,
			boolean depthKnown, long dayTime, OptionalLong sinceUnreachable, double distanceToMarker) {}

	public static Decision decide(PlanInputs in) {
		if (!in.hasSlot()) return new Decision(Action.IDLE_AT_MINE, StatusEffect.CLEAR);
		if (in.carriesNonFood() || in.pendingNotEmpty()) return new Decision(Action.YIELD, StatusEffect.KEEP);
		if (in.lease() == AwayCheck.Lease.UNKNOWN) return new Decision(Action.IDLE_AT_MINE, StatusEffect.KEEP);
		if (in.lease() == AwayCheck.Lease.NONE) return new Decision(Action.IDLE_AT_MINE, StatusEffect.NEEDS_TOOL);
		if (!in.shaftValid()) return new Decision(Action.IDLE_AT_MINE, StatusEffect.NO_WORK);
		if (in.sinceUnreachable().isPresent() && in.sinceUnreachable().getAsLong() < Balance.MINER_RETRY_TICKS) {
			return new Decision(Action.IDLE_AT_MINE, StatusEffect.SITE_UNREACHABLE);
		}
		if (!in.depthKnown() || in.dayTime() >= Balance.MINER_LAST_DESCENT) return new Decision(Action.IDLE_AT_MINE, StatusEffect.CLEAR);
		if (in.distanceToMarker() > Balance.MINER_FAR_FROM_SHAFT) return new Decision(Action.WALK_TO_MINE, StatusEffect.CLEAR);
		return new Decision(Action.GO_DOWN, StatusEffect.CLEAR);
	}
}
