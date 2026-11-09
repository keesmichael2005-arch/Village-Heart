package dev.villageheart.worker.guard;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/** A guard's gear (M18 3.7): reading, planning, trips. Filled in by the gear step. */
public final class GuardGear {
	private GuardGear() {}

	/** Moves all equipment out of the guard (Barracks chest, else Storage, else the ground). */
	public static void returnAll(ServerLevel level, Villager guard) {}
}
