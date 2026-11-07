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
	}

	/** Keeps the stall statistics but points at a new spot (an item that rolled). */
	public void retarget(BlockPos newTarget) {
		if (target == null || newTarget.distSqr(target) < 4) return;
		target = newTarget;
		settled = null;
		nextPathAt = 0;
	}

	/** True when another villager stands within a block and has the lower id: this one gives way. */
	private static boolean jamNeighbour(Villager v) {
		for (Villager o : v.level().getEntitiesOfClass(Villager.class, v.getBoundingBox().inflate(1.0), x -> x != v && x.isAlive())) {
			if (o.getId() < v.getId()) return true;
		}
		return false;
	}

	public void clear() { target = null; }

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
		} else if (now - jamAt >= JAM_TICKS && !nav.isDone()) {
			// Stopped on the way (another villager in the doorway). The one with the higher id steps back along its own path and waits,
			// so the other one gets through; the one with the lower id just goes on. Moving back counts as moving for the stall rule.
			jamAnchor = null;
			Path cur = nav.getPath();
			if (cur != null && cur.getNextNodeIndex() > 0 && jamNeighbour(v)) {
				var back = cur.getNode(Math.max(0, cur.getNextNodeIndex() - 4)).asBlockPos();
				nav.moveTo(back.getX() + 0.5, back.getY(), back.getZ() + 0.5, SPEED);
				nextPathAt = now + 30 + v.getRandom().nextInt(30);
				if (TRACE) dev.villageheart.VillageHeart.LOG.info("[lumber] {} @{} jam at {}: steps back to {}", v.getId(), now, v.blockPosition(), back);
			} else {
				jamAt = now;
			}
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
			if (moving) v.getBrain().setMemory(MemoryModuleType.PATH, path);
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
