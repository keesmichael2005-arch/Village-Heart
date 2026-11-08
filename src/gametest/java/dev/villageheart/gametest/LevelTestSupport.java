package dev.villageheart.gametest;

import dev.villageheart.core.Tier;
import dev.villageheart.core.job.Progress;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.worker.WorkerAttachments;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Helpers for the level GameTests (M15). Rules: own environment per test ({@code levels_<name>}, a JSON file like the lumber ones), the
 * lumber scene ({@link LumberTestSupport#scene}), {@code begin}/{@code done}/{@code end}. Levels are set by writing the attachment.
 */
public final class LevelTestSupport {
	private LevelTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:levels_" + name; }

	public static WorkerData data(Villager v) { return v.getAttached(WorkerAttachments.WORKER); }

	public static Progress progress(Villager v) { return new Progress(data(v).level(), data(v).xp()); }

	/** Writes level and XP straight onto the attachment (no listeners, no registry mirror). */
	public static void put(Villager v, int level, int xp) {
		v.setAttached(WorkerAttachments.WORKER, data(v).withProgress(level, xp));
		v.setVillagerData(v.getVillagerData().withLevel(level));
	}

	public static void tier(net.minecraft.gametest.framework.GameTestHelper h, Tier t) { BuildingTestSupport.setTier(h, t); }
}
