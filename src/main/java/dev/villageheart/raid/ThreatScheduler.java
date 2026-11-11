package dev.villageheart.raid;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.raid.RaidOrigin;
import dev.villageheart.core.raid.RaidOutcome;
import dev.villageheart.core.raid.RaidPlan;
import dev.villageheart.core.raid.RaidPlans;
import dev.villageheart.core.raid.RaidRecord;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.siege.ItemValue;
import dev.villageheart.core.siege.PendingRaid;
import dev.villageheart.core.siege.Threat;
import dev.villageheart.core.siege.ThreatClock;
import dev.villageheart.core.siege.ThreatRoll;
import dev.villageheart.core.siege.ThreatRules;
import dev.villageheart.core.siege.ThreatState;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.economy.Treasury;
import dev.villageheart.mixin.RaidAccessor;
import dev.villageheart.siege.SiegeGamerules;
import dev.villageheart.siege.ZombieSiege;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.guard.GuardAttachments;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSystem;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * Rolls a random raid once a day, starts it once a player has been near, adds the extra raiders, and pays a victory
 * (docs/plans/M25.md 3.13, 3.13a, 3.14). The same tick asks {@link ZombieSiege} to roll and to spawn.
 */
public final class ThreatScheduler implements VillageSystem {
	public static final ThreatScheduler INSTANCE = new ThreatScheduler();
	/** The entity tag on a raider this mod added to a wave. */
	public static final String EXTRA_TAG = "villageheart.extra";

	/** How many postponed raids were dropped because vanilla refused to start one. */
	public static int warnings;

	private static ThreatClock clock;
	private static int loadedStreak;
	private static boolean lateRaid;
	private static boolean lateSiege;
	private static boolean clearUnspawned;

	private ThreatScheduler() {}

	public static void clearForTests() {
		clock = null;
		loadedStreak = 0;
		lateRaid = false;
		lateSiege = false;
		clearUnspawned = false;
		warnings = 0;
		ThreatRules.clearForTest();
		ZombieSiege.clearForTests();
	}

	/** The siege wrote its first zombie, so dawn must not treat the roll as unspawned. */
	public static void noteSiegeSpawned() {
		if (clock != null) clock = clock.withSiegeSpawned(true);
	}

	public static ServerLevel levelOf(MinecraftServer server, HeartLocation heart) { return level(server, heart); }

	@Override
	public void tick(MinecraftServer server, VillageState village) {
		ServerLevel level = level(server, village.heart());
		if (level == null) return;
		ThreatState threats = VillageClaims.current(server).map(VillageState::threats).orElse(village.threats());
		long now = level.getGameTime();
		advance(level, threats, now);
		threats = VillageClaims.current(server).map(VillageState::threats).orElse(threats);
		if (threats.raid().isPresent() && threats.pending().isPresent()) {
			VillageClaims.data(server).update(s -> s.withThreats(s.threats().withPending(Optional.empty())));
			loadedStreak = 0;
			threats = VillageClaims.current(server).orElseThrow().threats();
		}
		int day = ThreatRoll.day(now);
		if (lateRaid || ThreatRules.raidForced()) {
			boolean forced = ThreatRules.raidForced();
			if (rollRaid(server, level, village, threats, day) && forced) ThreatRules.clearRaidForce();
			if (!forced) lateRaid = false;
		}
		threats = VillageClaims.current(server).map(VillageState::threats).orElse(threats);
		if (threats.pending().isPresent()) tryStart(server, level, village, threats);
		else loadedStreak = 0;
		spawnExtras(server, level, village);
		boolean forcedSiege = ThreatRules.siegeForced();
		ZombieSiege.onTick(server, level, village, lateSiege || forcedSiege, clearUnspawned);
		if (!forcedSiege) lateSiege = false;
		clearUnspawned = false;
	}

