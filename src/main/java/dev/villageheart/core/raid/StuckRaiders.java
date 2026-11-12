package dev.villageheart.core.raid;

/**
 * When a raider that is not making progress is hiding, and what happens to it (owner, 2026-11-11). The only removal of a
 * raider that is allowed. A raider is stuck when it has made no progress toward the village for {@code stuckTicks} and no
 * villager, guard, golem or player is near it. It first glows. If it is still stuck {@code graceTicks} later it flees.
 * A raider that moves closer, fights, or has anyone near is never removed, and its timer starts again.
 */
public final class StuckRaiders {
	private StuckRaiders() {}

	/** {@code best} is the smallest distance to the village seen. {@code progressAt} is the tick of the last progress. {@code glowAt} is -1 until it glows. */
	public record Watch(double best, long progressAt, long glowAt) {
		public static Watch start(double distance, long now) { return new Watch(distance, now, -1); }

		public boolean glowing() { return glowAt >= 0; }
	}

	public enum Action { NONE, GLOW, CLEAR_GLOW, FLEE }

	public record Result(Watch watch, Action action) {}

	/**
	 * @param previous the watch from the last check, or null the first time this raider is seen
	 * @param distance blocks to the village now
	 * @param fighting the raider hurt something, was hurt, or is attacking
	 * @param helperNear a villager, guard, golem or player is within the helper range
	 * @param progressBlocks closer than the best distance by this much counts as progress
	 */
	public static Result step(Watch previous, double distance, boolean fighting, boolean helperNear, long now, int stuckTicks, int graceTicks,
			double progressBlocks) {
		if (previous == null) return new Result(Watch.start(distance, now), Action.NONE);
		boolean progress = distance < previous.best() - progressBlocks;
		if (progress || fighting || helperNear) {
			Watch fresh = new Watch(Math.min(previous.best(), distance), now, -1);
			return new Result(fresh, previous.glowing() ? Action.CLEAR_GLOW : Action.NONE);
		}
		if (!previous.glowing()) {
			if (now - previous.progressAt() >= stuckTicks) return new Result(new Watch(previous.best(), previous.progressAt(), now), Action.GLOW);
			return new Result(previous, Action.NONE);
		}
		if (now - previous.glowAt() >= graceTicks) return new Result(previous, Action.FLEE);
		return new Result(previous, Action.NONE);
	}
}
