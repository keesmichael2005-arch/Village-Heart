package dev.villageheart.core.smith;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.gear.CampNeed;
import dev.villageheart.core.gear.CancelCheck;
import dev.villageheart.core.gear.CraftPlan;
import dev.villageheart.core.gear.Deficit;
import dev.villageheart.core.gear.GearDemand;
import dev.villageheart.core.gear.GearDemandPlanner;
import dev.villageheart.core.gear.GearInfo;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.GearOrder;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.OpenOrder;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SlotPresence;
import dev.villageheart.core.gear.SlotState;
import dev.villageheart.core.gear.Urgency;
import dev.villageheart.core.smith.SmithDecision.Hint;
import dev.villageheart.core.smith.SmithFacts.RepairJob;
import dev.villageheart.core.smith.SmithFacts.Trip;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The smith's day, first match wins (M19 plan 3.2 #7). A craft in progress is only pre-empted by a hurt golem. A saved golem build is
 * pre-empted by a missing tool or by a guard without a weapon or armour, and not by a worn tool or a hoe.
 */
public final class SmithPlanner {
	private static final Set<Urgency> URGENT = Set.of(Urgency.U1_MISSING_REQUIRED, Urgency.U2_GUARD_WEAPON, Urgency.U3_GUARD_ARMOUR);
	private static final Set<Urgency> LATER = Set.of(Urgency.U4_WORN_REQUIRED, Urgency.U5_HOE);
	private static final Set<Urgency> BLOCKS_GOLEM = Set.of(Urgency.U1_MISSING_REQUIRED, Urgency.U2_GUARD_WEAPON, Urgency.U3_GUARD_ARMOUR, Urgency.U4_WORN_REQUIRED);

	private SmithPlanner() {}

	public static SmithDecision decide(SmithFacts f) {
		if (!f.smithyValid) return new SmithDecision.Idle(Hint.OK, "", true);
		if (f.speed <= 0) return new SmithDecision.Idle(Hint.TOO_HUNGRY, "", false);
		if (f.depositNow && f.trip != Trip.FULL && f.trip != Trip.UNREACHABLE) return new SmithDecision.Yield();
		if (f.dayTime >= Balance.WORK_END) return new SmithDecision.Idle(Hint.NO_WORK, "", false);

		if (f.golemWaiting.isPresent() && f.golemIngotsReady) return new SmithDecision.RepairGolem(f.golemWaiting.getAsInt());
		if (f.carriedTool.isPresent() && !f.deliveryBlocked.contains(f.carriedTool.get())) return new SmithDecision.Deliver(f.carriedTool.get());

		// A saved craft owns this furnace until it is finished or cancelled. UNKNOWN pauses it and never frees the furnace, and a craft
		// whose refund is pending is never resumed (the refund system gives its items back); neither lets a new craft be committed.
		if (f.savedCraft.isPresent() && !f.craftRefundPending) {
			CancelCheck check = GearDemandPlanner.cancel(f.craftPresence, f.craftCancelCount);
			if (check.reason().isPresent()) return new SmithDecision.CancelCraft(check.reason().get());
			if (f.craftPresence != SlotPresence.UNKNOWN) return new SmithDecision.Craft(check.consecutiveDefinite());
		}

		Optional<SmithDecision> urgent = takeOrder(f, URGENT);
		if (urgent.isPresent()) return urgent.get();
		if (f.buildSaved && f.buildOwnedByMe) return new SmithDecision.BuildGolem();

		if (f.chestRepair != null && repairable(f, f.chestRepair, Balance.SMITH_DONE_BY))
			return new SmithDecision.RepairInPlace(f.chestRepair);
		List<RepairJob> fetch = fetchable(f);
		if (!fetch.isEmpty()) return new SmithDecision.FetchForRepair(fetch);

		Optional<SmithDecision> later = takeOrder(f, LATER);
		if (later.isPresent()) return later.get();

		if (!f.buildSaved && !f.golemPending && !hasOrderable(f, BLOCKS_GOLEM) && f.golemAllowed && golemMats(f) && f.dayTime <= Balance.GOLEM_LAST_START)
			return new SmithDecision.StartGolem();

		SmithBatch.Plan batch = smelt(f);
		if (batch.step().isPresent()) return new SmithDecision.Smelt(batch.step().get());
		return idle(f, batch.needsFuel());
	}

