package dev.villageheart.growth;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.npc.villager.Villager;

/** Population growth glue (M23). Server thread only. */
public final class Growth {
	private Growth() {}

	/** A make-love run of {@code self} ended without a baby. */
	public static void onRunStopped(ServerLevel level, Villager self, AgeableMob partner, BreedTrace.Abort reason, double distance) {
		BreedTrace.abort(reason, distance, false);
	}
}
