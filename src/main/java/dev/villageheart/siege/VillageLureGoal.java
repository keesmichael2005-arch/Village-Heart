package dev.villageheart.siege;

import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.world.VillageClaims;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;

/**
 * Walks a zombie toward the village with no line-of-sight test (docs/plans/M25.md 3.11). A blocked path is walked until
 * the zombie stands next to a defence cell, then this goal stops so {@link BreachGoal} can take the move flag. The last
 * node of a blocked path is the cell in front of the wall, not the wall itself.
 */
public final class VillageLureGoal extends Goal {
	private final Mob mob;
	private BlockPos goal;
	private int repathAt;
	private int pauseUntil;
	private boolean handoff;

	public VillageLureGoal(Mob mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE));
	}

	@Override
	public boolean requiresUpdateEveryTick() { return true; }

	@Override
	public boolean canUse() {
		if (mob.getTarget() != null) return false;
		if (Math.floorMod(mob.tickCount + mob.getId(), 20) != 0) return false;
		if (!(mob.level() instanceof ServerLevel level)) return false;
		MinecraftServer server = level.getServer();
		if (!LureSlots.on(server) || !LureSlots.holds(server, mob)) return false;
		if (away(server)) return false;
		goal = LureSlots.point(server, mob);
		return goal != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (handoff || mob.getTarget() != null || goal == null) return false;
		if (!(mob.level() instanceof ServerLevel level)) return false;
		MinecraftServer server = level.getServer();
		return LureSlots.on(server) && LureSlots.holds(server, mob) && !away(server);
	}

	@Override
	public void start() {
		handoff = false;
		repathAt = mob.tickCount;
		pauseUntil = 0;
	}

	@Override
	public void stop() {
		handoff = false;
		mob.getNavigation().stop();
	}

	@Override
	public void tick() {
		if (handoff || mob.tickCount < pauseUntil) return;
		if (mob.tickCount < repathAt && mob.getNavigation().isInProgress()) return;
		if (!repath()) handoff = true;
	}

	/** False when the way to the next waypoint is a defence cell. The breach goal takes over. */
	private boolean repath() {
		repathAt = mob.tickCount + Balance.LURE_REPATH_TICKS;
		if (!(mob.level() instanceof ServerLevel level) || goal == null) return false;
		Vec3 waypoint = waypoint();
		if (waypoint == null) {
			pauseUntil = mob.tickCount + 100;
			mob.getNavigation().stop();
			return true;
		}
		MinecraftServer server = level.getServer();
		if (!PathBudget.take(server)) {
			repathAt = mob.tickCount + 1;
			return true;
		}
		Path path = mob.getNavigation().createPath(waypoint.x, waypoint.y, waypoint.z, 1);
		if (path == null || path.getNodeCount() == 0) {
			// A missing sample is not a house in the way. Try another waypoint shortly. The 100 tick wait is for a
			// partial path that ends off the walls.
			pauseUntil = mob.tickCount + 20;
			mob.getNavigation().stop();
			return true;
		}
		if (!path.canReach() && besideDefence(level)) {
			mob.getNavigation().stop();
			return false;
		}
		mob.getNavigation().moveTo(path, 1.0);
		return true;
	}

	private Vec3 waypoint() {
		Vec3 at = Vec3.atCenterOf(goal);
		if (mob.position().distanceTo(at) <= 24) return at;
		if (mob instanceof PathfinderMob pathfinder) {
			Vec3 sampled = DefaultRandomPos.getPosTowards(pathfinder, 16, 7, at, Math.PI / 2);
			if (sampled != null) return sampled;
		}
		Vec3 from = mob.position();
		Vec3 delta = at.subtract(from);
		double len = delta.length();
		if (len < 1.0E-3) return at;
		double step = Math.min(10.0, len);
		return from.add(delta.scale(step / len));
	}

	private boolean away(MinecraftServer server) {
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return true;
		var heart = village.heart();
		double dx = mob.getX() - (heart.x() + 0.5);
		double dy = mob.getY() - heart.y();
		double dz = mob.getZ() - (heart.z() + 0.5);
		return dx * dx + dy * dy + dz * dz > (double) Balance.LURE_RADIUS * Balance.LURE_RADIUS;
	}

	private boolean besideDefence(ServerLevel level) {
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return false;
		DefenceIndex index = DefenceIndex.of(village.get().defences());
		for (var cell : index.near(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(), 2)) {
			var p = cell.pos();
			int dx = Math.abs(p.x() - mob.getBlockX());
			int dy = Math.abs(p.y() - mob.getBlockY());
			int dz = Math.abs(p.z() - mob.getBlockZ());
			if (Math.max(dx, Math.max(dy, dz)) <= 1) return true;
		}
		return false;
	}
}
