package dev.villageheart.siege;

import dev.villageheart.VillageHeart;
import dev.villageheart.building.BuildingSystem;
import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.siege.ItemValue;
import dev.villageheart.core.siege.SiegeRecord;
import dev.villageheart.core.siege.SiegeSize;
import dev.villageheart.core.siege.Threat;
import dev.villageheart.core.siege.ThreatRoll;
import dev.villageheart.core.siege.ThreatRules;
import dev.villageheart.core.siege.ThreatState;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.raid.ThreatScheduler;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.worker.guard.GuardAttachments;
import dev.villageheart.world.VillageClaims;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.AABB;

/**
 * The mod's zombie siege (docs/plans/M25.md 3.12). A won roll is a pending day, not a {@link SiegeRecord}: a record
 * turns the lure on and calls the guards, and that waits until the first zombie exists at day time 18000.
 */
public final class ZombieSiege {
	private static int nextGroup;
	private static int groupTotal;
	private static int gap;
	/** A chunk forced on the spawn tick is not entity-ticking yet. The group is tried again before it is skipped. */
	private static int tickingWaits;
	private static final int TICKING_TRIES = 20;
	private static int[] counts = new int[0];
	private static double baseAngle;
	private static int spawnDistance;

	private ZombieSiege() {}

	public static void clearForTests() {
		nextGroup = 0;
		tickingWaits = 0;
		groupTotal = 0;
		gap = 0;
		counts = new int[0];
		baseAngle = 0;
		spawnDistance = 0;
	}

	/**
	 * @param rollNow the day's siege window was crossed, or a test forced the roll
	 * @param clearPending dawn passed on a roll that never spawned
	 */
	public static void onTick(MinecraftServer server, ServerLevel level, VillageState village, boolean rollNow, boolean clearPending) {
		if (clearPending) forgetPending(server);
		if (rollNow) {
			boolean forced = ThreatRules.siegeForced();
			if (consider(server, level, village) && forced) ThreatRules.clearSiegeForce();
		}
		maintain(server, level);
	}

	/** The {@code siege} action: this many zombies now, with the 3.12 spawn rule and no roll or size rule. */
	public static void startNow(MinecraftServer server, int count) {
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return;
		ServerLevel level = ThreatScheduler.levelOf(server, village.heart());
		if (level == null || village.threats().siege().isPresent()) return;
		begin(server, level, village, Math.max(1, Math.min(20, count)));
	}

	private static boolean consider(MinecraftServer server, ServerLevel level, VillageState village) {
		ThreatState threats = village.threats();
		if (threats.siege().isPresent() || threats.pendingSiegeDay() != ThreatState.NO_DAY) return true;
		int day = ThreatRoll.day(level.getGameTime());
		int sinceSiege = since(threats.lastSiegeDay(), day);
		int sinceRaid = since(threats.lastRaidEndDay(), day);
		int age = Math.max(0, day - ThreatRoll.day(village.claimedAtGameTime()));
		if (!ThreatRules.mayRollSiege(village.tier(), age, sinceSiege, sinceRaid, threats.raid().isPresent(), threats.pending().isPresent(), gates(level))) {
			return false;
		}
		if (!playerNear(level, heart(village))) return false;
		boolean forced = ThreatRules.siegeForced();
		double score = score(server, level, village);
		if (!forced && !ThreatRoll.roll(level.getSeed(), day, ThreatRoll.Kind.SIEGE, ThreatRules.siegeChance(score))) return true;
		VillageClaims.data(server).update(s -> s.withThreats(s.threats().withPendingSiegeDay(day)));
		return true;
	}

	private static void forgetPending(MinecraftServer server) {
		VillageClaims.data(server).update(s -> {
			if (s.threats().siege().isPresent() || s.threats().pendingSiegeDay() == ThreatState.NO_DAY) return s;
			return s.withThreats(s.threats().withPendingSiegeDay(ThreatState.NO_DAY));
		});
	}

	private static void maintain(MinecraftServer server, ServerLevel level) {
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return;
		ThreatState threats = village.threats();
		if (threats.siege().isPresent()) {
			SiegeRecord fresh = recount(level, threats.siege().get());
			int time = ThreatRoll.dayTime(level.getDayTime());
			int day = ThreatRoll.day(level.getGameTime());
			boolean rules = !gates(level).peaceful() && gates(level).sieges();
			boolean over = !rules || time >= Balance.LURE_NIGHT_END || fresh.startDay() < day || fresh.killed() >= fresh.spawned() && nextGroup >= groupTotal;
			if (over) {
				end(server, level, fresh);
				return;
			}
			if (!fresh.equals(threats.siege().get())) write(server, Optional.of(fresh));
			if (nextGroup < groupTotal) spawnDue(server, level, village);
			return;
		}
		int owed = threats.pendingSiegeDay();
		if (owed == ThreatState.NO_DAY) return;
		int time = ThreatRoll.dayTime(level.getDayTime());
		int day = ThreatRoll.day(level.getGameTime());
		if (owed != day || time < 18000 || time >= Balance.LURE_NIGHT_END) return;
		int n = SiegeSize.zombies(score(server, level, village), qualified(level, village));
		begin(server, level, village, n);
	}

