package dev.villageheart.sim;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.death.DeathCauses;
import dev.villageheart.core.sim.DayMetrics;
import dev.villageheart.core.sim.DeathRecord;
import dev.villageheart.core.sim.TickStats;
import dev.villageheart.world.VillageClaims;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Turns the live world into {@link DayMetrics}. A villager's cause, job and mob flag come from the village death log
 * when a claim exists (one log, M25). The position, wall side and attacker origin are filled in here (M26 prep).
 * With no claim, the same {@link DeathCauses} rule writes the row directly, which is what the collector tests call.
 */
public final class SimMetricsCollector {
	public record Census(int population, int adults, int babies) {}

	private static final class Pending {
		final UUID id;
		DeathRecord record;

		Pending(UUID id, DeathRecord record) {
			this.id = id;
			this.record = record;
		}
	}

	private final ServerLevel level;
	private Vec3 centre;
	private final int radius;
	private volatile boolean enabled = true;
	private final List<Pending> pending = new ArrayList<>();
	private final Set<UUID> seen = new HashSet<>();
	private final RaidMeter raids = new RaidMeter();
	private long lastInClaimLogged;
	private TickStats entityMs = TickStats.EMPTY;
	/** {@link dev.villageheart.golem.GolemGate#blocked()} is cumulative; each day records the increase. */
	private long lastVanillaBlocked;

	public SimMetricsCollector(ServerLevel level, Vec3 centre, int radius) {
		this.level = level;
		this.centre = centre;
		this.radius = radius;
	}

	public void setCentre(Vec3 centre) { this.centre = centre; }

	/** A resumed run already counted these. The next day records only what happens after {@code gameTime}. */
	public void resumeAfterReload(long gameTime, long inClaimLogged, Long raidStartedAt, int raidDeaths, int raidKills) {
		lastInClaimLogged = inClaimLogged;
		raids.resume(gameTime, raidStartedAt, raidDeaths, raidKills);
	}

	/** The overworld entity timer for the day about to be recorded. The baseline leaves this empty. */
	public void setEntityMs(TickStats entityMs) { this.entityMs = entityMs == null ? TickStats.EMPTY : entityMs; }

	private java.util.function.Supplier<dev.villageheart.core.sim.WallRing> wallRing = () -> null;

	/** The scenario's wall ring (or null), read at each death for the log's inside/outside column. */
	public void setWallRing(java.util.function.Supplier<dev.villageheart.core.sim.WallRing> wallRing) { this.wallRing = wallRing; }

	/** Fabric events cannot be unregistered, so tests switch the handlers off instead. */
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

	/** Reads the tracked raid often enough that a raid which ends before the day record still has its guard census. */
	public void sample(MinecraftServer server) {
		raids.sample(server);
		int tod = (int) Math.floorMod(level.getDayTime(), 24000L);
		if (tod >= 9000) dev.villageheart.siege.SiegeStats.noteNoon(dev.villageheart.siege.SiegeDamage.damage(server).breached().size());
	}

	public String raidSection(List<DayMetrics> days) { return raids.section(days); }

	/** Entity tags for the death log's attacker origin: a scenario {@code night_spawn} mob, a villager turned into a zombie villager. */
	public static final String TAG_SCRIPTED = "villageheart_sim_scripted", TAG_CONVERTED = "villageheart_sim_converted";