	/** A golem repair leaves the smelt, craft or repair it interrupted as the current job. */
	public static boolean keepsCurrent(SmithDecision d) { return d instanceof SmithDecision.RepairGolem; }

	/** The batch to resume after {@code d}. Empty when {@code d} replaces it. */
	public static Optional<SmithBatch.Step> currentAfter(SmithFacts f, SmithDecision d) {
		return keepsCurrent(d) ? f.currentBatch : Optional.empty();
	}

	/** True when every input of the plan is in hand. A short fetch is not committed; the glue re-plans. */
	public static boolean readyToCommit(CraftPlan plan, Map<String, Integer> carried) {
		for (ItemCount c : plan.take()) if (carried.getOrDefault(c.id(), 0) < c.count()) return false;
		return true;
	}

	/**
	 * A smith with no craft of its own takes the first orphaned furnace of the same Smithy. The caller re-keys that craft onto {@code mine}.
	 */
	public static Optional<HeartLocation> adoptFurnace(HeartLocation mine, boolean ownCraft, List<HeartLocation> orphanFurnaces) {
		if (ownCraft) return Optional.empty();
		for (HeartLocation furnace : orphanFurnaces) if (!furnace.equals(mine)) return Optional.of(furnace);
		return Optional.empty();
	}

	private static Optional<SmithDecision> takeOrder(SmithFacts f, Set<Urgency> classes) {
		Optional<SmithDecision> fromStorage = deliverStored(f, classes);
		if (fromStorage.isPresent()) return fromStorage;
		Map<CampNeed, List<OpenOrder>> open = new HashMap<>();
		for (var e : f.openByCamp.entrySet()) open.put(e.getKey(), new ArrayList<>(e.getValue()));
		for (int guard = 0; guard < 8; guard++) {
			Optional<GearOrder> found = GearDemandPlanner.next(f.demands, open, classes, f.materials, f.smeltableUnits, f.gear, f.settings);
			if (found.isEmpty()) return Optional.empty();
			GearOrder order = found.get();
			OrderKey key = order.key();
			if (f.deliveryBlocked.contains(key)) {
				hold(open, key);
				continue;
			}
			String stored = bestStored(f, key.need());
			if (stored != null) {
				if (endsBy(f, f.walkTicks, Balance.SMITH_DONE_BY)) return Optional.of(new SmithDecision.FetchAndDeliver(key, stored));
				hold(open, key);
				continue;
			}
			if (f.savedCraft.isEmpty() && endsBy(f, f.walkTicks + prerequisiteTicks(order), Balance.SMITH_DONE_BY))
				return Optional.of(new SmithDecision.StartCraft(order));
			hold(open, key);
		}
		return Optional.empty();
	}

	/**
	 * A tool already in Storage is carried over even when the stockpile can no longer afford a new one (the camp chest was full, so the
	 * axe just made is sitting in Storage and the planks are gone).
	 */
	private static Optional<SmithDecision> deliverStored(SmithFacts f, Set<Urgency> classes) {
		List<GearDemand> sorted = new ArrayList<>(f.demands);
		sorted.sort(Comparator.comparingInt((GearDemand d) -> d.urgency().rank())
				.thenComparing(d -> d.key().building().dimension())
				.thenComparingInt(d -> d.key().building().x())
				.thenComparingInt(d -> d.key().building().y())
				.thenComparingInt(d -> d.key().building().z())
				.thenComparingInt(d -> d.key().slot()));
		for (GearDemand d : sorted) {
			if (!classes.contains(d.urgency()) || d.state() == SlotState.UNKNOWN) continue;
			if (f.deliveryBlocked.contains(d.key())) continue;
			CampNeed camp = new CampNeed(d.key().building(), d.key().need());
			List<OpenOrder> open = f.openByCamp.getOrDefault(camp, List.of());
			int slots = 0;
			for (GearDemand other : f.demands)
				if (other.key().building().equals(camp.building()) && other.key().need() == camp.need()) slots++;
			if (open.size() >= slots) continue;
			boolean taken = false;
			for (OpenOrder o : open) if (o.key().equals(d.key())) taken = true;
			if (taken || !lowestFree(d, open)) continue;
			String item = bestStored(f, d.key().need());
			if (item != null && endsBy(f, f.walkTicks, Balance.SMITH_DONE_BY))
				return Optional.of(new SmithDecision.FetchAndDeliver(d.key(), item));
		}
		return Optional.empty();
	}

