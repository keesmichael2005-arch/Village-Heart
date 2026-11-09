package dev.villageheart.core.farm;

import dev.villageheart.core.Balance;

/**
 * The farmer's decision from a snapshot of the world (M10 plan 3.4). First matching row wins:
 * <ol>
 *   <li>workplace invalid: idle</li>
 *   <li>a deposit trip is wanted (and Storage is not known to be full or out of reach): yield to it</li>
 *   <li>the worker cannot work (work speed 0: too hungry, or unpaid, M11): idle, whatever it was doing</li>
 *   <li>an action is in progress and its target is still valid: carry on</li>
 *   <li>few free slots and surplus seeds: compost before harvesting more</li>
 *   <li>Storage not full and a mature crop: harvest</li>
 *   <li>a wanted item to collect</li>
 *   <li>empty farmland and a seed (carried, or in the Farmhouse chest): plant</li>
 *   <li>composter ready, or a batch of surplus seeds: compost</li>
 *   <li>bone meal carried and a young crop: bone meal</li>
 *   <li>any surplus seeds: compost</li>
 *   <li>idle ("no farmland" only when a complete sweep found none)</li>
 * </ol>
 */
public final class FarmerPlanner {
	private FarmerPlanner() {}

	public enum Action { IDLE_AT_FARMHOUSE, YIELD, CONTINUE, COMPOST, HARVEST, COLLECT, PLANT, FETCH, BONEMEAL }

	/** Statuses are sticky: KEEP leaves whatever is set. */
	public enum StatusChange { KEEP, CLEAR, NO_WORK }

	public record Snapshot(boolean workplaceValid, boolean depositWanted, boolean storageBlocked, boolean inProgress, int freeSlots,
			int surplusSeeds, boolean matureAvailable, boolean collectAvailable, boolean plantAvailable, boolean plantNeedsFetch,
			boolean composterReady, boolean boneMealTarget, boolean sweepComplete, boolean noFarmland, boolean cannotWork) {
		/** A worker that can work. */
		public Snapshot(boolean workplaceValid, boolean depositWanted, boolean storageBlocked, boolean inProgress, int freeSlots,
				int surplusSeeds, boolean matureAvailable, boolean collectAvailable, boolean plantAvailable, boolean plantNeedsFetch,
				boolean composterReady, boolean boneMealTarget, boolean sweepComplete, boolean noFarmland) {
			this(workplaceValid, depositWanted, storageBlocked, inProgress, freeSlots, surplusSeeds, matureAvailable, collectAvailable,
					plantAvailable, plantNeedsFetch, composterReady, boneMealTarget, sweepComplete, noFarmland, false);
		}
	}

	public record Decision(Action action, StatusChange status) {}

	public static Decision decide(Snapshot s) {
		if (!s.workplaceValid()) return new Decision(Action.IDLE_AT_FARMHOUSE, StatusChange.KEEP);
		if (s.depositWanted() && !s.storageBlocked()) return new Decision(Action.YIELD, StatusChange.KEEP);
		if (s.cannotWork()) return new Decision(Action.IDLE_AT_FARMHOUSE, StatusChange.KEEP);
		if (s.inProgress()) return new Decision(Action.CONTINUE, StatusChange.CLEAR);
		if (s.freeSlots() <= Balance.SLOT_PRESSURE_FREE_SLOTS && s.surplusSeeds() > 0) return new Decision(Action.COMPOST, StatusChange.CLEAR);
		if (!s.storageBlocked() && s.matureAvailable()) return new Decision(Action.HARVEST, StatusChange.CLEAR);
		if (s.collectAvailable()) return new Decision(Action.COLLECT, StatusChange.CLEAR);
		if (s.plantAvailable()) return new Decision(s.plantNeedsFetch() ? Action.FETCH : Action.PLANT, StatusChange.CLEAR);
		if (s.composterReady() || s.surplusSeeds() >= Balance.COMPOST_BATCH) return new Decision(Action.COMPOST, StatusChange.CLEAR);
		if (s.boneMealTarget()) return new Decision(Action.BONEMEAL, StatusChange.CLEAR);
		if (s.surplusSeeds() > 0) return new Decision(Action.COMPOST, StatusChange.CLEAR);
		if (s.sweepComplete() && s.noFarmland()) return new Decision(Action.IDLE_AT_FARMHOUSE, StatusChange.NO_WORK);
		return new Decision(Action.IDLE_AT_FARMHOUSE, s.sweepComplete() ? StatusChange.CLEAR : StatusChange.KEEP);
	}
}
