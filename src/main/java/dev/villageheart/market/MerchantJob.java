package dev.villageheart.market;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.job.WorkClock;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.market.SaleDecision;
import dev.villageheart.core.worker.WorkGate;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.phys.Vec3;

/**
 * The merchant at its stall (M22 plan 3.2 #1, #7, #8). Every 20 ticks it re-plans on the shared view and sets its status; while a sale is
 * possible its work clock runs, and a finished unit becomes a pending sale that {@link Markets#trySell} carries out on a fresh view. Transient
 * state only (a part-unit lost on restart costs less than one sale).
 */
public final class MerchantJob {
	private static final int PLAN_INTERVAL = 20;
	private static final Map<UUID, State> STATES = new HashMap<>();

	private MerchantJob() {}

	private static final class State {
		final WorkClock clock = new WorkClock(Balance.MERCHANT_SALE_UNITS);
		boolean pending;
		SaleDecision plan;
		long planAt = Long.MIN_VALUE;
		long holdUntil;
		int awayTicks;
		/** The last attempt came up short at the chests: the status stays STORAGE_UNREACHABLE until a sale succeeds. */
		boolean shortAtChests;
		boolean slotOk = true;
	}

	public static void forget(UUID villager) { STATES.remove(villager); }

	static void clearAll() { STATES.clear(); }

	private static State state(UUID id) { return STATES.computeIfAbsent(id, k -> new State()); }

	/** Test hook: the merchant's current plan, or null. */
	public static SaleDecision planOf(UUID villager) {
		State s = STATES.get(villager);
		return s == null ? null : s.plan;
	}

	static Optional<BlockPos> stallIn(ServerLevel level, WorkerData data) {
		HeartLocation b = data.jobBlock();
		if (!b.dimension().equals(level.dimension().identifier().toString())) return Optional.empty();
		return Optional.of(new BlockPos(b.x(), b.y(), b.z()));
	}

	public static void tick(ServerLevel level, Villager v, long now) {
		WorkerData data = v.getAttached(WorkerAttachments.WORKER);
		if (data == null) return;
		Optional<BlockPos> stall = stallIn(level, data);
		if (stall.isEmpty()) return;
		State s = state(v.getUUID());
		double dist = v.position().distanceTo(Vec3.atBottomCenterOf(stall.get()));
		if (dist > Balance.JOB_BLOCK_REACH) {
			if (++s.awayTicks >= Balance.MERCHANT_STALL_PATIENCE) WorkerStatuses.set(v.getUUID(), WorkerStatus.SITE_UNREACHABLE);
			return;
		}
		s.awayTicks = 0;
		if (WorkerStatuses.get(v.getUUID()) == WorkerStatus.SITE_UNREACHABLE) WorkerStatuses.set(v.getUUID(), null);
		if (!WorkGate.mayWork(data.needs())) {
			WorkerStatuses.set(v.getUUID(), null); // Workers.status shows the strike or the hunger
			return;
		}
		if (s.plan == null || now - s.planAt >= PLAN_INTERVAL || now < s.planAt) replan(level, v, data, s, now);
		if (!s.slotOk) {
			s.clock.reset();
			return;
		}
		if (s.pending) {
			if (s.plan instanceof SaleDecision.Stop || now < s.holdUntil) return; // frozen until a refresh says Sell
			attempt(level, v, stall.get(), s, now);
			return;
		}
		if (!(s.plan instanceof SaleDecision.Sell<?>)) return;
		if (s.clock.tick(WorkSpeed.untooled(data.level(), data.needs())) > 0) {
			s.pending = true;
			attempt(level, v, stall.get(), s, now);
		}
	}

	/** False when the Market is invalid or the stall is no longer this merchant's slot (M7 reports WORKPLACE_INVALID): no selling then. */
	private static boolean hasSlot(ServerLevel level, Villager v) {
		return VillageClaims.current(level.getServer()).flatMap(vs -> vs.worker(v.getUUID()))
				.map(rec -> Workers.status(level.getServer(), rec) != WorkerStatus.WORKPLACE_INVALID).orElse(false);
	}

	private static void replan(ServerLevel level, Villager v, WorkerData data, State s, long now) {
		s.planAt = now;
		s.slotOk = hasSlot(level, v);
		if (!s.slotOk) {
			s.plan = new SaleDecision.Stop(dev.villageheart.core.market.StopReason.NOTHING_TO_SELL);
			WorkerStatuses.set(v.getUUID(), null); // Workers.status shows WORKPLACE_INVALID
			return;
		}
		Optional<SaleDecision> d = Markets.plan(level, v, data, false);
		if (d.isEmpty()) return;
		s.plan = d.get();
		WorkerStatuses.set(v.getUUID(), s.shortAtChests && s.plan instanceof SaleDecision.Sell<?> ? WorkerStatus.STORAGE_UNREACHABLE : Markets.statusOf(s.plan));
	}

	private static void attempt(ServerLevel level, Villager v, BlockPos stall, State s, long now) {
		if (!hasSlot(level, v)) {
			s.slotOk = false;
			return;
		}
		Markets.Result r = Markets.trySell(level, v, stall);
		switch (r.outcome()) {
			case SOLD -> {
				s.pending = false;
				s.shortAtChests = false;
				s.plan = r.decision();
				s.planAt = now - PLAN_INTERVAL; // plan again next tick: the stock changed
			}
			case STOPPED -> {
				s.plan = r.decision(); // a fresh plan: pending stays frozen until the next refresh says Sell
				s.planAt = now;
				WorkerStatuses.set(v.getUUID(), Markets.statusOf(s.plan));
			}
			case SHORT -> {
				s.holdUntil = now + PLAN_INTERVAL;
				s.shortAtChests = true;
				WorkerStatuses.set(v.getUUID(), WorkerStatus.STORAGE_UNREACHABLE);
			}
			case NO_VILLAGE -> s.holdUntil = now + PLAN_INTERVAL;
		}
	}

	static void stop(Villager v) {
		WorkerStatus st = WorkerStatuses.get(v.getUUID());
		if (st != null && st != WorkerStatus.STORAGE_FULL) WorkerStatuses.set(v.getUUID(), null);
		State s = STATES.get(v.getUUID());
		if (s != null) {
			s.plan = null;
			s.awayTicks = 0;
		}
	}
}