	/** A random victory pays the treasury once. {@code record} is the raid that just closed. */
	public static void grant(MinecraftServer server, RaidRecord record, int day) {
		if (record.origin() != RaidOrigin.RANDOM) return;
		var state = VillageClaims.current(server);
		if (state.isEmpty()) return;
		var last = state.get().threats().lastRaid();
		if (last.isEmpty() || last.get().outcome() != RaidOutcome.VICTORY || last.get().reward() > 0) return;
		int pay = ThreatRules.reward(RaidOrigin.RANDOM, last.get().wavesSpawned(), record.plan().omen(), record.extraRaidersKilled(),
				day, state.get().threats().lastRewardDay());
		if (pay <= 0) return;
		Treasury.deposit(server, pay, null);
		VillageClaims.data(server).update(s -> s.withThreats(s.threats()
				.withLastRaid(s.threats().lastRaid().map(r -> r.withReward(pay)))
				.withLastRewardDay(day)));
		RaidNotices.paid(server, pay);
	}

	private static void advance(ServerLevel level, ThreatState threats, long now) {
		int day = ThreatRoll.day(now);
		int time = ThreatRoll.dayTime(now);
		if (clock == null) {
			lateRaid = threats.lastRaidRollDay() != day && time >= Balance.RAID_ROLL_TICK;
			lateSiege = threats.lastSiegeRollDay() != day && time >= Balance.SIEGE_ROLL_TICK;
			clearUnspawned = false;
			clock = new ThreatClock(day, time, lateRaid ? day : threats.lastRaidRollDay(), lateSiege ? day : threats.lastSiegeRollDay(),
					threats.siege().isPresent());
		} else {
			ThreatClock.Step step = clock.advance(now);
			clock = step.clock();
			lateRaid = step.raidRoll();
			lateSiege = step.siegeRoll();
			clearUnspawned = step.clearUnspawnedSiege();
		}
		if (clock.lastRaidRollDay() != threats.lastRaidRollDay() || clock.lastSiegeRollDay() != threats.lastSiegeRollDay()) {
			VillageClaims.data(level.getServer()).update(s -> s.withThreats(s.threats().withRollDays(clock.lastRaidRollDay(), clock.lastSiegeRollDay())));
		}
	}

	private static boolean rollRaid(MinecraftServer server, ServerLevel level, VillageState village, ThreatState threats, int day) {
		int since = since(threats.lastRaidEndDay(), day);
		ThreatRules.Gates gates = gates(level);
		int age = Math.max(0, day - ThreatRoll.day(village.claimedAtGameTime()));
		boolean forced = ThreatRules.raidForced();
		if (!ThreatRules.mayRollRaid(village.tier(), age, since, threats.raid().isPresent(), threats.pending().isPresent(), gates)) return false;
		double score = score(server, level, village);
		if (!forced && !ThreatRoll.roll(level.getSeed(), day, ThreatRoll.Kind.RAID, ThreatRules.raidChance(score))) return true;
		int start = ThreatRoll.startTick(level.getSeed(), day);
		VillageClaims.data(server).update(s -> s.withThreats(s.threats().withPending(Optional.of(new PendingRaid(day, start)))));
		return true;
	}

	private static void tryStart(MinecraftServer server, ServerLevel level, VillageState village, ThreatState threats) {
		PendingRaid pending = threats.pending().orElseThrow();
		BlockPos heart = heart(village);
		boolean near = playerWithin(level, heart);
		int next = ThreatRules.loaded(loadedStreak, near);
		ThreatRules.Gates gates = gates(level);
		if (next == 1 && loadedStreak == 0 && !gates.peaceful() && gates.randomRaids() && gates.vanillaRaids()) RaidNotices.gathering(server);
		loadedStreak = next;
		if (loadedStreak >= 1) ringReady(level, heart, true);
		int day = ThreatRoll.day(level.getGameTime());
		int sinceSiege = since(threats.lastSiegeDay(), day);
		boolean raidNear = level.getRaidAt(heart) != null;
		if (!ThreatRules.mayStart(ThreatRoll.dayTime(level.getDayTime()), pending.startTick(), loadedStreak, raidNear, sinceSiege)) return;
		if (gates.peaceful() || !gates.randomRaids()) return;
		if (!level.isVillage(heart) || !ringReady(level, heart, false)) return;
		ServerPlayer player = nearest(level, heart);
		if (player == null) return;
		Raid raid = level.getRaids().createOrExtendRaid(player, heart);
		if (raid == null) {
			warnings++;
			VillageHeart.LOG.warn("[threats] a pending raid was dropped: vanilla would not start one");
			VillageClaims.data(server).update(s -> s.withThreats(s.threats().withPending(Optional.empty())));
			loadedStreak = 0;
			return;
		}
		if (raid.isStarted() || raid.getGroupsSpawned() != 0) return;
		var id = level.getRaids().getId(raid);
		if (id.isEmpty()) return;
		int qualified = qualified(level, village);
		double score = score(server, level, village);
		RaidPlan plan = RaidPlans.of(village.tier(), qualified, score, RaidOrigin.RANDOM);
		if (plan.waveCap() > 0) ((RaidAccessor) raid).villageheart$setNumGroups(plan.waveCap());
		raid.setRaidOmenLevel(plan.omen());
		level.getRaids().setDirty();
		VillageRaids.mark(id.getAsInt(), RaidOrigin.RANDOM, plan);
		VillageClaims.data(server).update(s -> s.withThreats(s.threats().withPending(Optional.empty())));
		loadedStreak = 0;
	}

