package dev.villageheart.worker.guard;

import net.minecraft.world.entity.npc.villager.Villager;

/** A guard's fighting (M18 3.6). Filled in by the combat step; the gate rules already ask {@link #engaged}. */
public final class GuardCombat {
	private GuardCombat() {}

	/** True while this guard has a fight target. */
	public static boolean engaged(Villager guard) { return false; }
}
