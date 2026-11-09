package dev.villageheart.growth;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.economy.PayResult;
import dev.villageheart.core.food.FoodMath;
import dev.villageheart.core.growth.BreedingPair;
import dev.villageheart.core.growth.Candidate;
import dev.villageheart.core.growth.Decision;
import dev.villageheart.core.growth.GrowthRules;
import dev.villageheart.core.growth.GrowthState;
import dev.villageheart.core.growth.Kind;
import dev.villageheart.core.growth.LapseReason;
import dev.villageheart.core.growth.License;
import dev.villageheart.core.growth.Reason;
import dev.villageheart.core.growth.Role;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.economy.VillageDawn;
import dev.villageheart.food.DietAttachments;
import dev.villageheart.food.Dinner;
import dev.villageheart.mixin.VillagerFoodAccess;
import dev.villageheart.needs.BedPoi;
import dev.villageheart.needs.Housing;
import dev.villageheart.needs.NeedsOverride;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystems;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.AwayTrips;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalLong;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Population growth (M23). The village does not breed by itself: at each dawn {@link #onDawn} decides, from the free House beds, the food
 * days and the fed and housed adults, whether one pair gets a licence for today; {@link Courtship} walks the pair to each other and seeds
 * vanilla's make-love behaviour; {@link BreedingGate} stops every other villager in the claim from breeding. Server thread only.
 */
public final class Growth {
	public static final String ID = "villageheart:growth";

	private Growth() {}

	/** Wires the dawn listener (after M14's tiers) and the per-tick system. */
	public static void register() {
		VillageDawn.register(ID, Growth::onDawn);
		VillageSystems.register(ID, GrowthSystem.INSTANCE);
		ServerLifecycleEvents.SERVER_STOPPED.register(s -> reset());
	}

	/** Forgets the transient state (server stop, GameTest reset). */
	public static void reset() {
		BreedingGate.reset();
		Courtship.forget();
	}

	// ---- saved state -----------------------------------------------------------------------------

	public static GrowthState state(MinecraftServer server) { return VillageClaims.data(server).growth(); }

	static void save(MinecraftServer server, GrowthState g) {
		VillageClaims.data(server).setGrowth(g);
		refreshGate(server);
	}

	static void setLicence(MinecraftServer server, License l) { save(server, state(server).withLicence(l)); }

	/** Points the gate at the current claim and today's licence. */
	public static void refreshGate(MinecraftServer server) {
		Optional<VillageState> v = VillageClaims.current(server);
		if (v.isEmpty()) {
			BreedingGate.reset();
			return;
		}
		HeartLocation h = v.get().heart();
		ServerLevel level = Workers.levelOf(server, h);
		if (level == null) {
			BreedingGate.reset();
			return;
		}
		BreedingGate.set(level.dimension(), h.x(), h.y(), h.z(), state(server).licence());
	}

	// ---- the dawn --------------------------------------------------------------------------------

	static void onDawn(MinecraftServer server, VillageState village, long day, PayResult wages) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return;
		GrowthState gs = endLicence(server, level, state(server), LapseReason.NEXT_DAWN).pruned(day);
		if (!NeedsOverride.growthEnabled) {
			save(server, gs);
			return;
		}
		Housing.rebuild(server);
		List<Villager> census = VillageCensus.of(server, village);
		List<Candidate> candidates = new ArrayList<>();
		for (Villager v : census) if (isResident(v, level)) candidates.add(candidate(server, village, v));
		VillageCensus.Headcount hc = VillageCensus.headcount(server, village);
		OptionalDouble days = FoodMath.daysLeft(Dinner.nutrition(server), hc.adults(), hc.babies());
		List<long[]> beds = freeHouseBeds(server, level);
		Decision d = GrowthRules.decide(beds.size(), days, candidates, gs.skipPairs());
		Reason reason = d.reason();
		if (d.pair().isPresent()) {
			BreedingPair p = d.pair().get();
			Villager a = find(census, p.a()), b = find(census, p.b());
			OptionalLong bed = a == null || b == null ? OptionalLong.empty() : reserveBed(level, a, b, beds);
			if (bed.isPresent()) gs = gs.withLicence(License.issue(day, p, bed));
			else reason = Reason.NO_FREE_BED;
		}
		save(server, gs);
		int ready = (int) candidates.stream().filter(Candidate::ready).count();
		double dayValue = days.isPresent() ? days.getAsDouble() : -1;
		VillageHeart.LOG.info("[growth] day {}: {} (free House beds {}, food days {}, ready {})", day, reason, beds.size(),
				days.isPresent() ? String.format("%.1f", dayValue) : "-", ready);
		GrowthStats.dawn(new GrowthStats.Dawn(day, reason, beds.size(), dayValue, ready));
	}

	private static Villager find(List<Villager> census, java.util.UUID id) {
		for (Villager v : census) if (v.getUUID().equals(id)) return v;
		return null;
	}

	/** An adult that can be a parent: not a baby, not away on a trip, not frozen, in the Heart's level. */
	static boolean isResident(Villager v, ServerLevel level) {
		return !v.isBaby() && !AwayTrips.isAway(v) && !v.isNoAi() && v.level() == level;
	}

	static Candidate candidate(MinecraftServer server, VillageState village, Villager v) {
		Optional<HeartLocation> own = Optional.ofNullable(v.getAttached(WorkerAttachments.WORKER)).map(WorkerData::building);
		boolean fed = DietAttachments.read(v, village.claimedAtGameTime()).fed();
		boolean housed = Housing.housed(server, v, own);
		return new Candidate(v.getUUID(), v.getX(), v.getY(), v.getZ(), GrowthRules.ready(fed, housed, v.getAge()), kindOf(v));
	}

	/** The role and meeting point decide when the villager is in its IDLE activity (plan 3.2 #5). */
	static Kind kindOf(Villager v) {
		Role role = WorkerAttachments.isWorker(v) ? Role.WORKER
				: v.getBrain().hasMemoryValue(MemoryModuleType.JOB_SITE) ? Role.PROFESSIONAL : Role.UNEMPLOYED;
		return new Kind(role, v.getBrain().hasMemoryValue(MemoryModuleType.MEETING_POINT));
	}

	// ---- beds ------------------------------------------------------------------------------------

	/** Free beds of valid Houses (loaded, a bed nobody lies in, a HOME POI with a free ticket): {packed pos, x, y, z}. */
	static List<long[]> freeHouseBeds(MinecraftServer server, ServerLevel level) {
		List<long[]> out = new ArrayList<>();
		for (long packed : Housing.beds(server).houseBeds()) {
			BlockPos p = BlockPos.of(packed);
			if (BedPoi.isFreeBed(level, p)) out.add(new long[] {packed, p.getX(), p.getY(), p.getZ()});
		}
		return out;
	}

	/** Takes the ticket of the free House bed nearest to the pair's midpoint (plan 3.2 #6). */
	static OptionalLong reserveBed(ServerLevel level, Villager a, Villager b, List<long[]> free) {
		double mx = (a.getX() + b.getX()) / 2, my = (a.getY() + b.getY()) / 2, mz = (a.getZ() + b.getZ()) / 2;
		List<long[]> rest = new ArrayList<>(free);
		while (!rest.isEmpty()) {
			int i = GrowthRules.nearestBed(mx, my, mz, rest).orElseThrow();
			long[] bed = rest.remove(i);
			if (BedPoi.take(level, BlockPos.of(bed[0]))) return OptionalLong.of(bed[0]);
		}
		return OptionalLong.empty();
	}

	static OptionalLong reserveBedFor(MinecraftServer server, ServerLevel level, Villager a, Villager b) {
		Housing.rebuild(server);
		return reserveBed(level, a, b, freeHouseBeds(server, level));
	}

	/**
	 * Test and command hook: licenses {@code a} and {@code b} for today as the dawn would (a House bed reserved), without the checks.
	 * Empty when no House bed is free.
	 */
	public static Optional<License> issueLicence(MinecraftServer server, VillageState village, long day, Villager a, Villager b) {
		ServerLevel level = Workers.levelOf(server, village.heart());
		if (level == null) return Optional.empty();
		GrowthState gs = endLicence(server, level, state(server), LapseReason.NEXT_DAWN);
		OptionalLong bed = reserveBedFor(server, level, a, b);
		if (bed.isEmpty()) {
			save(server, gs);
			return Optional.empty();
		}
		License l = License.issue(day, BreedingPair.of(a.getUUID(), b.getUUID()), bed);
		save(server, gs.withLicence(l));
		return Optional.of(l);
	}

	// ---- ending a licence ----------------------------------------------------------------------------

	/** Lapses the active licence in the saved state (if any) and returns the new state without saving it. */
	static GrowthState endLicence(MinecraftServer server, ServerLevel level, GrowthState gs, LapseReason reason) {
		License l = gs.licence().filter(License::active).orElse(null);
		if (l == null) return gs;
		if (l.holdsBed()) BedPoi.release(level, BlockPos.of(l.bed().getAsLong()));
		Villager a = loaded(level, l.pair().a()), b = loaded(level, l.pair().b());
		if (l.seeds() > 0) {
			restoreFood(a, l.priorFoodA());
			restoreFood(b, l.priorFoodB());
		}
		clearBreedTarget(a, b);
		clearBreedTarget(b, a);
		GrowthState next = gs.withLicence(l.lapsed(reason));
		if (reason == LapseReason.CANNOT_MEET) next = next.withFailed(l.pair(), l.day());
		GrowthStats.lapse(reason);
		VillageHeart.LOG.info("[growth] day {}: licence for {} and {} lapsed: {} (seeds {}, aborts {})", l.day(), l.pair().a(), l.pair().b(), reason,
				l.seeds(), l.aborts());
		return next;
	}

	/** Lapses today's licence and saves. */
	static void lapse(MinecraftServer server, ServerLevel level, LapseReason reason) {
		save(server, endLicence(server, level, state(server), reason));
	}

	/** The claim is released: give back the ticket and the villagers' food before the saved state goes. */
	public static void onRelease(MinecraftServer server) {
		VillageClaims.current(server).ifPresent(v -> {
			ServerLevel level = Workers.levelOf(server, v.heart());
			if (level != null) endLicence(server, level, state(server), LapseReason.RELEASED);
		});
		BreedingGate.reset();
		Courtship.forget();
	}

	static Villager loaded(ServerLevel level, java.util.UUID id) {
		return level.getEntity(id) instanceof Villager v && v.isAlive() ? v : null;
	}

	/** Test hook: replaces today's licence as it is (no checks, no ticket taken). */
	public static void forceLicenceForTest(MinecraftServer server, License l) { setLicence(server, l); }

	/** Raises the villager's vanilla food level to the breeding threshold. */
	public static void topUpFood(Villager v) {
		VillagerFoodAccess f = (VillagerFoodAccess) v;
		if (f.villageheart$getFoodLevel() < Villager.BREEDING_FOOD_THRESHOLD) f.villageheart$setFoodLevel(Villager.BREEDING_FOOD_THRESHOLD);
	}

	private static void restoreFood(Villager v, int prior) {
		if (v == null) return;
		VillagerFoodAccess f = (VillagerFoodAccess) v;
		f.villageheart$setFoodLevel(Math.min(f.villageheart$getFoodLevel(), prior));
	}

	private static void clearBreedTarget(Villager v, Villager partner) {
		if (v == null) return;
		Optional<AgeableMob> t = v.getBrain().getMemory(MemoryModuleType.BREED_TARGET);
		if (t.isPresent() && (partner == null || t.get() == partner)) v.getBrain().eraseMemory(MemoryModuleType.BREED_TARGET);
	}

	// ---- hooks of the make-love behaviour (VillagerMakeLoveMixin) -------------------------------------

	/** The licence whose initiator is {@code v}, still SEEDED with a reserved bed; empty for anybody else. */
	public static Optional<License> seededLicenceOf(Villager v) {
		return BreedingGate.licenceOf(v).filter(l -> l.state() == dev.villageheart.core.growth.LicenceState.SEEDED && l.pair().a().equals(v.getUUID()));
	}

	/**
	 * Vanilla's {@code takeVacantBed}: a licensed initiator gets the bed reserved at dawn if it is still a bed with a HOME POI (the ticket stays
	 * ours until the baby's HOME is set); an unusable bed returns empty (vanilla's NO_BED path). Empty result outside a licence.
	 */
    public static Optional<Optional<BlockPos>> bedForBirth(ServerLevel level, Villager v) {
		Optional<License> l = seededLicenceOf(v);
		if (l.isEmpty()) return Optional.empty();
		MinecraftServer server = level.getServer();
		if (l.get().bed().isEmpty() || !BedPoi.isBedWithPoi(level, BlockPos.of(l.get().bed().getAsLong()))) return Optional.of(Optional.empty());
		BlockPos bed = BlockPos.of(l.get().bed().getAsLong());
		if (!l.get().handedOver()) setLicence(server, l.get().handOver());
		return Optional.of(Optional.of(bed));
	}

	/** A bed vanilla took for an unlicensed villager outside the claim lies inside it: give it back (plan 3.2 #6). */
	public static boolean mustDropBed(ServerLevel level, Villager v, BlockPos bed) {
		boolean drop = !seededLicenceOf(v).isPresent() && BreedingGate.claimContains(level.dimension(), bed);
		if (drop) GrowthStats.droppedBed();
		return drop;
	}

	/** The behaviour set a baby's HOME: it counts only for the licence that reserved this bed. */
	public static void onBirth(ServerLevel level, Villager child, BlockPos bed) {
		MinecraftServer server = level.getServer();
		GrowthState gs = state(server);
		License l = gs.licence().filter(x -> x.active() && x.bed().isPresent() && x.bed().getAsLong() == bed.asLong() && x.handedOver()).orElse(null);
		if (l == null) {
			GrowthStats.unlicensedBirth();
			VillageHeart.LOG.error("[growth] a baby ({}) was born without a licence at bed {}", child.getUUID(), bed.toShortString());
			return;
		}
		License born = l.born(child.getUUID());
		save(server, gs.afterBirth(l.day(), born));
		GrowthStats.birth();
		Courtship.forget();
		VillageHeart.LOG.info("[growth] day {}: {} and {} had a baby {} (bed {})", l.day(), l.pair().a(), l.pair().b(), child.getUUID(), bed.toShortString());
	}

	/** A make-love run ended without a baby. A licensed initiator's licence goes back to approaching (plan 3.2 #5, abort). */
	public static void onRunStopped(ServerLevel level, Villager self, AgeableMob partner, BreedTrace.Abort reason, double distance) {
		Optional<License> l = seededLicenceOf(self);
		BreedTrace.abort(reason, distance, l.isPresent());
		if (l.isEmpty()) return;
		MinecraftServer server = level.getServer();
		setLicence(server, l.get().aborted());
		GrowthStats.abort();
		VillageHeart.LOG.warn("[growth] day {}: make-love of {} and {} ended without a baby: {} (distance {}, seed {}, aborts {})", l.get().day(),
				l.get().pair().a(), l.get().pair().b(), reason, String.format("%.2f", distance), l.get().seeds(), l.get().aborts() + 1);
	}

	static Entity entity(ServerLevel level, java.util.UUID id) { return level.getEntity(id); }
}