	private static void spawnExtras(MinecraftServer server, ServerLevel level, VillageState village) {
		var tracked = VillageClaims.current(server).map(s -> s.threats().raid()).orElse(Optional.empty());
		if (tracked.isEmpty()) return;
		RaidRecord record = tracked.get();
		RaidPlan plan = record.plan();
		if (plan.early() || (plan.extraVindicators() == 0 && plan.extraRavagers() == 0)) return;
		Raid raid = level.getRaids().get(record.vanillaId());
		if (raid == null) return;
		int groups = raid.getGroupsSpawned();
		if (groups <= record.extrasForWave()) return;
		BlockPos heart = heart(village);
		int outermost = outermost(village);
		for (int wave = record.extrasForWave() + 1; wave <= groups; wave++) {
			int vindicators = wave >= 2 ? plan.extraVindicators() : 0;
			int ravagers = wave >= 3 ? plan.extraRavagers() : 0;
			BlockPos at = spawnAt(raid, heart, outermost);
			for (int i = 0; i < vindicators; i++) add(level, raid, wave, EntityType.VINDICATOR, at);
			for (int i = 0; i < ravagers; i++) add(level, raid, wave, EntityType.RAVAGER, at);
		}
		int spawned = groups;
		VillageClaims.data(server).update(s -> {
			var cur = s.threats().raid();
			if (cur.isEmpty() || cur.get().vanillaId() != record.vanillaId()) return s;
			return s.withRaid(Optional.of(cur.get().withExtrasForWave(Math.max(cur.get().extrasForWave(), spawned))));
		});
	}

	private static void add(ServerLevel level, Raid raid, int wave, EntityType<? extends Raider> type, BlockPos at) {
		Raider raider = type.create(level, EntitySpawnReason.EVENT);
		if (raider == null) return;
		raider.addTag(EXTRA_TAG);
		raid.joinRaid(level, wave, raider, at, false);
	}

	private static BlockPos spawnAt(Raid raid, BlockPos heart, int outermost) {
		Raider first = null;
		for (Raider raider : raid.getAllRaiders()) {
			first = raider;
			break;
		}
		if (first == null) return ringPoint(heart, 0);
		BlockPos at = first.blockPosition();
		double dx = at.getX() - heart.getX(), dz = at.getZ() - heart.getZ();
		if (Math.sqrt(dx * dx + dz * dz) > outermost) return at;
		return ringPoint(heart, Math.atan2(dz, dx));
	}

	private static BlockPos ringPoint(BlockPos heart, double angle) {
		int x = heart.getX() + (int) Math.round(100 * Math.cos(angle));
		int z = heart.getZ() + (int) Math.round(100 * Math.sin(angle));
		return new BlockPos(x, heart.getY(), z);
	}

	private static int outermost(VillageState village) {
		int best = 0;
		for (DefenceCell cell : DefenceIndex.of(village.defences()).cells()) {
			Pos p = cell.pos();
			long dx = p.x() - village.heart().x(), dz = p.z() - village.heart().z();
			int dist = (int) Math.sqrt(dx * dx + dz * dz);
			if (dist > best) best = dist;
		}
		return best;
	}

