package dev.villageheart.sim;

import dev.villageheart.VillageHeart;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.village.VillageSiege;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.CustomSpawner;
import net.minecraft.world.phys.Vec3;

/**
 * Turns vanilla's zombie siege off inside a claim, for the sim harness only. There is no gamerule for {@link VillageSiege}
 * (and the main mod does not grow one: raids are M25). The level's spawner list is an immutable list held in a final field,
 * so this replaces that field with a copy whose {@code VillageSiege} is wrapped. The wrapper returns immediately while any
 * living, non-spectator player stands within {@code radius} of {@code centre}. Natural monster spawning is untouched.
 */
public final class SiegeGate {
	private static final WeakHashMap<ServerLevel, List<CustomSpawner>> ORIGINALS = new WeakHashMap<>();

	private SiegeGate() {}

	/** Installs the gate. {@code centre} is the bell until the Heart exists, then {@link #moveCentre}. */
	public static void suppressInsideClaim(ServerLevel level, BlockPos centre, int radius) {
		if (gated(level)) return;
		List<CustomSpawner> original = spawners(level);
		List<CustomSpawner> copy = new ArrayList<>(original);
		boolean found = false;
		for (int i = 0; i < copy.size(); i++) {
			if (copy.get(i) instanceof VillageSiege siege) {
				copy.set(i, new Gated(siege, centre, radius));
				found = true;
				break;
			}
		}
		if (!found) throw new IllegalStateException("village_siege: this level has no VillageSiege spawner");
		ORIGINALS.put(level, original);
		setSpawners(level, copy);
		VillageHeart.LOG.info("[sim] village siege off inside {} blocks of {}", radius, centre.toShortString());
	}

	/** The Heart replaced the bell as the claim centre. No-op when the gate is not installed. */
	public static void moveCentre(ServerLevel level, BlockPos centre) {
		for (CustomSpawner s : spawners(level)) {
			if (s instanceof Gated g) {
				g.centre = centre;
				VillageHeart.LOG.info("[sim] village siege centre moved to {}", centre.toShortString());
			}
		}
	}

	/** Puts the level's own spawner list back. Safe when the gate was never installed. */
	public static void restore(ServerLevel level) {
		List<CustomSpawner> original = ORIGINALS.remove(level);
		if (original != null) setSpawners(level, original);
	}

	public static boolean gated(ServerLevel level) {
		for (CustomSpawner s : spawners(level)) if (s instanceof Gated) return true;
		return false;
	}

	public static boolean hasRawSiege(ServerLevel level) {
		for (CustomSpawner s : spawners(level)) if (s instanceof VillageSiege) return true;
		return false;
	}

	/** The siege object the level will tick: the wrapper's inner, or the raw one when the gate is off. */
	public static VillageSiege siege(ServerLevel level) {
		for (CustomSpawner s : spawners(level)) {
			if (s instanceof Gated g) return g.inner;
			if (s instanceof VillageSiege siege) return siege;
		}
		throw new IllegalStateException("village_siege: this level has no VillageSiege spawner");
	}

	/** Arms an already-set-up siege so one tick would try to spawn. Tests only. */
	public static void armTonight(VillageSiege siege) {
		set(siege, "hasSetupSiege", true);
		set(siege, "siegeState", state("SIEGE_TONIGHT"));
		set(siege, "zombiesToSpawn", 5);
		set(siege, "nextSpawnTime", 0);
	}

	/** Back to the constructor's idle state, so a test does not leave a live siege in the shared world. */
	public static void disarm(ServerLevel level) {
		if (!hasRawSiege(level) && !gated(level)) return;
		VillageSiege siege = siege(level);
		set(siege, "hasSetupSiege", false);
		set(siege, "siegeState", state("SIEGE_DONE"));
		set(siege, "zombiesToSpawn", 0);
		set(siege, "nextSpawnTime", 0);
	}

	public static int zombies(VillageSiege siege) { return (int) get(siege, "zombiesToSpawn"); }

	public static String stateName(VillageSiege siege) { return String.valueOf(get(siege, "siegeState")); }

	@SuppressWarnings("unchecked")
	private static List<CustomSpawner> spawners(ServerLevel level) {
		try {
			Field f = ServerLevel.class.getDeclaredField("customSpawners");
			f.setAccessible(true);
			return (List<CustomSpawner>) f.get(level);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("village_siege: cannot reach customSpawners", e);
		}
	}

	private static void setSpawners(ServerLevel level, List<CustomSpawner> list) {
		try {
			Field f = ServerLevel.class.getDeclaredField("customSpawners");
			f.setAccessible(true);
			f.set(level, list);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("village_siege: cannot replace customSpawners", e);
		}
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object state(String name) {
		try {
			Class<?> type = Class.forName("net.minecraft.world.entity.ai.village.VillageSiege$State");
			return Enum.valueOf(type.asSubclass(Enum.class), name);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("village_siege: no siege state " + name, e);
		}
	}

	private static Object get(VillageSiege siege, String field) {
		try {
			Field f = VillageSiege.class.getDeclaredField(field);
			f.setAccessible(true);
			return f.get(siege);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("village_siege: cannot read " + field, e);
		}
	}

	private static void set(VillageSiege siege, String field, Object value) {
		try {
			Field f = VillageSiege.class.getDeclaredField(field);
			f.setAccessible(true);
			f.set(siege, value);
		} catch (ReflectiveOperationException e) {
			throw new IllegalStateException("village_siege: cannot write " + field, e);
		}
	}

	/** Skips {@link VillageSiege#tick} while a real player is inside the claim. */
	static final class Gated implements CustomSpawner {
		final VillageSiege inner;
		BlockPos centre;
		final int radius;

		Gated(VillageSiege inner, BlockPos centre, int radius) {
			this.inner = inner;
			this.centre = centre;
			this.radius = radius;
		}

		@Override
		public void tick(ServerLevel level, boolean spawnEnemies) {
			if (playerInside(level)) return;
			inner.tick(level, spawnEnemies);
		}

		private boolean playerInside(ServerLevel level) {
			long r2 = (long) radius * radius;
			Vec3 c = Vec3.atCenterOf(centre);
			for (Player p : level.players()) {
				if (p.isSpectator() || !p.isAlive()) continue;
				if (p.distanceToSqr(c) <= r2) return true;
			}
			return false;
		}
	}
}
