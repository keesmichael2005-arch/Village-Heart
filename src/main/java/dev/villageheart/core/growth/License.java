package dev.villageheart.core.growth;

import dev.villageheart.core.Balance;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

/**
 * Today's permission for one pair to have a baby (M23 plan 3.2 #5-#6). Immutable; every transition is validated, nothing leaves BORN or
 * LAPSED. The licence holds a POI ticket of a House bed from the dawn it is issued ({@code bed}) until the make-love behaviour takes it
 * ({@code handedOver}: from then on vanilla owns the ticket, and the licence never releases it).
 */
public record License(long day, BreedingPair pair, LicenceState state, int seeds, int aborts, OptionalLong bed, boolean handedOver,
		int priorFoodA, int priorFoodB, long approachTicks, Optional<UUID> child, Optional<LapseReason> lapse) {
	public License {
		Objects.requireNonNull(pair, "pair");
		Objects.requireNonNull(state, "state");
		Objects.requireNonNull(bed, "bed");
		Objects.requireNonNull(child, "child");
		Objects.requireNonNull(lapse, "lapse");
		if (seeds < 0 || aborts < 0 || approachTicks < 0) throw new IllegalArgumentException("negative counter");
		if (state == LicenceState.BORN && child.isEmpty()) throw new IllegalArgumentException("a BORN licence names the child");
		if (state != LicenceState.BORN && child.isPresent()) throw new IllegalArgumentException("only a BORN licence has a child");
		if ((state == LicenceState.LAPSED) != lapse.isPresent()) throw new IllegalArgumentException("a lapse reason exactly when LAPSED");
		if (handedOver && bed.isEmpty()) throw new IllegalArgumentException("handed over without a bed");
	}

	/** A fresh licence, WAITING, holding {@code bed} (empty when none could be reserved: the courtship then lapses NO_BED). */
	public static License issue(long day, BreedingPair pair, OptionalLong bed) {
		return new License(day, pair, LicenceState.WAITING, 0, 0, bed, false, 0, 0, 0, Optional.empty(), Optional.empty());
	}

	public boolean active() { return state != LicenceState.BORN && state != LicenceState.LAPSED; }

	/** True while the mod itself holds a bed ticket it has to give back on a lapse. */
	public boolean holdsBed() { return active() && bed.isPresent() && !handedOver; }

	public boolean mayReseed() { return active() && seeds < Balance.COURTSHIP_MAX_SEEDS; }

	private License with(LicenceState s, int seeds, int aborts, OptionalLong bed, boolean handed, int fa, int fb, long ticks, Optional<UUID> child,
			Optional<LapseReason> lapse) {
		return new License(day, pair, s, seeds, aborts, bed, handed, fa, fb, ticks, child, lapse);
	}

	private void requireActive(String what) {
		if (!active()) throw new IllegalStateException(what + " on a " + state + " licence");
	}

	public License approaching() {
		requireActive("approach");
		if (state == LicenceState.SEEDED) throw new IllegalStateException("approach on a SEEDED licence");
		return with(LicenceState.APPROACHING, seeds, aborts, bed, handedOver, priorFoodA, priorFoodB, approachTicks, child, lapse);
	}

	/** One tick of approach time (window time spent walking towards each other). */
	public License tickApproach() {
		requireActive("tick");
		return with(state, seeds, aborts, bed, handedOver, priorFoodA, priorFoodB, approachTicks + 1, child, lapse);
	}

	/** Seeds the initiator; only from WAITING or APPROACHING and only with a bed. The first seed remembers the parents' food level. */
	public License seeded(long bedPos, int foodA, int foodB) {
		if (state != LicenceState.WAITING && state != LicenceState.APPROACHING) throw new IllegalStateException("seed on a " + state + " licence");
		if (seeds >= Balance.COURTSHIP_MAX_SEEDS) throw new IllegalStateException("too many seeds");
		boolean first = seeds == 0;
		return with(LicenceState.SEEDED, seeds + 1, aborts, OptionalLong.of(bedPos), false, first ? foodA : priorFoodA, first ? foodB : priorFoodB,
				approachTicks, child, lapse);
	}

	/** Vanilla's make-love took the reserved bed: the ticket is vanilla's from now on. */
	public License handOver() {
		if (state != LicenceState.SEEDED || bed.isEmpty()) throw new IllegalStateException("hand-over needs a SEEDED licence with a bed");
		return with(state, seeds, aborts, bed, true, priorFoodA, priorFoodB, approachTicks, child, lapse);
	}

	/**
	 * The make-love run ended without a birth. Back to APPROACHING. A bed already handed over is gone (vanilla released it, or it was
	 * broken), so the licence holds none any more and the courtship reserves another.
	 */
	public License aborted() {
		if (state != LicenceState.SEEDED) throw new IllegalStateException("abort on a " + state + " licence");
		return with(LicenceState.APPROACHING, seeds, aborts + 1, handedOver ? OptionalLong.empty() : bed, false, priorFoodA, priorFoodB, 0, child, lapse);
	}

	/** A bed reserved again after the old one was lost. */
	public License withBed(long bedPos) {
		requireActive("re-reserve");
		return with(state, seeds, aborts, OptionalLong.of(bedPos), false, priorFoodA, priorFoodB, approachTicks, child, lapse);
	}

	/** The baby is there: only from SEEDED. The bed is the child's now. */
	public License born(UUID childId) {
		if (state != LicenceState.SEEDED) throw new IllegalStateException("birth on a " + state + " licence");
		return with(LicenceState.BORN, seeds, aborts, bed, true, priorFoodA, priorFoodB, approachTicks, Optional.of(childId), lapse);
	}

	public License lapsed(LapseReason reason) {
		requireActive("lapse");
		return with(LicenceState.LAPSED, seeds, aborts, bed, handedOver, priorFoodA, priorFoodB, approachTicks, child, Optional.of(reason));
	}

	/** A licence for another day than {@code today} is stale. */
	public boolean staleOn(long today) { return day != today; }
}
