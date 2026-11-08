package dev.villageheart.worker;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.world.VillageTicker;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerDataHolder;
import net.minecraft.world.entity.npc.villager.VillagerProfession;

/**
 * Keeps the village registry and the villager entities consistent (DESIGN section 12). Every 20 ticks (a {@link VillageSystem}):
 * a record whose building was removed is fired; a worker whose brain is not the worker brain gets it back; a record without its
 * attachment gets it back. Entity events: a villager that loads (queued, handled at the end of the tick, with or without a claim) with the
 * attachment but no record is restored to vanilla; one that loads claiming a reserved job block loses the claim; a dead or converted worker
 * loses its record, and a zombie villager never keeps a mod profession.
 */
public final class WorkerSystem implements VillageSystem {
	public static final WorkerSystem INSTANCE = new WorkerSystem();
	private static final int INTERVAL = 20;

	private final Set<Villager> loaded = new LinkedHashSet<>();
	/** Test hook: villagers made workers without a record (framework tests) are not treated as orphans. */
	private final Set<java.util.UUID> exemptFromOrphanSweep = new java.util.HashSet<>();

	private WorkerSystem() {}

	public static void register() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v) INSTANCE.loaded.add(v);
		});
		ServerTickEvents.END_SERVER_TICK.register(INSTANCE::processLoaded);
		ServerTickEvents.END_SERVER_TICK.register(server -> {
			if (server.getTickCount() % INTERVAL != 0) return;
			try (var t = VillageTicker.section()) {
				JobSiteReservations.processPending(server);
			}
		});
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> WorkXp.clear());
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (entity instanceof Villager v && v.level() instanceof ServerLevel level) Workers.fireById(level.getServer(), v.getUUID());
		});
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> {
			if (previous instanceof Villager v && v.level() instanceof ServerLevel level) Workers.fireById(level.getServer(), v.getUUID());
			// unconditional on the namespace, no record lookup: the result of a conversion never keeps a mod profession
			if (converted instanceof VillagerDataHolder holder && ModProfessions.isModProfession(holder.getVillagerData().profession())) {
				// level 1 too: the level badge of a worker (M15) must not survive as a vanilla level on the cured villager
				holder.setVillagerData(holder.getVillagerData().withProfession(converted.level().registryAccess(), VillagerProfession.NONE).withLevel(1));
			}
		});
	}

	public static void exemptFromOrphanSweepForTest(java.util.UUID id) { INSTANCE.exemptFromOrphanSweep.add(id); }

	public static void clearOrphanExemptionsForTest() { INSTANCE.exemptFromOrphanSweep.clear(); }

	/** The loaded villager with this UUID in any level, or null. */
	static Villager findLoaded(MinecraftServer server, java.util.UUID id) {
		for (ServerLevel level : server.getAllLevels()) {
			if (level.getEntity(id) instanceof Villager v && !v.isRemoved()) return v;
		}
		return null;
	}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.getTickCount() % INTERVAL != 0) return;
		JobSiteSource source = Workers.jobSites();
		for (WorkerRecord rec : List.copyOf(village.workers())) {
			if (!source.isRegistered(server, rec.building())) {
				VillageHeart.LOG.info("Worker {} fired: its building at {} no longer exists", rec.villager(), rec.building());
				Workers.fireRecord(server, rec);
				continue;
			}
			Villager v = findLoaded(server, rec.villager());
			if (v == null || !(v.level() instanceof ServerLevel level)) continue;
			if (!WorkerAttachments.isWorker(v)) {
				JobType.byId(rec.jobId()).ifPresent(job -> Workers.install(level, v, WorkerData.hired(job, rec.building(), rec.jobBlock())));
			} else {
				Optional<JobType> job = JobType.byId(rec.jobId());
				if (job.isPresent() && !v.getVillagerData().profession().equals(ModProfessions.holder(job.get()))) {
					v.setVillagerData(v.getVillagerData().withProfession(ModProfessions.holder(job.get())));
				}
				if (!v.isBaby() && !WorkerBrain.isWorkerBrain(v)) v.refreshBrain(level);
				WorkXp.pass(level, v, village.tier());
			}
		}
	}

	/** Entities that loaded this tick: orphan workers back to vanilla, vanilla claimants of reserved blocks evicted. */
	private void processLoaded(MinecraftServer server) {
		if (loaded.isEmpty()) return;
		List<Villager> batch = new ArrayList<>(loaded);
		loaded.clear();
		try (var t = VillageTicker.section()) {
			Optional<VillageState> village = VillageClaims.current(server);
			Set<HeartLocation> held = null, desired = null;
			for (Villager v : batch) {
				if (v.isRemoved() || !(v.level() instanceof ServerLevel level)) continue;
				WorkerData data = v.getAttached(WorkerAttachments.WORKER);
				if (data != null) {
					boolean registered = village.flatMap(s -> s.worker(v.getUUID())).isPresent();
					if (!registered && !exemptFromOrphanSweep.contains(v.getUUID())) {
						VillageHeart.LOG.info("Villager {} carried worker data but has no record: back to vanilla", v.getUUID());
						Workers.restoreVanilla(level, v);
					}
					continue;
				}
				if (village.isEmpty()) continue;
				if (!hasJobSiteMemory(v)) continue;
				if (held == null) {
					held = VillageClaims.data(server).reservedJobBlocks();
					desired = new LinkedHashSet<>(Workers.jobSites().reservable(server));
				}
				evictOnLoad(level, v, held, desired);
			}
		}
	}

	private static boolean hasJobSiteMemory(Villager v) {
		return v.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE) || v.getBrain().hasMemoryValue(MemoryModuleType.POTENTIAL_JOB_SITE);
	}

	/**
	 * A returning vanilla villager whose job-site memory names a held or about-to-be-held block loses the claim. If the block is
	 * only desired (not yet held) the villager may own the ticket, so it is released once; if it is held the ticket is ours.
	 */
	private static void evictOnLoad(ServerLevel level, Villager v, Set<HeartLocation> held, Set<HeartLocation> desired) {
		for (MemoryModuleType<GlobalPos> m : List.of(MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE)) {
			Optional<GlobalPos> gp = v.getBrain().getMemory(m);
			if (gp.isEmpty()) continue;
			HeartLocation loc = new HeartLocation(gp.get().dimension().identifier().toString(), gp.get().pos().getX(), gp.get().pos().getY(), gp.get().pos().getZ());
			boolean isHeld = held.contains(loc);
			if (!isHeld && !desired.contains(loc)) continue;
			v.getBrain().eraseMemory(m);
			if (!isHeld) {
				ServerLevel target = level.getServer().getLevel(gp.get().dimension());
				if (target != null && target.isLoaded(gp.get().pos())) {
					PoiManager poi = target.getPoiManager();
					if (poi.getType(gp.get().pos()).isPresent()) poi.release(gp.get().pos());
				}
			}
		}
	}
}
