package dev.villageheart.golem;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.world.VillageClaims;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Vanilla villager golems ({@code Villager.spawnGolemIfNeeded}) are refused inside the claim and 8 blocks past it, which is as far as
 * vanilla will place one from the villager who asked. A player's pumpkin golem, a spawn egg and a command are other methods and are not
 * touched. Every call is counted; {@link #blocked()} counts only a call that would really have tried ({@code wantsToSpawnGolem}).
 */
public final class GolemGate {
	private static int calls;
	private static int blocked;

	private GolemGate() {}

	public static int calls() { return calls; }

	public static int blocked() { return blocked; }

	public static void reset() { calls = 0; blocked = 0; }

	/** True when the mixin should cancel. Counts the call either way. */
	public static boolean block(Villager villager, long gameTime) {
		calls++;
		if (!villager.wantsToSpawnGolem(gameTime)) return false;
		if (!(villager.level() instanceof ServerLevel level)) return false;
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return false;
		HeartLocation heart = village.get().heart();
		if (!heart.dimension().equals(level.dimension().identifier().toString())) return false;
		double dx = villager.getX() - heart.x(), dy = villager.getY() - heart.y(), dz = villager.getZ() - heart.z();
		double r = Balance.CLAIM_RADIUS + 8.0;
		if (dx * dx + dy * dy + dz * dz > r * r) return false;
		blocked++;
		return true;
	}
}
