package dev.villageheart.heart;

import dev.villageheart.VillageHeart;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.ActionGate;
import dev.villageheart.core.heart.HeartAction;
import dev.villageheart.core.heart.Rejection;
import dev.villageheart.core.heart.RateLimiter;
import dev.villageheart.core.worker.HireRefusal;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.heart.net.HeartActionResultPayload;
import dev.villageheart.heart.net.HeartNetwork;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.HireResult;
import dev.villageheart.worker.Workers;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * Server side of the Heart screen actions. Every C2S receiver calls {@link #handle}: rate limit first (a flood of bad packets
 * costs one map lookup each), then {@link ActionGate} (open screen, current village, rank re-read from the current state), then the
 * action itself, then a reply. Everything a client names (villager, building) is looked up in the Heart's own dimension.
 */
public final class HeartActions {
	private HeartActions() {}

	/** Shared by all players, keyed by player UUID: survives reopening the screen. */
	public static final RateLimiter RATE = new RateLimiter();

	private static final int RATE_REPLY_TICKS = 20;
	private static final int WARN_TICKS = 1200;
	private static final Map<UUID, Long> RATE_REPLIED = new HashMap<>();
	private static final Map<UUID, Long> WARNED = new HashMap<>();

	/** Test hook: how many NO_PERMISSION warnings were logged. */
	public static int warnCount;

	/** Forget one player (disconnect). */
	public static void forget(UUID player) {
		RATE.forget(player);
		RATE_REPLIED.remove(player);
		WARNED.remove(player);
	}

	/** Forget everything (server stopping, and between GameTests). */
	public static void reset() {
		RATE.clear();
		RATE_REPLIED.clear();
		WARNED.clear();
		warnCount = 0;
	}

	public static void handle(ServerPlayer player, int containerId, HeartAction action) {
		MinecraftServer server = player.level().getServer();
		long now = server.getTickCount();
		UUID id = player.getUUID();

		boolean allowed = RATE.tryAcquire(id, now);
		HeartMenu menu = player.containerMenu instanceof HeartMenu m && m.containerId == containerId && m.nearHeart(player) ? m : null;
		boolean sameEpoch = menu != null && menu.epochCurrent();
		Optional<VillageState> village = VillageClaims.current(server);
		Optional<Rejection> rejected = ActionGate.check(allowed, menu != null, sameEpoch, village, id, action);
		if (rejected.isPresent()) {
			reject(player, containerId, rejected.get(), now);
			return;
		}
		VillageState state = village.get();
		ServerLevel heartLevel = Workers.levelOf(server, state.heart());
		if (heartLevel == null) {
			reject(player, containerId, Rejection.NO_VILLAGE, now);
			return;
		}
		boolean ok = switch (action) {
			case HeartAction.Assign a -> assign(player, containerId, server, state, heartLevel, a);
			case HeartAction.Fire f -> fire(player, containerId, server, f);
			case HeartAction.Recheck r -> recheck(player, containerId, server, state, r);
		};
		if (ok) {
			HeartFacts.invalidate();
			HeartMenu.markAllDirty(server);
		}
	}

	// ---- the actions ---------------------------------------------------------------------------

	private static boolean assign(ServerPlayer player, int containerId, MinecraftServer server, VillageState state,
			ServerLevel heartLevel, HeartAction.Assign a) {
		long now = server.getTickCount();
		Optional<JobType> job = JobType.byId(a.jobId());
		if (job.isEmpty()) return reject(player, containerId, Rejection.UNKNOWN_JOB, now);
		Entity target = heartLevel.getEntity(a.villager());
		if (target == null) return reject(player, containerId, Rejection.VILLAGER_NOT_FOUND, now);
		if (a.building().isPresent() && state.building(a.building().get()).isEmpty()) {
			return reject(player, containerId, Rejection.NOT_A_BUILDING, now);
		}
		HireResult result = Workers.hire(heartLevel, target, job.get(), a.building());
		if (result instanceof HireResult.Refused r) {
			reply(player, containerId, false, r.reason().langKey(), refusalText(r.reason()), List.of());
			return false;
		}
		reply(player, containerId, true, "message.villageheart.hire.hired", "Hired as %s.", List.of(job.get().displayName()));
		return true;
	}

	private static boolean fire(ServerPlayer player, int containerId, MinecraftServer server, HeartAction.Fire f) {
		Optional<Villager> loaded = Workers.findLoaded(server, f.villager());
		boolean fired;
		if (loaded.isPresent() && loaded.get().level() instanceof ServerLevel level) fired = Workers.fire(level, loaded.get());
		else fired = Workers.fireById(server, f.villager());
		if (fired) reply(player, containerId, true, "message.villageheart.hire.fired", "That villager is no longer a worker.", List.of());
		else reply(player, containerId, false, "message.villageheart.hire.not_a_worker", "That villager is not one of your workers.", List.of());
		return fired;
	}

	private static boolean recheck(ServerPlayer player, int containerId, MinecraftServer server, VillageState state,
			HeartAction.Recheck r) {
		HeartLocation marker = r.marker();
		if (state.building(marker).isEmpty()) return reject(player, containerId, Rejection.NOT_A_BUILDING, server.getTickCount());
		Buildings.request(server, marker);
		reply(player, containerId, true, "gui.villageheart.heart.checking", "Checking the building now.", List.of());
		return true;
	}

	private static String refusalText(HireRefusal r) {
		return switch (r) {
			case NO_VILLAGE -> "There is no village in this world.";
			case NOT_A_VILLAGER -> "That is not a villager.";
			case NOT_ADULT -> "Only adult villagers can be hired.";
			case NITWIT -> "Nitwits cannot work.";
			case OUTSIDE_CLAIM -> "That villager is outside the village.";
			case ALREADY_EMPLOYED -> "That villager already has a job.";
			case TIER_LOCKED -> "The village is too small for that job.";
			case NOT_IN_PREFERRED_BUILDING -> "That building has no free job block.";
			case NO_FREE_SLOT -> "There is no free job block for that job.";
		};
	}

	// ---- replies -------------------------------------------------------------------------------

	private static void reply(ServerPlayer player, int containerId, boolean ok, String key, String fallback, List<String> args) {
		HeartNetwork.send(player, new HeartActionResultPayload(containerId, ok, key, fallback, args));
	}

	/** Always returns false (so an action can {@code return reject(...)}). */
	private static boolean reject(ServerPlayer player, int containerId, Rejection why, long now) {
		UUID id = player.getUUID();
		if (why == Rejection.RATE_LIMITED) {
			Long last = RATE_REPLIED.get(id);
			if (last != null && now >= last && now - last < RATE_REPLY_TICKS) return false;
			RATE_REPLIED.put(id, now);
		}
		if (why == Rejection.NO_PERMISSION) {
			// a normal client never sends this (the buttons are hidden): warn, but at most once per player per minute
			Long last = WARNED.get(id);
			if (last == null || now < last || now - last >= WARN_TICKS) {
				WARNED.put(id, now);
				warnCount++;
				VillageHeart.LOG.warn("{} sent a Heart screen action without permission", player.getName().getString());
			}
		} else {
			VillageHeart.LOG.debug("Heart screen action from {} rejected: {}", player.getName().getString(), why);
		}
		reply(player, containerId, false, why.langKey(), why.message(), List.of());
		return false;
	}
}