	private static void begin(MinecraftServer server, ServerLevel level, VillageState village, int count) {
		int groups = Math.min(3, Math.max(1, count));
		counts = new int[groups];
		int base = count / groups;
		int extra = count % groups;
		for (int i = 0; i < groups; i++) counts[i] = base + (i < extra ? 1 : 0);
		groupTotal = groups;
		nextGroup = 0;
		tickingWaits = 0;
		gap = 0;
		spawnDistance = ThreatRules.spawnDistance(outermost(village));
		long seed = level.getSeed() ^ (ThreatRoll.day(level.getGameTime()) * 0x9E3779B97F4A7C15L);
		baseAngle = (seed & 0xffff) / (double) 0xffff * Math.PI * 2;
		VillageClaims.data(server).update(s -> s.withThreats(s.threats().withPendingSiegeDay(ThreatState.NO_DAY)));
		SiegeStats.siegeStarted();
		spawnDue(server, level, village);
	}

	private static void spawnDue(MinecraftServer server, ServerLevel level, VillageState village) {
		if (gap > 0) gap--;
		if (gap > 0) return;
		BlockPos heart = heart(village);
		while (nextGroup < groupTotal) {
			int group = nextGroup;
			double angle = baseAngle + group * Math.PI * 2 / Math.max(1, groupTotal);
			BlockPos at = find(server, level, heart, angle);
			if (at == null) {
				if (lastMiss.startsWith("not ticking") && tickingWaits < TICKING_TRIES) {
					tickingWaits++;
					return;
				}
				VillageHeart.LOG.warn("[siege] group {} had no cell at {} blocks from the Heart ({})", group, spawnDistance, lastMiss);
				nextGroup++;
				tickingWaits = 0;
				continue;
			}
			nextGroup++;
			tickingWaits = 0;
			for (int i = 0; i < counts[group]; i++) spawnOne(server, level, at);
			gap = 2;
			return;
		}
	}

