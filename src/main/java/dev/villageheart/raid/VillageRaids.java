package dev.villageheart.raid;

import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.raid.RaidFacts;
import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.raid.RaidPlan;
import dev.villageheart.core.raid.RaidRecord;
import dev.villageheart.core.raid.RaidTracker;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.mixin.RaidGroups;
import dev.villageheart.mixin.RaidsView;
import dev.villageheart.worker.guard.GuardActivities;
import dev.villageheart.worker.guard.GuardAttachments;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.phys.AABB;

/** Watches the vanilla raid on the claim and keeps {@code VillageState.threats} in step with it (M25). */
public final class VillageRaids implements VillageSystem {
	public static final VillageRaids INSTANCE = new VillageRaids();

	/** Facts for this tick. The real source reads the level. A test can stand in for it. */
	@FunctionalInterface
	public interface RaidSource {
		RaidFacts facts(ServerLevel level, VillageState village);
	}

	private record Mark(RaidOrigin origin, RaidPlan plan) {}

	private static final Map<Integer, Mark> MARKS = new HashMap<>();
	private static RaidSource source = VillageRaids::realFacts;

	private VillageRaids() {}

	/** A test raid (or, later, a random one) tells the tracker how it began. Read once, when that id starts. */
	public static void mark(int vanillaId, RaidOrigin origin, RaidPlan plan) { MARKS.put(vanillaId, new Mark(origin, plan)); }

	public static void setSourceForTest(RaidSource next) {
		source = next == null ? VillageRaids::realFacts : next;
	}

