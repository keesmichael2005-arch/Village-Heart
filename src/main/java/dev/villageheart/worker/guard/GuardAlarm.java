package dev.villageheart.worker.guard;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.worker.Workers;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.Level;

/**
 * The guard alarm (M18 review r1, orchestrator decision): when a hostile mob hurts a villager (a guard, a worker or an ordinary one) inside the
 * claim, or a guard dies (M18 r3), every guard that is not wounded or in training comes on duty (waking up if need be) until the threat is
 * gone, then goes back to its schedule. {@link #raise} is called by the damage and death events; {@link #refresh} (every 20 ticks) keeps the
 * alarm up while a hostile that hunts a villager is near one, and the alarm ends {@link Balance#GUARD_ALARM_HOLD_TICKS} after the last sign
 * of a threat, so it does not flap. The state is static, so it is cleared when a server stops or starts (a game time of another world means
 * nothing), and the spot is kept with its dimension.
 */
public final class GuardAlarm {
	private static long until = Long.MIN_VALUE;
	private static BlockPos spot;
	private static ResourceKey<Level> spotDimension;

	private GuardAlarm() {}

	public static void register() {
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, base, taken, blocked) -> {
			if (entity instanceof Villager v && source.getEntity() instanceof Enemy && v.level() instanceof ServerLevel level && GuardCombat.inClaim(level, v)) raise(level, v.blockPosition());
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> reset());
		ServerLifecycleEvents.SERVER_STARTING.register(server -> reset());
	}

	public static void raise(ServerLevel level, BlockPos where) {
		until = level.getGameTime() + Balance.GUARD_ALARM_HOLD_TICKS;
		spot = where;
		spotDimension = level.dimension();
	}

	/** Where the trouble is (the attacked villager, or the hostile that hunts one): the guards gather there. Only in the dimension it was raised in. */
	public static Optional<BlockPos> spot(ServerLevel level) {
		return active(level) && spot != null && level.dimension().equals(spotDimension) ? Optional.of(spot) : Optional.empty();
	}

	public static boolean active(ServerLevel level) { return level.getGameTime() < until; }

	public static void resetForTests() { reset(); }

	/** Clears the alarm: called when a server stops and when one starts (and by tests). */
	public static void reset() {
		until = Long.MIN_VALUE;
		spot = null;
		spotDimension = null;
	}

	/** Raises and keeps up the alarm while some hostile that hunts a villager is within 24 blocks of a loaded guard of the village. */
	static void refresh(MinecraftServer server, VillageState village) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return;
		for (UUID id : village.guardIds()) {
			Villager g = Workers.findLoaded(server, id).orElse(null);
			if (g == null || g.level() != level) continue;
			var threats = level.getEntitiesOfClass(Mob.class, g.getBoundingBox().inflate(24.0),
					m -> m.isAlive() && m instanceof Enemy && m.getTarget() instanceof Villager && GuardCombat.inClaim(level, m));
			if (!threats.isEmpty()) {
				raise(level, threats.get(0).blockPosition());
				return;
			}
		}
	}
}
