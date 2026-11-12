package dev.villageheart.core.growth;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.Set;

/** What the village remembers about growth (M23 plan 3.7): today's licence, the last birth, a counter, and pairs that could not meet. */
public record GrowthState(Optional<License> licence, OptionalLong lastBirthDay, long birthsTotal, List<FailedPair> failedPairs) {
	public GrowthState {
		failedPairs = List.copyOf(failedPairs);
		if (birthsTotal < 0) throw new IllegalArgumentException("birthsTotal");
	}

	public static final GrowthState EMPTY = new GrowthState(Optional.empty(), OptionalLong.empty(), 0, List.of());

	public GrowthState withLicence(Optional<License> l) { return new GrowthState(l, lastBirthDay, birthsTotal, failedPairs); }

	public GrowthState withLicence(License l) { return withLicence(Optional.of(l)); }

	/** A birth on {@code day}: the licence moves to BORN (done by the caller) and the counters follow. */
	public GrowthState afterBirth(long day, License born) {
		return new GrowthState(Optional.of(born), OptionalLong.of(day), birthsTotal + 1, failedPairs);
	}

	public GrowthState withFailed(BreedingPair p, long day) {
		List<FailedPair> l = new ArrayList<>(failedPairs);
		l.removeIf(f -> f.pair().equals(p));
		l.add(new FailedPair(p, day));
		return new GrowthState(licence, lastBirthDay, birthsTotal, l);
	}

	/** Drops failures older than {@link Balance#FAILED_PAIR_DAYS} days (kept at exactly that age). */
	public GrowthState pruned(long today) {
		List<FailedPair> l = new ArrayList<>();
		for (FailedPair f : failedPairs) if (today - f.day() <= Balance.FAILED_PAIR_DAYS) l.add(f);
		return l.size() == failedPairs.size() ? this : new GrowthState(licence, lastBirthDay, birthsTotal, l);
	}

	public Set<BreedingPair> skipPairs() {
		Set<BreedingPair> s = new HashSet<>();
		for (FailedPair f : failedPairs) s.add(f.pair());
		return s;
	}
}