	private static boolean ringReady(ServerLevel level, BlockPos center, boolean force) {
		boolean ready = true;
		for (int i = 0; i < 16; i++) {
			double ang = i * Math.PI * 2 / 16.0;
			int x = center.getX() + (int) Math.round(100 * Math.cos(ang));
			int z = center.getZ() + (int) Math.round(100 * Math.sin(ang));
			BlockPos p = new BlockPos(x, center.getY(), z);
			if (level.isPositionEntityTicking(p)) continue;
			ready = false;
			if (!force) continue;
			int cx = x >> 4, cz = z >> 4;
			for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
				level.getChunkSource().getChunk(cx + dx, cz + dz, ChunkStatus.FULL, true);
				level.setChunkForced(cx + dx, cz + dz, true);
			}
		}
		return ready;
	}

	/** The live threat score times 100, for the day report (3.17). 0 when there is no claim. */
	public static int scoreHundred(MinecraftServer server) {
		var state = VillageClaims.current(server);
		if (state.isEmpty()) return 0;
		ServerLevel level = dev.villageheart.worker.Workers.levelOf(server, state.get().heart());
		if (level == null) return 0;
		return (int) Math.round(score(server, level, state.get()) * 100.0);
	}

	private static double score(MinecraftServer server, ServerLevel level, VillageState village) {
		Map<String, Integer> counts = new HashMap<>();
		for (var e : Stockpiles.totalsById(server).entrySet()) counts.put(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue()));
		int cells = DefenceIndex.of(village.defences()).size();
		return Threat.current(adults(level, village), ItemValue.wealth(village.treasury(), counts), cells, village.tier());
	}

	private static int adults(ServerLevel level, VillageState village) {
		BlockPos heart = heart(village);
		AABB box = new AABB(heart).inflate(Balance.CLAIM_RADIUS);
		int n = 0;
		for (Villager v : level.getEntities(EntityType.VILLAGER, box, e -> e.isAlive() && !e.isBaby()
				&& ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, e.blockPosition())))) n++;
		return n;
	}

	private static int qualified(ServerLevel level, VillageState village) {
		int n = 0;
		long time = level.getDayTime();
		for (WorkerRecord w : village.workers()) {
			if (!JobType.GUARD.id().equals(w.jobId())) continue;
			if (!(level.getEntity(w.villager()) instanceof Villager g) || !g.isAlive()) continue;
			if (!ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, g.blockPosition()))) continue;
			var data = GuardAttachments.get(g);
			if (!data.training(time) && !data.wounded()) n++;
		}
		return n;
	}

	private static boolean playerWithin(ServerLevel level, BlockPos heart) {
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			if (player.blockPosition().distSqr(heart) <= (long) Balance.RAID_REACH * Balance.RAID_REACH) return true;
		}
		return false;
	}

	private static ServerPlayer nearest(ServerLevel level, BlockPos heart) {
		ServerPlayer best = null;
		double bestD = Double.MAX_VALUE;
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			double d = player.blockPosition().distSqr(heart);
			if (d <= (long) Balance.RAID_REACH * Balance.RAID_REACH && d < bestD) {
				best = player;
				bestD = d;
			}
		}
		return best;
	}

	private static ThreatRules.Gates gates(ServerLevel level) {
		var rules = level.getGameRules();
		return new ThreatRules.Gates(level.getDifficulty() == Difficulty.PEACEFUL, rules.get(SiegeGamerules.RANDOM_RAIDS),
				rules.get(GameRules.RAIDS), rules.get(SiegeGamerules.SIEGES), rules.get(GameRules.SPAWN_MOBS));
	}

	private static int since(int last, int day) { return last == ThreatState.NO_DAY ? ThreatState.NO_DAY : day - last; }

	private static BlockPos heart(VillageState village) {
		return new BlockPos(village.heart().x(), village.heart().y(), village.heart().z());
	}

	private static ServerLevel level(MinecraftServer server, HeartLocation heart) {
		return server.getLevel(ResourceKey.create(Registries.DIMENSION, Identifier.parse(heart.dimension())));
	}
}
