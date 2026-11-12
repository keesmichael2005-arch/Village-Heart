package dev.villageheart.core.siege;

import dev.villageheart.core.Balance;

/**
 * One roll when the day clock crosses a mark (docs/plans/M25.md 3.12). Sleeping through a window rolls once, late.
 * Setting the time backward rolls nothing. A saved day in the future is forgotten. An unspawned siege is cleared
 * once the day moves past the day it was rolled.
 */
public record ThreatClock(int day, int dayTime, int lastRaidRollDay, int lastSiegeRollDay, boolean siegeSpawned) {
	public static ThreatClock at(long gameTime) {
		return new ThreatClock(ThreatRoll.day(gameTime), ThreatRoll.dayTime(gameTime), ThreatState.NO_DAY, ThreatState.NO_DAY, false);
	}

	public ThreatClock withSiegeSpawned(boolean spawned) {
		return new ThreatClock(day, dayTime, lastRaidRollDay, lastSiegeRollDay, spawned);
	}

	public record Step(ThreatClock clock, boolean raidRoll, boolean siegeRoll, boolean clearUnspawnedSiege) {}

	public Step advance(long gameTime) {
		int nextDay = ThreatRoll.day(gameTime);
		int nextTime = ThreatRoll.dayTime(gameTime);
		int raidMark = future(lastRaidRollDay, day, nextDay) ? ThreatState.NO_DAY : lastRaidRollDay;
		int siegeMark = future(lastSiegeRollDay, day, nextDay) ? ThreatState.NO_DAY : lastSiegeRollDay;
		boolean raid = crossed(day, dayTime, nextDay, nextTime, Balance.RAID_ROLL_TICK) && raidMark != nextDay;
		boolean siege = crossed(day, dayTime, nextDay, nextTime, Balance.SIEGE_ROLL_TICK) && siegeMark != nextDay;
		boolean clear = !siegeSpawned && siegeMark >= 0 && nextDay > siegeMark;
		int keptRaid = raid ? nextDay : raidMark;
		int keptSiege = siege ? nextDay : (clear ? ThreatState.NO_DAY : siegeMark);
		boolean spawned = siege ? false : siegeSpawned && !clear;
		return new Step(new ThreatClock(nextDay, nextTime, keptRaid, keptSiege, spawned), raid, siege, clear);
	}

	/** A roll day saved ahead of either sample is forgotten, so arriving on that day can still roll. */
	private static boolean future(int saved, int fromDay, int toDay) { return saved > fromDay || saved > toDay; }

	/** True when some day's mark lies strictly after the previous sample and at or before this one. */
	static boolean crossed(int fromDay, int fromTime, int toDay, int toTime, int mark) {
		if (toDay < fromDay || (toDay == fromDay && toTime <= fromTime)) return false;
		int first = fromTime < mark ? fromDay : fromDay + 1;
		int last = toTime >= mark ? toDay : toDay - 1;
		return first <= last;
	}
}
