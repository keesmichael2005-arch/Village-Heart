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

	public static Choice choose(double distanceToMarker, double closest, boolean touchingVillager, boolean walkFailed) {
		if (touchingVillager && distanceToMarker <= closest) return Choice.ARRIVE;
		if (walkFailed && touchingVillager) return Choice.YIELD;
		if (walkFailed) return Choice.UNREACHABLE;
		return Choice.WALK;
	}

	/**
	 * @param lowerIdNeighbour the villager in the way has the lower id, so this hunter is the one who gives way
	 */
	public static boolean yieldNow(long jammedFor, boolean touchingVillager, boolean lowerIdNeighbour, double distanceToMarker, double closest) {
		return touchingVillager && lowerIdNeighbour && distanceToMarker > closest && jammedFor >= YIELD_AFTER;
	}
}
