package dev.villageheart.golem;

import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.smith.GolemRules;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.gear.ToolCensus;
import dev.villageheart.worker.smith.SmithyLayout;
import dev.villageheart.world.VillageClaims;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.phys.Vec3;

/**
 * A hurt player-built golem walks to the nearest Smithy that can repair it and waits there. Combat wins: a target cancels the walk.
 * One golem per Smithy. Server thread only.
 */
public final class SmithyRepairGoal extends Goal {
	private final IronGolem golem;
	private SmithyRepairs.Offer offer;
	private long started;
	private long arrived = -1;
	private long lastMove;

	public SmithyRepairGoal(IronGolem golem) {
		this.golem = golem;
		setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
	}

	@Override
	public boolean canUse() {
		if (golem.getTarget() != null) return false;
		if (!GolemRules.wantsRepair(golem.getHealth(), golem.getMaxHealth())) return false;
		if (!(golem.level() instanceof ServerLevel level)) return false;
		if (level.getGameTime() < SmithyRepairs.cooldown(golem.getUUID())) return false;
		offer = offer(level);
		if (offer == null) return false;
		SmithyRepairs.hold(offer.smithy(), golem.getUUID());
		started = level.getGameTime();
		arrived = -1;
		return true;
	}

	@Override
	public boolean canContinueToUse() {
		if (golem.getTarget() != null) return false;
		if (!(golem.level() instanceof ServerLevel level)) return false;
		if (offer == null) return false;
		long now = level.getGameTime();
		if (arrived < 0 && now - started > Balance.GOLEM_WALK_TIMEOUT) return false;
		if (arrived >= 0 && now - arrived > Balance.GOLEM_WAIT_TIMEOUT) return false;
		if (GolemRules.ingotsFor(golem.getHealth(), golem.getMaxHealth()) < 1) return false;
		return offerStands(level, offer);
	}

	@Override
	public void tick() {
		if (!(golem.level() instanceof ServerLevel level) || offer == null) return;
		long now = level.getGameTime();
		double dist = golem.position().distanceTo(Vec3.atBottomCenterOf(offer.spot()));
		if (dist <= Balance.GOLEM_WAIT_RADIUS) {
			if (arrived < 0) arrived = now;
			golem.getNavigation().stop();
			golem.getLookControl().setLookAt(Vec3.atCenterOf(offer.door()));
			SmithyRepairs.waiting(offer.smithy(), golem.getUUID());
			return;
		}
		arrived = -1;
		SmithyRepairs.waitingAt(offer.smithy()).filter(id -> id.equals(golem.getUUID())).ifPresent(id -> SmithyRepairs.release(id));
		SmithyRepairs.hold(offer.smithy(), golem.getUUID());
		if (now - lastMove >= 40) {
			golem.getNavigation().moveTo(offer.spot().getX() + 0.5, offer.spot().getY(), offer.spot().getZ() + 0.5, 0.6);
			lastMove = now;
		}
	}

	@Override
	public void stop() {
		if (golem.level() instanceof ServerLevel level) {
			long now = level.getGameTime();
			if (offer != null && arrived >= 0 && now - arrived > Balance.GOLEM_WAIT_TIMEOUT) SmithyRepairs.cooldown(golem.getUUID(), now + Balance.GOLEM_REPAIR_COOLDOWN);
			if (offer != null && arrived < 0 && now - started > Balance.GOLEM_WALK_TIMEOUT) SmithyRepairs.cooldown(golem.getUUID(), now + Balance.GOLEM_REPAIR_COOLDOWN);
		}
		if (golem.getUUID() != null) SmithyRepairs.release(golem.getUUID());
		golem.getNavigation().stop();
		offer = null;
	}

	@Override
	public boolean requiresUpdateEveryTick() { return true; }

	private SmithyRepairs.Offer offer(ServerLevel level) {
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return null;
		if (!inClaim(level, village.get(), golem.blockPosition())) return null;
		SmithyRepairs.Offer best = null;
		double bestD = Double.MAX_VALUE;
		for (var w : village.get().workers()) {
			if (!w.jobId().equals(JobType.BLACKSMITH.id())) continue;
			if (!ToolCensus.valid(level.getServer(), w.building())) continue;
			if (SmithyRepairs.heldByOther(w.building(), golem.getUUID())) continue;
			if (!smithWorking(level, w)) continue;
			var scan = SmithyLayout.scan(level.getServer(), w.building());
			if (scan.isEmpty() || scan.get().door() == null) continue;
			var spot = SmithyLayout.waitSpot(level, scan.get());
			if (spot.isEmpty()) continue;
			if (stockIron(level) < 1) continue;
			double d = golem.blockPosition().distSqr(spot.get());
			if (d < bestD) {
				bestD = d;
				BlockPos door = new BlockPos(scan.get().door().x(), scan.get().door().y(), scan.get().door().z());
				best = new SmithyRepairs.Offer(w.building(), spot.get(), door);
			}
		}
		return best;
	}

	private static boolean offerStands(ServerLevel level, SmithyRepairs.Offer offer) {
		if (!ToolCensus.valid(level.getServer(), offer.smithy())) return false;
		if (stockIron(level) < 1) return false;
		long day = Math.floorMod(level.getDayTime(), (long) Balance.DAY_TICKS);
		return DaySchedule.phaseAt(day) == DayPhase.WORK;
	}

	private static boolean smithWorking(ServerLevel level, WorkerRecord w) {
		var loaded = Workers.findLoaded(level.getServer(), w.villager());
		if (loaded.isEmpty()) return false;
		var data = loaded.get().getAttached(WorkerAttachments.WORKER);
		if (data == null || data.job() != JobType.BLACKSMITH) return false;
		long day = Math.floorMod(level.getDayTime(), (long) Balance.DAY_TICKS);
		return DaySchedule.phaseAt(day) == DayPhase.WORK;
	}

	private static boolean inClaim(ServerLevel level, VillageState village, BlockPos pos) {
		return village.heart().dimension().equals(level.dimension().identifier().toString())
				&& ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, pos));
	}

	private static long stockIron(ServerLevel level) {
		long n = 0;
		for (var t : dev.villageheart.stock.Stockpiles.snapshot(level.getServer()).totals())
			if (t.key().id().equals("minecraft:iron_ingot") && t.key().patch().isEmpty()) n += t.count();
		return n;
	}

	/** Kept so a smithy type reference stays honest if the building check moves. */
	@SuppressWarnings("unused")
	private static BuildingType smithy() { return BuildingType.SMITHY; }
}
