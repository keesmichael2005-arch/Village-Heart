package dev.villageheart.core.sim;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Scenario actions in run order: sorted by (day, tick), stable. Day-0 actions are setup and are never "due";
 * {@link #actionsDone()} counts every action that has been passed, setup included, so a resumed run can restore it.
 */
public final class ActionSchedule {
	private final List<ScenarioAction> sorted;
	private int index;

	public ActionSchedule(List<ScenarioAction> actions) {
		List<ScenarioAction> l = new ArrayList<>(actions);
		l.sort(Comparator.comparingInt(ScenarioAction::day).thenComparingInt(ScenarioAction::tick));
		this.sorted = l;
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
