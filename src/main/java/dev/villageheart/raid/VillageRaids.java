package dev.villageheart.raid;

import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.raid.RaidFacts;
import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.raid.RaidPlan;
import dev.villageheart.core.raid.RaidRecord;
import dev.villageheart.core.raid.RaidTracker;
import dev.villageheart.core.raid.RaiderRecall;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
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
	private static final Map<Integer, Stall> STALLS = new HashMap<>();
	private static long nextRecallAt;
	private static RaidSource source = VillageRaids::realFacts;

	/** Best horizontal distance to the Heart, and the game time that distance was last improved. */
	private record Stall(double best, long since) {}

	private VillageRaids() {}

	/** A test raid (or, later, a random one) tells the tracker how it began. Read once, when that id starts. */
	public static void mark(int vanillaId, RaidOrigin origin, RaidPlan plan) { MARKS.put(vanillaId, new Mark(origin, plan)); }

	public static void setSourceForTest(RaidSource next) {
		source = next == null ? VillageRaids::realFacts : next;
	}

	public static void clearForTests() {
		MARKS.clear();
		STALLS.clear();
		nextRecallAt = 0;
		source = VillageRaids::realFacts;
	}

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		ServerLevel level = level(server, village.heart());
		if (level == null) return;
		recallStuck(level, village);
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
		int threatDay = dev.villageheart.core.siege.ThreatRoll.day(level.getGameTime());
		Optional<RaidRecord> closing = step.ended().isPresent() ? village.threats().raid() : Optional.empty();
		VillageClaims.data(server).update(s -> {
			Optional<RaidRecord> kept = keepCounts(s.threats().raid(), raid);
			VillageState out = s.withRaid(kept);
			if (step.ended().isPresent()) {
				out = out.withLastRaid(Optional.of(step.ended().get()));
				out = out.withThreats(out.threats().withLastRaidEndDay(threatDay));
			}
			return out;
		});
		if (step.started()) RaidNotices.started(server);
		step.ended().ifPresent(summary -> RaidNotices.ended(server, summary));
		if (closing.isPresent() && step.ended().isPresent() && step.ended().get().outcome() == dev.villageheart.core.raid.RaidOutcome.VICTORY) {
			ThreatScheduler.grant(server, closing.get(), threatDay);
		}
	}

	/** A death or a kill in this same tick already raised the saved counts. The wave update must not put the old number back. */
	private static Optional<RaidRecord> keepCounts(Optional<RaidRecord> saved, Optional<RaidRecord> next) {
		if (saved.isEmpty() || next.isEmpty() || saved.get().vanillaId() != next.get().vanillaId()) return next;
		RaidRecord was = saved.get();
		RaidRecord now = next.get();
		if (was.extrasForWave() > now.extrasForWave()) now = now.withExtrasForWave(was.extrasForWave());
		if (was.villagerDeaths() > now.villagerDeaths()) now = now.withVillagerDeaths(was.villagerDeaths());
		if (was.raidersKilled() > now.raidersKilled() || was.extraRaidersKilled() > now.extraRaidersKilled()) {
			now = now.withKills(Math.max(was.raidersKilled(), now.raidersKilled()), Math.max(was.extraRaidersKilled(), now.extraRaidersKilled()));
		}
		if (was.allGuardsOnDutyAt() >= 0 && now.allGuardsOnDutyAt() < 0) now = now.withAllGuardsOnDutyAt(was.allGuardsOnDutyAt());
		return Optional.of(now);
	}

	/**
	 * A raider that is not closing on the Heart is placed just outside the nearest gate, one at a time.
	 * Leaving it on a ledge holds the wave until vanilla stops the raid.
	 */
	private static void recallStuck(ServerLevel level, VillageState village) {
		if (village.threats().raid().isEmpty()) {
			STALLS.clear();
			return;
		}
		Raid raid = level.getRaids().get(village.threats().raid().get().vanillaId());
		if (raid == null || raid.isOver() || raid.isStopped() || raid.isVictory() || raid.isLoss()) {
			STALLS.clear();
			return;
		}
		int[] gates = gateCells(village);
		if (gates.length < 3) return;
		HeartLocation heart = village.heart();
		long now = level.getGameTime();
		int busy = raidersOnGuards(level, village, raid);
		List<Integer> live = new ArrayList<>();
		Raider chosen = null;
		int[] chosenGate = null;
		double chosenGateDist = -1;
		for (Raider raider : raid.getAllRaiders()) {
			if (!raider.isAlive()) continue;
			live.add(raider.getId());
			double dx = raider.getX() - heart.x(), dz = raider.getZ() - heart.z();
			double dist = Math.sqrt(dx * dx + dz * dz);
			Stall stall = STALLS.get(raider.getId());
			if (stall == null || dist < stall.best() - Balance.RAIDER_RECALL_CLOSE) {
				STALLS.put(raider.getId(), new Stall(dist, now));
				continue;
			}
			int[] gate = RaiderRecall.nearestGate(raider.getBlockX(), raider.getBlockY(), raider.getBlockZ(), gates);
			if (gate == null) continue;
			double gdx = raider.getX() - gate[0], gdz = raider.getZ() - gate[2];
			double gateDist = Math.sqrt(gdx * gdx + gdz * gdz);
			if (!RaiderRecall.recall(true, now - stall.since(), gateDist, busy)) continue;
			if (gateDist > chosenGateDist) {
				chosen = raider;
				chosenGate = gate;
				chosenGateDist = gateDist;
			}
		}
		STALLS.keySet().retainAll(live);
		if (chosen == null || chosenGate == null || now < nextRecallAt) return;
		Villager guard = nearestGuard(level, village, chosen);
		if (guard == null) return;
		double[] at = RaiderRecall.besideGuard(guard.getBlockX(), guard.getBlockY(), guard.getBlockZ(), chosenGate[0], chosenGate[2]);
		Entity body = chosen;
		while (body.getVehicle() != null) body = body.getVehicle();
		body.teleportTo(at[0], at[1], at[2]);
		if (body instanceof LivingEntity living) living.fallDistance = 0;
		nextRecallAt = now + Balance.RAIDER_RECALL_GAP;
		double ndx = body.getX() - heart.x(), ndz = body.getZ() - heart.z();
		STALLS.put(chosen.getId(), new Stall(Math.sqrt(ndx * ndx + ndz * ndz), now));
	}

	/** Raiders already in a guard's reach. Another one is not added to that fight. */
	private static int raidersOnGuards(ServerLevel level, VillageState village, Raid raid) {
		int n = 0;
		double limit = (double) Balance.RAIDER_RECALL_BUSY * Balance.RAIDER_RECALL_BUSY;
		for (Raider raider : raid.getAllRaiders()) {
			if (!raider.isAlive()) continue;
			for (WorkerRecord w : village.workers()) {
				if (!JobType.GUARD.id().equals(w.jobId())) continue;
				if (!(level.getEntity(w.villager()) instanceof Villager g) || !g.isAlive()) continue;
				if (g.distanceToSqr(raider) <= limit) {
					n++;
					break;
				}
			}
		}
		return n;
	}

	private static Villager nearestGuard(ServerLevel level, VillageState village, Raider raider) {
		Villager best = null;
		double bestD = Double.MAX_VALUE;
		for (WorkerRecord w : village.workers()) {
			if (!JobType.GUARD.id().equals(w.jobId())) continue;
			if (!(level.getEntity(w.villager()) instanceof Villager g) || !g.isAlive()) continue;
			double d = g.distanceToSqr(raider);
			if (d < bestD) {
				bestD = d;
				best = g;
			}
		}
		return best;
	}

	private static int[] gateCells(VillageState village) {
		List<Pos> gates = new ArrayList<>();
		for (DefenceCell cell : DefenceIndex.of(village.defences()).cells()) {
			if (cell.kind() == DefenceKind.GATE) gates.add(cell.pos());
		}
		int[] out = new int[gates.size() * 3];
		for (int i = 0; i < gates.size(); i++) {
			Pos p = gates.get(i);
			out[i * 3] = p.x();
			out[i * 3 + 1] = p.y();
			out[i * 3 + 2] = p.z();
		}
		return out;
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

	/**
	 * Living raiders of the tracked raid that are still inside the claim plus {@link Balance#GUARD_RAID_CHASE}.
	 * On-duty guards follow these even when they have walked past the claim edge.
	 */
	public static List<Raider> chaseTargets(ServerLevel level) {
		Optional<VillageState> state = VillageClaims.current(level.getServer());
		if (state.isEmpty() || state.get().threats().raid().isEmpty()) return List.of();
		Raid raid = level.getRaids().get(state.get().threats().raid().get().vanillaId());
		if (raid == null) return List.of();
		HeartLocation heart = state.get().heart();
		List<Raider> out = new ArrayList<>();
		double r = Balance.CLAIM_RADIUS + Balance.GUARD_RAID_CHASE;
		for (Raider raider : raid.getAllRaiders()) {
			if (!raider.isAlive()) continue;
			double dx = raider.getX() - heart.x(), dy = raider.getY() - heart.y(), dz = raider.getZ() - heart.z();
			if (dx * dx + dy * dy + dz * dz <= r * r) out.add(raider);
		}
		return out;
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