	/** True when no open order uses a lower slot than this demand. */
	private static boolean lowestFree(GearDemand d, List<OpenOrder> open) {
		for (int s = 0; s < d.key().slot(); s++) {
			OrderKey lower = new OrderKey(d.key().building(), d.key().need(), s);
			boolean used = false;
			for (OpenOrder o : open) if (o.key().equals(lower)) used = true;
			if (!used) return false;
		}
		return true;
	}

	/** A blocked or time-gated key is set aside for this pass only, so a later demand of the same class can still be taken. */
	private static void hold(Map<CampNeed, List<OpenOrder>> open, OrderKey key) {
		open.computeIfAbsent(new CampNeed(key.building(), key.need()), k -> new ArrayList<>()).add(new OpenOrder(key, 0, 0));
	}

	private static int prerequisiteTicks(GearOrder order) {
		int ticks = 0;
		for (SmithRecipe p : order.prerequisites()) ticks += p.ticksPerUnit();
		return ticks;
	}

	private static String bestStored(SmithFacts f, GearNeed need) {
		String best = null;
		int rank = -1;
		for (String id : f.storageTools) {
			GearInfo info = GearInfo.of(id).orElse(null);
			if (info == null || info.need() != need || info.rank() <= rank) continue;
			best = id;
			rank = info.rank();
		}
		return best;
	}

	private static boolean endsBy(SmithFacts f, int ticks, int deadline) {
		return f.speed > 0 && f.dayTime + ticks / f.speed <= deadline;
	}

	private static boolean repairable(SmithFacts f, RepairJob job, int deadline) {
		if (job.units() < 1) return false;
		if (f.stock.getOrDefault(job.materialId(), 0L) < job.units()) return false;
		return endsBy(f, job.units() * Balance.SMITH_REPAIR_TICKS, deadline);
	}

	private static List<RepairJob> fetchable(SmithFacts f) {
		int deadline = Balance.SMITH_DONE_BY - 1000;
		List<RepairJob> out = new ArrayList<>();
		for (RepairJob job : f.fetchRepairs) {
			if (out.size() >= Balance.SMITH_REPAIR_CARRY) break;
			if (repairable(f, job, deadline)) out.add(job);
		}
		return out;
	}

	private static boolean hasOrderable(SmithFacts f, Set<Urgency> classes) {
		return GearDemandPlanner.next(f.demands, f.openByCamp, classes, f.materials, f.smeltableUnits, f.gear, f.settings).isPresent();
	}

	private static boolean golemMats(SmithFacts f) {
		return f.stock.getOrDefault("minecraft:iron_ingot", 0L) >= Balance.GOLEM_INGOTS
				&& f.stock.getOrDefault("minecraft:carved_pumpkin", 0L) >= 1;
	}

	private static SmithBatch.Plan smelt(SmithFacts f) {
		return SmithBatch.plan(f.stock, f.fuelCount, f.fuelHeat, f.furnaceBank, f.cookHired, f.freeSlots, f.dayTime, f.speed, f.walkTicks);
	}

	private static SmithDecision idle(SmithFacts f, boolean needsFuel) {
		List<Deficit> defs = GearDemandPlanner.deficits(f.demands, f.materials, f.smeltableUnits, f.gear, f.settings);
		boolean capOnly = golemMats(f) && !f.golemAllowed && !hasOrderable(f, BLOCKS_GOLEM);
		if (!defs.isEmpty()) return new SmithDecision.Idle(Hint.NEEDS_MATERIAL, defs.get(0).cheapestMaterial(), false);
		if (capOnly && !needsFuel) return new SmithDecision.Idle(Hint.NEEDS_VILLAGERS, "", false);
		if (needsFuel) return new SmithDecision.Idle(Hint.NEEDS_FUEL, "", false);
		return new SmithDecision.Idle(Hint.NO_WORK, "", false);
	}
}
