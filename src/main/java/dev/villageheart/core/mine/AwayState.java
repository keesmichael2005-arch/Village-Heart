package dev.villageheart.core.mine;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.room.Pos;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What a worker that goes away to its site (the miner, later the hunter) keeps on its entity, apart from its worker data so that firing
 * cannot lose what is needed to restore the villager's flags. Pure data; the codec is in {@code world.AwayCodecs}.
 *
 * @param trip          set while the villager is hidden in the site
 * @param progress      underground work progress towards the next haul
 * @param progressShaft the shaft the progress belongs to (progress is reset when the miner goes down another one)
 * @param rolls         hauls rolled so far: the index of the next haul's seeded random source
 * @param pending       what did not fit into the inventory yet (item id to count)
 * @param lastReason    why the villager came up the last time (tests and logs)
 * @param unknownSteps  consecutive underground steps spent waiting for an unreadable chest or an unknown depth
 * @param broken        the saved trip could not be read (decode only, never saved): the flags are repaired to the vanilla defaults
 * @param doneDay       the day (day time / 24000) {@code doneToday} was counted on, -1 = never (the hunter's daily trip cap, M17)
 * @param doneToday     trips completed on {@code doneDay}
 */
public record AwayState(Optional<AwayTrip> trip, AwayProgress progress, Optional<HeartLocation> progressShaft, long rolls,
		Map<String, Integer> pending, Optional<SurfaceReason> lastReason, int unknownSteps, boolean broken, long doneDay, int doneToday) {
	public static final AwayState NONE = new AwayState(Optional.empty(), AwayProgress.ZERO, Optional.empty(), 0, Map.of(), Optional.empty(), 0, false);

	/** The M16 shape: no trip counter. */
	public AwayState(Optional<AwayTrip> trip, AwayProgress progress, Optional<HeartLocation> progressShaft, long rolls,
			Map<String, Integer> pending, Optional<SurfaceReason> lastReason, int unknownSteps, boolean broken) {
		this(trip, progress, progressShaft, rolls, pending, lastReason, unknownSteps, broken, -1L, 0);
	}

	public AwayState {
		Objects.requireNonNull(trip, "trip");
		Objects.requireNonNull(progress, "progress");
		Objects.requireNonNull(progressShaft, "progressShaft");
		Objects.requireNonNull(lastReason, "lastReason");
		if (rolls < 0) throw new IllegalArgumentException("rolls must be >= 0: " + rolls);
		if (unknownSteps < 0) throw new IllegalArgumentException("unknownSteps must be >= 0: " + unknownSteps);
		if (doneToday < 0) throw new IllegalArgumentException("doneToday must be >= 0: " + doneToday);
		pending = Map.copyOf(new LinkedHashMap<>(pending));
	}

	public AwayState withTrip(Optional<AwayTrip> t) {
		return new AwayState(t, progress, progressShaft, rolls, pending, lastReason, unknownSteps, false, doneDay, doneToday);
	}

	public AwayState withProgress(AwayProgress p, Optional<HeartLocation> shaft) {
		return new AwayState(trip, p, shaft, rolls, pending, lastReason, unknownSteps, broken, doneDay, doneToday);
	}

	public AwayState withRolls(long r) {
		return new AwayState(trip, progress, progressShaft, r, pending, lastReason, unknownSteps, broken, doneDay, doneToday);
	}

	public AwayState withPending(Map<String, Integer> p) {
		return new AwayState(trip, progress, progressShaft, rolls, p, lastReason, unknownSteps, broken, doneDay, doneToday);
	}

	public AwayState withLastReason(Optional<SurfaceReason> r) {
		return new AwayState(trip, progress, progressShaft, rolls, pending, r, unknownSteps, broken, doneDay, doneToday);
	}

	public AwayState withUnknownSteps(int n) {
		return new AwayState(trip, progress, progressShaft, rolls, pending, lastReason, n, broken, doneDay, doneToday);
	}

	/** The completed-trips counter (the hunter's daily cap). */
	public AwayState withDone(long day, int n) {
		return new AwayState(trip, progress, progressShaft, rolls, pending, lastReason, unknownSteps, broken, day, n);
	}

	public AwayState repaired() {
		return new AwayState(trip, progress, progressShaft, rolls, pending, lastReason, unknownSteps, false, doneDay, doneToday);
	}

	/**
	 * The villager's flags before it was hidden.
	 */
	public record PriorFlags(boolean invisible, boolean invulnerable, boolean silent, boolean noAi, boolean customNameVisible) {
		/** The vanilla defaults: used when the saved prior values are lost. */
		public static final PriorFlags VANILLA = new PriorFlags(false, false, false, false, false);
	}

	/**
	 * @param jobId   the job whose trip it is ("miner")
	 * @param site    the site's marker (the Mineshaft)
	 * @param surface where the villager stood when it went down: where it comes up
	 * @param hold    where it is held while hidden
	 * @param since   game time of the descent
	 */
	public record AwayTrip(String jobId, HeartLocation site, Pos surface, Pos hold, long since, PriorFlags prior) {
		public AwayTrip {
			Objects.requireNonNull(jobId, "jobId");
			Objects.requireNonNull(site, "site");
			Objects.requireNonNull(surface, "surface");
			Objects.requireNonNull(hold, "hold");
			Objects.requireNonNull(prior, "prior");
		}
	}
}
