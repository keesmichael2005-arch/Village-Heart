package dev.villageheart.sim;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.gametest.raid.RaidStart;
import dev.villageheart.worker.Workers;
import dev.villageheart.worker.guard.Guards;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.AABB;

/**
 * The raid actions (M25 step 8): start a raid whose centre is in the claim, set the golem count, top the adult census up,
 * and set one job's level. {@code scaled: true} is accepted and does not add raiders. RaidScale extras belong to a random raid.
 */
public final class RaidSim {
	private RaidSim() {}

	static void raid(SimulationRun ctx, ScenarioAction a) {
		int omen = ((Number) a.fields().get("omen")).intValue();
		boolean scaled = Boolean.TRUE.equals(a.fields().get("scaled"));
		start(ctx.level(), ctx.player().player, ctx.heart(), omen, scaled);
	}

	/** The count the scenario asked for. -1 until the golems action. A later village-spawned golem is removed again. */
	private static int wantedGolems = -1;

	static void golems(SimulationRun ctx, ScenarioAction a) {
		wantedGolems = ((Number) a.fields().get("count")).intValue();
		setGolems(ctx.level(), wantedGolems);
	}

	/** A new server process does not remember the action. Resume reads it off the scenario again. */
	static void rememberGolems(Iterable<ScenarioAction> actions) {
		wantedGolems = -1;
		if (actions == null) return;
		for (ScenarioAction a : actions) {
			if (a.type().equals("golems")) wantedGolems = ((Number) a.fields().get("count")).intValue();
		}
	}

	/** Puts the claim back to the scenario's golem count when a world golem has walked in or been spawned. */
	public static void keepGolemCount(ServerLevel level) {
		if (wantedGolems < 0) return;
		if (VillageClaims.current(level.getServer()).isEmpty()) return;
		setGolems(level, wantedGolems);
	}

	static void topUp(SimulationRun ctx, ScenarioAction a) {
		List<?> at = (List<?>) a.fields().get("at");
		topUp(ctx.level(), ((Number) a.fields().get("adults")).intValue(), ((Number) at.get(0)).intValue(), ((Number) at.get(1)).intValue());
	}

	static void workerLevel(SimulationRun ctx, ScenarioAction a) {
		setWorkerLevel(ctx.server(), (String) a.fields().get("job"), ((Number) a.fields().get("level")).intValue());
	}