	public static void clearForTests() {
		MARKS.clear();
		source = VillageRaids::realFacts;
	}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		ServerLevel level = level(server, village.heart());
		if (level == null) return;
		RaidFacts facts = source.facts(level, village);
		RaidTracker.Start start = startOf(level, village, facts);
		long now = level.getGameTime();
		int day = (int) (level.getDayTime() / Balance.DAY_TICKS) + 1;
		var step = RaidTracker.step(village.threats().raid(), facts, now, Math.max(1, day), () -> adults(level, village), start);
		if (step.started()) MARKS.remove(facts.vanillaId());
		Optional<RaidRecord> next = step.next();
		if (next.isPresent() && next.get().allGuardsOnDutyAt() < 0 && allOnDuty(level, village)) {
			next = Optional.of(RaidTracker.onAllGuardsOnDuty(next.get(), now));
		}
		boolean changed = !next.equals(village.threats().raid()) || step.ended().isPresent();
		if (!changed) return;
		Optional<RaidRecord> raid = next;
		VillageClaims.data(server).update(s -> {
			Optional<RaidRecord> kept = keepCounts(s.threats().raid(), raid);
			VillageState out = s.withRaid(kept);
			if (step.ended().isPresent()) out = out.withLastRaid(Optional.of(step.ended().get()));
			return out;
		});
		if (step.started()) RaidNotices.started(server);
		step.ended().ifPresent(summary -> RaidNotices.ended(server, summary));
	}

	/** A death or a kill in this same tick already raised the saved counts. The wave update must not put the old number back. */
	private static Optional<RaidRecord> keepCounts(Optional<RaidRecord> saved, Optional<RaidRecord> next) {
		if (saved.isEmpty() || next.isEmpty() || saved.get().vanillaId() != next.get().vanillaId()) return next;
		RaidRecord was = saved.get();
		RaidRecord now = next.get();
		if (was.villagerDeaths() > now.villagerDeaths()) now = now.withVillagerDeaths(was.villagerDeaths());
		if (was.raidersKilled() > now.raidersKilled() || was.extraRaidersKilled() > now.extraRaidersKilled()) {
			now = now.withKills(Math.max(was.raidersKilled(), now.raidersKilled()), Math.max(was.extraRaidersKilled(), now.extraRaidersKilled()));
		}
		if (was.allGuardsOnDutyAt() >= 0 && now.allGuardsOnDutyAt() < 0) now = now.withAllGuardsOnDutyAt(was.allGuardsOnDutyAt());
		return Optional.of(now);
	}

	private static RaidTracker.Start startOf(ServerLevel level, VillageState village, RaidFacts facts) {
		int guards = 0, qualified = 0, wounded = 0, unloaded = 0;
		long time = level.getDayTime();
		for (WorkerRecord w : village.workers()) {
			if (!JobType.GUARD.id().equals(w.jobId())) continue;
			guards++;
			if (!(level.getEntity(w.villager()) instanceof Villager g) || !g.isAlive()
					|| !ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, g.blockPosition()))) {
				unloaded++;
				continue;
			}
			var data = GuardAttachments.get(g);
			if (data.wounded()) wounded++;
			if (!data.training(time)) qualified++;
		}
		Mark mark = MARKS.get(facts.vanillaId());
		RaidOrigin origin = mark == null ? RaidOrigin.BAD_OMEN : mark.origin();
		RaidPlan plan = mark == null ? planOf(level, facts) : mark.plan();
		return new RaidTracker.Start(origin, plan, guards, qualified, wounded, unloaded);
	}

	private static RaidPlan planOf(ServerLevel level, RaidFacts facts) {
		if (!facts.present()) return RaidPlan.VANILLA;
		Raid raid = level.getRaids().get(facts.vanillaId());
		if (raid == null) return RaidPlan.VANILLA;
		int omen = Math.max(1, Math.min(5, raid.getRaidOmenLevel()));
		return new RaidPlan(omen, 0, 0, 0, false);
	}

	private static boolean allOnDuty(ServerLevel level, VillageState village) {
		boolean any = false;
		for (WorkerRecord w : village.workers()) {
			if (!JobType.GUARD.id().equals(w.jobId())) continue;
			if (!(level.getEntity(w.villager()) instanceof Villager g) || !g.isAlive()) continue;
			if (!ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, g.blockPosition()))) continue;
			var data = GuardAttachments.get(g);
			if (data.training(level.getDayTime()) || data.wounded()) continue;
			any = true;
			if (!g.getBrain().isActive(GuardActivities.DUTY)) return false;
		}
		return any;
	}

	private static int adults(ServerLevel level, VillageState village) {
		BlockPos heart = new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());
		int r = Balance.CLAIM_RADIUS;
		AABB box = new AABB(heart).inflate(r);
		int n = 0;
		for (Villager v : level.getEntities(EntityType.VILLAGER, box, e -> e.isAlive() && !e.isBaby()
				&& ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, e.blockPosition())))) n++;
		return n;
	}

	/** The raid whose centre or a raider is inside the claim. {@code getRaidAt} would also match a raid that is only nearby. */
	private static Raid raidOnClaim(ServerLevel level, VillageState village) {
		Raid byRaider = null;
		for (Raid raid : ((RaidsView) level.getRaids()).villageheart$raidMap().values()) {
			if (ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, raid.getCenter()))) return raid;
			if (byRaider == null) {
				for (Raider raider : raid.getAllRaiders()) {
					if (ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, raider.blockPosition()))) {
						byRaider = raid;
						break;
					}
				}
			}
		}
		return byRaider;
	}

	private static RaidFacts realFacts(ServerLevel level, VillageState village) {
		Raid raid = null;
		var tracked = village.threats().raid();
		if (tracked.isPresent()) raid = level.getRaids().get(tracked.get().vanillaId());
		else raid = raidOnClaim(level, village);
		if (raid == null) return RaidFacts.NONE;
		boolean ended = raid.isOver() || raid.isStopped() || raid.isVictory() || raid.isLoss();
		boolean recount = ended || tracked.isEmpty() || level.getServer().getTickCount() % Balance.RAID_CHECK_TICKS == 0;
		var id = level.getRaids().getId(raid);
		if (id.isEmpty()) return RaidFacts.NONE;
		if (!recount) {
			RaidRecord r = tracked.get();
			return new RaidFacts(true, id.getAsInt(), false, false, false, false, true, 1, r.raidersAlive(), r.wavesSpawned(),
					r.wavesTotal(), r.betweenWaves(), raid.isStarted());
		}
		List<Pos> at = new ArrayList<>();
		for (Raider raider : raid.getAllRaiders()) {
			BlockPos p = raider.blockPosition();
			at.add(new Pos(p.getX(), p.getY(), p.getZ()));
		}
		BlockPos c = raid.getCenter();
		int groups = ((RaidGroups) raid).villageheart$numGroups();
		return RaidFacts.from(id.getAsInt(), raid.isOver(), raid.isStopped(), raid.isVictory(), raid.isLoss(),
				new Pos(c.getX(), c.getY(), c.getZ()), at, village.heart(), raid.getTotalRaidersAlive(), raid.getGroupsSpawned(),
				RaidFacts.wavesTotal(groups, raid.getRaidOmenLevel()), raid.isBetweenWaves(), raid.isStarted());
	}

	private static ServerLevel level(MinecraftServer server, HeartLocation heart) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(heart.dimension())));
	}
}
