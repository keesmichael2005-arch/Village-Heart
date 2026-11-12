package dev.villageheart.growth;

import dev.villageheart.core.growth.LapseReason;
import dev.villageheart.core.growth.Reason;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/** What the growth glue did, for tests and later for the balance run (like {@code FoodStats}). Server thread only. */
public final class GrowthStats {
	public record Dawn(long day, Reason reason, int freeBeds, double foodDays, int ready) {}

	private static final List<Dawn> DAWNS = new ArrayList<>();
	private static final List<LapseReason> LAPSES = new ArrayList<>();
	private static int births, unlicensedBirths, aborts, droppedBeds, sightRescues;
	private static Consumer<Dawn> listener;

	private GrowthStats() {}

	public static void reset() {
		DAWNS.clear();
		LAPSES.clear();
		births = 0;
		unlicensedBirths = 0;
		aborts = 0;
		droppedBeds = 0;
		sightRescues = 0;
		listener = null;
	}

	public static void setTestListener(Consumer<Dawn> l) { listener = l; }

	static void dawn(Dawn d) {
		DAWNS.add(d);
		if (listener != null) listener.accept(d);
	}

	static void birth() { births++; }

	static void unlicensedBirth() { unlicensedBirths++; }

	static void abort() { aborts++; }

	static void droppedBed() { droppedBeds++; }

	public static void sightRescue() { sightRescues++; }

	static void lapse(LapseReason r) { LAPSES.add(r); }

	public static List<Dawn> dawns() { return List.copyOf(DAWNS); }

	public static Dawn lastDawn() { return DAWNS.isEmpty() ? null : DAWNS.get(DAWNS.size() - 1); }

	public static List<LapseReason> lapses() { return List.copyOf(LAPSES); }

	/** Babies born with a licence. */
	public static int births() { return births; }

	/** Babies the make-love behaviour made without a licence: must stay 0 (the gate). */
	public static int unlicensedBirths() { return unlicensedBirths; }

	/** Times a licensed pair within birth distance was kept in the make-love run although vanilla's cached sight test said the partner is unseen. */
	public static int sightRescues() { return sightRescues; }

	/** Beds inside the claim that vanilla's make-love took for an unlicensed villager and the hook gave back. */
	public static int droppedBeds() { return droppedBeds; }

	/** Make-love runs of licensed pairs that ended without a baby. */
	public static int aborts() { return aborts; }
}
