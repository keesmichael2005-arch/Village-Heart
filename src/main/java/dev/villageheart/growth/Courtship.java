package dev.villageheart.growth;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.growth.CourtshipWindow;
import dev.villageheart.core.growth.Kind;
import dev.villageheart.core.growth.LapseReason;
import dev.villageheart.core.growth.LicenceState;
import dev.villageheart.core.growth.License;
import dev.villageheart.core.time.DailyClock;
import dev.villageheart.mixin.VillagerFoodAccess;
import dev.villageheart.needs.BedPoi;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.AwayTrips;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * The per-tick courtship of today's licence (M23 plan 3.2 #5): wait for both parents to be awake in their IDLE window, walk them to each
 * other, seed vanilla's make-love behaviour on the initiator, and lapse the licence when the pair cannot meet. Server thread only.
 */
final class Courtship {
	private static final int WALK_PERIOD = 10;
	private static final long CANNOT_REACH_TICKS = 200;
	/** A SEEDED licence whose run never ends (the behaviour did not start) is treated as an abort after this long. */
	private static final long SEEDED_PATIENCE = 400;

	/** The last known parents, so a dead parent can be told from an unloaded one. */
	private static Villager refA, refB;
	private static long seededSince = -1;
	private static BreedingPairKey tracked;

	private record BreedingPairKey(long day, UUID a, UUID b) {}

	private Courtship() {}

	static void forget() {
		refA = null;
		refB = null;
		seededSince = -1;
		tracked = null;
	}

