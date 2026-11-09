package dev.villageheart.worker.guard;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.guard.GuardBeds;
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
 * current health, heals a guard asleep in its own bed ({@link Balance#GUARD_SLEEP_HEAL_TICKS} per half heart) and re-applies the level perks
 * (covers {@code Workers.setLevel}, which fires no listener, and a reload). The mod keeps the POI ticket of a Barracks bed; a guard does not
 * need one to sleep.
 */
public final class GuardSystem implements VillageSystem {
	public static final GuardSystem INSTANCE = new GuardSystem();
	private static final int INTERVAL = 20;

	/** Guard -> its Barracks bed (head block). Not saved: a pass rebuilds it from the guards' HOME memories. */
	private static final Map<UUID, Pos> BEDS = new HashMap<>();
	/** Guard -> game time of its last sleep heal. */
	private static final Map<UUID, Long> LAST_HEAL = new HashMap<>();

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
		LAST_HEAL.clear();
	}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (server.getTickCount() % INTERVAL != 0) return;
		try (var t = VillageTicker.section()) {
			assignBeds(server, village);
			for (UUID id : village.guardIds()) {
				Villager g = Workers.findLoaded(server, id).orElse(null);
				if (g == null || !(g.level() instanceof ServerLevel level) || !Guards.isGuard(g)) continue;
				Guards.applyPerks(g, false);
				GuardData d = GuardAttachments.get(g);
				boolean hasBed = BEDS.containsKey(id);
				boolean wounded = hasBed && WoundRule.wounded(d.wounded(), g.getHealth(), g.getMaxHealth());
				if (wounded != d.wounded()) GuardAttachments.set(g, new GuardData(wounded));
				healInBed(level, g);
			}
		}
	}

	private static void healInBed(ServerLevel level, Villager g) {
		BlockPos bed = bedOf(g.getUUID()).orElse(null);
		if (bed == null || !g.isSleeping() || g.getHealth() >= g.getMaxHealth()) return;
		if (g.getSleepingPos().filter(bed::equals).isEmpty()) return;
		long now = level.getGameTime();
		Long last = LAST_HEAL.get(g.getUUID());
		if (last == null || now - last >= Balance.GUARD_SLEEP_HEAL_TICKS) {
			if (last != null) g.heal(1.0F);
			LAST_HEAL.put(g.getUUID(), now);
		}
	}

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
		LAST_HEAL.keySet().removeIf(id -> !seen.contains(id));
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
