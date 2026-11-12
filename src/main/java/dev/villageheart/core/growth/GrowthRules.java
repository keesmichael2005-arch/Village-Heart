package dev.villageheart.core.growth;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Set;

/** The dawn decision of M23 (plan 3.2 #1-#4): may the village have a baby today, and who are the parents. Pure. */
public final class GrowthRules {
	private GrowthRules() {}

	/** An adult is ready when it is fed, housed and its breeding cooldown (vanilla age) is over. */
	public static boolean ready(boolean fed, boolean housed, int age) { return fed && housed && age == 0; }

	/**
	 * @param freeHouseBeds free beds of valid Houses
	 * @param foodDaysLeft the Overview's food days (empty with nobody)
	 * @param census the adult residents, ready or not
	 * @param skipPairs pairs that could not meet lately; skipped while another pair exists
	 */
	public static Decision decide(int freeHouseBeds, OptionalDouble foodDaysLeft, List<Candidate> census, Set<BreedingPair> skipPairs) {
		if (census.isEmpty()) return new Decision(Optional.empty(), Reason.NOBODY);
		if (freeHouseBeds <= 0) return new Decision(Optional.empty(), Reason.NO_FREE_BED);
		if (foodDaysLeft.isEmpty() || foodDaysLeft.getAsDouble() < Balance.GROWTH_MIN_FOOD_DAYS) return new Decision(Optional.empty(), Reason.LOW_FOOD);
		List<Candidate> ready = new ArrayList<>();
		for (Candidate c : census) if (c.ready()) ready.add(c);
		if (ready.size() < 2) return new Decision(Optional.empty(), Reason.TOO_FEW_READY);
		BreedingPair best = null, bestAny = null;
		double bestD = Double.MAX_VALUE, bestAnyD = Double.MAX_VALUE;
		for (int i = 0; i < ready.size(); i++) {
			for (int j = i + 1; j < ready.size(); j++) {
				Candidate x = ready.get(i), y = ready.get(j);
				if (x.id().equals(y.id())) continue;
				BreedingPair p = BreedingPair.of(x.id(), y.id());
				double d = x.distanceSq(y);
				if (better(p, d, bestAny, bestAnyD)) { bestAny = p; bestAnyD = d; }
				if (!skipPairs.contains(p) && better(p, d, best, bestD)) { best = p; bestD = d; }
			}
		}
		BreedingPair pick = best != null ? best : bestAny;
		if (pick == null) return new Decision(Optional.empty(), Reason.TOO_FEW_READY);
		return new Decision(Optional.of(pick), Reason.LICENSED);
	}

	private static final Comparator<BreedingPair> PAIR_ORDER = Comparator.comparing(BreedingPair::a).thenComparing(BreedingPair::b);

	private static boolean better(BreedingPair p, double d, BreedingPair cur, double curD) {
		if (cur == null || d < curD) return true;
		return d == curD && PAIR_ORDER.compare(p, cur) < 0;
	}

	/** Index of the bed nearest to the point (ties: the lower packed position); each entry is {packedPos, x, y, z}. Empty when there is none. */
	public static Optional<Integer> nearestBed(double mx, double my, double mz, List<long[]> beds) {
		int best = -1;
		double bestD = Double.MAX_VALUE;
		for (int i = 0; i < beds.size(); i++) {
			long[] b = beds.get(i);
			double dx = b[1] - mx, dy = b[2] - my, dz = b[3] - mz;
			double d = dx * dx + dy * dy + dz * dz;
			if (best < 0 || d < bestD || (d == bestD && b[0] < beds.get(best)[0])) { best = i; bestD = d; }
		}
		return best < 0 ? Optional.empty() : Optional.of(best);
	}
}
