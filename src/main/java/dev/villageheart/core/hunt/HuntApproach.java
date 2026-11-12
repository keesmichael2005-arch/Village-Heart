package dev.villageheart.core.hunt;

/**
 * What a hunter does when another villager is in the way of the ground, or the walk gives up.
 * A neighbour is a jam, not a missing ground: close to the marker the hunter goes out beside them, farther out the
 * higher id steps aside. Only a walk that fails with nobody in the way takes the long unreachable retry.
 */
public final class HuntApproach {
	private HuntApproach() {}

	public enum Choice { WALK, ARRIVE, YIELD, UNREACHABLE }

	/** Ticks of being pushed against another villager before the higher id steps aside. Short enough that the 200-tick walk stall never starts. */
	public static final int YIELD_AFTER = 30;

	/** Walk give-ups beside a neighbour that may be called a jam. The next one in a row is a real unreachable ground. */
	public static final int MAX_JAM_GIVE_UPS = 1;

	/**
	 * @param touchingMover   a villager that is away or walking is next to the hunter: it will move on, so "goes out here" is right near the marker
	 * @param touchingAny     any villager is next to the hunter (a bystander can be NoAI or standing still: it only earns one give-up)
	 * @param recentGiveUps   give-ups beside a neighbour that this hunter already had in a row (a walk that arrived resets it)
	 */
	public static Choice choose(double distanceToMarker, double closest, boolean touchingMover, boolean touchingAny, boolean walkFailed, int recentGiveUps) {
		if (touchingMover && distanceToMarker <= closest) return Choice.ARRIVE;
		if (walkFailed && touchingAny && recentGiveUps < MAX_JAM_GIVE_UPS) return Choice.YIELD;
		if (walkFailed) return Choice.UNREACHABLE;
		return Choice.WALK;
	}

	/**
	 * @param lowerIdNeighbour the villager in the way has the lower id (and can be asked to keep its path), so this hunter is the one who gives way
	 * @param awayNeighbour    the villager in the way is away (hidden, NoAI): it can never move aside, so the walker always goes around it, whatever the ids.
	 *                         Without this the walker waits out the whole walk stall (200 ticks) against a hunter that hid just past the arrival distance.
	 */
	public static boolean yieldNow(long jammedFor, boolean touchingVillager, boolean lowerIdNeighbour, boolean awayNeighbour, double distanceToMarker, double closest) {
		return touchingVillager && (lowerIdNeighbour || awayNeighbour) && distanceToMarker > closest && jammedFor >= YIELD_AFTER;
	}
}
