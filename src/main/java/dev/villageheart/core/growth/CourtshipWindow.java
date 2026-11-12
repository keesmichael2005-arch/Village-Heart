package dev.villageheart.core.growth;

import dev.villageheart.core.Balance;

/**
 * When a licensed pair may be seeded (plan 3.2 #5): both must be in their IDLE activity for a whole make-love run. Windows are the day
 * phases (ticks into the day) in which a villager of that kind runs IDLE. An activity whose memory is missing falls back to IDLE, so a
 * village without a bell has wider windows.
 */
public final class CourtshipWindow {
	private CourtshipWindow() {}

	/** Half-open [start, end) IDLE windows of a kind. */
	static int[][] windows(Kind k) {
		return switch (k.role()) {
			case WORKER -> new int[][] {{0, Balance.WORK_START}};
			case UNEMPLOYED -> k.hasMeetingPoint() ? new int[][] {{10, 9000}, {11000, 12000}} : new int[][] {{10, 12000}};
			case PROFESSIONAL -> k.hasMeetingPoint() ? new int[][] {{10, 2000}, {11000, 12000}} : new int[][] {{10, 2000}, {9000, 12000}};
		};
	}

	/** True when {@code phase} and {@code phase + COURTSHIP_MARGIN} lie in one IDLE window of this kind. */
	public static boolean fits(int phase, Kind k) {
		for (int[] w : windows(k)) if (phase >= w[0] && phase + Balance.COURTSHIP_MARGIN < w[1]) return true;
		return false;
	}

	public static boolean canSeed(int phase, Kind a, Kind b) { return fits(phase, a) && fits(phase, b); }

	/** True when {@code phase} is inside an IDLE window of this kind. */
	public static boolean idleAt(int phase, Kind k) {
		for (int[] w : windows(k)) if (phase >= w[0] && phase < w[1]) return true;
		return false;
	}

	/** True when this phase or a later one of the same day can still seed (the day's last seed window has not closed for this pair). */
	public static boolean canStillSeedToday(int phase, Kind a, Kind b) {
		for (int p = Math.max(phase, 0); p < Balance.DAY_TICKS; p++) if (canSeed(p, a, b)) return true;
		return false;
	}
}
