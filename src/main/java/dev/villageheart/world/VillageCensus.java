package dev.villageheart.world;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.worker.Workers;
import java.util.List;
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
}
