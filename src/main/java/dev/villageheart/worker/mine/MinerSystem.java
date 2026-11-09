package dev.villageheart.worker.mine;

import dev.villageheart.VillageHeart;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.building.ShaftScanner.ShaftDepth;
import dev.villageheart.core.job.ToolRequirement;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.job.WorkSpeed;
import dev.villageheart.core.job.WorkUnit;
import dev.villageheart.core.mine.AwayCheck;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.core.mine.AwayState.AwayTrip;
import dev.villageheart.core.mine.AwayProgress;
import dev.villageheart.core.mine.DepthBand;
import dev.villageheart.core.mine.HaulRng;
import dev.villageheart.core.mine.HaulTable;
import dev.villageheart.core.mine.SurfaceReason;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.worker.WorkXp;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Steps the miners that are inside a Mineshaft, every 20 ticks each (staggered by entity id), see M16 plan 3.4 and
 * {@link AwayCheck}. A miner whose chunk is not entity-ticking is not stepped (DESIGN section 6: everything freezes).
 */
public final class MinerSystem implements VillageSystem {
	public static final MinerSystem INSTANCE = new MinerSystem();

	private MinerSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		long now = server.overworld().getGameTime();
		for (WorkerRecord rec : village.workers()) {
			if (!rec.jobId().equals(JobType.MINER.id())) continue;
			Villager v = Workers.findLoaded(server, rec.villager()).orElse(null);
			if (v == null || !(v.level() instanceof ServerLevel level) || !AwayTrips.isAway(v)) continue;
			if ((now + v.getId()) % Balance.MINER_STEP_TICKS != 0) continue;
			if (!level.isPositionEntityTicking(v.blockPosition())) continue;
			step(server, level, v, rec, now);
		}
	}

	private static void step(MinecraftServer server, ServerLevel level, Villager v, WorkerRecord rec, long now) {
		AwayState st = AwayTrips.state(v);
		AwayTrip trip = st.trip().orElse(null);
		if (trip == null) return;
		AwayTrips.repin(v);
		Optional<WorkerData> data = WorkerAttachments.get(v);
		boolean isMiner = data.isPresent() && data.get().job() == JobType.MINER && trip.jobId().equals(MinerJob.JOB);
		boolean phaseWork = DaySchedule.phaseAt(level.getDayTime()) == DayPhase.WORK;
		Optional<MineSites.ShaftSite> shaft = isMiner ? MineSites.validLinkedShaft(server, trip.site(), data.get().building()) : Optional.empty();
		boolean slotHeld = isMiner && Workers.status(server, rec) != WorkerStatus.WORKPLACE_INVALID;
		CampTools.Result lease = isMiner ? CampTools.lease(server, v, JobType.MINER.tool().orElseThrow().kind()) : CampTools.Missing.UNKNOWN;
		AwayCheck.Lease leaseState = MinerJob.leaseState(lease);
		double mult = 1.0;
		ToolTier tier = ToolTier.WOOD;
		if (lease instanceof CampTools.Held held) {
			tier = held.lease().tier();
			ToolRequirement req = JobType.MINER.tool().orElseThrow();
			mult = WorkSpeed.multiplier(Optional.of(tier), req, data.get().level(), data.get().needs());
		}
		Optional<ShaftDepth> depth = Buildings.shaftDepth(server, trip.site());
		AwayCheck.Inputs in = new AwayCheck.Inputs(isMiner, phaseWork, shaft.isPresent(), slotHeld, leaseState, mult, depth.isPresent(),
				st.progress(), st.unknownSteps());
		AwayCheck.Outcome out = AwayCheck.check(in);
		if (out.stays()) {
			WorkStats.add(MinerJob.JOB, "underground_ticks", Balance.MINER_STEP_TICKS);
			if (!out.progress().equals(st.progress()) || out.unknownSteps() != st.unknownSteps()) {
				AwayTrips.set(v, st.withProgress(out.progress(), st.progressShaft()).withUnknownSteps(out.unknownSteps()));
			}
			return;
		}
		SurfaceReason reason = out.surface().get();
		if (!AwayTrips.surface(level, v, reason)) return; // no free cell to stand on: stays hidden, retried next step
		AwayState after = AwayTrips.state(v);
		Optional<dev.villageheart.core.HeartLocation> progressShaft = reason == SurfaceReason.SITE_INVALID ? Optional.empty() : after.progressShaft();
		after = after.withProgress(out.progress(), progressShaft).withUnknownSteps(out.unknownSteps());
		WorkStats.add(MinerJob.JOB, "underground_ticks", Balance.MINER_STEP_TICKS);
		if (reason == SurfaceReason.HAUL) {
			Map<String, Integer> haul = HaulTable.roll(DepthBand.of(depth.get().deepestY()), tier, HaulRng.forHaul(level.getSeed(), v.getUUID(), after.rolls()));
			MinerJob.trace("{} @{} haul #{} {}", v.getId(), now, after.rolls(), haul);
			AwayTrips.set(v, after.withRolls(after.rolls() + 1));
			if (lease instanceof CampTools.Held held) CampTools.use(level, held.lease(), Balance.MINER_WEAR_PER_HAUL);
			MinerHauls.give(v, haul);
			WorkStats.add(MinerJob.JOB, "hauls", 1);
			WorkXp.award(v, WorkUnit.ITEM_MINED, haul.values().stream().mapToInt(Integer::intValue).sum());
		} else {
			AwayTrips.set(v, after);
			WorkStats.add(MinerJob.JOB, "aborted", 1);
			MinerJob.trace("{} @{} came up: {}", v.getId(), now, reason);
			switch (reason) {
				case NO_TOOL -> WorkerStatuses.set(v.getUUID(), WorkerStatus.NEEDS_TOOL);
				case SITE_INVALID -> WorkerStatuses.set(v.getUUID(), WorkerStatus.NO_WORK);
				case STOPPED_WORKING -> VillageHeart.LOG.info("Miner {} stopped working underground (chest unreadable, depth unknown or no work speed)", v.getUUID());
				default -> { }
			}
		}
		MinerJob.forget(v.getUUID()); // plan afresh on the surface
	}
}
