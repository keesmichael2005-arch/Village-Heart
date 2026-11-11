package dev.villageheart.sim;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
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

/** Turns the live world into {@link DayMetrics}. Villager deaths are caught through Fabric events. */
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

	public SimMetricsCollector(ServerLevel level, Vec3 centre, int radius) {
		this.level = level;
		this.centre = centre;
		this.radius = radius;
	}

	public void setCentre(Vec3 centre) { this.centre = centre; }

	private java.util.function.Supplier<dev.villageheart.core.sim.WallRing> wallRing = () -> null;

	/** The scenario's wall ring (or null), read at each death for the log's inside/outside column. */
	public void setWallRing(java.util.function.Supplier<dev.villageheart.core.sim.WallRing> wallRing) { this.wallRing = wallRing; }

	/** Fabric events cannot be unregistered, so tests switch the handlers off instead. */
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

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
		if (!enabled || !(entity instanceof Villager) || !inRadius(entity)) return;
		if (!seen.add(entity.getUUID())) return;
		String damage = source.typeHolder().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown");
		String cause = source.getEntity() != null ? id(source.getEntity().getType()) : damage;
		net.minecraft.world.entity.Entity by = source.getEntity() != null ? source.getEntity() : source.getDirectEntity();
		DeathRecord r = new DeathRecord(cause, source.getEntity() instanceof Enemy, level.getGameTime(), detail(entity, damage, by));
		pending.add(new Pending(entity.getUUID(), r));
		log(r);
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

	/** Vanilla fires death and then conversion for one villager lost to a zombie: that is one loss, cause "zombified". */
	public void onConversion(Mob previous, Mob converted) {
		if (!enabled || !(previous instanceof Villager) || !inRadius(previous)) return;
		converted.addTag(TAG_CONVERTED);
		long now = level.getGameTime();
		for (Pending p : pending) {
			if (p.id.equals(previous.getUUID()) && now - p.record.gameTime() <= 1) {
				p.record = p.record.withCause(DeathRecord.ZOMBIFIED, true);
				log(p.record);
				return;
			}
		}
		if (seen.add(previous.getUUID())) {
			// vanilla converts a villager killed by a zombie without a death event: the attacker is the last mob that hurt it
			DeathRecord r = new DeathRecord(DeathRecord.ZOMBIFIED, true, now, detail(previous, "zombified", previous.getLastHurtByMob()));
			pending.add(new Pending(previous.getUUID(), r));
			log(r);
		}
	}

	/** Deaths since the last drain. */
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
		return new DayMetrics(day, gameTime, village.isPresent(), c.population(), c.adults(), c.babies(),
				village.map(v -> v.workers().size()).orElse(0),
				village.map(v -> (int) v.buildings().stream().filter(b -> b.status() == dev.villageheart.core.building.BuildingStatus.VALID).count()).orElse(0),
				village.map(v -> (int) v.buildings().stream().filter(b -> b.status() != dev.villageheart.core.building.BuildingStatus.VALID).count()).orElse(0),
				village.isPresent() ? dev.villageheart.stock.Stockpiles.totalsById(server) : Map.of(), (int) Math.min(Integer.MAX_VALUE, nutrition), daysLeft,
				village.map(VillageState::treasury).orElse(0),
				drainDeaths(), villageMs, mspt, tps, wallSeconds, heapMb, folderBytes(server.getWorldPath(LevelResource.ROOT)),
				dev.villageheart.worker.job.WorkStats.snapshotAndReset(), workerStatuses(server, village), stockpileTags(server, village),
				needsDay(server, village), wageDay());
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
