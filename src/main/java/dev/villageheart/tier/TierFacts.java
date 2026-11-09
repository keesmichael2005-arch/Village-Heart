package dev.villageheart.tier;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.tier.Population;
import dev.villageheart.core.tier.TierInputs;
import dev.villageheart.core.worker.JobType;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/** Reads the live facts the tier goals need (M14). Server thread. */
public final class TierFacts {
	private TierFacts() {}

	/** {@code censusIds}: the loaded villagers in the claim (the caller already scanned them). */
	public static TierInputs inputs(MinecraftServer server, VillageState village, Set<UUID> censusIds) {
		int guards = 0;
		for (var w : village.workers()) if (w.jobId().equals(JobType.GUARD.id())) guards++;
		return new TierInputs(Population.count(censusIds, village.workers()), village.workers().size(), guards,
				!Buildings.valid(server, BuildingType.STORAGE).isEmpty(),
				!Buildings.valid(server, BuildingType.FARMHOUSE).isEmpty(),
				!Buildings.valid(server, BuildingType.KITCHEN).isEmpty());
	}
}