	static void step(MinecraftServer server, VillageState village, License lic) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null || !level.isLoaded(new BlockPos(village.heart().x(), village.heart().y(), village.heart().z()))) return;
		long dayTime = level.getDayTime();
		if (lic.staleOn(DailyClock.dayIndex(dayTime))) {
			Growth.lapse(server, level, LapseReason.NEXT_DAWN);
			return;
		}
		BreedingPairKey key = new BreedingPairKey(lic.day(), lic.pair().a(), lic.pair().b());
		if (!key.equals(tracked)) {
			forget();
			tracked = key;
		}
		Villager a = parent(level, lic.pair().a(), true), b = parent(level, lic.pair().b(), false);
		if ((a == null && died(true)) || (b == null && died(false))) {
			Growth.lapse(server, level, LapseReason.PARENT_GONE);
			return;
		}
		if (a == null || b == null) return; // unloaded: wait
		if (a.isBaby() || b.isBaby() || AwayTrips.isAway(a) || AwayTrips.isAway(b) || !inClaim(village, level, a) || !inClaim(village, level, b)) {
			Growth.lapse(server, level, LapseReason.PARENT_GONE);
			return;
		}
		if (lic.state() == LicenceState.SEEDED) seeded(server, level, lic, a, b);
		else approach(server, village, level, lic, a, b, (int) Math.floorMod(dayTime, (long) Balance.DAY_TICKS));
	}

	private static boolean inClaim(VillageState village, ServerLevel level, Villager v) {
		return ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, v.blockPosition()));
	}

	private static Villager parent(ServerLevel level, UUID id, boolean first) {
		Entity e = level.getEntity(id);
		if (e instanceof Villager v && v.isAlive()) {
			if (first) refA = v; else refB = v;
			return v;
		}
		return null;
	}

	/** True when the last villager seen as this parent was removed for good (killed, discarded), not just unloaded. */
	private static boolean died(boolean first) {
		Villager ref = first ? refA : refB;
		return ref != null && ref.isRemoved() && ref.getRemovalReason() != null && ref.getRemovalReason().shouldDestroy();
	}

	// ---- WAITING / APPROACHING ---------------------------------------------------------------------------

	private static void approach(MinecraftServer server, VillageState village, ServerLevel level, License lic, Villager a, Villager b, int phase) {
		if (!lic.mayReseed()) {
			Growth.lapse(server, level, LapseReason.MAX_SEEDS);
			return;
		}
		if (lic.bed().isEmpty() || !BedPoi.isBedWithPoi(level, BlockPos.of(lic.bed().getAsLong()))) {
			// the reserved bed is gone (broken, or lost with an abort): another free House bed, or no baby today
			if (lic.holdsBed()) BedPoi.release(level, BlockPos.of(lic.bed().getAsLong()));
			OptionalLong nb = Growth.reserveBedFor(server, level, a, b);
			if (nb.isEmpty()) {
				Growth.lapse(server, level, LapseReason.NO_BED);
				return;
			}
			lic = lic.withBed(nb.getAsLong());
			Growth.setLicence(server, lic);
		}
		Kind ka = Growth.kindOf(a), kb = Growth.kindOf(b);
		boolean awake = !a.isSleeping() && !b.isSleeping();
		boolean idle = a.getBrain().isActive(Activity.IDLE) && b.getBrain().isActive(Activity.IDLE);
		boolean approaching = lic.state() == LicenceState.APPROACHING;
		if (!awake || !idle || !CourtshipWindow.idleAt(phase, ka) || !CourtshipWindow.idleAt(phase, kb) || !CourtshipWindow.canSeed(phase, ka, kb)) {
			// outside the window: wait. A pair that was already walking and has no seed window left today gives up.
			if (approaching && !CourtshipWindow.canStillSeedToday(phase, ka, kb)) Growth.lapse(server, level, LapseReason.CANNOT_MEET);
			return;
		}
		if (a.distanceToSqr(b) <= Balance.COURTSHIP_SEED_DISTANCE * Balance.COURTSHIP_SEED_DISTANCE
				&& BehaviorUtils.entityIsVisible(a.getBrain(), b) && BehaviorUtils.entityIsVisible(b.getBrain(), a)) {
			seed(server, level, lic, a, b);
			return;
		}
		if (!approaching) {
			lic = lic.approaching();
			Growth.setLicence(server, lic);
		}
		if (server.getTickCount() % WALK_PERIOD != 0) return;
		long now = level.getGameTime();
		if (cannotReach(a, now) || cannotReach(b, now)) {
			Growth.lapse(server, level, LapseReason.CANNOT_MEET);
			return;
		}
		walkTowards(a, b);
		walkTowards(b, a);
		lic = lic.tickApproach(WALK_PERIOD);
		if (lic.approachTicks() >= Balance.COURTSHIP_APPROACH_TICKS) {
			Growth.setLicence(server, lic);
			Growth.lapse(server, level, LapseReason.CANNOT_MEET);
			return;
		}
		Growth.setLicence(server, lic);
	}

	private static boolean cannotReach(Villager v, long now) {
		Optional<Long> since = v.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
		return since.isPresent() && now - since.get() > CANNOT_REACH_TICKS;
	}

	/** Re-issues the walk target only when it is absent or does not follow the partner (so the path is not recomputed every tick). */
	private static void walkTowards(Villager v, Villager partner) {
		Optional<WalkTarget> wt = v.getBrain().getMemory(MemoryModuleType.WALK_TARGET);
		boolean tracking = wt.isPresent() && wt.get().getTarget() instanceof EntityTracker t && t.getEntity() == partner;
		if (!tracking) BehaviorUtils.setWalkAndLookTargetMemories(v, partner, 0.5F, 1);
	}

	// ---- seeding ---------------------------------------------------------------------------------------

	private static void seed(MinecraftServer server, ServerLevel level, License lic, Villager a, Villager b) {
		int fa = ((VillagerFoodAccess) a).villageheart$getFoodLevel(), fb = ((VillagerFoodAccess) b).villageheart$getFoodLevel();
		Growth.topUpFood(a);
		Growth.topUpFood(b);
		a.getBrain().setMemory(MemoryModuleType.BREED_TARGET, b);
		lic = lic.seeded(lic.bed().getAsLong(), fa, fb);
		Growth.setLicence(server, lic);
		seededSince = level.getGameTime();
		VillageHeart.LOG.info("[growth] day {}: {} and {} are courting (seed {}, bed {})", lic.day(), a.getUUID(), b.getUUID(), lic.seeds(),
				BlockPos.of(lic.bed().getAsLong()).toShortString());
	}

	private static void seeded(MinecraftServer server, ServerLevel level, License lic, Villager a, Villager b) {
		if (seededSince < 0) seededSince = level.getGameTime(); // after a load: the clock starts now
		if (!a.getBrain().hasMemoryValue(MemoryModuleType.BREED_TARGET)) {
			// the behaviour state is not saved: after a reload the initiator forgot its target. Seed again without counting a seed.
			Growth.topUpFood(a);
			Growth.topUpFood(b);
			a.getBrain().setMemory(MemoryModuleType.BREED_TARGET, b);
			seededSince = level.getGameTime();
			return;
		}
		if (level.getGameTime() - seededSince > SEEDED_PATIENCE) {
			// the behaviour never ran to its end (it could not start, or it was switched off): count it as an abort
			a.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
			VillageHeart.LOG.warn("[growth] day {}: make-love of {} and {} did not end within {} ticks", lic.day(), a.getUUID(), b.getUUID(), SEEDED_PATIENCE);
			Growth.setLicence(server, lic.aborted());
			GrowthStats.abort();
			seededSince = -1;
		}
	}
}
