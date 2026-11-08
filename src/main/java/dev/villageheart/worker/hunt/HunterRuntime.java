package dev.villageheart.worker.hunt;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.hunt.MeatWeights;
import java.util.HashMap;
import java.util.Map;
import org.jetbrains.annotations.Nullable;

/**
 * Transient hunter state: the meat weights per Hunting Ground (only complete reads are cached; dropped whenever the ground is
 * re-checked, forgotten with the building runtime). Not saved: recomputed from the same biomes after a restart.
 */
public final class HunterRuntime {
	private static final Map<HeartLocation, MeatWeights> WEIGHTS = new HashMap<>();

	private HunterRuntime() {}

	@Nullable
	static MeatWeights weights(HeartLocation ground) { return WEIGHTS.get(ground); }

	static void cacheWeights(HeartLocation ground, MeatWeights w) { WEIGHTS.put(ground, w); }

	/** The ground was re-checked (or forgotten): read its biomes again next time. */
	public static void dropWeights(HeartLocation ground) { WEIGHTS.remove(ground); }

	public static void resetForTests() {
		WEIGHTS.clear();
		HunterJob.clearForTests();
	}
}
