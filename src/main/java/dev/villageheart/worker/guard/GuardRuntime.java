package dev.villageheart.worker.guard;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.guard.GuardShift;
import dev.villageheart.core.guard.PatrolRoute;
import dev.villageheart.core.room.Pos;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageTicker;
import dev.villageheart.worker.job.WalkTo;
import dev.villageheart.worker.job.WorkStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;

/**
 * The per-tick work of the two guard behaviours (M18 3.2 #3). The behaviour classes stay thin, as M9's do; the state that must survive a
 * behaviour restart lives here, per guard, not in the behaviour. Server thread only.
 */
public final class GuardRuntime {
	/** What the guard does now on duty (for tests and the Villagers tab). */
	public enum Mode { PATROL, FIGHT, EQUIP }

	/** One guard's runtime state. */
	static final class State {
		final WalkTo walk = new WalkTo();
		final PatrolRoute route = new PatrolRoute();
		BlockPos point;
		long dwellUntil;
		Mode mode = Mode.PATROL;
		int arrivals;
		BlockPos lastArrival;
	}

	private static final Map<UUID, State> STATES = new HashMap<>();

	private GuardRuntime() {}

	static State state(Villager g) { return STATES.computeIfAbsent(g.getUUID(), k -> new State()); }

	public static void resetForTests() { STATES.clear(); }

	public static Mode modeOf(UUID guard) {
		State s = STATES.get(guard);
		return s == null ? Mode.PATROL : s.mode;
	}

	/** How many patrol points this guard has reached (and dwelt at) so far. */
	public static int arrivals(UUID guard) {
		State s = STATES.get(guard);
		return s == null ? 0 : s.arrivals;
	}

	public static Optional<BlockPos> lastArrival(UUID guard) {
		State s = STATES.get(guard);
		return s == null ? Optional.empty() : Optional.ofNullable(s.lastArrival);
	}

	public static Optional<BlockPos> patrolTarget(UUID guard) {
		State s = STATES.get(guard);
		return s == null ? Optional.empty() : Optional.ofNullable(s.point);
	}

	/** True while the guard is dwelling at a patrol point it has reached. */
	public static boolean isDwelling(UUID guard, long now) {
		State s = STATES.get(guard);
		return s != null && s.dwellUntil != 0 && now < s.dwellUntil;
	}

	public static boolean isUnreachable(UUID guard, BlockPos point, long now) {
		State s = STATES.get(guard);
		return s != null && s.route.skipped(new Pos(point.getX(), point.getY(), point.getZ()), now);
	}

	static void tickDuty(ServerLevel level, Villager guard, long now) {
		try (var t = VillageTicker.section()) {
			if (now % 100 == guard.getId() % 100) WorkStats.add("guard", "duty_samples", 1);
			State s = state(guard);
			if (GuardCombat.engaged(guard)) {
				s.mode = Mode.FIGHT;
				return; // the combat behaviour in CORE owns the navigation
			}
			s.mode = Mode.PATROL;
			patrol(level, guard, s, now);
		}
	}

	static void stopDuty(ServerLevel level, Villager guard) {
		State s = STATES.get(guard.getUUID());
		if (s != null) s.walk.release(guard);
	}

	static void tickOff(ServerLevel level, Villager guard, long now) {
		try (var t = VillageTicker.section()) {
			// filled in by the gear-return and sparring steps
		}
	}

	static void stopOff(ServerLevel level, Villager guard) {
		State s = STATES.get(guard.getUUID());
		if (s != null) s.walk.release(guard);
	}

	// ---- patrol ----------------------------------------------------------------------------------

	private static Pos pos(BlockPos p) { return new Pos(p.getX(), p.getY(), p.getZ()); }

	private static void patrol(ServerLevel level, Villager guard, State s, long now) {
		List<BlockPos> points = PatrolPoints.of(level.getServer());
		if (points.isEmpty()) return;
		List<Pos> route = new ArrayList<>();
		for (BlockPos p : points) route.add(pos(p));
		if (s.point == null || !points.contains(s.point)) {
			s.walk.release(guard);
			s.point = points.get(startIndex(level, guard, points.size()));
			s.dwellUntil = 0;
		}
		if (s.dwellUntil != 0) {
			if (now < s.dwellUntil) return; // looking around
			Pos next = s.route.next(route, pos(s.point), now);
			s.point = points.get(route.indexOf(next));
			s.dwellUntil = 0;
		}
		if (!s.walk.active() || !s.walk.target().equals(s.point)) s.walk.begin(s.point, 1.5, 6.0, now);
		WalkTo.State st = s.walk.tick(guard, now);
		if (st == WalkTo.State.ARRIVED) {
			s.arrivals++;
			s.lastArrival = s.point;
			s.dwellUntil = now + Balance.PATROL_DWELL_TICKS;
			s.walk.release(guard);
		} else if (st == WalkTo.State.UNREACHABLE) {
			s.route.markUnreachable(pos(s.point), now);
			s.walk.release(guard);
			Pos next = s.route.next(route, pos(s.point), now);
			s.point = points.get(route.indexOf(next));
		}
	}

	/** Guard {@code i} of {@code n} on its shift starts at {@code i * points / n}. */
	private static int startIndex(ServerLevel level, Villager guard, int pointCount) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return 0;
		Optional<GuardShift> mine = village.get().guardShift(guard.getUUID());
		if (mine.isEmpty()) return 0;
		int index = 0, count = 0;
		for (UUID id : village.get().guardIds()) {
			Optional<GuardShift> other = village.get().guardShift(id);
			if (other.isEmpty() || other.get().shift() != mine.get().shift()) continue;
			if (id.equals(guard.getUUID())) index = count;
			count++;
		}
		return PatrolRoute.start(pointCount, index, count);
	}
}
