package dev.villageheart.worker.gear;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.gear.CampDemand;
import dev.villageheart.core.gear.CancelCheck;
import dev.villageheart.core.gear.GearDemandPlanner;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.SelfCraft;
import dev.villageheart.core.gear.SlotPresence;
import dev.villageheart.core.gear.ToolDemand;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.worker.job.CampContainers;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;

/**
 * Every 100 ticks (M19a plan 3.4): runs the cancel check of every saved self-craft, also while the craft is paused (overnight, the worker
 * unloaded); refunds the cancelled ones and the ones waiting for a refund; refunds the records of a building that stays invalid for days;
 * releases the reservations of a fetch that stopped ticking (an unloaded villager gets no {@code stop}).
 */
public final class SelfCraftSystem implements VillageSystem {
	public static final SelfCraftSystem INSTANCE = new SelfCraftSystem();

	private SelfCraftSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		if (!SelfCraftSwitch.enabled()) return;
		if (server.getTickCount() % Balance.SELF_CANCEL_CHECK_TICKS != 0) return;
		long now = server.overworld().getGameTime();
		SelfCraftRuntime rt = SelfCraftRuntime.get(server);
		// a fetch whose worker stopped ticking (unloaded, or the behaviour never ended cleanly)
		for (Map.Entry<UUID, SelfCraftRuntime.State> e : new ArrayList<>(rt.workers.entrySet())) {
			SelfCraftRuntime.State st = e.getValue();
			if (st.fetching != null && now - st.fetchTick > Balance.SELF_FETCH_TIMEOUT) {
				VillageHeart.LOG.info("Self-craft fetch of {} stopped ticking: reservations released", e.getKey());
				SelfCraftJob.releaseStale(server, e.getKey(), st);
			}
		}
		for (SelfCraft sc : new ArrayList<>(village.selfCrafts())) {
			SelfCraftRuntime.State st = rt.state(sc.worker());
			JobType job = jobOf(sc);
			if (sc.refundPending()) {
				if (SelfCrafts.refund(server, sc)) countCancelled(job);
				continue;
			}
			SlotPresence presence = presenceOf(server, village, sc, job, st, now);
			CancelCheck c = GearDemandPlanner.cancel(presence, st.cancelCount);
			st.cancelCount = c.consecutiveDefinite();
			if (c.reason().isPresent()) {
				st.cancelReason = c.reason().get();
				SelfCraftJob.cancel(server, sc, job, st);
			}
		}
	}

	private static void countCancelled(JobType job) { dev.villageheart.worker.job.WorkStats.add(job.id(), "tools_cancelled", 1); }

	private static JobType jobOf(SelfCraft sc) {
		return switch (sc.craft().key().need()) {
			case AXE -> JobType.LUMBERJACK;
			case PICKAXE -> JobType.MINER;
			case SWORD -> JobType.HUNTER;
			case HOE -> JobType.FARMER;
		};
	}

	private static SlotPresence presenceOf(MinecraftServer server, VillageState village, SelfCraft sc, JobType job, SelfCraftRuntime.State st, long now) {
		var building = sc.craft().key().building();
		if (!ToolCensus.registered(village, building)) return SlotPresence.BUILDING_UNREGISTERED;
		Optional<WorkerRecord> rec = village.worker(sc.worker());
		if (rec.isEmpty() || !rec.get().building().equals(building) || !rec.get().jobId().equals(job.id())) return SlotPresence.HIRED_DROPPED;
		// a building that stays registered but invalid for days refunds its records; a merely unloaded one never does
		Optional<dev.villageheart.building.Buildings.BuildingView> view = CampContainers.building(server, building);
		if (view.isPresent() && view.get().status() == BuildingStatus.INVALID) {
			if (st.invalidSince < 0) st.invalidSince = now;
			if (now - st.invalidSince >= (long) Balance.SELF_CRAFT_INVALID_REFUND_DAYS * Balance.DAY_TICKS) return SlotPresence.BUILDING_UNREGISTERED;
			return SlotPresence.UNKNOWN;
		}
		st.invalidSince = -1;
		CampDemand census = ToolCensus.of(server, village, building, job, GearOrders.crafting(), GearLead.forCamp(server, village, building, job));
		if (census.isUnknown()) return SlotPresence.UNKNOWN;
		GearNeed need = sc.craft().key().need();
		return ToolDemand.presence(census, GearOrders.openOrders(village, building, need)).getOrDefault(sc.craft().key(), SlotPresence.COVERED);
	}
}
