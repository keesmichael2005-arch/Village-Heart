package dev.villageheart.core.guard;

/**
 * What a guard remembers across a reload (M18): whether it is wounded (the {@link WoundRule} bit), the day time (world clock) its one training day ends
 * ({@code 0}: it is qualified, see {@link GuardTraining}) and the food ration it carries (nutrition points, see {@link GuardRation}).
 */
public record GuardData(boolean wounded, long trainingUntil, int ration) {
	public static final GuardData FRESH = new GuardData(false, 0, 0);

	public GuardData {
		if (trainingUntil < 0) throw new IllegalArgumentException("trainingUntil must be >= 0: " + trainingUntil);
		if (ration < 0) throw new IllegalArgumentException("ration must be >= 0: " + ration);
	}

	public GuardData withWounded(boolean w) { return new GuardData(w, trainingUntil, ration); }

	public GuardData withTrainingUntil(long t) { return new GuardData(wounded, t, ration); }

	public GuardData withRation(int r) { return new GuardData(wounded, trainingUntil, r); }

	/** True while the guard is still in its training day at game time {@code now}. */
	public boolean training(long now) { return GuardTraining.training(now, trainingUntil); }
}
