package dev.villageheart.siege;

import dev.villageheart.core.siege.SiegeHits;

/**
 * The day's siege counters (docs/plans/M25.md 3.17). Point-in-time figures (cells, the noon breach count's source, the
 * threat score) are read by the collector. These are the ones that add up between day records. Server thread only.
 */
public final class SiegeStats {
	private static int blocksBroken;
	private static int brokenZombie;
	private static int brokenRavager;
	private static int brokenVindicator;
	private static int brokenCreeper;
	private static int brokenOther;
	private static int repairs;
	private static int repairItems;
	private static int started;
	private static int zombies;
	private static int killed;
	private static int villagerDeaths;
	private static int paths;
	private static int maxLured;
	private static int breachedNoon;
	private static boolean noonTaken;

	private SiegeStats() {}

	public static void broken(SiegeHits.Attacker attacker) {
		blocksBroken++;
		switch (attacker) {
			case ZOMBIE, BABY_ZOMBIE, HUSK, ZOMBIE_VILLAGER -> brokenZombie++;
			case RAVAGER -> brokenRavager++;
			case VINDICATOR -> brokenVindicator++;
			default -> brokenOther++;
		}
	}

	/** A creeper blast removed a defence cell. */
	public static void brokenByCreeper() {
		blocksBroken++;
		brokenCreeper++;
	}

	public static void repaired(int items) {
		repairs++;
		if (items > 0) repairItems += items;
	}

	public static void siegeStarted() { started++; }

	public static void zombieSpawned() { zombies++; }

	public static void zombiesKilled(int n) { if (n > 0) killed += n; }

	public static void villagerDeath() { villagerDeaths++; }

	public static void path() { paths++; }

	public static void lure(int holding) { if (holding > maxLured) maxLured = holding; }

	/** The first look at or after day time 9000. Later looks the same day do not move it. */
	public static void noteNoon(int breached) {
		if (noonTaken) return;
		breachedNoon = breached;
		noonTaken = true;
	}

	public static Counts take() {
		Counts out = new Counts(blocksBroken, brokenZombie, brokenRavager, brokenVindicator, brokenCreeper, brokenOther,
				repairs, repairItems, started, zombies, killed, villagerDeaths, paths, maxLured, noonTaken ? breachedNoon : 0);
		blocksBroken = brokenZombie = brokenRavager = brokenVindicator = brokenCreeper = brokenOther = 0;
		repairs = repairItems = started = zombies = killed = villagerDeaths = paths = maxLured = breachedNoon = 0;
		noonTaken = false;
		return out;
	}

	public static void resetForTests() { take(); }

	public record Counts(int blocksBroken, int brokenZombie, int brokenRavager, int brokenVindicator, int brokenCreeper,
			int brokenOther, int repairs, int repairItems, int started, int zombies, int killed, int villagerDeaths, int paths,
			int maxLured, int breachedNoon) {}
}
