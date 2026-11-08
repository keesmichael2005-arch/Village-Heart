package dev.villageheart.core.farm;

/**
 * Deterministic composting (M10 plan 3.2 #7). Vanilla's composter fills on the first item and then with chance 0.3 per seed (mean
 * 1 + 6 / 0.3 = 21 seeds per bone meal); the mod keeps that mean without a random number: the first seed raises the level to 1, every
 * further seed adds 3 tenths and every 10 tenths raise one level. Level 7 is full (the block turns READY, 8, by itself); the farmer takes
 * the bone meal at READY and the level drops to 0.
 */
public final class CompostRule {
	private CompostRule() {}

	public static final int SEEDS_PER_BONE_MEAL = 21;
	public static final int FULL = 7;
	public static final int READY = 8;

	/** @param accepted false when the composter is full (7) or ready (8): level and tenths are unchanged */
	public record Result(boolean accepted, int level, int tenths) {}

	public static Result insert(int level, int tenths) {
		if (level >= FULL) return new Result(false, level, tenths);
		if (level <= 0) return new Result(true, 1, 0);
		int t = tenths + 3;
		int l = level;
		if (t >= 10) {
			t -= 10;
			l++;
		}
		return new Result(true, l, t);
	}
}
