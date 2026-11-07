package dev.villageheart.worker;

import dev.villageheart.core.worker.WorkerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/** Server-thread operations on workers. The registry-aware hire / fire API (with slot checks) is built on these two primitives. */
public final class Workers {
	private Workers() {}

	/**
	 * Turns {@code villager} into a worker with {@code data}: drops its job-site claims (while the old profession is still set, because
	 * the POI release predicate uses it), takes the mod profession (which clears trades), and rebuilds the brain (the mixin installs the
	 * worker brain). Does not touch the village registry.
	 */
	public static void install(ServerLevel level, Villager villager, WorkerData data) {
		villager.releasePoi(MemoryModuleType.JOB_SITE);
		villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
		villager.getBrain().eraseMemory(MemoryModuleType.POTENTIAL_JOB_SITE);
		if (villager.isTrading()) villager.setTradingPlayer(null);
		villager.setAttached(WorkerAttachments.WORKER, data);
		villager.setVillagerData(villager.getVillagerData().withProfession(ModProfessions.holder(data.job())).withLevel(1));
		villager.setVillagerXp(0);
		villager.refreshBrain(level);
	}

	/** Turns a worker back into an ordinary unemployed vanilla villager (no profession, no attachment, vanilla brain). */
	public static void restoreVanilla(ServerLevel level, Villager villager) {
		villager.removeAttached(WorkerAttachments.WORKER);
		villager.setVillagerData(villager.getVillagerData()
				.withProfession(level.registryAccess(), VillagerProfession.NONE).withLevel(1));
		villager.setVillagerXp(0);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		villager.refreshBrain(level);
	}
}
