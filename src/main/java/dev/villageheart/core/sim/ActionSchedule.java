package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

/**
 * Scenario actions in run order: sorted by (day, tick), stable. Day-0 actions are setup and are never "due";
 * {@link #actionsDone()} counts every action that has been passed, setup included, so a resumed run can restore it.
 */
public final class ActionSchedule {
	private final List<ScenarioAction> sorted;
	/** Standing orders (a "when" condition), in (day, tick) order. Each runs once, when it is due and its condition holds. */
	private final List<ScenarioAction> pending;
	private int index;

	public ActionSchedule(List<ScenarioAction> actions) {
		List<ScenarioAction> l = new ArrayList<>();
		List<ScenarioAction> c = new ArrayList<>();
		for (ScenarioAction a : actions) (a.when() != null ? c : l).add(a);
		Comparator<ScenarioAction> order = Comparator.comparingInt(ScenarioAction::day).thenComparingInt(ScenarioAction::tick);
		l.sort(order);
		c.sort(order);
		this.sorted = l;
		this.pending = c;
	}

	/** Puts a standing order back (it could not run yet, for example its building is still being re-checked after a tier-up). */
	public void retry(ScenarioAction a) {
		if (a.when() == null) throw new IllegalArgumentException("only a standing order can be retried");
		pending.add(0, a);
	}

	/** Standing orders not yet run. */
	public int pendingConditional() {
		return pending.size();
	}

	/** The unconditional due actions as {@link #due(long)}, then each standing order that is due and whose condition holds (once, in order). */
	public List<ScenarioAction> due(long elapsedTicks, Predicate<String> conditionHolds) {
		List<ScenarioAction> out = due(elapsedTicks);
		for (java.util.Iterator<ScenarioAction> it = pending.iterator(); it.hasNext();) {
			ScenarioAction a = it.next();
			if (a.dueTick() <= elapsedTicks && conditionHolds.test(a.when())) {
				out.add(a);
				it.remove();
			}
		}
		return out;
	}

	public List<ScenarioAction> setupActions() {
		List<ScenarioAction> out = new ArrayList<>();
		for (ScenarioAction a : sorted) if (a.day() == 0) out.add(a);
		return out;
	}

	/** Called by the harness once the setup actions have run. */
	public void markSetupDone() {
		skipSetup();
	}

	private void skipSetup() {
		while (index < sorted.size() && sorted.get(index).day() == 0) index++;
	}

	/** Every not-yet-returned action with dueTick <= elapsed (ticks since the baseline), in order, each exactly once. */
	public List<ScenarioAction> due(long elapsedTicks) {
		skipSetup();
		List<ScenarioAction> out = new ArrayList<>();
		while (index < sorted.size() && sorted.get(index).dueTick() <= elapsedTicks) out.add(sorted.get(index++));
		return out;
	}

	public int actionsDone() {
		return index;
	}

	public void restore(int actionsDone) {
		if (actionsDone < 0 || actionsDone > sorted.size()) throw new IllegalArgumentException("actions_done out of range: " + actionsDone);
		index = actionsDone;
	}
}
