package dev.villageheart.worker.guard;

import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The guard alarm (M18 review r1, orchestrator decision): when a hostile mob hurts a villager (a guard, a worker or an ordinary one) inside the
 * claim, every guard that is not wounded comes on duty (waking up if need be) until the threat is gone, then goes back to its schedule.
 * {@link #raise} is called by the damage event; {@link #refresh} (every 20 ticks) keeps the alarm up while a hostile that hunts a villager is
 * near one, and the alarm ends {@link Balance#GUARD_ALARM_HOLD_TICKS} after the last sign of a threat, so it does not flap.
 */
public final class GuardAlarm {
	private static long until = Long.MIN_VALUE;

	private GuardAlarm() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Villager v && source.getEntity() instanceof Enemy && v.level() instanceof ServerLevel level && GuardCombat.inClaim(level, v)) raise(level);
		});
	}

	public static void raise(ServerLevel level) { until = level.getGameTime() + Balance.GUARD_ALARM_HOLD_TICKS; }

	public static boolean active(ServerLevel level) { return level.getGameTime() < until; }

	public static void resetForTests() { until = Long.MIN_VALUE; }

	/** Keeps the alarm up while some hostile that hunts a villager is within 16 blocks of a loaded villager of the village. */
	static void refresh(MinecraftServer server, VillageState village) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null || !active(level)) return;
		for (UUID id : village.guardIds()) {
			Villager g = Workers.findLoaded(server, id).orElse(null);
			if (g == null || g.level() != level) continue;
			boolean threat = !level.getEntitiesOfClass(Mob.class, g.getBoundingBox().inflate(24.0),
					m -> m.isAlive() && m instanceof Enemy && m.getTarget() instanceof Villager && GuardCombat.inClaim(level, m)).isEmpty();
			if (threat) {
				raise(level);
				return;
			}
		}
	}
}
