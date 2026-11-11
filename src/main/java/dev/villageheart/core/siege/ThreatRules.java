package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.raid.RaidOrigin;

/**
 * When a raid or a siege may roll or start, and what a victory pays (docs/plans/M25.md 3.12, 3.13, 3.13a, 3.14).
 * A day count of {@link ThreatState#NO_DAY} means that thing has never happened. Spawn distance follows 3.12:
 * {@code max(outermost, 32) + 12}, clamped to {@code [32, CLAIM_RADIUS + 16]}.
 */
public final class ThreatRules {
	private ThreatRules() {}

	/** The world switches a roll reads. Peaceful, or a rule that is off, blocks that roll. */
	public record Gates(boolean peaceful, boolean randomRaids, boolean vanillaRaids, boolean sieges, boolean mobSpawning) {
		public static final Gates OPEN = new Gates(false, true, true, true, true);
	}

	public static double raidChance(double threat) { return Math.min(0.25, 0.02 + 0.03 * threat); }

	public static double siegeChance(double threat) { return Math.min(0.20, 0.04 + 0.02 * threat); }

	/** A test asks for the next roll to succeed. It stays set until a roll is actually allowed. */
	private static boolean forceRaid;
	/** A test shortens the grace and the gap. Null uses {@link Balance}. */
	private static Integer graceForTest;
	private static Integer gapForTest;

	/** The next allowed raid roll succeeds, whatever the seeded chance was. */
	public static void forceRoll() { forceRaid = true; }

	public static boolean raidForced() { return forceRaid; }

	public static void clearRaidForce() { forceRaid = false; }

	/** Grace and gap of 0 let a brand-new village roll. Cleared with {@link #clearForTest()}. */
	public static void graceAndGapForTest(int grace, int gap) {
		graceForTest = grace;
		gapForTest = gap;
	}

	public static void clearForTest() {
		forceRaid = false;
		graceForTest = null;
		gapForTest = null;
	}

	public static boolean mayRollRaid(Tier tier, int ageDays, int daysSinceRaid, boolean tracked, boolean pending, Gates gates) {
		int grace = graceForTest == null ? Balance.RAID_GRACE_DAYS : graceForTest;
		int gap = gapForTest == null ? Balance.RAID_GAP_DAYS : gapForTest;
		if (tier == Tier.HAMLET || ageDays < grace) return false;
		if (daysSinceRaid != ThreatState.NO_DAY && daysSinceRaid < gap) return false;
		if (tracked || pending || gates.peaceful() || !gates.randomRaids() || !gates.vanillaRaids()) return false;
		return true;
	}

	public static boolean mayRollSiege(Tier tier, int ageDays, int daysSinceSiege, int daysSinceRaid, boolean tracked, boolean pending, Gates gates) {
		if (tier == Tier.HAMLET || ageDays < Balance.SIEGE_GRACE_DAYS) return false;
		if (daysSinceSiege != ThreatState.NO_DAY && daysSinceSiege < Balance.SIEGE_GAP_DAYS) return false;
		if (daysSinceRaid != ThreatState.NO_DAY && daysSinceRaid < Balance.THREAT_REST_DAYS) return false;
		if (tracked || pending || gates.peaceful() || !gates.sieges() || !gates.mobSpawning()) return false;
		return true;
	}

	/** A player inside 96 adds one tick. Anyone leaving, or a gap, resets the streak. */
	public static int loaded(int streak, boolean playerWithin96) { return playerWithin96 ? streak + 1 : 0; }

	public static boolean loadedEnough(int streak) { return streak >= Balance.RAID_LOADED_TICKS; }

	/**
	 * A pending raid starts inside today's {@code [startTick, 11000]} once a player has been close for 600 ticks,
	 * two days have passed since a siege, and no other raid is within 96 blocks.
	 */
	public static boolean mayStart(int dayTime, int startTick, int loadedTicks, boolean raidWithin96, int daysSinceSiege) {
		if (raidWithin96 || !loadedEnough(loadedTicks)) return false;
		if (daysSinceSiege != ThreatState.NO_DAY && daysSinceSiege < Balance.THREAT_REST_DAYS) return false;
		return dayTime >= startTick && dayTime <= Balance.PENDING_START_MAX;
	}

	/** Random victories only, and at most one inside {@link Balance#RAID_GAP_DAYS}. The result is capped at 40. */
	public static int reward(RaidOrigin origin, int wavesSpawned, int omen, int extraRaidersKilled, int day, int lastRewardDay) {
		if (origin != RaidOrigin.RANDOM) return 0;
		if (lastRewardDay != ThreatState.NO_DAY && day - lastRewardDay < Balance.RAID_GAP_DAYS) return 0;
		int n = 4 + 2 * wavesSpawned + 3 * Math.max(0, omen - 1) + extraRaidersKilled;
		return Math.min(Balance.REWARD_CAP, Math.max(0, n));
	}

	/** Blocks from the Heart to a siege spawn. Outermost 10 is 44, not 32: the floor is applied before the pad. */
	public static int spawnDistance(int outermostCell) {
		int raw = Math.max(outermostCell, Balance.SIEGE_SPAWN_MIN) + Balance.SIEGE_SPAWN_PAD;
		int cap = Balance.CLAIM_RADIUS + 16;
		return Math.min(cap, Math.max(Balance.SIEGE_SPAWN_MIN, raw));
	}
}
