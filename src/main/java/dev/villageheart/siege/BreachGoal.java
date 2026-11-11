package dev.villageheart.siege;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.defence.DefenceCell;
import dev.villageheart.core.defence.DefenceCells;
import dev.villageheart.core.defence.DefenceIndex;
import dev.villageheart.core.defence.DefenceSite;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.siege.BreachChoice;
import dev.villageheart.core.siege.SiegeHits;
import dev.villageheart.world.VillageClaims;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.Ravager;
import net.minecraft.world.entity.monster.illager.Vindicator;
import net.minecraft.world.entity.monster.zombie.Husk;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.entity.monster.zombie.ZombieVillager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Breaks the defence cells between this mob and where it is going (docs/plans/M25.md 3.11). Ravagers and vindicators run
 * it at priority 2, above the raid path. Zombies run it at priority 4 while they are lured, or while their target is in the claim.
 */
public final class BreachGoal extends Goal {
	private final Mob mob;
	private DefenceCell cell;
	private BlockPos anchor;
	private int cooldown;
	private int lastPathTick = -1000;
	private boolean pathBlocked;
	private boolean pathClose;
	private BlockPos goalPos;
	private int lastTargetTick = -1000;
	private boolean lastTargetReachable;

	public BreachGoal(Mob mob) {
		this.mob = mob;
		setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
	}

	@Override
	public boolean requiresUpdateEveryTick() { return true; }

	@Override
	public boolean canUse() {
		if (!allowed()) return false;
		if (!refreshBlocked()) return false;
		cell = pick();
		return cell != null;
	}

	@Override
	public boolean canContinueToUse() {
		if (!allowed() || cell == null) return false;
		if (pushed() || targetReachable()) return false;
		ServerLevel level = (ServerLevel) mob.level();
		if (!holding(level, cell) || SiegeDamage.isBreached(level.getServer(), cell.packed())) return false;
		return canTouch(level, cell);
	}

	@Override
	public void start() {
		anchor = mob.blockPosition();
		cooldown = 0;
	}

	@Override
	public void stop() { cell = null; }

