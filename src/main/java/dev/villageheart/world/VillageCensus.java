package dev.villageheart.world;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.worker.Workers;
import dev.villageheart.core.WorkerRecord;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.AABB;

/** The loaded villagers inside the claim sphere. Never loads chunks. One scan of a 161-block box: call at most once a second. */
public final class VillageCensus {
	private VillageCensus() {}

	public static List<Villager> of(MinecraftServer server, VillageState village) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return List.of();
		int r = Balance.CLAIM_RADIUS;
		AABB box = new AABB(village.heart().x() - r, village.heart().y() - r, village.heart().z() - r,
				village.heart().x() + r + 1, village.heart().y() + r + 1, village.heart().z() + r + 1);
		return level.getEntities(EntityType.VILLAGER, box,
				v -> v.isAlive() && ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, v.blockPosition())));
	}

	/** Who eats in the village: adults and babies (M22 plan A6). */
	public record Headcount(int adults, int babies) {
		/** Nutrition the village needs per day (adults 10, babies 5). */
		public long demand() { return (long) adults * Balance.ADULT_NUTRITION + (long) babies * Balance.CHILD_NUTRITION; }
	}

	/**
	 * The same count the Overview uses (HeartFacts.collect): loaded villagers in the claim, plus recorded workers that are not in the claim
	 * census (loaded elsewhere, or not loaded: they count as adults). One entity scan: call at most once a second.
	 */
	public static Headcount headcount(MinecraftServer server, VillageState village) {
		List<Villager> census = of(server, village);
		Set<UUID> inCensus = new HashSet<>();
		int babies = 0, adults = 0;
		for (Villager v : census) {
			inCensus.add(v.getUUID());
			if (v.isBaby()) babies++;
		}
		Set<UUID> workerIds = new HashSet<>();
		for (WorkerRecord rec : village.workers()) {
			workerIds.add(rec.villager());
			if (inCensus.contains(rec.villager())) {
				Villager v = census.stream().filter(c -> c.getUUID().equals(rec.villager())).findFirst().orElse(null);
				if (v != null && !v.isBaby()) adults++;
				continue;
			}
			Villager v = Workers.findLoaded(server, rec.villager()).orElse(null);
			if (v == null || !v.isBaby()) adults++;
		}
		for (Villager v : census) if (!v.isBaby() && !workerIds.contains(v.getUUID())) adults++;
		return new Headcount(adults, babies);
	}
}
