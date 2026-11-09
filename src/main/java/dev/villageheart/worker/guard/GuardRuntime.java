package dev.villageheart.worker.guard;

import dev.villageheart.world.VillageTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The per-tick work of the two guard behaviours (M18 3.2 #3). The behaviour classes stay thin, as M9's do; state that must survive a
 * behaviour restart lives in the runtime maps of the classes used here, not in the behaviour.
 */
final class GuardRuntime {
	private GuardRuntime() {}

	static void tickDuty(ServerLevel level, Villager guard, long now) {
		try (var t = VillageTicker.section()) {
			// filled in by the patrol, combat and gear steps
		}
	}

	static void stopDuty(ServerLevel level, Villager guard) {}

	static void tickOff(ServerLevel level, Villager guard, long now) {
		try (var t = VillageTicker.section()) {
			// filled in by the gear-return and sparring steps
		}
	}

	static void stopOff(ServerLevel level, Villager guard) {}
}