	@Override
	public void tick() {
		if (!(mob.level() instanceof ServerLevel level) || cell == null) return;
		BlockPos pos = block(cell);
		mob.getLookControl().setLookAt(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
		mob.getNavigation().stop();
		if (cooldown > 0) {
			cooldown--;
			return;
		}
		SiegeHits.Attacker attacker = attacker();
		SiegeDamage.hit(level.getServer(), level, pos, attacker);
		mob.swing(InteractionHand.MAIN_HAND);
		cooldown = SiegeHits.interval(attacker);
	}

	/** Zombies only while lured, or while their vanilla target stands in the claim. Raiders only in the tracked raid. */
	private boolean allowed() {
		if (!(mob.level() instanceof ServerLevel level)) return false;
		if (!level.getGameRules().get(SiegeGamerules.SIEGE_DAMAGE)) return false;
		if (mob instanceof Zombie) return LureSlots.holds(level.getServer(), mob) || targetInClaim(level);
		if (mob instanceof Raider raider) return TrackedRaids.member(level, raider);
		return false;
	}

	private boolean targetInClaim(ServerLevel level) {
		LivingEntity target = mob.getTarget();
		if (target == null) return false;
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return false;
		return ClaimGeometry.inClaim(village.get().heart(), VillageClaims.locate(level, target.blockPosition()));
	}

	private boolean refreshBlocked() {
		BlockPos goal = goal();
		if (goal == null) return false;
		goalPos = goal;
		int now = mob.tickCount;
		if (now - lastPathTick < 40 && lastPathTick >= 0) return pathBlocked && pathClose;
		MinecraftServer server = ((ServerLevel) mob.level()).getServer();
		if (!PathBudget.take(server, mob.getUUID())) return false;
		lastPathTick = now;
		Path path = mob.getNavigation().createPath(goal, 1);
		BlockPos through = path == null ? null : defenceOn(path);
		// A path that only reaches by stepping on a wall or a gate is not a way through.
		pathBlocked = path == null || !path.canReach() || through != null;
		if (!pathBlocked) {
			pathClose = false;
			return false;
		}
		if (path == null) {
			pathClose = true;
			return true;
		}
		BlockPos end = through != null ? through : path.getNodePos(path.getNodeCount() - 1);
		double dx = mob.getX() - (end.getX() + 0.5);
		double dy = mob.getY() - end.getY();
		double dz = mob.getZ() - (end.getZ() + 0.5);
		pathClose = dx * dx + dy * dy + dz * dz <= 2.25;
		return pathClose;
	}

	/** The first path node that stands on a defence cell, or null. */
	private BlockPos defenceOn(Path path) {
		var village = VillageClaims.current(((ServerLevel) mob.level()).getServer());
		if (village.isEmpty()) return null;
		DefenceIndex index = DefenceIndex.of(village.get().defences());
		for (int i = 0; i < path.getNodeCount(); i++) {
			BlockPos pos = path.getNodePos(i);
			if (index.get(Pos.pack(pos.getX(), pos.getY(), pos.getZ())) != null) return pos;
			if (index.get(Pos.pack(pos.getX(), pos.getY() - 1, pos.getZ())) != null && i > 0) return pos.below();
		}
		return null;
	}

	private BlockPos goal() {
		LivingEntity target = mob.getTarget();
		if (target != null) return target.blockPosition();
		if (mob instanceof Zombie && mob.level() instanceof ServerLevel level) return LureSlots.point(level.getServer(), mob);
		if (mob instanceof Raider raider) {
			Raid raid = raider.getCurrentRaid();
			return raid == null ? null : raid.getCenter();
		}
		return null;
	}

	private DefenceCell pick() {
		if (!(mob.level() instanceof ServerLevel level) || goalPos == null) return null;
		var village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return null;
		VillageState state = village.get();
		DefenceIndex index = DefenceIndex.of(state.defences());
		List<DefenceCell> touchable = new ArrayList<>();
		for (DefenceCell candidate : index.near(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(), 3)) {
			if (!counts(level, state, candidate) || !canTouch(level, candidate)) continue;
			touchable.add(candidate);
		}
		boolean ravager = mob instanceof Ravager;
		List<DefenceCell> picked = BreachChoice.pick(mob.getBlockX(), mob.getBlockY(), mob.getBlockZ(), ravager ? 2 : 1, ravager ? 3 : 2,
				goalPos.getX(), goalPos.getY(), goalPos.getZ(), touchable);
		return picked.isEmpty() ? null : picked.get(0);
	}

	private boolean counts(ServerLevel level, VillageState village, DefenceCell candidate) {
		if (SiegeDamage.isBreached(level.getServer(), candidate.packed())) return false;
		DefenceSite site = village.defence(candidate.siteId()).orElse(null);
		if (site == null) return false;
		if (site.opened().contains(DefenceCells.relative(site.ground(), candidate.pos()))) return false;
		return holding(level, candidate);
	}

	private boolean holding(ServerLevel level, DefenceCell candidate) {
		String id = BuiltInRegistries.BLOCK.getKey(level.getBlockState(block(candidate)).getBlock()).toString();
		String expect = candidate.expectedState();
		int cut = expect.indexOf('[');
		if (cut >= 0) expect = expect.substring(0, cut);
		return id.equals(expect);
	}

	private boolean canTouch(ServerLevel level, DefenceCell candidate) {
		BlockPos pos = block(candidate);
		Vec3 eye = mob.getEyePosition();
		Vec3 center = Vec3.atCenterOf(pos);
		if (eye.distanceToSqr(center) > 16) return false;
		BlockHitResult hit = level.clip(new ClipContext(eye, center, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mob));
		return hit.getType() == HitResult.Type.MISS || hit.getBlockPos().equals(pos);
	}

	private boolean pushed() {
		if (anchor == null) return false;
		double dx = mob.getX() - (anchor.getX() + 0.5);
		double dz = mob.getZ() - (anchor.getZ() + 0.5);
		return dx * dx + dz * dz > 9;
	}

	private boolean targetReachable() {
		LivingEntity target = mob.getTarget();
		if (target == null) return false;
		int now = mob.tickCount;
		if (now - lastTargetTick < 40 && lastTargetTick >= 0) return lastTargetReachable;
		MinecraftServer server = ((ServerLevel) mob.level()).getServer();
		if (!PathBudget.take(server, mob.getUUID())) return false;
		lastTargetTick = now;
		Path path = mob.getNavigation().createPath(target, 1);
		lastTargetReachable = path != null && path.canReach() && defenceOn(path) == null;
		return lastTargetReachable;
	}

	private SiegeHits.Attacker attacker() {
		if (mob instanceof Ravager) return SiegeHits.Attacker.RAVAGER;
		if (mob instanceof Vindicator) return SiegeHits.Attacker.VINDICATOR;
		if (mob instanceof Husk) return SiegeHits.Attacker.HUSK;
		if (mob instanceof ZombieVillager) return SiegeHits.Attacker.ZOMBIE_VILLAGER;
		if (mob instanceof Zombie zombie && zombie.isBaby()) return SiegeHits.Attacker.BABY_ZOMBIE;
		return SiegeHits.Attacker.ZOMBIE;
	}

	private static BlockPos block(DefenceCell candidate) {
		Pos p = candidate.pos();
		return new BlockPos(p.x(), p.y(), p.z());
	}
}
