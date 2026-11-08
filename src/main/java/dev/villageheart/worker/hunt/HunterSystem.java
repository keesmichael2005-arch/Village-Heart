package dev.villageheart.worker.hunt;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.hunt.HuntBonus;
import dev.villageheart.core.hunt.HuntPace;
import dev.villageheart.core.hunt.HuntTable;
import dev.villageheart.core.hunt.MeatWeights;
import dev.villageheart.core.hunt.TripDay;
import dev.villageheart.core.job.ToolKind;
import dev.villageheart.core.mine.AwayCheck;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.core.mine.AwayState.AwayTrip;
import dev.villageheart.core.mine.HaulRng;
import dev.villageheart.core.mine.SurfaceReason;
import dev.villageheart.core.worker.DayPhase;
import dev.villageheart.core.worker.DaySchedule;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.world.VillageSystem;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.job.AwayHauls;
import dev.villageheart.worker.job.AwayTrips;
import dev.villageheart.worker.job.CampTools;
import dev.villageheart.worker.job.WorkStats;
import dev.villageheart.worker.job.WorkerStatuses;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Steps the hunters that are out in a Hunting Ground, every 20 ticks each (staggered by entity id), see M17 plan 3.2 #14 and
 * {@link AwayCheck}. A hunter whose chunk is not entity-ticking is not stepped (DESIGN section 6: everything freezes).
 */
public final class HunterSystem implements VillageSystem {
	public static final HunterSystem INSTANCE = new HunterSystem();

	private HunterSystem() {}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		long now = server.overworld().getGameTime();
		for (WorkerRecord rec : village.workers()) {
			if (!rec.jobId().equals(JobType.HUNTER.id())) continue;
			Villager v = Workers.findLoaded(server, rec.villager()).orElse(null);
			if (v == null || !(v.level() instanceof ServerLevel level) || !AwayTrips.isAway(v)) continue;
			if ((now + v.getId()) % Balance.HUNTER_STEP_TICKS != 0) continue;
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
		boolean isHunter = data.isPresent() && data.get().job() == JobType.HUNTER && trip.jobId().equals(HunterJob.JOB);
		boolean phaseWork = DaySchedule.phaseAt(level.getDayTime()) == DayPhase.WORK;
		Optional<HuntSites.GroundSite> ground = isHunter ? HuntSites.validLinkedGround(server, trip.site(), data.get().building()) : Optional.empty();
		boolean slotHeld = isHunter && Workers.status(server, rec) != WorkerStatus.WORKPLACE_INVALID;
		CampTools.Result lease = isHunter ? CampTools.lease(server, v, ToolKind.BOW_OR_SWORD, HuntGear::rank) : CampTools.Missing.UNKNOWN;
		AwayCheck.Lease leaseState = HunterJob.leaseState(lease);
		double mult = isHunter ? HuntPace.multiplier(lease instanceof CampTools.Held, data.get().level(), data.get().needs()) : 1.0;
		AwayCheck.Inputs in = new AwayCheck.Inputs(isHunter, phaseWork, ground.isPresent(), slotHeld, leaseState, mult, true,
				st.progress(), st.unknownSteps());
		AwayCheck.Outcome out = AwayCheck.check(in, Balance.HUNTER_TRIP_UNITS, Balance.HUNTER_STEP_TICKS, Balance.HUNTER_UNKNOWN_LIMIT);
		if (out.stays()) {
			WorkStats.add(HunterJob.JOB, "away_ticks", Balance.HUNTER_STEP_TICKS);
			if (!out.progress().equals(st.progress()) || out.unknownSteps() != st.unknownSteps()) {
				AwayTrips.set(v, st.withProgress(out.progress(), st.progressShaft()).withUnknownSteps(out.unknownSteps()));
			}
			return;
		}
		SurfaceReason reason = out.surface().get();
		if (!AwayTrips.surface(level, v, reason)) return; // no free cell to stand on: stays hidden, retried next step
		AwayState after = AwayTrips.state(v);
		Optional<dev.villageheart.core.HeartLocation> progressSite = reason == SurfaceReason.SITE_INVALID ? Optional.empty() : after.progressShaft();
		after = after.withProgress(out.progress(), progressSite).withUnknownSteps(out.unknownSteps());
		WorkStats.add(HunterJob.JOB, "away_ticks", Balance.HUNTER_STEP_TICKS);
		if (reason == SurfaceReason.HAUL) {
			boolean ranged = lease instanceof CampTools.Held h && HuntGear.isRanged(h.lease().stack());
			Optional<String> arrow = ranged ? HuntGear.takeArrow(server, data.get().building()) : Optional.empty();
			boolean bonus = HuntBonus.applies(ranged, arrow.isPresent());
			MeatWeights weights = HuntSites.meatWeights(level, ground.get().loc(), ground.get().marker());
			Map<String, Integer> yield = HuntTable.roll(weights, bonus, HaulRng.forHaul(level.getSeed(), v.getUUID(), after.rolls()));
			HunterJob.trace("{} @{} trip #{} {} (arrow {})", v.getId(), now, after.rolls(), yield, arrow);
			long day = Math.floorDiv(level.getDayTime(), (long) Balance.DAY_TICKS);
			TripDay done = TripDay.of(after.doneDay(), after.doneToday(), day).plusOne();
			AwayTrips.set(v, after.withRolls(after.rolls() + 1).withDone(done.day(), done.done()));
			if (lease instanceof CampTools.Held held) {
				CampTools.use(level, held.lease(), Balance.HUNTER_WEAR_PER_TRIP);
				WorkStats.add(HunterJob.JOB, "weapon_wear", Balance.HUNTER_WEAR_PER_TRIP);
			}
			if (bonus) WorkStats.add(HunterJob.JOB, "arrows", 1);
			AwayHauls.give(v, HunterJob.JOB, yield);
			WorkStats.add(HunterJob.JOB, "returns", 1);
		} else {
			AwayTrips.set(v, after);
			WorkStats.add(HunterJob.JOB, "aborted", 1);
			HunterJob.trace("{} @{} came back: {}", v.getId(), now, reason);
			switch (reason) {
				case NO_TOOL -> WorkerStatuses.set(v.getUUID(), WorkerStatus.NEEDS_TOOL);
				case SITE_INVALID -> WorkerStatuses.set(v.getUUID(), WorkerStatus.NO_WORK);
				case STOPPED_WORKING -> VillageHeart.LOG.info("Hunter {} stopped hunting (chest unreadable or no work speed)", v.getUUID());
				default -> { }
			}
		}
		HunterJob.forget(v.getUUID()); // plan afresh at the Lodge
	}
}
