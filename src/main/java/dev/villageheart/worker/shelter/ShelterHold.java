package dev.villageheart.worker.shelter;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;

/**
 * Brains of unhired villagers that are sheltering for a raid or a siege (owner, 2026-11-11). Vanilla's {@code SetRaidStatus} moves
 * a villager to PRE_RAID (the meeting point, outdoors) or RAID about once a second, and a bell rung during a raid does nothing
 * ({@code ReactToBell} skips a raid). While a brain is held, those three activities are refused, so the shelter walk is not undone.
 */
public final class ShelterHold {
	private static final Set<Brain<?>> HELD = Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<>()));

	private ShelterHold() {}

	public static void hold(Brain<?> brain) { HELD.add(brain); }

	public static void release(Brain<?> brain) { HELD.remove(brain); }

	public static boolean holds(Brain<?> brain) { return HELD.contains(brain); }

	public static int count() { return HELD.size(); }

	public static void clear() { HELD.clear(); }

	public static void clearForTests() { HELD.clear(); }

	/** True when a held brain must not enter this activity. Called from the brain mixin on every activity change. */
	public static boolean blocks(Brain<?> brain, Activity activity) {
		if (HELD.isEmpty() || !HELD.contains(brain)) return false;
		return activity == Activity.PRE_RAID || activity == Activity.RAID || activity == Activity.HIDE;
	}
}