	/** Starts a test raid at {@code at} and holds the next wave. The centre must fall inside the claim. */
	public static Raid start(ServerLevel level, ServerPlayer player, BlockPos at, int omen, boolean scaled) {
		if (omen < 1 || omen > 5) throw new IllegalArgumentException("omen must be 1..5: " + omen);
		if (level.getDifficulty() == Difficulty.PEACEFUL) level.getServer().setDifficulty(Difficulty.NORMAL, true);
		Raid raid = RaidStart.start(player, at, omen);
		RaidStart.holdWaves(raid);
		BlockPos center = raid.getCenter();
		VillageState village = VillageClaims.current(level.getServer()).orElseThrow(() -> new IllegalStateException("no claim"));
		if (!ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, center))) {
			throw new IllegalStateException("raid centre " + center + " is outside the claim");
		}
		forceRing(level, center);
		if (scaled) VillageHeart.LOG.info("[sim] raid scaled=true: a scripted raid does not add RaidScale extras; those belong to a random raid");
		return raid;
	}

	public static int countGolems(ServerLevel level, HeartLocation heart) { return golemsIn(level, heart).size(); }

	/** Makes the number of iron golems inside the claim equal {@code count}. A world-generated golem is discarded before a player-made one. */
	public static void setGolems(ServerLevel level, int count) {
		if (count < 0 || count > 4) throw new IllegalArgumentException("count must be 0..4: " + count);
		VillageState village = VillageClaims.current(level.getServer()).orElseThrow(() -> new IllegalStateException("no claim"));
		List<IronGolem> extras = new ArrayList<>();
		List<IronGolem> keepers = new ArrayList<>();
		for (IronGolem g : golemsIn(level, village.heart())) (g.isPlayerCreated() ? keepers : extras).add(g);
		List<IronGolem> ordered = new ArrayList<>();
		ordered.addAll(extras);
		ordered.addAll(keepers);
		while (ordered.size() > count) ordered.remove(0).discard();
		int made = 0;
		while (ordered.size() < count) {
			ordered.add(summonGolem(level, village.heart(), made++));
		}
	}

	/**
	 * Summons unemployed adult villagers at the heart plus {@code dx}/{@code dz} until the census counts {@code adults}.
	 * Does nothing when the village already has that many.
	 */
	public static void topUp(ServerLevel level, int adults, int dx, int dz) {
		if (adults < 1 || adults > 50) throw new IllegalArgumentException("adults must be 1..50: " + adults);
		MinecraftServer server = level.getServer();
		VillageState village = VillageClaims.current(server).orElseThrow(() -> new IllegalStateException("no claim"));
		int guard = 0;
		while (VillageCensus.headcount(server, VillageClaims.current(server).orElseThrow()).adults() < adults) {
			if (++guard > adults + 4) {
				throw new IllegalStateException("top_up_villagers could not reach " + adults + " adults at " + dx + ", " + dz);
			}
			Villager v = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
			if (v == null) throw new IllegalStateException("could not create a villager");
			v.setBaby(false);
			v.setPersistenceRequired();
			HeartLocation heart = village.heart();
			v.snapTo(heart.x() + dx + 0.5, heart.y() + 1, heart.z() + dz + guard - 1 + 0.5, 0, 0);
			level.addFreshEntity(v);
		}
	}

	/** Sets every loaded worker of {@code job} to {@code level}. A guard is healed up to the new maximum. */
	public static void setWorkerLevel(MinecraftServer server, String job, int level) {
		JobType type = JobType.byId(job).orElseThrow(() -> new IllegalArgumentException("job: unknown job " + job));
		if (level < 1 || level > 3) throw new IllegalArgumentException("level must be 1..3: " + level);
		VillageState village = VillageClaims.current(server).orElseThrow(() -> new IllegalStateException("no claim"));
		for (WorkerRecord rec : village.workers()) {
			if (!rec.jobId().equals(type.id())) continue;
			var loaded = Workers.findLoaded(server, rec.villager());
			if (loaded.isEmpty()) {
				VillageClaims.data(server).update(s -> s.withWorkerLevel(rec.villager(), level));
				continue;
			}
			Workers.setLevel(server, loaded.get(), level);
			if (type == JobType.GUARD) Guards.applyPerks(loaded.get(), true);
		}
	}

	private static List<IronGolem> golemsIn(ServerLevel level, HeartLocation heart) {
		int r = Balance.CLAIM_RADIUS;
		AABB box = new AABB(heart.x() - r, heart.y() - r, heart.z() - r, heart.x() + r + 1, heart.y() + r + 1, heart.z() + r + 1);
		return level.getEntities(EntityType.IRON_GOLEM, box,
				g -> g.isAlive() && ClaimGeometry.inClaim(heart, VillageClaims.locate(level, g.blockPosition())));
	}

	private static IronGolem summonGolem(ServerLevel level, HeartLocation heart, int i) {
		IronGolem g = EntityType.IRON_GOLEM.create(level, EntitySpawnReason.COMMAND);
		if (g == null) throw new IllegalStateException("could not create an iron golem");
		g.setPlayerCreated(true);
		g.setPersistenceRequired();
		g.snapTo(heart.x() + 0.5 + i, heart.y() + 1, heart.z() + 0.5, 0, 0);
		level.addFreshEntity(g);
		return g;
	}

	/**
	 * Sixteen points on a ring of 100 blocks around the centre must be entity-ticking for the rest of the run.
	 * A miss forces those chunks and is logged. Promotion takes a few ticks, so a chunk that is not ticking yet is not a failure.
	 */
	private static void forceRing(ServerLevel level, BlockPos center) {
		for (int i = 0; i < 16; i++) {
			double ang = i * Math.PI * 2 / 16.0;
			int x = center.getX() + (int) Math.round(100 * Math.cos(ang));
			int z = center.getZ() + (int) Math.round(100 * Math.sin(ang));
			BlockPos p = new BlockPos(x, center.getY(), z);
			if (level.isPositionEntityTicking(p)) continue;
			int cx = x >> 4, cz = z >> 4;
			VillageHeart.LOG.info("[sim] raid ring at {} was not entity-ticking; forcing chunks around {}, {}", p, cx, cz);
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
				level.setChunkForced(cx + dx, cz + dz, true);
			}
			if (!level.isPositionEntityTicking(p)) VillageHeart.LOG.info("[sim] raid ring at {} is forced and waiting to be entity-ticking", p);
		}
	}
}
