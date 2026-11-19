package dev.villageheart.core.hunt;

import dev.villageheart.core.Balance;
import dev.villageheart.core.mine.AwayCheck;
import java.util.OptionalLong;

/** The hunter's decision at the Lodge (M17 plan 3.2 #13): the first row that matches wins. Pure; the glue gathers the inputs. */
public final class HunterPlanner {
	private HunterPlanner() {}

	public enum Action {
		/** Stand at the fletching table. */
		IDLE_AT_LODGE,
		/** A deposit trip takes the yield first. */
		YIELD,
		/** Too far from the ground for one walk: go to the fletching table first. */
		WALK_TO_LODGE,
		/** Walk to the ground marker and vanish into it. */
		GO_OUT
	}

	public enum StatusEffect { KEEP, CLEAR, NEEDS_TOOL, NO_WORK, SITE_UNREACHABLE, NEEDS_FLAG }

	public record Decision(Action action, StatusEffect status) {}

	/**
	 * @param hasSlot          the hunter still holds a Lodge slot
	 * @param carriesYield     it carries an item of the hunter yield set
	 * @param pendingNotEmpty  part of a yield is still waiting for room in its inventory
	 * @param lease            the weapon state
	 * @param groundValid      a VALID Hunting Ground is linked to the Lodge
	 * @param dayTime          day time in [0, 24000)
	 * @param progressUnits    saved progress for this ground
	 * @param multiplier       {@link HuntPace} now
	 * @param level            the worker level
	 * @param doneToday        trips completed today
	 * @param sinceUnreachable ticks since the ground last turned out unreachable, if it did
	 * @param distanceToMarker blocks from the hunter to the ground marker
	 */
	public record PlanInputs(boolean hasSlot, boolean carriesYield, boolean pendingNotEmpty, AwayCheck.Lease lease, boolean groundValid,
			long dayTime, double progressUnits, double multiplier, int level, int doneToday, OptionalLong sinceUnreachable,
			double distanceToMarker, double blocksThere, double blocksHome) {
		/** The walk clock is zero: a short walk leaves the day as it was, and a far hunter still stages at the lodge. */
		public PlanInputs(boolean hasSlot, boolean carriesYield, boolean pendingNotEmpty, AwayCheck.Lease lease, boolean groundValid,
				long dayTime, double progressUnits, double multiplier, int level, int doneToday, OptionalLong sinceUnreachable,
				double distanceToMarker) {
			this(hasSlot, carriesYield, pendingNotEmpty, lease, groundValid, dayTime, progressUnits, multiplier, level, doneToday,
					sinceUnreachable, distanceToMarker, 0, 0);
		}
	}

	public static int tripsPerDay(int level) {
		int idx = Math.max(0, Math.min(Balance.HUNTER_TRIPS_PER_DAY.length - 1, level - 1));
		return Balance.HUNTER_TRIPS_PER_DAY[idx];
	}

	/** The trip can end in the work phase with the margin (also the re-check just before the hunter vanishes). A walk of zero. */
	public static boolean fitsNow(long dayTime, double progressUnits, double multiplier) {
		return fitsNow(dayTime, progressUnits, multiplier, 0, 0);
	}

	/** The walk there, the time away, and the walk back all have to fit. */
	public static boolean fitsNow(long dayTime, double progressUnits, double multiplier, double blocksThere, double blocksHome) {
		return dev.villageheart.core.site.FlagDay.newHunt(dayTime, blocksThere, blocksHome, progressUnits, multiplier);
	}

	public static Decision decide(PlanInputs in) {
		if (!in.hasSlot()) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.CLEAR);
		if (in.carriesYield() || in.pendingNotEmpty()) return new Decision(Action.YIELD, StatusEffect.KEEP);
		if (in.lease() == AwayCheck.Lease.UNKNOWN) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.KEEP);
		if (in.lease() == AwayCheck.Lease.NONE) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.NEEDS_TOOL);
		if (!in.groundValid()) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.NEEDS_FLAG);
		if (in.sinceUnreachable().isPresent() && in.sinceUnreachable().getAsLong() < Balance.HUNTER_RETRY_TICKS) {
			return new Decision(Action.IDLE_AT_LODGE, StatusEffect.SITE_UNREACHABLE);
		}
		if (in.doneToday() >= tripsPerDay(in.level())) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.CLEAR);
		if (!fitsNow(in.dayTime(), in.progressUnits(), in.multiplier(), in.blocksThere(), in.blocksHome())) {
			return new Decision(Action.IDLE_AT_LODGE, StatusEffect.CLEAR);
		}
		// A hunter far from a flag that sits by the lodge walks to the lodge first. A flag that is itself far is the walk.
		if (in.distanceToMarker() > Balance.HUNTER_FAR_FROM_GROUND && in.blocksHome() <= Balance.HUNTER_FAR_FROM_GROUND) {
			return new Decision(Action.WALK_TO_LODGE, StatusEffect.CLEAR);
		}
		return new Decision(Action.GO_OUT, StatusEffect.CLEAR);
	}
}
