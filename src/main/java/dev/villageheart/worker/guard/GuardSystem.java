package dev.villageheart.worker.guard;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.guard.GuardBeds;
import dev.villageheart.core.guard.GuardRation;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import dev.villageheart.core.guard.GuardData;
import dev.villageheart.core.guard.WoundRule;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomContents;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Every 20 ticks (M18 3.5, 3.3): assigns the beds of each valid Barracks to its guards and sets their HOME, evaluates the wound bit from the
 * current health, ends a training day, feeds the guards (refill the ration, eat to heal: the only healing there is), re-ranks the shifts with
 * the guards' XP and re-applies the level perks (covers {@code Workers.setLevel}, which fires no listener, and a reload). The mod keeps the POI ticket of a Barracks bed; a guard does not
 * need one to sleep.
 */
public final class GuardSystem implements VillageSystem {
	public static final GuardSystem INSTANCE = new GuardSystem();
	private static final int INTERVAL = 20;

	/** Guard -> its Barracks bed (head block). Not saved: a pass rebuilds it from the guards' HOME memories. */
	private static final Map<UUID, Pos> BEDS = new HashMap<>();
		/** Guard -> its last known XP (the village state mirrors levels only; unloaded guards keep their last value). */
	private static final Map<UUID, Integer> XP = new HashMap<>();

	private GuardSystem() {}

	/** The Barracks bed assigned to this guard, if any. */
	public static Optional<BlockPos> bedOf(UUID guard) {
		Pos p = BEDS.get(guard);
		return p == null ? Optional.empty() : Optional.of(new BlockPos(p.x(), p.y(), p.z()));
	}

	/** The guard that bed belongs to, if any. */
	public static Optional<UUID> guardAt(BlockPos bed) {
		for (var e : BEDS.entrySet()) if (e.getValue().x() == bed.getX() && e.getValue().y() == bed.getY() && e.getValue().z() == bed.getZ()) return Optional.of(e.getKey());
		return Optional.empty();
	}

