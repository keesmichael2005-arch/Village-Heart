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
		String cause;
		boolean byMob;
		final long time;

		Pending(UUID id, String cause, boolean byMob, long time) {
			this.id = id;
			this.cause = cause;
			this.byMob = byMob;
			this.time = time;
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

	/** Fabric events cannot be unregistered, so tests switch the handlers off instead. */
	public void setEnabled(boolean enabled) { this.enabled = enabled; }

	public void register() {
		ServerLivingEntityEvents.AFTER_DEATH.register(this::onDeath);
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
		String cause = source.getEntity() != null
				? id(source.getEntity().getType())
				: source.typeHolder().unwrapKey().map(k -> k.identifier().toString()).orElse("unknown");
		pending.add(new Pending(entity.getUUID(), cause, source.getEntity() instanceof Enemy, level.getGameTime()));
	}

	/** Vanilla fires death and then conversion for one villager lost to a zombie: that is one loss, cause "zombified". */
	public void onConversion(Mob previous, Mob converted) {
		if (!enabled || !(previous instanceof Villager) || !inRadius(previous)) return;
		long now = level.getGameTime();
		for (Pending p : pending) {
			if (p.id.equals(previous.getUUID()) && now - p.time <= 1) {
				p.cause = "zombified";
				p.byMob = true;
				return;
			}
		}
		if (seen.add(previous.getUUID())) pending.add(new Pending(previous.getUUID(), "zombified", true, now));
	}

	/** Deaths since the last drain. */
	public List<DeathRecord> drainDeaths() {
		List<DeathRecord> out = new ArrayList<>();
		for (Pending p : pending) out.add(new DeathRecord(p.cause, p.byMob, p.time));
		pending.clear();
		return out;
	}

	public int pendingDeaths() { return pending.size(); }

	public DayMetrics collect(MinecraftServer server, int day, long gameTime, TickStats villageMs,
			double wallSeconds, long ticksInDay) {
		Optional<VillageState> village = VillageClaims.current(server);
		Census c = centre == null ? new Census(0, 0, 0) : census(level, centre, radius);
		double mspt = server.getAverageTickTimeNanos() / 1e6;
		double tps = wallSeconds > 0 ? ticksInDay / wallSeconds : 0;
		Runtime rt = Runtime.getRuntime();
		long heapMb = (rt.totalMemory() - rt.freeMemory()) >> 20;
		return new DayMetrics(day, gameTime, village.isPresent(), c.population(), c.adults(), c.babies(),
				village.map(v -> v.workers().size()).orElse(0),
				0, village.map(v -> v.buildings().size()).orElse(0),
				Map.of(), 0, 0.0, village.map(VillageState::treasury).orElse(0),
				drainDeaths(), villageMs, mspt, tps, wallSeconds, heapMb, folderBytes(server.getWorldPath(LevelResource.ROOT)));
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
