package dev.villageheart.worker;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.worker.HireDecision;
import dev.villageheart.core.worker.HireRefusal;
import dev.villageheart.core.worker.JobSlots;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
	 * Hires {@code entity} (a command can name anything) for {@code job}. Refusals in order: no village, not a villager (or dead),
	 * baby, nitwit, outside the claim sphere, then {@link JobSlots#decide} (already employed, tier, preferred building, no free slot).
	 * On success the record is appended (hiring order) and the villager becomes a worker (see {@link #install}). Server thread only.
	 */
	public static HireResult hire(ServerLevel level, Entity entity, JobType job, Optional<HeartLocation> preferredBuilding) {
		MinecraftServer server = level.getServer();
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return new HireResult.Refused(HireRefusal.NO_VILLAGE);
		if (!(entity instanceof Villager villager) || !villager.isAlive()) return new HireResult.Refused(HireRefusal.NOT_A_VILLAGER);
		if (villager.isBaby()) return new HireResult.Refused(HireRefusal.NOT_ADULT);
		if (villager.getVillagerData().profession().is(VillagerProfession.NITWIT)) return new HireResult.Refused(HireRefusal.NITWIT);
		VillageState v = village.get();
		if (!ClaimGeometry.inClaim(v.heart(), VillageClaims.locate(level, villager.blockPosition()))) {
			return new HireResult.Refused(HireRefusal.OUTSIDE_CLAIM);
		}
		HireDecision decision = JobSlots.decide(jobSites.sites(server, job), v.workers(), v.tier(), villager.getUUID(), job, preferredBuilding);
		if (decision instanceof HireDecision.Refused r) return new HireResult.Refused(r.reason());
		HireDecision.Hired hired = (HireDecision.Hired) decision;
		WorkerData data = WorkerData.hired(job, hired.site().building(), hired.jobBlock());
		WorkerRecord record = new WorkerRecord(villager.getUUID(), job.id(), data.building(), data.jobBlock());
		install(level, villager, data);
		VillageClaims.data(server).update(s -> s.withWorker(record));
		return new HireResult.Hired(record);
	}

	/** Fires a loaded worker: record and attachment removed, vanilla brain back. False when it was neither registered nor attached. */
	public static boolean fire(ServerLevel level, Villager villager) {
		MinecraftServer server = level.getServer();
		boolean registered = VillageClaims.current(server).flatMap(v -> v.worker(villager.getUUID())).isPresent();
		if (registered) VillageClaims.data(server).update(s -> s.withoutWorker(villager.getUUID()));
		boolean attached = WorkerAttachments.isWorker(villager);
		dev.villageheart.worker.job.WorkerStatuses.clear(villager.getUUID());
		dev.villageheart.worker.lumber.LumberJob.forget(server, villager.getUUID());
		dev.villageheart.build.BuildJobs.onWorkerRemoved(server, villager.getUUID(), villager);
		dev.villageheart.worker.farm.FarmJob.forget(server, villager.getUUID());
		dev.villageheart.worker.mine.MinerJob.forget(villager.getUUID());
		if (attached || registered) restoreVanilla(level, villager);
		return attached || registered;
	}

	/** The loaded villager with this UUID in any level, or empty. Never loads anything. */
	public static Optional<Villager> findLoaded(MinecraftServer server, UUID id) {
		return Optional.ofNullable(WorkerSystem.findLoaded(server, id));
	}

	/** Fires the worker of {@code record}, loaded or not. */
	static void fireRecord(MinecraftServer server, WorkerRecord record) {
		Villager v = WorkerSystem.findLoaded(server, record.villager());
		if (v != null && v.level() instanceof ServerLevel level) fire(level, v);
		else fireById(server, record.villager());
	}

	/** After the village was released: every worker that is loaded goes back to vanilla (unloaded ones are restored when they load). */
	public static void restoreAfterRelease(MinecraftServer server, java.util.List<WorkerRecord> workers) {
		for (WorkerRecord w : workers) {
			Villager v = WorkerSystem.findLoaded(server, w.villager());
			if (v != null && v.level() instanceof ServerLevel level) restoreVanilla(level, v);
		}
	}

	private static final java.util.Set<Villager> TRIPS = java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>());

	/** True while a deposit trip runs for this worker: nothing else may take its walk target, and no second trip may start. */
	public static boolean tripRunning(Villager v) { return TRIPS.contains(v); }

	static void setTripRunning(Villager v, boolean running) {
		if (running) TRIPS.add(v); else TRIPS.remove(v);
	}

	private static final java.util.Map<UUID, WorkerStatus> TRIP_STATUS = new java.util.HashMap<>();

	/** Transient result of the worker's last deposit trip (null clears it). Not saved. */
	static void setTripStatus(UUID villager, WorkerStatus status) {
		if (status == null) TRIP_STATUS.remove(villager); else TRIP_STATUS.put(villager, status);
	}

	/** The result of the worker's last deposit trip (STORAGE_FULL, STORAGE_UNREACHABLE) or null. */
	public static WorkerStatus tripStatus(UUID villager) { return TRIP_STATUS.get(villager); }

	public static WorkerStatus status(MinecraftServer server, WorkerRecord record) {
		WorkerStatus trip = TRIP_STATUS.get(record.villager());
		WorkerStatus slot = slotStatus(server, record);
		if (slot != WorkerStatus.OK) return slot;
		if (trip != null) return trip;
		WorkerStatus job = dev.villageheart.worker.job.WorkerStatuses.get(record.villager());
		return job == null ? WorkerStatus.OK : job;
	}

	private static WorkerStatus slotStatus(MinecraftServer server, WorkerRecord record) {
		Optional<JobType> job = JobType.byId(record.jobId());
		Optional<VillageState> village = VillageClaims.current(server);
		if (job.isEmpty() || village.isEmpty()) return WorkerStatus.WORKPLACE_INVALID;
		return JobSlots.holdsSlot(jobSites.sites(server, job.get()), village.get().workers(), record)
				? WorkerStatus.OK : WorkerStatus.WORKPLACE_INVALID;
	}

	/** Fires a worker that may be unloaded: only the record can be removed now (the attachment is cleaned when it loads). */
	public static boolean fireById(MinecraftServer server, UUID villager) {
		if (VillageClaims.current(server).flatMap(v -> v.worker(villager)).isEmpty()) return false;
		VillageClaims.data(server).update(s -> s.withoutWorker(villager));
		dev.villageheart.worker.job.WorkerStatuses.clear(villager);
		dev.villageheart.worker.lumber.LumberJob.forget(server, villager);
		dev.villageheart.build.BuildJobs.onWorkerRemoved(server, villager, null);
		dev.villageheart.worker.farm.FarmJob.forget(server, villager);
		dev.villageheart.worker.mine.MinerJob.forget(villager);
		return true;
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
		dev.villageheart.worker.job.WorkerStatuses.clear(villager.getUUID());
		villager.setVillagerData(villager.getVillagerData().withProfession(ModProfessions.holder(data.job())).withLevel(1));
		villager.setVillagerXp(0);
		villager.refreshBrain(level);
	}

	/** Turns a worker back into an ordinary unemployed vanilla villager (no profession, no attachment, vanilla brain). */
	public static void restoreVanilla(ServerLevel level, Villager villager) {
		dev.villageheart.worker.job.AwayTrips.release(level, villager); // a hidden miner comes up first (M16)
		villager.removeAttached(WorkerAttachments.WORKER);
		villager.setVillagerData(villager.getVillagerData()
				.withProfession(level.registryAccess(), VillagerProfession.NONE).withLevel(1));
		villager.setVillagerXp(0);
		villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		villager.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		villager.refreshBrain(level);
	}
}