	public static void resetForTests() {
		BEDS.clear();
		XP.clear();
		GuardRations.resetForTests();
	}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.getTickCount() % INTERVAL != 0) return;
		try (var t = VillageTicker.section()) {
			assignBeds(server, village);
			GuardAlarm.refresh(server, village);
			for (UUID id : village.guardIds()) {
				Villager g = Workers.findLoaded(server, id).orElse(null);
				if (g == null || !(g.level() instanceof ServerLevel level) || !Guards.isGuard(g)) continue;
				Guards.applyPerks(g, false);
				GuardData d = GuardAttachments.get(g);
				WorkerData wd = g.getAttached(WorkerAttachments.WORKER);
				if (wd != null) XP.put(id, wd.xp());
				boolean hasBed = BEDS.containsKey(id);
				boolean wounded = hasBed && WoundRule.wounded(d.wounded(), g.getHealth(), g.getMaxHealth());
				long now = level.getDayTime();
				boolean qualifiedNow = d.trainingUntil() > 0 && !d.training(now);
				if (wounded != d.wounded() || qualifiedNow) {
					d = d.withWounded(wounded);
					if (qualifiedNow) d = d.withTrainingUntil(0);
					GuardAttachments.set(g, d);
				}
				feed(level, g, d);
			}
			int night = 0, day = 0;
			for (UUID id : village.guardIds()) {
				var s = village.guardShift(id);
				if (s.isPresent() && s.get().shift() == dev.villageheart.core.guard.Shift.NIGHT) night++; else day++;
			}
			dev.villageheart.worker.job.WorkStats.max("guard", "guards_night", night);
			dev.villageheart.worker.job.WorkStats.max("guard", "guards_day", day);
			// seniors take the nights: re-rank with the XP the mirror does not hold (M18 r3)
			var ranked = village.withGuardSeniority(XP);
			if (ranked != village) VillageClaims.data(server).update(s -> s.withGuardSeniority(XP));
		}
	}

	/** Food: refill a low ration when off duty or at the Barracks, and eat when hurt with no hostile near. No other healing exists (M18 r3). */
	private static void feed(ServerLevel level, Villager g, GuardData d) {
		boolean hurt = g.getHealth() < g.getMaxHealth();
		boolean wantsRefill = GuardRation.wantsRefill(d.ration());
		if (!hurt && !wantsRefill) return;
		if (!level.getEntitiesOfClass(Mob.class, g.getBoundingBox().inflate(Balance.GUARD_EAT_SAFE_DISTANCE), m -> m.isAlive() && m instanceof Enemy).isEmpty()) return;
		if (wantsRefill && (Guards.activityFor(g, level) != GuardActivities.DUTY || nearBarracks(g))) GuardRations.refill(level, g);
		if (hurt && server(g).getTickCount() % Balance.GUARD_EAT_TICKS == 0) GuardRations.bite(level, g);
	}

	private static boolean nearBarracks(Villager g) {
		WorkerData d = g.getAttached(WorkerAttachments.WORKER);
		if (d == null) return false;
		var b = d.building();
		return g.blockPosition().distSqr(new BlockPos(b.x(), b.y(), b.z())) <= 16 * 16;
	}

	private static MinecraftServer server(Villager g) { return g.level().getServer(); }

	private static void assignBeds(MinecraftServer server, VillageState village) {
		List<UUID> seen = new ArrayList<>();
		for (Buildings.BuildingView b : Buildings.valid(server, BuildingType.BARRACKS)) {
			if (b.latestScan().isEmpty()) continue;
			String dim = b.level().dimension().identifier().toString();
			HeartLocation building = new HeartLocation(dim, b.marker().getX(), b.marker().getY(), b.marker().getZ());
			List<UUID> guards = new ArrayList<>();
			for (WorkerRecord w : village.workers()) {
				if (w.jobId().equals(JobType.GUARD.id()) && w.building().equals(building)) guards.add(w.villager());
			}
			List<Pos> beds = new ArrayList<>(b.latestScan().get().positions(RoomContents.BED));
			Map<UUID, Pos> current = new HashMap<>();
			for (UUID id : guards) {
				Pos p = BEDS.get(id);
				if (p == null) p = homeBedOf(server, id, dim);
				if (p != null) current.put(id, p);
			}
			Map<UUID, Pos> assigned = GuardBeds.assign(beds, guards, current);
			for (UUID id : guards) {
				seen.add(id);
				Pos p = assigned.get(id);
				if (p == null) BEDS.remove(id); else BEDS.put(id, p);
				Villager g = Workers.findLoaded(server, id).orElse(null);
				if (g == null) continue;
				GlobalPos want = p == null ? null : GlobalPos.of(b.level().dimension(), new BlockPos(p.x(), p.y(), p.z()));
				Optional<GlobalPos> have = g.getBrain().getMemory(MemoryModuleType.HOME);
				if (want == null) {
					if (have.isPresent()) g.getBrain().eraseMemory(MemoryModuleType.HOME);
				} else if (have.isEmpty() || !have.get().equals(want)) {
					g.getBrain().setMemory(MemoryModuleType.HOME, want);
				}
			}
		}
		// guards of no valid Barracks (invalid building, fired): no bed
		BEDS.keySet().removeIf(id -> !seen.contains(id));
		XP.keySet().removeIf(id -> !village.guardIds().contains(id));
	}

	private static Pos homeBedOf(MinecraftServer server, UUID id, String dim) {
		Villager g = Workers.findLoaded(server, id).orElse(null);
		if (g == null) return null;
		Optional<GlobalPos> home = g.getBrain().getMemory(MemoryModuleType.HOME);
		if (home.isEmpty() || !home.get().dimension().identifier().toString().equals(dim)) return null;
		BlockPos p = home.get().pos();
		return new Pos(p.getX(), p.getY(), p.getZ());
	}
}