	public void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(this::onDeath);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents.ENTITY_LOAD.register((entity, l) -> onLoad(entity));
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, ctx) -> onConversion(previous, converted));
	}

	public static Census census(ServerLevel level, Vec3 centre, int radius) {
		double r2 = (double) radius * radius;
		List<Villager> vs = level.getEntities(EntityType.VILLAGER, AABB.ofSize(centre, 2.0 * radius, 2.0 * radius, 2.0 * radius),
				v -> v.isAlive() && v.distanceToSqr(centre) <= r2);
		int babies = 0;
		for (Villager v : vs) if (v.isBaby()) babies++;
		return new Census(vs.size(), vs.size() - babies, babies);
	}

	private boolean inRadius(LivingEntity e) {
		return centre != null && e.level() == level && e.distanceToSqr(centre) <= (double) radius * radius;
	}

	private static String id(EntityType<?> t) {
		return BuiltInRegistries.ENTITY_TYPE.getKey(t).toString();
	}

	public void onDeath(LivingEntity entity, DamageSource source) {
		if (!enabled || !(entity instanceof Villager villager)) return;
		boolean claimed = VillageClaims.current(level.getServer()).isPresent();
		if (claimed) {
			if (!inClaim(villager)) return;
		} else if (!inRadius(villager)) return;
		if (!seen.add(villager.getUUID())) return;
		String damage = source.typeHolder().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown");
		net.minecraft.world.entity.Entity by = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		DeathRecord.Detail where = detail(villager, damage, by);
		long now = level.getGameTime();
		if (claimed && absorbLogged(villager.getUUID(), now, where)) return;
		Optional<String> sourceId = source.getEntity() != null ? Optional.of(id(source.getEntity().getType())) : Optional.empty();
		DeathCauses.Cause cause = DeathCauses.of(sourceId, source.getEntity() instanceof Enemy, damage);
		remember(villager.getUUID(), new DeathRecord(cause.cause(), cause.byMob(), now, where));
	}

	/** Who died where (the death log, M26 prep). */
	private DeathRecord.Detail detail(LivingEntity e, String damage, net.minecraft.world.entity.Entity by) {
		String name = e.hasCustomName() ? e.getCustomName().getString() : dev.villageheart.core.heart.VillagerNames.of(e.getUUID());
		String job = dev.villageheart.worker.WorkerAttachments.get(e).map(dev.villageheart.core.worker.WorkerData::jobId)
				.or(() -> VillageClaims.current(level.getServer()).flatMap(v -> v.workers().stream().filter(w -> w.villager().equals(e.getUUID()))
						.map(w -> w.jobId()).findFirst()))
				.orElse("");
		net.minecraft.core.BlockPos p = e.blockPosition();
		dev.villageheart.core.sim.WallRing ring = wallRing.get();
		net.minecraft.core.BlockPos bp = by == null ? null : by.blockPosition();
		return new DeathRecord.Detail(Math.floorMod(level.getDayTime(), 24000L), name, job, damage, by == null ? "" : id(by.getType()),
				p.getX(), p.getY(), p.getZ(), ring == null ? "" : ring.side(p.getX(), p.getZ()), origin(by),
				ring == null || bp == null ? "" : ring.side(bp.getX(), bp.getZ()));
	}

	private static void log(DeathRecord r) {
		DeathRecord.Detail t = r.detail();
		dev.villageheart.VillageHeart.LOG.info("[sim-death] {} ({}) cause={} damage={} attacker={} origin={} attacker_wall={} at {} {} {} wall={} day_time={}",
				t.name(), t.job().isEmpty() ? "-" : t.job(), r.cause(), t.damage(), t.attacker(), t.origin(), t.attackerWall(), t.x(), t.y(), t.z(), t.wall(),
				t.dayTime());
	}

	/**
	 * A hostile mob that appears near the village with no tag and on its first tick is a natural spawn (or, rarely, one loaded from disk with
	 * a chunk): counted in {@code work.sim.hostile_spawn.<side of the wall ring>} and logged. Zombie villagers are left out (most are converted
	 * villagers; the conversion event comes after the load).
	 */
	private void onLoad(net.minecraft.world.entity.Entity e) {
		if (!enabled || !(e instanceof Mob m) || !(e instanceof Enemy) || !inRadius(m) || e.tickCount != 0) return;
		if (m.getType() == EntityType.ZOMBIE_VILLAGER || m.getTags().contains(TAG_SCRIPTED) || m.getTags().contains(TAG_CONVERTED)) return;
		dev.villageheart.core.sim.WallRing ring = wallRing.get();
		net.minecraft.core.BlockPos p = e.blockPosition();
		String side = ring == null ? "none" : ring.side(p.getX(), p.getZ());
		dev.villageheart.worker.job.WorkStats.add("sim", "hostile_spawn." + side, 1);
		dev.villageheart.VillageHeart.LOG.info("[sim-spawn] {} at {} {} {} wall={} light={} day_time={}", id(m.getType()), p.getX(), p.getY(), p.getZ(), side,
				level.getMaxLocalRawBrightness(p), Math.floorMod(level.getDayTime(), 24000L));
	}

	private final Set<UUID> insideSeen = new HashSet<>();

	/**
	 * Every 100 ticks: counts the hostiles within the claim radius by origin ({@code work.sim.hostile_samples.<origin>}). In wall-ring
	 * scenarios, also: a hostile mob seen strictly inside the ring for the first time is counted in
	 * {@code work.sim.hostile_inside.<origin>} and logged with the state of the ring's fence gates, so the report shows how mobs got in.
	 */
	public void watchRing() {
		if (enabled && centre != null) {
			// hostiles present within the claim radius, by origin (100-tick samples): a cross-check of the natural spawn count
			for (Mob m : level.getEntitiesOfClass(Mob.class, AABB.ofSize(centre, 2.0 * radius, 2.0 * radius, 2.0 * radius),
					x -> x instanceof Enemy && x.isAlive() && inRadius(x))) {
				dev.villageheart.worker.job.WorkStats.add("sim", "hostile_samples." + origin(m), 1);
			}
		}
		dev.villageheart.core.sim.WallRing ring = wallRing.get();
		if (!enabled || ring == null) return;
		AABB box = new AABB(ring.x(), level.getMinY(), ring.z(), ring.x() + ring.w(), level.getMaxY(), ring.z() + ring.d());
		for (Mob m : level.getEntitiesOfClass(Mob.class, box, x -> x instanceof Enemy && x.isAlive())) {
			net.minecraft.core.BlockPos p = m.blockPosition();
			if (!dev.villageheart.core.sim.WallRing.INSIDE.equals(ring.side(p.getX(), p.getZ())) || !insideSeen.add(m.getUUID())) continue;
			String origin = origin(m);
			dev.villageheart.worker.job.WorkStats.add("sim", "hostile_inside." + origin, 1);
			StringBuilder gates = new StringBuilder();
			for (int x = ring.x(); x < ring.x() + ring.w(); x++) {
				for (int z = ring.z(); z < ring.z() + ring.d(); z++) {
					if (!dev.villageheart.core.sim.WallRing.ON_WALL.equals(ring.side(x, z))) continue;
					for (int dy = 0; dy <= 3; dy++) {
						net.minecraft.core.BlockPos g = new net.minecraft.core.BlockPos(x, p.getY() - 2 + dy, z);
						var s = level.getBlockState(g);
						if (s.getBlock() instanceof net.minecraft.world.level.block.FenceGateBlock) {
							gates.append(g.toShortString()).append(s.getValue(net.minecraft.world.level.block.FenceGateBlock.OPEN) ? " open " : " closed ");
						}
					}
				}
			}
			dev.villageheart.VillageHeart.LOG.info("[sim-breach] {} ({}) inside the wall at {} day_time={} gates: {}", id(m.getType()), origin,
					p.toShortString(), Math.floorMod(level.getDayTime(), 24000L), gates.isEmpty() ? "none found" : gates.toString().trim());
		}
	}

	private String origin(net.minecraft.world.entity.Entity attacker) {
		if (attacker == null) return "";
		if (attacker.getTags().contains(TAG_SCRIPTED)) return DeathRecord.SCRIPTED;
		if (attacker.getTags().contains(TAG_CONVERTED)) return DeathRecord.CONVERTED;
		return DeathRecord.NATURAL;
	}

	/**
	 * Vanilla may report the conversion before the death, or the death before the conversion. Either way it is one loss.
	 * The cause comes from {@link DeathCauses}, and from the village log when that log already wrote the row: a zombie
	 * villager is {@code zombified}, a witch is {@code minecraft:lightning_bolt}. A cure adds nothing.
	 */
	public void onConversion(Mob previous, Mob converted) {
		if (!enabled || !(previous instanceof Villager villager)) return;
		boolean claimed = VillageClaims.current(level.getServer()).isPresent();
		if (claimed) {
			if (!inClaim(villager)) return;
		} else if (!inRadius(villager)) return;
		converted.addTag(TAG_CONVERTED);
		Optional<String> convertedCause = DeathCauses.conversion(id(previous.getType()), id(converted.getType()));
		if (convertedCause.isEmpty()) return;
		long now = level.getGameTime();
		DeathRecord.Detail where = detail(villager, convertedCause.get(), villager.getLastHurtByMob());
		if (claimed && absorbLogged(villager.getUUID(), now, where)) {
			seen.add(villager.getUUID());
			return;
		}
		boolean mob = DeathCauses.conversionCountsAsMob(convertedCause.get());
		for (Pending p : pending) {
			if (p.id.equals(villager.getUUID()) && DeathCauses.sameLoss(p.record.gameTime(), now)) {
				p.record = new DeathRecord(convertedCause.get(), mob, p.record.gameTime(), p.record.detail());
				log(p.record);
				return;
			}
		}
		if (seen.add(villager.getUUID())) {
			remember(villager.getUUID(), new DeathRecord(convertedCause.get(), mob, now, where));
		}
	}

	/**
	 * Copies the village log's row for this villager (cause, job, mob flag) onto the sim list and keeps the spatial
	 * detail captured here. Returns false when the log has no matching row, so a direct test call still records one.
	 */
	private boolean absorbLogged(UUID id, long time, DeathRecord.Detail where) {
		var state = VillageClaims.current(level.getServer());
		if (state.isEmpty()) return false;
		dev.villageheart.core.death.DeathEntry row = null;
		for (int i = state.get().deaths().entries().size() - 1; i >= 0; i--) {
			dev.villageheart.core.death.DeathEntry e = state.get().deaths().entries().get(i);
			if (e.villager().equals(id) && DeathCauses.sameLoss(e.gameTime(), time)) {
				row = e;
				break;
			}
		}
		if (row == null) return false;
		DeathRecord.Detail d = where;
		if (d != null) {
			d = new DeathRecord.Detail(d.dayTime(), row.name(), row.jobId(), d.damage(), d.attacker(), d.x(), d.y(), d.z(),
					d.wall(), d.origin(), d.attackerWall());
		}
		remember(id, new DeathRecord(row.cause(), row.byMob(), row.gameTime(), d));
		return true;
	}

	private void remember(UUID id, DeathRecord record) {
		for (Pending p : pending) {
			if (p.id.equals(id)) {
				p.record = record;
				log(record);
				return;
			}
		}
		pending.add(new Pending(id, record));
		log(record);
	}

	private boolean inClaim(LivingEntity entity) {
		var state = VillageClaims.current(level.getServer());
		if (state.isEmpty()) return false;
		return dev.villageheart.core.ClaimGeometry.inClaim(state.get().heart(), VillageClaims.locate(level, entity.blockPosition()));
	}

	/** Deaths since the last drain. With a claim, each row was copied from the village log. */
	public List<DeathRecord> drainDeaths() {
		List<DeathRecord> out = new ArrayList<>();
		for (Pending p : pending) out.add(p.record);
		pending.clear();
		return out;
	}

	public int pendingDeaths() { return pending.size(); }

	public DayMetrics collect(MinecraftServer server, int day, long gameTime, TickStats villageMs,
			double wallSeconds, long ticksInDay) {
		Optional<VillageState> village = VillageClaims.current(server);
		Census c = centre == null ? new Census(0, 0, 0) : census(level, centre, radius);
		long nutrition = 0;
		double daysLeft = 0.0;
		if (village.isPresent()) {
			nutrition = dev.villageheart.food.Dinner.nutrition(server);
			daysLeft = dev.villageheart.core.food.FoodMath.daysLeft(nutrition, c.adults(), c.babies()).orElse(Double.NaN);
		}
		double mspt = server.getAverageTickTimeNanos() / 1e6;
		double tps = wallSeconds > 0 ? ticksInDay / wallSeconds : 0;
		Runtime rt = Runtime.getRuntime();
		long heapMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
		long logged = 0;
		if (village.isPresent()) {
			long nowLogged = village.get().deaths().inClaimLogged();
			logged = Math.max(0, nowLogged - lastInClaimLogged);
			lastInClaimLogged = nowLogged;
		}
		return new DayMetrics(day, gameTime, village.isPresent(), c.population(), c.adults(), c.babies(),
				village.map(v -> v.workers().size()).orElse(0),
				village.map(v -> (int) v.buildings().stream().filter(b -> b.status() == dev.villageheart.core.building.BuildingStatus.VALID).count()).orElse(0),
				village.map(v -> (int) v.buildings().stream().filter(b -> b.status() != dev.villageheart.core.building.BuildingStatus.VALID).count()).orElse(0),
				village.isPresent() ? dev.villageheart.stock.Stockpiles.totalsById(server) : Map.of(), (int) Math.min(Integer.MAX_VALUE, nutrition), daysLeft,
				village.map(VillageState::treasury).orElse(0),
				drainDeaths(), villageMs, mspt, tps, wallSeconds, heapMb, folderBytes(server.getWorldPath(LevelResource.ROOT)),
				dev.villageheart.worker.job.WorkStats.snapshotAndReset(), workerStatuses(server, village), stockpileTags(server, village),
				needsDay(server, village), wageDay(), raids.take(server, gameTime), logged, golemDay(village),
				siegeDay(server, village, entityMs), tierDay(village));
	}

	/** Cells, cracks and the day's siege counters (M25 3.17). The counters reset here. */
	private static DayMetrics.SiegeDay siegeDay(MinecraftServer server, Optional<VillageState> village, TickStats entities) {
		dev.villageheart.siege.SiegeStats.Counts c = dev.villageheart.siege.SiegeStats.take();
		int cells = 0, unseen = 0, breached = 0, cracked = 0;
		if (village.isPresent()) {
			for (var site : village.get().defences()) {
				for (var placed : dev.villageheart.core.defence.DefenceCells.of(site)) {
					cells++;
					long relative = dev.villageheart.core.defence.DefenceCells.relative(site.ground(), placed.pos());
					if (!site.seen().contains(relative)) unseen++;
				}
			}
			var damage = dev.villageheart.siege.SiegeDamage.damage(server);
			breached = damage.breached().size();
			cracked = damage.cracked().size();
		}
		return new DayMetrics.SiegeDay(cells, unseen, breached, cracked, c.breachedNoon(), c.blocksBroken(), c.brokenZombie(),
				c.brokenRavager(), c.brokenVindicator(), c.brokenCreeper(), c.brokenOther(), c.repairs(), c.repairItems(),
				c.started(), c.zombies(), c.killed(), c.villagerDeaths(), c.paths(), c.maxLured(),
				dev.villageheart.raid.ThreatScheduler.scoreHundred(server), entities);
	}

	/** Vanilla spawns the gate refused since the last sample, and iron golems standing in the claim now. */
	private DayMetrics.GolemDay golemDay(Optional<VillageState> village) {
		long blocked = dev.villageheart.golem.GolemGate.blocked();
		long delta = Math.max(0, blocked - lastVanillaBlocked);
		lastVanillaBlocked = blocked;
		if (village.isEmpty()) return new DayMetrics.GolemDay(delta, 0);
		var v = village.get();
		int r = Balance.CLAIM_RADIUS;
		AABB box = new AABB(v.heart().x() - r, v.heart().y() - r, v.heart().z() - r, v.heart().x() + r + 1, v.heart().y() + r + 1,
				v.heart().z() + r + 1);
		long inClaim = level.getEntities(EntityType.IRON_GOLEM, box, e -> e.isAlive()
				&& dev.villageheart.core.ClaimGeometry.inClaim(v.heart(), VillageClaims.locate(level, e.blockPosition()))).size();
		return new DayMetrics.GolemDay(delta, inClaim);
	}

	/** The tier and the fed-days streak at the end of the day. No village reads as hamlet / 0. */
	private static DayMetrics.TierDay tierDay(Optional<VillageState> village) {
		if (village.isEmpty()) return DayMetrics.TierDay.NONE;
		VillageState v = village.get();
		return new DayMetrics.TierDay(v.tier().name().toLowerCase(java.util.Locale.ROOT), v.tierProgress().fedStreak());
	}

	/** The wage runs of the day (reset here, M12). */
	private static DayMetrics.WageDay wageDay() {
		dev.villageheart.economy.WageStats.Counts c = dev.villageheart.economy.WageStats.snapshotAndReset();
		return new DayMetrics.WageDay(c.runs(), c.bill(), c.paid(), c.spent(), c.strikers());
	}

	/** The dinner totals of the day (reset here), the hungriest villager now, and who sleeps in a valid House (M11). */
	private static DayMetrics.NeedsDay needsDay(MinecraftServer server, Optional<VillageState> village) {
		dev.villageheart.food.FoodStats.Day food = dev.villageheart.food.FoodStats.snapshotAndReset();
		if (village.isEmpty()) return DayMetrics.NeedsDay.ZERO;
		VillageState v = village.get();
		dev.villageheart.needs.Housing.rebuild(server);
		long maxHungry = 0, housed = 0;
		for (Villager villager : dev.villageheart.world.VillageCensus.of(server, v)) {
			maxHungry = Math.max(maxHungry, dev.villageheart.food.DietAttachments.read(villager, v.claimedAtGameTime()).hungryDays());
			if (dev.villageheart.needs.Housing.housed(server, villager, Optional.empty())) housed++;
		}
		return new DayMetrics.NeedsDay(food.dinners(), food.diners(), food.fed(), food.hungry(), food.eatenNutrition(), food.eatenItems(),
				maxHungry, housed, dev.villageheart.needs.Housing.beds(server).houseBeds().size());
	}

	/** Workers in each status right now (lowercase status name): "ok", "needs_tool", "no_work" ... */
	private static Map<String, Long> workerStatuses(MinecraftServer server, Optional<VillageState> village) {
		Map<String, Long> out = new java.util.TreeMap<>();
		if (village.isEmpty()) return out;
		for (var w : village.get().workers()) {
			out.merge(dev.villageheart.worker.Workers.status(server, w).name().toLowerCase(java.util.Locale.ROOT), 1L, Long::sum);
		}
		return out;
	}

	/** Stockpile counts by item tag, for the families M11 asserts on (logs and saplings). */
	private static Map<String, Long> stockpileTags(MinecraftServer server, Optional<VillageState> village) {
		Map<String, Long> out = new java.util.TreeMap<>();
		if (village.isEmpty()) return out;
		out.put("minecraft:logs", dev.villageheart.stock.Stockpiles.count(server, net.minecraft.tags.ItemTags.LOGS));
		out.put("minecraft:saplings", dev.villageheart.stock.Stockpiles.count(server, net.minecraft.tags.ItemTags.SAPLINGS));
		return out;
	}

	private static long folderBytes(Path dir) {
		try (Stream<Path> s = Files.walk(dir)) {
			return s.filter(Files::isRegularFile).mapToLong(p -> {
				try { return Files.size(p); } catch (IOException e) { return 0L; }
			}).sum();
		} catch (IOException e) {
			return 0L;
		}
	}

	public static int defaultRadius() { return Balance.CLAIM_RADIUS; }
}
