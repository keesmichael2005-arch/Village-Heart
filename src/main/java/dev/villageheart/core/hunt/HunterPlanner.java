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

	public enum StatusEffect { KEEP, CLEAR, NEEDS_TOOL, NO_WORK, SITE_UNREACHABLE }

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
			double distanceToMarker) {}

	public static int tripsPerDay(int level) {
		int idx = Math.max(0, Math.min(Balance.HUNTER_TRIPS_PER_DAY.length - 1, level - 1));
		return Balance.HUNTER_TRIPS_PER_DAY[idx];
	}

	/** The trip can end in the work phase with the margin (also the re-check just before the hunter vanishes). */
	public static boolean fitsNow(long dayTime, double progressUnits, double multiplier) {
		if (multiplier <= 0) return false;
		long t = Math.floorMod(dayTime, (long) Balance.DAY_TICKS);
		if (t < Balance.WORK_START) return false;
		double left = Math.max(0, Balance.HUNTER_TRIP_UNITS - progressUnits);
		long need = (long) Math.ceil(left / multiplier - 1e-9);
		return t + need + Balance.HUNTER_DEPART_MARGIN <= Balance.WORK_END;
	}

	public static Decision decide(PlanInputs in) {
		if (!in.hasSlot()) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.CLEAR);
		if (in.carriesYield() || in.pendingNotEmpty()) return new Decision(Action.YIELD, StatusEffect.KEEP);
		if (in.lease() == AwayCheck.Lease.UNKNOWN) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.KEEP);
		if (in.lease() == AwayCheck.Lease.NONE) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.NEEDS_TOOL);
		if (!in.groundValid()) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.NO_WORK);
		if (in.sinceUnreachable().isPresent() && in.sinceUnreachable().getAsLong() < Balance.HUNTER_RETRY_TICKS) {
			return new Decision(Action.IDLE_AT_LODGE, StatusEffect.SITE_UNREACHABLE);
		}
		if (in.doneToday() >= tripsPerDay(in.level())) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.CLEAR);
		if (!fitsNow(in.dayTime(), in.progressUnits(), in.multiplier())) return new Decision(Action.IDLE_AT_LODGE, StatusEffect.CLEAR);
		if (in.distanceToMarker() > Balance.HUNTER_FAR_FROM_GROUND) return new Decision(Action.WALK_TO_LODGE, StatusEffect.CLEAR);
		return new Decision(Action.GO_OUT, StatusEffect.CLEAR);
	}
}