	private static void spawnOne(MinecraftServer server, ServerLevel level, BlockPos at) {
		Zombie zombie = EntityType.ZOMBIE.create(level, EntitySpawnReason.EVENT);
		if (zombie == null) return;
		zombie.snapTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0.0F, 0.0F);
		zombie.finalizeSpawn(level, level.getCurrentDifficultyAt(at), EntitySpawnReason.EVENT, null);
		zombie.setPersistenceRequired();
		zombie.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
		zombie.addTag(LureSlots.SIEGE_TAG);
		level.addFreshEntity(zombie);
		SiegeStats.zombieSpawned();
		UUID id = zombie.getUUID();
		int day = ThreatRoll.day(level.getGameTime());
		VillageClaims.data(server).update(s -> {
			SiegeRecord cur = s.threats().siege().orElse(new SiegeRecord(day, List.of(), 0, 0, 0));
			return s.withThreats(s.threats().withSiege(Optional.of(cur.plus(id))));
		});
		ThreatScheduler.noteSiegeSpawned();
	}

	private static String lastMiss = "";

	private static BlockPos find(MinecraftServer server, ServerLevel level, BlockPos heart, double angle) {
		lastMiss = "none";
		for (int i = 0; i < 10; i++) {
			double a = angle + i * Math.PI * 2 / 10.0;
			int x = heart.getX() + (int) Math.round(spawnDistance * Math.cos(a));
			int z = heart.getZ() + (int) Math.round(spawnDistance * Math.sin(a));
			BlockPos feet = surface(level, x, z, heart.getY());
			if (feet == null) {
				lastMiss = "no surface at " + x + "," + z + " heartY " + heart.getY() + " floor " + level.getBlockState(new BlockPos(x, heart.getY() - 2, z));
				continue;
			}
			if (!level.isPositionEntityTicking(feet)) {
				level.getChunkSource().getChunk(x >> 4, z >> 4, ChunkStatus.FULL, true);
				level.setChunkForced(x >> 4, z >> 4, true);
			}
			if (!level.isPositionEntityTicking(feet)) {
				lastMiss = "not ticking " + feet.toShortString();
				continue;
			}
			if (BuildingSystem.insideRoom(server, feet.getX(), feet.getY(), feet.getZ())) {
				lastMiss = "room " + feet.toShortString();
				continue;
			}
			if (!level.getFluidState(feet).isEmpty()) {
				lastMiss = "fluid " + feet.toShortString();
				continue;
			}
			return feet;
		}
		return null;
	}

	/** A solid floor and two free cells, light ignored. Searches a few blocks either side of the Heart's height. */
	private static BlockPos surface(ServerLevel level, int x, int z, int heartY) {
		for (int y = heartY + 4; y >= heartY - 8; y--) {
			BlockPos feet = new BlockPos(x, y, z);
			BlockState floor = level.getBlockState(feet.below());
			if (!floor.isSolid()) continue;
			if (!open(level, feet) || !open(level, feet.above())) continue;
			if (!level.getFluidState(feet).isEmpty() || !level.getFluidState(feet.below()).isEmpty()) continue;
			return feet;
		}
		return null;
	}

	private static boolean open(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.isAir() || state.getCollisionShape(level, pos).isEmpty();
	}

	private static SiegeRecord recount(ServerLevel level, SiegeRecord record) {
		int dead = 0;
		for (UUID id : record.zombieIds()) {
			Entity entity = level.getEntity(id);
			if (entity == null) continue;
			if (!entity.isAlive() || entity.isRemoved() || !entity.getTags().contains(LureSlots.SIEGE_TAG)) dead++;
		}
		if (dead == record.killed()) return record;
		SiegeStats.zombiesKilled(dead - record.killed());
		return record.withKilled(dead);
	}

	private static void end(MinecraftServer server, ServerLevel level, SiegeRecord record) {
		for (UUID id : record.zombieIds()) {
			if (!(level.getEntity(id) instanceof Zombie zombie) || !zombie.isAlive()) continue;
			boolean near = false;
			for (ServerPlayer player : level.players()) {
				if (player.isSpectator()) continue;
				if (zombie.distanceTo(player) < 64) near = true;
			}
			if (!near) zombie.discard();
		}
		groupTotal = 0;
		nextGroup = 0;
		tickingWaits = 0;
		gap = 0;
		int day = ThreatRoll.day(level.getGameTime());
		VillageClaims.data(server).update(s -> s.withThreats(s.threats()
				.withLastSiege(Optional.of(record))
				.withSiege(Optional.empty())
				.withLastSiegeDay(day)
				.withPendingSiegeDay(ThreatState.NO_DAY)));
	}

	private static void write(MinecraftServer server, Optional<SiegeRecord> siege) {
		VillageClaims.data(server).update(s -> s.withThreats(s.threats().withSiege(siege)));
	}

	private static int outermost(VillageState village) {
		int best = 0;
		for (DefenceCell cell : DefenceIndex.of(village.defences()).cells()) {
			Pos p = cell.pos();
			long dx = p.x() - village.heart().x();
			long dz = p.z() - village.heart().z();
			int dist = (int) Math.sqrt(dx * dx + dz * dz);
			if (dist > best) best = dist;
		}
		return best;
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

	private static double score(MinecraftServer server, ServerLevel level, VillageState village) {
		Map<String, Integer> counts = new HashMap<>();
		for (var e : Stockpiles.totalsById(server).entrySet()) counts.put(e.getKey(), (int) Math.min(Integer.MAX_VALUE, e.getValue()));
		int cells = DefenceIndex.of(village.defences()).size();
		BlockPos heart = heart(village);
		AABB box = new AABB(heart).inflate(Balance.CLAIM_RADIUS);
		int adults = 0;
		for (Villager v : level.getEntities(EntityType.VILLAGER, box, e -> e.isAlive() && !e.isBaby()
				&& ClaimGeometry.inClaim(village.heart(), VillageClaims.locate(level, e.blockPosition())))) adults++;
		return Threat.current(adults, ItemValue.wealth(village.treasury(), counts), cells, village.tier());
	}

	private static boolean playerNear(ServerLevel level, BlockPos heart) {
		for (ServerPlayer player : level.players()) {
			if (player.isSpectator()) continue;
			if (player.blockPosition().distSqr(heart) <= (long) Balance.RAID_REACH * Balance.RAID_REACH) return true;
		}
		return false;
	}

	private static ThreatRules.Gates gates(ServerLevel level) {
		var rules = level.getGameRules();
		return new ThreatRules.Gates(level.getDifficulty() == Difficulty.PEACEFUL, rules.get(SiegeGamerules.RANDOM_RAIDS),
				rules.get(GameRules.RAIDS), rules.get(SiegeGamerules.SIEGES), rules.get(GameRules.SPAWN_MOBS));
	}

	private static int since(int last, int day) { return last == ThreatState.NO_DAY ? ThreatState.NO_DAY : day - last; }

	private static BlockPos heart(VillageState village) {
		HeartLocation h = village.heart();
		return new BlockPos(h.x(), h.y(), h.z());
	}
}
