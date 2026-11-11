package dev.villageheart.core.siege;

/**
 * When a running siege is over (M25 3.17). A siege the scenario started keeps going while the roll switch is off.
 * A rolled siege stops as soon as that switch is off. Both stop at the end of the night, on the next day, and once
 * every group has spawned and every zombie is dead.
 */
public final class SiegeEnd {
	private SiegeEnd() {}

	public static boolean over(boolean scripted, boolean rulesOn, int dayTime, int nightEnd, int startDay, int today,
			int killed, int spawned, int nextGroup, int groups) {
		if (!scripted && !rulesOn) return true;
		if (dayTime >= nightEnd || startDay < today) return true;
		return killed >= spawned && nextGroup >= groups;
	}
}
