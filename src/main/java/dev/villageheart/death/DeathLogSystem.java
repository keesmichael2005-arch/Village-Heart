package dev.villageheart.death;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.death.DeathCauses;
import dev.villageheart.core.death.DeathEntry;
import dev.villageheart.core.raid.RaidTracker;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Writes the village death log (M25). Registered before {@code WorkerSystem}, so a dying worker's attachment is still
 * readable. A hired worker is logged wherever they die. Any other villager is logged only inside the claim.
 */
public final class DeathLogSystem {
	private DeathLogSystem() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(DeathLogSystem::onDeath);
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> onConversion(previous, converted));
	}

	private static void onDeath(LivingEntity entity, DamageSource source) {
		if (!(entity instanceof Villager villager) || !(entity.level() instanceof ServerLevel level)) return;
		MinecraftServer server = level.getServer();
		Optional<VillageState> state = VillageClaims.current(server);
		if (state.isEmpty()) return;
		Optional<WorkerData> worker = WorkerAttachments.get(villager);
		boolean inClaim = inClaim(level, state.get(), villager);
		if (worker.isEmpty() && !inClaim) return;
		Optional<String> sourceId = source.getEntity() == null ? Optional.empty()
				: Optional.of(id(source.getEntity().getType()));
		String damage = source.typeHolder().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown");
		DeathCauses.Cause cause = DeathCauses.of(sourceId, source.getEntity() instanceof Enemy, damage);
		remember(server, villager, worker, cause.cause(), cause.byMob(), inClaim, level);
	}

	private static void onConversion(Mob previous, Mob converted) {
		if (!(previous instanceof Villager villager) || !(previous.level() instanceof ServerLevel level)) return;
		Optional<String> cause = DeathCauses.conversion(id(previous.getType()), id(converted.getType()));
		if (cause.isEmpty()) return;
		MinecraftServer server = level.getServer();
		Optional<VillageState> state = VillageClaims.current(server);
		if (state.isEmpty()) return;
		Optional<WorkerData> worker = WorkerAttachments.get(villager);
		boolean inClaim = inClaim(level, state.get(), villager);
		if (worker.isEmpty() && !inClaim) return;
		String name = villager.getName().getString();
		String job = worker.map(WorkerData::jobId).orElse("");
		int day = day(level);
		boolean raid = state.get().threats().raid().isPresent();
		VillageClaims.data(server).update(s -> s.withDeaths(s.deaths().markConverted(villager.getUUID(), level.getGameTime(),
				cause.get(), name, job, day, inClaim, raid, false)));
	}

	private static void remember(MinecraftServer server, Villager villager, Optional<WorkerData> worker, String cause, boolean byMob,
			boolean inClaim, ServerLevel level) {
		VillageClaims.data(server).update(s -> {
			boolean duringRaid = s.threats().raid().isPresent();
			boolean duringSiege = s.threats().siege().filter(r -> r.spawned() > 0).isPresent();
			var deaths = s.deaths().appendUnlessSameLoss(new DeathEntry(0, villager.getUUID(), villager.getName().getString(),
					worker.map(WorkerData::jobId).orElse(""), cause, byMob, level.getGameTime(), day(level), inClaim, duringRaid, duringSiege));
			var next = s.withDeaths(deaths);
			if (inClaim && duringRaid) next = next.withRaid(RaidTracker.onVillagerDeath(next.threats().raid()));
			if (inClaim && duringSiege) {
				next = next.withThreats(next.threats().withSiege(next.threats().siege()
						.map(r -> r.withVillagerDeaths(r.villagerDeaths() + 1))));
				dev.villageheart.siege.SiegeStats.villagerDeath();
			}
			return next;
		});
	}

	private static boolean inClaim(ServerLevel level, VillageState state, Villager villager) {
		return ClaimGeometry.inClaim(state.heart(), VillageClaims.locate(level, villager.blockPosition()));
	}

	private static int day(ServerLevel level) { return (int) (level.getDayTime() / 24000L + 1); }

	private static String id(EntityType<?> type) { return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString(); }
}
