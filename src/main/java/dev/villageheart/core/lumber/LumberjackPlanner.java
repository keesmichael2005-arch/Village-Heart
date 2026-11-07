package dev.villageheart.core.lumber;

import dev.villageheart.core.Balance;

/**
 * The lumberjack's decision from a snapshot of the world (DESIGN 5.1; the plan of M9, section 3.5). First matching row wins:
 * <ol>
 *   <li>camp invalid: idle at the camp</li>
 *   <li>a deposit trip is wanted: yield to it</li>
 *   <li>a fell is in progress (and the axe is there): carry on felling</li>
 *   <li>axe missing or unknown: only replanting and collecting may run, otherwise idle (status "Needs axe" when it is certain)</li>
 *   <li>a replant spot the worker can plant now</li>
 *   <li>an unclaimed felling task of this camp (a fell somebody had to leave)</li>
 *   <li>before {@link Balance#LUMBER_LAST_FELL}: a tree the finder has found</li>
 *   <li>a wanted drop to collect</li>
 *   <li>the finder still has columns to look at: search</li>
 *   <li>idle ("no trees" when the finder is done and nothing is left to do)</li>
 * </ol>
 */
public final class LumberjackPlanner {
	private LumberjackPlanner() {}

	public enum ToolState { PRESENT, NONE, UNKNOWN }

	/** EXHAUSTED_UNKNOWN: the sweep is done but it met unloaded cells, so "no trees" is not certain. */
	public enum FinderState { HAS_TREE, PAUSED, EXHAUSTED, EXHAUSTED_UNKNOWN }

	public enum Action { IDLE_AT_CAMP, YIELD, CONTINUE_FELL, REPLANT, COLLECT, FELL_TASK, FELL_NEW, SEARCH }

	/** What the planner asks the status to do. Statuses are sticky: KEEP leaves whatever is set. */
	public enum StatusChange { KEEP, CLEAR_ALL, CLEAR_NEEDS_TOOL, NEEDS_TOOL, NO_WORK }

	public record Snapshot(boolean campValid, boolean depositWanted, boolean fellInProgress, ToolState tool, boolean replantReady,
			boolean collectAvailable, boolean fellingTaskAvailable, long dayTime, FinderState finder) {}

	public record Decision(Action action, StatusChange status) {}

	public static Decision plan(Snapshot s) {
		if (!s.campValid()) return new Decision(Action.IDLE_AT_CAMP, StatusChange.KEEP);
		if (s.depositWanted()) return new Decision(Action.YIELD, StatusChange.KEEP);
		boolean tool = s.tool() == ToolState.PRESENT;
		if (tool && s.fellInProgress()) return new Decision(Action.CONTINUE_FELL, StatusChange.CLEAR_ALL);
		StatusChange base = tool ? StatusChange.CLEAR_NEEDS_TOOL : s.tool() == ToolState.NONE ? StatusChange.NEEDS_TOOL : StatusChange.KEEP;
		if (s.replantReady()) return new Decision(Action.REPLANT, base);
		// Trees come before drops while new fells are still allowed: the drops of a canopy keep coming for thousands of ticks, and a
		// worker that always had one to collect never started the next tree before the cutoff. Drops live 6000 ticks; the cutoff is
		// early enough to collect them afterwards.
		if (tool && s.fellingTaskAvailable()) return new Decision(Action.FELL_TASK, StatusChange.CLEAR_ALL);
		if (tool && s.dayTime() < Balance.LUMBER_LAST_FELL && s.finder() == FinderState.HAS_TREE) {
			return new Decision(Action.FELL_NEW, StatusChange.CLEAR_ALL);
		}
		if (s.collectAvailable()) return new Decision(Action.COLLECT, base);
		if (!tool) return new Decision(Action.IDLE_AT_CAMP, base);
		if (s.finder() == FinderState.PAUSED && s.dayTime() < Balance.LUMBER_LAST_FELL) return new Decision(Action.SEARCH, base);
		if (s.finder() == FinderState.EXHAUSTED) return new Decision(Action.IDLE_AT_CAMP, StatusChange.NO_WORK);
		return new Decision(Action.IDLE_AT_CAMP, base);
	}
}
