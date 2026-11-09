package dev.villageheart.core.defence;

import dev.villageheart.core.Balance;

/**
 * When hired workers and guards open and close the village's fence gates (M18 gate step, human decision 2026-11-09): they pass a gate inside
 * the claim like a door, mobs still cannot. Pure rules; the glue ({@code dev.villageheart.defence.GateTraffic}, {@code GateKeeper}) feeds the facts.
 */
public final class GateRules {
	private GateRules() {}

	/**
	 * May this walker open (and plan a path through) this closed gate? Only a gate inside the claim, not locked by redstone, and not while
	 * a hostile is within {@link Balance#GATE_HOSTILE_RANGE} blocks of it, unless the walker is a guard that is engaging.
	 */
	public static boolean mayOpen(boolean inClaim, boolean powered, boolean hostileNear, boolean engagedGuard) {
		return inClaim && !powered && (!hostileNear || engagedGuard);
	}

	/** What the keeper sees at a gate it opened. */
	public record OpenGate(long ageTicks, boolean occupied, boolean userNear, boolean hostileNear, boolean engagedGuardNear) {}

	/**
	 * Should the gate be closed now? Never on someone standing in it (until the safety limit), never before {@link Balance#GATE_MIN_OPEN_TICKS};
	 * always after {@link Balance#GATE_MAX_OPEN_TICKS}; a hostile near closes it unless an engaged guard still needs it; otherwise it stays
	 * open while a worker or guard that is passing needs it.
	 */
	public static boolean shouldClose(OpenGate g) {
		if (g.ageTicks() >= Balance.GATE_MAX_OPEN_TICKS) return true;
		if (g.occupied()) return false;
		if (g.ageTicks() < Balance.GATE_MIN_OPEN_TICKS) return false;
		if (g.hostileNear() && !g.engagedGuardNear()) return true;
		return !g.userNear();
	}
}
