package dev.villageheart.worker;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSavedData;
import dev.villageheart.world.VillageSystem;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Keeps vanilla villagers off the workstations of valid buildings (DESIGN section 3.4): the mod holds the one POI ticket of every job
 * block in a valid building, assigned or not, so {@code AcquirePoi}'s {@code HAS_SPACE} search never returns it. The held set is saved with
 * the village. Runs every {@link Balance#RESERVATION_INTERVAL_TICKS} ticks and never loads chunks.
 *
 * <p>POI add/remove is deferred by one {@code server.execute}, so a just-placed block has no POI yet: the pass simply retries. A vanilla
 * villager that already claimed the block is evicted first (its job-site memories erased, its ticket released).
 */
public final class JobSiteReservations implements VillageSystem {
	public static final JobSiteReservations INSTANCE = new JobSiteReservations();
	private static final int WARN_AFTER_FAILED_TAKES = 5;
	private static final int EVICT_RADIUS = 64;

	private final Map<HeartLocation, Integer> failedTakes = new HashMap<>();

	private JobSiteReservations() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.getTickCount() % Balance.RESERVATION_INTERVAL_TICKS != 0) return;
		pass(server);
	}

	private static BlockPos pos(HeartLocation l) { return new BlockPos(l.x(), l.y(), l.z()); }

	/** One reconcile pass (also called directly by tests). */
	public void pass(MinecraftServer server) {
		VillageSavedData data = VillageClaims.data(server);
		if (data.village().isEmpty()) return;
		JobSiteSource source = Workers.jobSites();
		Set<HeartLocation> held = data.reservedJobBlocks();
		Set<HeartLocation> desired = new LinkedHashSet<>(source.reservable(server));
		boolean pending = source.contentsPending(server);
		boolean changed = false;

		for (HeartLocation p : desired) {
			ServerLevel level = Workers.levelOf(server, p);
			BlockPos bp = pos(p);
			if (level == null || !level.isLoaded(bp)) continue;
			BlockState state = level.getBlockState(bp);
			if (PoiTypes.forState(state).isEmpty()) {
				// no POI will ever exist here (Chopping Block, ...), or the block changed: nothing to hold
				if (held.remove(p)) changed = true;
				continue;
			}
			PoiManager poi = level.getPoiManager();
			boolean bed = state.is(net.minecraft.tags.BlockTags.BEDS);
			if (held.contains(p)) {
				if (poi.getType(bp).isEmpty()) { // block replaced: the old POI is gone, the new one is not there yet
					held.remove(p);
					changed = true;
				} else if (hasFreeTicket(poi, bp)) { // the record was re-created with a free ticket
					poi.take(h -> true, (h, b) -> b.equals(bp), bp, 1);
				}
				// a Barracks bed: whoever else has it as HOME (workers too) loses it; the ticket is the mod's, so it is not released (M18 3.5)
				if (bed) evictHomeClaimants(level, bp, false);
				continue;
			}
			if (poi.getType(bp).isEmpty()) continue; // POI add still pending: retry next pass
			evictVanillaClaimants(level, bp);
			if (bed) evictHomeClaimants(level, bp, true);
			if (poi.take(h -> true, (h, b) -> b.equals(bp), bp, 1).isPresent()) {
				held.add(p);
				failedTakes.remove(p);
				changed = true;
			} else if (failedTakes.merge(p, 1, Integer::sum) == WARN_AFTER_FAILED_TAKES) {
				VillageHeart.LOG.warn("Cannot reserve the job block at {}: its POI ticket is taken by something unknown", p);
			}
		}

		if (!pending) {
			for (HeartLocation p : List.copyOf(held)) {
				if (desired.contains(p)) continue;
				ServerLevel level = Workers.levelOf(server, p);
				BlockPos bp = pos(p);
				if (level == null || !level.isLoaded(bp)) continue;
				PoiManager poi = level.getPoiManager();
				if (poi.getType(bp).isPresent()) poi.release(bp);
				held.remove(p);
				failedTakes.remove(p);
				changed = true;
			}
		}
		if (changed) data.setReservedJobBlocks(held);
	}

	private static boolean hasFreeTicket(PoiManager poi, BlockPos bp) {
		return poi.getInSquare(h -> true, bp, 0, PoiManager.Occupancy.HAS_SPACE).anyMatch(r -> r.getPos().equals(bp));
	}

	/** Non-worker villagers whose job-site memory points at {@code bp} lose it, and their ticket is released (once per villager). */
	static void evictVanillaClaimants(ServerLevel level, BlockPos bp) {
		GlobalPos target = GlobalPos.of(level.dimension(), bp);
		PoiManager poi = level.getPoiManager();
		AABB box = new AABB(bp).inflate(EVICT_RADIUS);
		for (Villager v : level.getEntitiesOfClass(Villager.class, box, x -> !WorkerAttachments.isWorker(x))) {
			if (evictFrom(v, target) && poi.getType(bp).isPresent()) poi.release(bp);
		}
	}

	/**
	 * Villagers (hired workers too, except the guard the bed was assigned to) whose HOME is the Barracks bed at {@code bp} lose it.
	 * {@code release}: the ticket was theirs (the bed was not held yet), so it is released once per evicted villager.
	 */
	static void evictHomeClaimants(ServerLevel level, BlockPos bp, boolean release) {
		GlobalPos target = GlobalPos.of(level.dimension(), bp);
		PoiManager poi = level.getPoiManager();
		java.util.Optional<java.util.UUID> owner = dev.villageheart.worker.guard.GuardSystem.guardAt(bp);
		AABB box = new AABB(bp).inflate(EVICT_RADIUS);
		for (Villager v : level.getEntitiesOfClass(Villager.class, box)) {
			if (owner.isPresent() && owner.get().equals(v.getUUID())) continue;
			if (v.getBrain().getMemory(MemoryModuleType.HOME).filter(target::equals).isPresent()) {
				v.getBrain().eraseMemory(MemoryModuleType.HOME);
				if (release && poi.getType(bp).isPresent() && !hasFreeTicket(poi, bp)) poi.release(bp);
			}
		}
	}

	/** Erases the villager's JOB_SITE / POTENTIAL_JOB_SITE when it names {@code target}; true when it did. */
	static boolean evictFrom(Villager v, GlobalPos target) {
		boolean claimed = false;
		for (MemoryModuleType<GlobalPos> m : List.of(MemoryModuleType.JOB_SITE, MemoryModuleType.POTENTIAL_JOB_SITE)) {
			if (v.getBrain().getMemory(m).filter(target::equals).isPresent()) {
				v.getBrain().eraseMemory(m);
				claimed = true;
			}
		}
		return claimed;
	}

	/**
	 * Releases the ticket of every held block whose POI exists (Heart released, test reset). A block whose chunk is not loaded cannot be
	 * touched without loading it: it is remembered in the saved data and finished by {@link #processPending} when the chunk loads.
	 */
	public static void releaseAll(MinecraftServer server, Set<HeartLocation> held) {
		Set<HeartLocation> later = null;
		for (HeartLocation p : held) {
			ServerLevel level = Workers.levelOf(server, p);
			BlockPos bp = pos(p);
			if (level == null) continue;
			if (!level.isLoaded(bp)) {
				if (later == null) later = new LinkedHashSet<>();
				later.add(p);
				continue;
			}
			PoiManager poi = level.getPoiManager();
			if (poi.getType(bp).isPresent()) poi.release(bp);
		}
		if (later != null) {
			VillageSavedData data = VillageClaims.data(server);
			Set<HeartLocation> pending = data.pendingTicketReleases();
			pending.addAll(later);
			data.setPendingTicketReleases(pending);
		}
		INSTANCE.failedTakes.clear();
	}

	/**
	 * Finishes releases that had to wait for a chunk (see {@link #releaseAll}); runs with or without a claim. A block that is held by the
	 * current village again is only dropped from the list (the ticket is the village's now).
	 */
	public static void processPending(MinecraftServer server) {
		VillageSavedData data = VillageClaims.data(server);
		Set<HeartLocation> pending = data.pendingTicketReleases();
		if (pending.isEmpty()) return;
		Set<HeartLocation> heldNow = data.reservedJobBlocks();
		boolean changed = false;
		for (HeartLocation p : List.copyOf(pending)) {
			ServerLevel level = Workers.levelOf(server, p);
			BlockPos bp = pos(p);
			if (level == null) {
				pending.remove(p);
				changed = true;
				continue;
			}
			if (!level.isLoaded(bp)) continue;
			PoiManager poi = level.getPoiManager();
			if (!heldNow.contains(p) && poi.getType(bp).isPresent()) poi.release(bp);
			pending.remove(p);
			changed = true;
		}
		if (changed) data.setPendingTicketReleases(pending);
	}

	/** Releases every held ticket and clears the saved set. */
	public static void releaseAll(MinecraftServer server) {
		VillageSavedData data = VillageClaims.data(server);
		Set<HeartLocation> held = data.reservedJobBlocks();
		if (held.isEmpty()) return;
		releaseAll(server, held);
		data.setReservedJobBlocks(Set.of());
	}
}
