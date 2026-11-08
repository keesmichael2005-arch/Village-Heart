package dev.villageheart.worker;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.economy.Wages;
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
		LevelUpFeedback.register();
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
			dev.villageheart.worker.job.WorkerUnstick.rescue(level, v);
			farFromWork(server, level, v, rec);
			if (!WorkerAttachments.isWorker(v)) {
				// back at the level the registry recorded (M12), not at level 1
				JobType.byId(rec.jobId()).ifPresent(job -> Workers.install(level, v,
						WorkerData.hired(job, rec.building(), rec.jobBlock()).withProgress(rec.level(), 0)));
			} else {
				Optional<JobType> job = JobType.byId(rec.jobId());
				if (job.isPresent() && !v.getVillagerData().profession().equals(ModProfessions.holder(job.get()))) {
					v.setVillagerData(v.getVillagerData().withProfession(ModProfessions.holder(job.get())));
				}
				if (!v.isBaby() && !WorkerBrain.isWorkerBrain(v)) v.refreshBrain(level);
				WorkXp.pass(level, v, village.tier());
				// the wage bill of a worker that is away reads this mirror (M12); M15's paths write it at once, this repairs a lag or a direct write
				WorkerData now = v.getAttached(WorkerAttachments.WORKER);
				if (now != null && now.level() != rec.level()) Wages.mirrorLevel(server, rec.villager(), now.level());
			}
		}
	}

	private static double FAR_BLOCKS = 40;

	/** Tests shrink the distance; a value below 1 restores the default. */
	public static void setFarBlocksForTest(double blocks) { FAR_BLOCKS = blocks < 1 ? 40 : blocks; }
	/** Time a worker may stay further than that from its job block before it is put back. */
	private static long LOST_TICKS = 2400;

	/** Tests shorten the time before a lost worker is put back; a value below 1 restores the default. */
	public static void setLostTicksForTest(long ticks) { LOST_TICKS = ticks < 1 ? 2400 : ticks; }
	private final java.util.Map<java.util.UUID, Long> farSince = new java.util.HashMap<>();
	private final java.util.Map<java.util.UUID, Long> farLogged = new java.util.HashMap<>();

	/** Evidence for a worker that is far from its job block (further than a path can reach): one line per 2400 ticks with what its brain is doing. */
	private void farFromWork(MinecraftServer server, ServerLevel level, Villager v, WorkerRecord rec) {
		if (dev.villageheart.worker.job.AwayTrips.isAway(v) || !level.dimension().identifier().toString().equals(rec.jobBlock().dimension())) return;
		double d = Math.hypot(v.getX() - rec.jobBlock().x() - 0.5, v.getZ() - rec.jobBlock().z() - 0.5);
		long now = level.getGameTime();
		if (d <= FAR_BLOCKS) {
			farSince.remove(v.getUUID());
			return;
		}
		long since = farSince.computeIfAbsent(v.getUUID(), k -> now);
		if (now - since >= LOST_TICKS && level.hasChunkAt(new net.minecraft.core.BlockPos(rec.jobBlock().x(), rec.jobBlock().y(), rec.jobBlock().z()))) {
			// lost: further than a path reaches for a long time (a panic ran it off, or it wandered). It is put back next to its job block.
			var jb = new net.minecraft.core.BlockPos(rec.jobBlock().x(), rec.jobBlock().y(), rec.jobBlock().z());
			var to = dev.villageheart.worker.job.WorkerUnstick.findFree(level, v, jb, 4, 1);
			farSince.remove(v.getUUID());
			if (to != null) {
				VillageHeart.LOG.warn("[far-worker] {} {} was {} blocks from its job block for {} ticks: moved from {} to {}", rec.jobId(), v.getUUID(), Math.round(d), now - since,
						v.blockPosition().toShortString(), to.toShortString());
				dev.villageheart.worker.job.WorkerUnstick.move(v, to);
				return;
			}
		}
		if (now - farLogged.getOrDefault(v.getUUID(), Long.MIN_VALUE / 2) < 2400) return;
		farLogged.put(v.getUUID(), now);
		var brain = v.getBrain();
		VillageHeart.LOG.warn("[far-worker] {} {} is {} blocks from its job block {},{},{}: pos {} hp {} activities {} walk-target {} path {} nav-done {} trip {} status {} daytime {}",
				rec.jobId(), v.getUUID(), Math.round(d), rec.jobBlock().x(), rec.jobBlock().y(), rec.jobBlock().z(), v.blockPosition().toShortString(), v.getHealth(),
				brain.getActiveActivities(), brain.getMemory(MemoryModuleType.WALK_TARGET).map(w -> w.getTarget().currentBlockPosition().toShortString()).orElse("-"),
				brain.hasMemoryValue(MemoryModuleType.PATH), v.getNavigation().isDone(), Workers.tripRunning(v), Workers.status(server, rec), level.getDayTime() % 24000);
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
