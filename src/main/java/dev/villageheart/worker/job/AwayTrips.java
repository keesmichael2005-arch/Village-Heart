package dev.villageheart.worker.job;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.core.mine.AwayState.AwayTrip;
import dev.villageheart.core.mine.AwayState.PriorFlags;
import dev.villageheart.core.mine.SurfaceReason;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.world.AwayCodecs;
import dev.villageheart.worker.WorkerAttachments;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * A worker that goes away to its site and vanishes there (the miner in the Mineshaft, M16 plan 3.2 #1 and #2). The villager stays a
 * loaded entity: it is moved to the hold spot and made invisible, invulnerable, silent and {@code NoAI} (which freezes the vanilla
 * brain), keeping its UUID, attachments and memories. The flags it had before are saved with the trip and given back when it comes up.
 * The state is its own persistent attachment, separate from the worker data, so firing cannot lose what is needed to restore the flags.
 */
public final class AwayTrips {
	public static final AttachmentType<AwayState> AWAY = AttachmentRegistry.createPersistent(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "away"), AwayCodecs.AWAY_STATE);

	/** Test and log hook: when, where and why a villager last came up. */
	public record SurfaceRecord(long gameTime, Vec3 pos, SurfaceReason reason) {}

	private static final Map<UUID, SurfaceRecord> LAST = new HashMap<>();
	/** Villagers that loaded with an away trip (or a broken one): looked at at the end of the tick, never inside the load callback. */
	private static final Set<Villager> LOADED = new LinkedHashSet<>();
	/** Villagers that must come up but found no free cell: retried every tick. */
	private static final Set<Villager> RETRY = Collections.newSetFromMap(new java.util.WeakHashMap<>());

	private AwayTrips() {}

	public static AwayState state(Entity e) {
		AwayState s = e.getAttached(AWAY);
		return s == null ? AwayState.NONE : s;
	}

	public static void set(Entity e, AwayState s) {
		if (s.equals(AwayState.NONE)) e.removeAttached(AWAY);
		else e.setAttached(AWAY, s);
	}

	public static boolean isAway(Entity e) { return state(e).trip().isPresent(); }

	public static Optional<SurfaceRecord> lastSurface(UUID villager) { return Optional.ofNullable(LAST.get(villager)); }

	public static void clearForTests() {
		LAST.clear();
		LOADED.clear();
		RETRY.clear();
	}

	private static Pos pos(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }

	private static BlockPos block(Pos p) { return new BlockPos(p.x(), p.y(), p.z()); }

	// ---- hide ---------------------------------------------------------------------------------------

	/** Hides {@code v} in {@code site}: it is moved to {@code hold} and its flags are set. No effect when it is already away. */
	public static void hide(ServerLevel level, Villager v, String jobId, HeartLocation site, BlockPos hold) {
		AwayState st = state(v);
		if (st.trip().isPresent()) return;
		PriorFlags prior = new PriorFlags(v.isInvisible(), v.isInvulnerable(), v.isSilent(), v.isNoAi(), v.isCustomNameVisible());
		AwayTrip trip = new AwayTrip(jobId, site, pos(v.blockPosition()), pos(hold), level.getGameTime(), prior);
		set(v, st.withTrip(Optional.of(trip)));
		// the brain is frozen from the next tick on: nothing stale may be left in it
		v.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
		v.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
		v.getBrain().eraseMemory(MemoryModuleType.PATH);
		v.getNavigation().stop();
		if (v.isTrading()) v.setTradingPlayer(null);
		v.setInvisible(true);
		v.setInvulnerable(true);
		v.setSilent(true);
		v.setNoAi(true);
		v.setCustomNameVisible(false);
		v.setDeltaMovement(Vec3.ZERO);
		v.teleportTo(hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5);
	}

	/** Keeps a hidden villager at its hold spot (a piston can move a NoAI entity). Returns true when it had to be moved back. */
	public static boolean repin(Villager v) {
		Optional<AwayTrip> trip = state(v).trip();
		if (trip.isEmpty()) return false;
		BlockPos hold = block(trip.get().hold());
		Vec3 want = new Vec3(hold.getX() + 0.5, hold.getY(), hold.getZ() + 0.5);
		if (v.position().distanceTo(want) <= 0.5) return false;
		v.setDeltaMovement(Vec3.ZERO);
		v.teleportTo(want.x, want.y, want.z);
		return true;
	}

	// ---- surface ------------------------------------------------------------------------------------

	/**
	 * Brings {@code v} up: the flags it had before are restored, the trip is cleared and it stands at the surface spot (or, if that is
	 * blocked, at the nearest free standable cell within 2 blocks of the site marker). With no such cell it stays hidden, its status
	 * becomes SITE_UNREACHABLE and false is returned. Also true when it was not away at all.
	 */
	public static boolean surface(ServerLevel level, Villager v, SurfaceReason reason) {
		AwayState st = state(v);
		if (st.trip().isEmpty()) {
			if (st.broken()) repairBroken(v, st);
			return true;
		}
		AwayTrip trip = st.trip().get();
		Vec3 where = findSurface(level, v, trip);
		if (where == null) {
			WorkerStatuses.set(v.getUUID(), WorkerStatus.SITE_UNREACHABLE);
			return false;
		}
		PriorFlags p = trip.prior();
		v.setInvisible(p.invisible());
		v.setInvulnerable(p.invulnerable());
		v.setSilent(p.silent());
		v.setNoAi(p.noAi());
		v.setCustomNameVisible(p.customNameVisible());
		set(v, st.withTrip(Optional.empty()).withLastReason(Optional.of(reason)));
		v.setDeltaMovement(Vec3.ZERO);
		v.teleportTo(where.x, where.y, where.z);
		LAST.put(v.getUUID(), new SurfaceRecord(level.getGameTime(), v.position(), reason));
		if (WorkerStatuses.get(v.getUUID()) == WorkerStatus.SITE_UNREACHABLE) WorkerStatuses.set(v.getUUID(), null);
		return true;
	}

	private static void repairBroken(Villager v, AwayState st) {
		VillageHeart.LOG.warn("Villager {} had an unreadable away trip: its flags go back to the vanilla defaults", v.getUUID());
		PriorFlags p = PriorFlags.VANILLA;
		v.setInvisible(p.invisible());
		v.setInvulnerable(p.invulnerable());
		v.setSilent(p.silent());
		v.setNoAi(p.noAi());
		set(v, st.repaired());
	}

	private static Vec3 findSurface(ServerLevel level, Villager v, AwayTrip trip) {
		BlockPos feet = block(trip.surface());
		if (free(level, v, feet)) return Vec3.atBottomCenterOf(feet);
		BlockPos marker = new BlockPos(trip.site().x(), trip.site().y(), trip.site().z());
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (int dx = -2; dx <= 2; dx++) {
			for (int dz = -2; dz <= 2; dz++) {
				for (int dy = -1; dy <= 2; dy++) {
					BlockPos c = marker.offset(dx, dy, dz);
					if (c.equals(marker) || c.above().equals(marker)) continue;
					if (!free(level, v, c) || !standable(level, c)) continue;
					double d = c.distSqr(feet);
					if (d < bestD) {
						bestD = d;
						best = c;
					}
				}
			}
		}
		return best == null ? null : Vec3.atBottomCenterOf(best);
	}

	private static boolean loaded(ServerLevel level, BlockPos p) { return level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4) != null; }

	private static boolean free(ServerLevel level, Villager v, BlockPos feet) {
		if (!loaded(level, feet)) return false;
		AABB box = v.getDimensions(v.getPose()).makeBoundingBox(Vec3.atBottomCenterOf(feet));
		return level.noCollision(box);
	}

	private static boolean standable(ServerLevel level, BlockPos feet) {
		BlockPos below = feet.below();
		return loaded(level, below) && !level.getBlockState(below).getCollisionShape(level, below).isEmpty();
	}

	// ---- fire / release -----------------------------------------------------------------------------

	/**
	 * The villager stops being a worker: it comes up (reason NOT_A_MINER) and what was never put into its inventory is dropped at its feet.
	 * Progress and trip are forgotten; the haul counter is kept (a re-hired miner does not repeat its first hauls). False when it
	 * could not come up yet (it stays hidden and is retried every tick).
	 */
	public static boolean release(ServerLevel level, Villager v) {
		AwayState st = state(v);
		if (st.trip().isEmpty() && !st.broken() && st.pending().isEmpty() && st.progress().units() == 0 && st.progressShaft().isEmpty()) return true;
		if (!surface(level, v, SurfaceReason.NOT_A_MINER)) {
			RETRY.add(v);
			return false;
		}
		st = state(v);
		for (Map.Entry<String, Integer> e : st.pending().entrySet()) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getKey()));
			int left = e.getValue();
			while (left > 0) {
				ItemStack stack = new ItemStack(item, Math.min(left, item.getDefaultMaxStackSize()));
				left -= stack.getCount();
				v.spawnAtLocation(level, stack);
			}
		}
		set(v, AwayState.NONE.withRolls(st.rolls()).withLastReason(st.lastReason()));
		return true;
	}

	// ---- events -------------------------------------------------------------------------------------

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isAway(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
		AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> isAway(entity) ? InteractionResult.FAIL : InteractionResult.PASS);
		ServerEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof Villager v) {
				AwayState st = state(v);
				if (st.trip().isPresent() || st.broken()) LOADED.add(v);
				// Invulnerable, Silent and NoAI are saved with the entity, Invisible is not (vanilla only saves it as a potion effect)
				if (st.trip().isPresent()) v.setInvisible(true);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(AwayTrips::processLoaded);
		ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> {
			// ConversionType copies NoAI, Invulnerable and Silent to the new mob: a witch made from a hidden miner must not inherit them
			Optional<AwayTrip> trip = state(previous).trip();
			if (trip.isPresent() && converted instanceof Mob m) {
				PriorFlags p = trip.get().prior();
				m.setInvisible(p.invisible());
				m.setInvulnerable(p.invulnerable());
				m.setSilent(p.silent());
				m.setNoAi(p.noAi());
				m.setCustomNameVisible(p.customNameVisible());
			}
		});
	}

	/**
	 * Villagers that loaded this tick with a trip: one that is not a worker of the trip's job comes up (a fired worker whose entity was
	 * unloaded, a removed village). Registered workers are left to the worker system and to {@code MinerSystem}. Runs at the end of a
	 * server tick, never inside the load callback, so no entity is moved while its chunk is loading. Also retries stuck surfacings.
	 */
	private static void processLoaded(MinecraftServer server) {
		if (LOADED.isEmpty() && RETRY.isEmpty()) return;
		List<Villager> batch = new ArrayList<>(LOADED);
		LOADED.clear();
		for (Villager v : batch) {
			if (v.isRemoved() || !(v.level() instanceof ServerLevel level)) continue;
			AwayState st = state(v);
			if (st.broken() && st.trip().isEmpty()) {
				repairBroken(v, st);
				continue;
			}
			Optional<AwayTrip> trip = st.trip();
			if (trip.isEmpty()) continue;
			Optional<WorkerData> data = WorkerAttachments.get(v);
			if (data.isEmpty() || !data.get().jobId().equals(trip.get().jobId())) release(level, v);
		}
		for (Villager v : new ArrayList<>(RETRY)) {
			if (v.isRemoved() || !(v.level() instanceof ServerLevel level)) {
				RETRY.remove(v);
				continue;
			}
			if (release(level, v)) RETRY.remove(v);
		}
	}
}
