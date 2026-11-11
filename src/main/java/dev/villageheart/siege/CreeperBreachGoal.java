package dev.villageheart.siege;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceCells;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceKind;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.room.Pos;
import dev.villageheart.world.VillageClaims;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.pathfinder.Path;

/**
 * Walks a creeper to the nearest wall or gate and lights it (docs/plans/M25.md 3.11). Only while the lure is on, the
 * creeper has no target, and it holds a lure slot. A cell closer to the Heart than the creeper is left alone, so a
 * creeper already inside the ring does not blow the wall outward.
 */
public final class CreeperBreachGoal extends Goal {
	/** Set the tick the creeper lights, so the explosion mixin spends the blast on defence cells. */
	public static final String BREACHER = "villageheart.breacher";

	/** The cell the goal chose, and whether the second core cell is the one above it. */
	public record Aim(BlockPos pos, boolean above) {}

	private static final Map<UUID, Aim> AIMS = new HashMap<>();

	private final Creeper creeper;
	private BlockPos cell;
	private BlockPos stand;
	private boolean above;
	private int repathAt;

	public CreeperBreachGoal(Creeper creeper) {
		this.creeper = creeper;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	public static Aim takeAim(UUID id) { return AIMS.remove(id); }

	@Override
	public boolean requiresUpdateEveryTick() { return true; }

	@Override
	public boolean canUse() {
		if (creeper.getTarget() != null || creeper.getSwellDir() > 0) return false;
		if (Math.floorMod(creeper.tickCount + creeper.getId(), 20) != 0) return false;
		return aim();
	}

	@Override
	public boolean canContinueToUse() {
		if (creeper.getTarget() != null || creeper.getSwellDir() > 0 || cell == null) return false;
		if (!(creeper.level() instanceof ServerLevel level)) return false;
		MinecraftServer server = level.getServer();
		return level.getGameRules().get(SiegeGamerules.SIEGE_DAMAGE) && LureSlots.on(server) && LureSlots.holds(server, creeper);
	}

	@Override
	public void start() { repathAt = creeper.tickCount; }

	@Override
	public void stop() { creeper.getNavigation().stop(); }

	@Override
	public void tick() {
		if (cell == null) return;
		int dx = Math.abs(creeper.getBlockX() - cell.getX());
		int dy = Math.abs(creeper.getBlockY() - cell.getY());
		int dz = Math.abs(creeper.getBlockZ() - cell.getZ());
		if (Math.max(dx, Math.max(dy, dz)) <= 1) {
			creeper.addTag(BREACHER);
			AIMS.put(creeper.getUUID(), new Aim(cell, above));
			creeper.getNavigation().stop();
			creeper.ignite();
			return;
		}
		if (creeper.tickCount < repathAt && creeper.getNavigation().isInProgress()) return;
		repath();
	}

	private boolean aim() {
		if (!(creeper.level() instanceof ServerLevel level)) return false;
		MinecraftServer server = level.getServer();
		if (!level.getGameRules().get(SiegeGamerules.SIEGE_DAMAGE)) return false;
		if (!LureSlots.on(server) || !LureSlots.holds(server, creeper)) return false;
		VillageState village = VillageClaims.current(server).orElse(null);
		if (village == null) return false;
		DefenceIndex index = DefenceIndex.of(village.defences());
		HeartLocation heart = village.heart();
		double fromHeart = distance(creeper.getX(), creeper.getY(), creeper.getZ(), heart);
		DefenceCell best = null;
		double bestD = Double.MAX_VALUE;
		for (DefenceCell candidate : index.near(creeper.getBlockX(), creeper.getBlockY(), creeper.getBlockZ(), Balance.CREEPER_SEEK_RADIUS)) {
			if (candidate.kind() != DefenceKind.STONE && candidate.kind() != DefenceKind.GATE) continue;
			DefenceSite site = village.defence(candidate.siteId()).orElse(null);
			if (site == null) continue;
			String id = site.blueprintId();
			if (!id.equals("wall_segment") && !id.equals("gate")) continue;
			if (SiegeDamage.isBreached(server, candidate.packed())) continue;
			if (site.opened().contains(DefenceCells.relative(site.ground(), candidate.pos()))) continue;
			Pos p = candidate.pos();
			if (fromHeart <= distance(p.x() + 0.5, p.y(), p.z() + 0.5, heart)) continue;
			double d = creeper.distanceToSqr(p.x() + 0.5, p.y(), p.z() + 0.5);
			if (d < bestD) {
				bestD = d;
				best = candidate;
			}
		}
		if (best == null) return false;
		BlockPos pos = new BlockPos(best.pos().x(), best.pos().y(), best.pos().z());
		BlockPos beside = standBeside(level, index, pos);
		if (beside == null) return false;
		cell = pos;
		stand = beside;
		above = index.get(Pos.pack(pos.getX(), pos.getY() + 1, pos.getZ())) != null;
		return true;
	}

	/** A floor cell next to the wall. A trench has no floor, so the creeper has nowhere to stand and does not light. */
	private BlockPos standBeside(ServerLevel level, DefenceIndex index, BlockPos wall) {
		BlockPos best = null;
		double bestD = Double.MAX_VALUE;
		for (BlockPos n : new BlockPos[] {wall.north(), wall.south(), wall.east(), wall.west()}) {
			if (index.get(Pos.pack(n.getX(), n.getY(), n.getZ())) != null) continue;
			if (!level.getBlockState(n).isAir() || !level.getBlockState(n.above()).isAir()) continue;
			if (!level.getBlockState(n.below()).isSolid()) continue;
			double d = creeper.distanceToSqr(n.getX() + 0.5, n.getY(), n.getZ() + 0.5);
			if (d < bestD) {
				bestD = d;
				best = n;
			}
		}
		return best;
	}

	private void repath() {
		repathAt = creeper.tickCount + Balance.LURE_REPATH_TICKS;
		if (!(creeper.level() instanceof ServerLevel level) || stand == null) return;
		if (!PathBudget.take(level.getServer())) {
			repathAt = creeper.tickCount + 1;
			return;
		}
		Path path = creeper.getNavigation().createPath(stand, 0);
		if (path == null || path.getNodeCount() == 0) {
			creeper.getNavigation().stop();
			return;
		}
		creeper.getNavigation().moveTo(path, 1.0);
	}

	private static double distance(double x, double y, double z, HeartLocation heart) {
		double dx = x - (heart.x() + 0.5);
		double dy = y - heart.y();
		double dz = z - (heart.z() + 0.5);
		return Math.sqrt(dx * dx + dy * dy + dz * dz);
	}
}
