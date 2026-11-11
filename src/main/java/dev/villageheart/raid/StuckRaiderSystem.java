package dev.villageheart.raid;

import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.raid.StuckRaiders;
import dev.villageheart.core.siege.ThreatWarnings;
import dev.villageheart.worker.guard.Guards;
import dev.villageheart.world.VillageSystem;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

/**
 * The one case in which the mod removes a raider (owner, 2026-11-11): a raider of the tracked raid that has made no progress
 * toward the village for {@link Balance#STUCK_RAIDER_TICKS} with no villager, guard, golem or player within
 * {@link Balance#STUCK_HELPER_RANGE}. It first glows and the players near the claim are told. If it is still stuck
 * {@link Balance#STUCK_FLEE_GRACE_TICKS} later it flees: removed with a smoke puff, counted as lost (not as a guard kill), and the
 * wave advances. A raider that moves closer, fights, or is found is left alone. Nothing here moves a raider. The rule itself is
 * {@link StuckRaiders}; this class only reads the world and applies the answer. Timers are not saved: a reload starts them again.
 */
public final class StuckRaiderSystem implements VillageSystem {
	public static final StuckRaiderSystem INSTANCE = new StuckRaiderSystem();

	private static final Map<UUID, StuckRaiders.Watch> WATCHES = new HashMap<>();
	private static int stuckTicks = Balance.STUCK_RAIDER_TICKS;
	private static int graceTicks = Balance.STUCK_FLEE_GRACE_TICKS;

	/** Raiders that glowed and raiders that fled since the last reset. The sim and the tests read them. */
	public static int glowed;
	public static int fled;

	private StuckRaiderSystem() {}

	/** Tests shorten the two waits. The defaults are the owner's 12000 and 1200. */
	public static void setLimitsForTest(int stuck, int grace) {
		stuckTicks = stuck;
		graceTicks = grace;
	}

	public static void clearForTests() {
		WATCHES.clear();
		stuckTicks = Balance.STUCK_RAIDER_TICKS;
		graceTicks = Balance.STUCK_FLEE_GRACE_TICKS;
		glowed = 0;
		fled = 0;
	}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(village.heart().dimension())));
		if (level == null) return;
		long now = level.getGameTime();
		if (now % Balance.STUCK_CHECK_TICKS != 0) return;
		var tracked = village.threats().raid();
		if (tracked.isEmpty()) {
			WATCHES.clear();
			return;
		}
		Raid raid = level.getRaids().get(tracked.get().vanillaId());
		if (raid == null || raid.isOver() || raid.isStopped()) {
			WATCHES.clear();
			return;
		}
		HeartLocation heart = village.heart();
		Set<UUID> seen = new HashSet<>();
		for (Raider raider : List.copyOf(raid.getAllRaiders())) {
			if (!raider.isAlive() || raider.isRemoved()) continue;
			seen.add(raider.getUUID());
			double dx = raider.getX() - heart.x(), dy = raider.getY() - heart.y(), dz = raider.getZ() - heart.z();
			double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
			StuckRaiders.Result r = StuckRaiders.step(WATCHES.get(raider.getUUID()), distance, fighting(raider), helperNear(level, raider), now,
					stuckTicks, graceTicks, Balance.STUCK_PROGRESS_BLOCKS);
			WATCHES.put(raider.getUUID(), r.watch());
			switch (r.action()) {
				case GLOW -> glow(server, raider);
				case CLEAR_GLOW -> raider.removeEffect(MobEffects.GLOWING);
				case FLEE -> flee(server, level, raider);
				case NONE -> {}
			}
		}
		WATCHES.keySet().retainAll(seen);
	}

	private static boolean fighting(Raider raider) {
		int tick = raider.tickCount;
		return tick - raider.getLastHurtByMobTimestamp() <= Balance.STUCK_COMBAT_TICKS && raider.getLastHurtByMobTimestamp() != 0
				|| tick - raider.getLastHurtMobTimestamp() <= Balance.STUCK_COMBAT_TICKS && raider.getLastHurtMobTimestamp() != 0;
	}

	/** A villager (guards are villagers), an iron golem or a player who is not a spectator within the helper range. */
	private static boolean helperNear(ServerLevel level, Raider raider) {
		double r = Balance.STUCK_HELPER_RANGE;
		AABB box = raider.getBoundingBox().inflate(r);
		double r2 = r * r;
		for (Villager v : level.getEntities(EntityType.VILLAGER, box, e -> e.isAlive())) if (v.distanceToSqr(raider) <= r2) return true;
		for (IronGolem g : level.getEntities(EntityType.IRON_GOLEM, box, e -> e.isAlive())) if (g.distanceToSqr(raider) <= r2) return true;
		for (ServerPlayer p : level.players()) if (!p.isSpectator() && p.distanceToSqr(raider) <= r2) return true;
		return false;
	}

	private static void glow(MinecraftServer server, Raider raider) {
		glowed++;
		raider.addEffect(new MobEffectInstance(MobEffects.GLOWING, stuckTicks > graceTicks ? graceTicks + 200 : graceTicks * 2 + 200, 0, false, false));
		ThreatMessages.chat(server, ThreatWarnings.STUCK_GLOW);
	}

	/** Removes the raider. It is not killed, so it is not a kill; the raid drops it on its next update and the wave can advance. */
	private static void flee(MinecraftServer server, ServerLevel level, Raider raider) {
		fled++;
		level.sendParticles(ParticleTypes.POOF, raider.getX(), raider.getY() + 1.0, raider.getZ(), 24, 0.4, 0.6, 0.4, 0.05);
		level.sendParticles(ParticleTypes.LARGE_SMOKE, raider.getX(), raider.getY() + 1.0, raider.getZ(), 12, 0.4, 0.6, 0.4, 0.02);
		WATCHES.remove(raider.getUUID());
		raider.discard();
		ThreatMessages.chat(server, ThreatWarnings.STUCK_FLED);
	}
}
