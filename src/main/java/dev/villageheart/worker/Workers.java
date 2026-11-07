package dev.villageheart.worker;

import dev.villageheart.core.worker.WorkerData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/** Server-thread operations on workers. The registry-aware hire / fire API (with slot checks) is built on these two primitives. */
public final class Workers {
	private static JobSiteSource jobSites = JobSiteSource.DEFAULT;

	private Workers() {}

	/** The building registry seam (M5), or the fixed source a GameTest installed. */
	public static JobSiteSource jobSites() { return jobSites; }

	/** Test hook: swap the source; pass the previous one (or {@code JobSiteSource.DEFAULT}) back to restore. */
	public static JobSiteSource setJobSiteSourceForTest(JobSiteSource source) {
		JobSiteSource previous = jobSites;
		jobSites = source == null ? JobSiteSource.DEFAULT : source;
		return previous;
	}

	/** The loaded level named by a stored location, or null. Never loads anything. */
	public static net.minecraft.server.level.ServerLevel levelOf(net.minecraft.server.MinecraftServer server, dev.villageheart.core.HeartLocation loc) {
		return server.getLevel(net.minecraft.resources.ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION,
				net.minecraft.resources.Identifier.parse(loc.dimension())));
	}

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
