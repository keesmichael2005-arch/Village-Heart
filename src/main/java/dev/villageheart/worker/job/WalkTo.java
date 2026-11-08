package dev.villageheart.worker.job;

import dev.villageheart.core.Balance;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Walking to a spot that may be far and behind walls. The vanilla route (a WALK_TARGET and {@code MoveToTargetSink}) plans at most about
 * 16 blocks (FOLLOW_RANGE) and gives up on the first dead end: a worker inside a room whose tree stands on the far side of a wall walks to
 * the nearest wall and stops. So this plans its own path with a longer range and more search nodes ({@link #RANGE}, {@link #NODES}) and
 * hands it to the navigation, and publishes the path in the PATH memory so {@code InteractWithDoor} still opens the doors on the way.
 * It gives up when the villager has not moved a block in {@link Balance#WALK_STALL_TICKS}, or after {@link Balance#WALK_TOTAL_TICKS}.
 * Not saved. WALK_TARGET is never set, so it does not collide with {@code DepositTrip}.
 */
public final class WalkTo {
	public enum State { WALKING, ARRIVED, UNREACHABLE }

	private static final boolean TRACE = Boolean.getBoolean("villageheart.lumber.trace");
	private static final int RANGE = 48;
	private static final float NODES = 4.0F;
	private static final float SPEED = 0.5F;
	private static final int REPATH_TICKS = 10;
	/** Two villagers squeezing through one door push each other off the doorway and both stop: after this long without a step, one waits. */
	private static final int JAM_TICKS = 30;

	private BlockPos target;
	private double reach;
	private double closest;
	private long start;
	private long anchorAt;
	private Vec3 anchor;
	private long nextPathAt;
	/** Where the pathfinder could get no nearer: while the villager stays there it counts as arrived. */
	private Vec3 settled;
	/** The path this class handed to the navigation: when the navigation holds another one, vanilla replaced it. */
	private Path mine;
	/** The last long path planned here (kept when the navigation drops it: the jam rule reads where the walker was heading). */
	private Path lastPath;
	/** Jam breaker: where the villager was {@link #JAM_TICKS} ago. */
	private Vec3 jamAnchor;
	private long jamAt;

	public boolean active() { return target != null; }

	public BlockPos target() { return target; }

	/** Same, with no relaxed arrival: only {@code reach} counts. */
	public void begin(BlockPos target, double reach, long now) { begin(target, reach, reach, now); }

	/**
	 * @param reach   arrived when the villager is this close (3D, to the bottom centre of {@code target})
	 * @param closest also arrived when the pathfinder can get no nearer (a partial path of one node: the target is behind leaves, a
	 *                fence or water) and the villager is within this distance
	 */
	public void begin(BlockPos target, double reach, double closest, long now) {
		this.target = target;
		this.reach = reach;
		this.closest = closest;
		this.start = now;
		this.anchorAt = now;
		this.anchor = null;
		this.nextPathAt = now;
		this.lastTickAt = Long.MIN_VALUE;
		this.settled = null;
		this.jamAnchor = null;
		this.lastPath = null;
	}

	/** Keeps the stall statistics but points at a new spot (an item that rolled). */
	public void retarget(BlockPos newTarget) {
		if (target == null || newTarget.distSqr(target) < 4) return;
		target = newTarget;
		settled = null;
		nextPathAt = 0;
	}

	/** Called with each villager a walker asks to step aside (tests observe it; null in play). */
	private static java.util.function.Consumer<Villager> nudgeSeam;

	public static void setNudgeListenerForTest(java.util.function.Consumer<Villager> listener) { nudgeSeam = listener; }

	/**
	 * A plain villager that stands in this walker's way: on one of the next cells of its path (the node it is heading for, the one before
	 * and the two after, each a 1 x 2 x 1 box) or within a block of it, standing still (navigation idle or no horizontal motion). It is
	 * asked to step aside, because no other rule moves it and it can idle there longer than {@link Balance#WALK_STALL_TICKS} (an idler
	 * on the grass in front of a door, two cells from a walker that stopped inside). Never one of our workers (two walkers settle it by
	 * the id rule; a worker standing at its work keeps working), nor a trading, sleeping or NoAI villager.
	 */
	private static Villager idleBlocker(Villager v, Path path, boolean live) {
		java.util.List<net.minecraft.world.phys.AABB> cells = new java.util.ArrayList<>();
		// with a live path also the cell just passed and a block around the walker (it pushes against the idler); once the navigation
		// gave up only the cells ahead on the last planned path count, so a villager that merely stands nearby is left alone
		if (live) cells.add(v.getBoundingBox().inflate(1.0));
		if (path != null && path.getNodeCount() > 0) {
			int next = Math.min(path.getNextNodeIndex(), path.getNodeCount() - 1);
			int i0 = live ? Math.max(0, next - 1) : next;
			for (int i = i0; i < Math.min(path.getNodeCount(), i0 + 4); i++) {
				BlockPos n = path.getNode(i).asBlockPos();
				cells.add(new net.minecraft.world.phys.AABB(n.getX(), n.getY(), n.getZ(), n.getX() + 1, n.getY() + 2, n.getZ() + 1));
			}
		}
		Villager best = null;
		for (Villager o : v.level().getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(4.0), x -> x != v && x.isAlive())) {
			if (dev.villageheart.worker.WorkerAttachments.isWorker(o) || o.isTrading() || o.isSleeping() || o.isNoAi()) continue;
			if (!o.getNavigation().isDone() && o.getDeltaMovement().horizontalDistanceSqr() > 1.0E-4) continue; // walking through
			boolean inTheWay = false;
			for (var c : cells) if (c.intersects(o.getBoundingBox())) inTheWay = true;
			if (inTheWay && (best == null || o.distanceToSqr(v) < best.distanceToSqr(v))) best = o;
		}
		return best;
	}

	/** Asks {@code o} to step aside: a vanilla WALK_TARGET a few blocks away from {@code from} (vanilla's MoveToTargetSink walks it there). */
	private static boolean stepAside(Villager o, Villager from) {
		Vec3 away = net.minecraft.world.entity.ai.util.LandRandomPos.getPosAway(o, 4, 2, from.position());
		if (away == null) away = net.minecraft.world.entity.ai.util.DefaultRandomPos.getPosAway(o, 4, 2, from.position());
		if (away == null) return false;
		o.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new net.minecraft.world.entity.ai.memory.WalkTarget(away, SPEED, 0));
		if (nudgeSeam != null) nudgeSeam.accept(o);
		return true;
	}

	/** True when another villager stands within a block and has the lower id: this one gives way. */
	private static boolean jamNeighbour(Villager v) {
		for (Villager o : v.level().getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(1.0), x -> x != v && x.isAlive())) {
			if (o.getId() < v.getId()) return true;
		}
		return false;
	}

	public void clear() { target = null; }

	/**
	 * Ends a walk for good: forgets the target, stops the navigation and erases the PATH memory. The vanilla {@code MoveToTargetSink} only
	 * starts while PATH is absent, so a path left behind by a walk that was cut short (the work day ended on the way) would keep a worker
	 * from ever walking to its bed again.
	 */
	public void release(Villager v) {
		target = null;
		if (mine != null || v.getBrain().hasMemoryValue(MemoryModuleType.PATH)) {
			v.getNavigation().stop();
			v.getBrain().eraseMemory(MemoryModuleType.PATH);
		}
		mine = null;
	}

	/** The 3D distance from the villager to the bottom centre of the target block. */
	public static double distance(Villager v, BlockPos target) { return v.position().distanceTo(Vec3.atBottomCenterOf(target)); }

	/** Why the last {@link State#UNREACHABLE} was reported (for logs). */
	public String reason() { return reason; }

	private String reason = "";
	private long lastTickAt = Long.MIN_VALUE;

	public State tick(Villager v, long now) {
		if (target == null) return State.ARRIVED;
		// A walk that was not ticked for a while (the worker yielded to a deposit trip, or WORK was interrupted) must not count that
		// time as standing still or as walking: the clocks start again.
		if (lastTickAt != Long.MIN_VALUE && now - lastTickAt > 5) {
			long gap = now - lastTickAt;
			start += gap;
			anchorAt = now;
			anchor = null;
			jamAnchor = null;
			nextPathAt = now;
		}
		lastTickAt = now;
		PathNavigation nav = v.getNavigation();
		double d = distance(v, target);
		// While the worker stands at its spot (chopping a 40-log tree takes 1600 ticks) the give-up clocks must not run: they measure
		// the walk that comes after the worker was pushed away or came back from a trip, not the work done here.
		if (settled != null && v.position().distanceTo(settled) <= 1.5) {
			start = now;
			anchorAt = now;
			return State.ARRIVED;
		}
		settled = null;
		if (d <= reach + 0.1) {
			start = now;
			anchorAt = now;
			anchor = null;
			nav.stop();
			v.getBrain().eraseMemory(MemoryModuleType.PATH);
			return State.ARRIVED;
		}
		if (anchor == null || v.position().distanceTo(anchor) >= 1.0) {
			anchor = v.position();
			anchorAt = now;
		}
		if (now - anchorAt > Balance.WALK_STALL_TICKS || now - start > Balance.WALK_TOTAL_TICKS) {
			reason = (now - anchorAt > Balance.WALK_STALL_TICKS ? "no movement for " + (now - anchorAt) : "walking for " + (now - start))
					+ " ticks, " + Math.round(d * 10) / 10.0 + " blocks from " + target + ", at " + v.blockPosition();
			nav.stop();
			return State.UNREACHABLE;
		}
		if (TRACE && (now - start) % 40 == 0) {
			Path cur = nav.getPath();
			String ahead = "";
			if (cur != null && cur.getNodeCount() > cur.getNextNodeIndex()) {
				var np = cur.getNode(cur.getNextNodeIndex()).asBlockPos();
				ahead = " exact=" + v.position() + " next=" + np + " " + v.level().getBlockState(np) + " above " + v.level().getBlockState(np.above());
			}
			dev.villageheart.VillageHeart.LOG.info("[lumber] {} @{} walking" + ahead + " pos={} d={} done={} stuck={} path={}", v.getId(), now, v.blockPosition(),
					Math.round(d * 10) / 10.0, nav.isDone(), nav.isStuck(), cur == null ? "null" : cur.getNextNodeIndex() + "/" + cur.getNodeCount()
					+ " end=" + (cur.getNodeCount() == 0 ? "-" : cur.getNode(cur.getNodeCount() - 1).asBlockPos()));
		}
		if (jamAnchor == null || v.position().distanceTo(jamAnchor) >= 0.6) {
			jamAnchor = v.position();
			jamAt = now;
		} else if (now - jamAt >= JAM_TICKS) {
			// Stopped on the way (another villager in the doorway). A villager that just stands there is asked to step aside (otherwise an
			// idle villager outside a door makes a worker give up a tree in the open: backlog-2). Of two walkers, the one with the higher id
			// steps back along its own path and waits, so the other one gets through; the one with the lower id just goes on. Moving back
			// counts as moving for the stall rule.
			jamAnchor = null;
			Path cur = nav.getPath();
			// also once the navigation gave up next to a standing villager: then the last path planned here shows the way
			Villager idle = idleBlocker(v, cur != null ? cur : lastPath, cur != null);
			if (idle != null && stepAside(idle, v)) {
				jamAt = now;
				if (TRACE) dev.villageheart.VillageHeart.LOG.info("[lumber] {} @{} jam at {}: asks the idle villager {} at {} to step aside", v.getId(), now,
						v.blockPosition(), idle.getId(), idle.blockPosition());
			} else if (!nav.isDone() && cur != null && cur.getNextNodeIndex() > 0 && jamNeighbour(v)) {
				var back = cur.getNode(Math.max(0, cur.getNextNodeIndex() - 4)).asBlockPos();
				nav.moveTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5, SPEED);
				mine = nav.getPath();
				nextPathAt = now + 30 + v.getRandom().nextInt(30);
				if (TRACE) dev.villageheart.VillageHeart.LOG.info("[lumber] {} @{} jam at {}: steps back to {}", v.getId(), now, v.blockPosition(), back);
			} else {
				jamAt = now;
			}
		}
		if (!nav.isDone() && mine != null && nav.getPath() != mine) {
			// The navigation recomputes its path when a block on it changes (a door opens) and does so with the villager's 16-block range, which
			// can lead away from the tree and cost a walk to the far corner of the yard. Plan the long path again at once.
			if (TRACE) dev.villageheart.VillageHeart.LOG.info("[lumber] {} @{} the navigation replaced my path: planning again", v.getId(), now);
			nav.stop();
			mine = null;
			nextPathAt = now;
		}
		if (nav.isDone() && now >= nextPathAt) {
			nextPathAt = now + REPATH_TICKS;
			nav.setMaxVisitedNodesMultiplier(NODES);
			Path path = nav.createPath(target, 1, RANGE);
			nav.setMaxVisitedNodesMultiplier(1.0F);
			if (path != null && !path.canReach() && path.getNodeCount() <= 1 && d <= closest) {
				nav.stop();
				v.getBrain().eraseMemory(MemoryModuleType.PATH);
				settled = v.position();
				return State.ARRIVED;
			}
			boolean moving = path != null && nav.moveTo(path, SPEED);
			if (moving) {
				v.getBrain().setMemory(MemoryModuleType.PATH, path);
				mine = nav.getPath();
				lastPath = mine;
			}
			if (TRACE) {
				dev.villageheart.VillageHeart.LOG.info("[lumber] {} @{} path to {} from {}: {} nodes={} reached={} moving={} end={}", v.getId(), now, target,
						v.blockPosition(), path == null ? "null" : "ok", path == null ? 0 : path.getNodeCount(), path != null && path.canReach(), moving,
						path == null || path.getNodeCount() == 0 ? "-" : path.getNode(path.getNodeCount() - 1).asBlockPos());
			}
		} else if (!nav.isDone() && nav.getPath() != null) {
			v.getBrain().setMemory(MemoryModuleType.PATH, nav.getPath());
		}
		return State.WALKING;
	}
}
