package dev.villageheart.worker;

import dev.villageheart.core.job.XpChange;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Level-change listeners (M15). {@link LevelUpFeedback} registers the player-facing effects; guard perks (M18) register theirs.
 * Listeners run on the server thread after the attachment (and the vanilla level badge) were written.
 */
public final class WorkerLevels {
	private static final List<LevelListener> LISTENERS = new CopyOnWriteArrayList<>();

	private WorkerLevels() {}

	public interface LevelListener {
		/** The worker's level went up ({@code change.after().level() > change.before().level()}). */
		void onLevelChanged(ServerLevel level, Villager villager, XpChange change);

		/** The worker has the XP for its next level but the village tier holds it back (once per threshold). */
		default void onCapped(ServerLevel level, Villager villager, XpChange change) {}
	}

	public static void register(LevelListener listener) {
		if (!LISTENERS.contains(listener)) LISTENERS.add(listener);
	}

	public static void unregister(LevelListener listener) { LISTENERS.remove(listener); }

	static void fireLevelChanged(ServerLevel level, Villager villager, XpChange change) {
		for (LevelListener l : LISTENERS) l.onLevelChanged(level, villager, change);
	}

	static void fireCapped(ServerLevel level, Villager villager, XpChange change) {
		for (LevelListener l : LISTENERS) l.onCapped(level, villager, change);
	}
}
