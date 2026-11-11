package dev.villageheart.worker.guard;

import dev.villageheart.core.Balance;
import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.guard.GuardChase;
import dev.villageheart.core.guard.GuardTargets;
import dev.villageheart.core.job.WorkUnit;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkXp;
import dev.villageheart.worker.job.WorkStats;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Weapon;
import net.minecraft.world.level.pathfinder.Path;

/**
 * A guard's fighting (M18 3.6). A villager's brain cannot run vanilla's {@code MeleeAttack} (it needs memories villagers do not register), so
 * this keeps the target in a field and does what {@code Mob.doHurtTarget} needs: the guard has a real {@code ATTACK_DAMAGE} attribute (a
 * mod registration), so the hit gives the sword's own damage, enchantments, knockback and kill credit for free. It runs in the CORE of the
 * guard brain (every activity, rest included) and owns the navigation while it has a target. A DEFEND override beats wounded and sleep.
 */
public final class GuardCombat {
	private static final boolean TRACE = Boolean.getBoolean("villageheart.guard.trace");
	private static final int REPATH_TICKS = 10;
	private static final int IGNORE_TICKS = 200;

	private static final class Fight {
		UUID target;
		long lastPathAt, bestAt, lastHitAt = Long.MIN_VALUE / 2;
		double bestDist = Double.MAX_VALUE;
		int shortPaths;
		final Map<UUID, Long> ignoreUntil = new HashMap<>();
		boolean navigating, retreating, chase;
		/** Last block of a raid chase. Changing it while the path runs is progress, even if the straight line got longer. */
		BlockPos progressCell;
		int switchedAt = -1;
	}

	private static final Map<UUID, Fight> FIGHTS = new HashMap<>();

	private GuardCombat() {}

	public static void register() {
		// kill credit by the guard's UUID: the source's entity is the guard itself
		ServerLivingEntityEvents.AFTER_DEATH.register((entity, source) -> {
			if (!(source.getEntity() instanceof Villager v) || !Guards.isGuard(v) || !(entity instanceof Enemy)) return;
			if (!(entity.level() instanceof ServerLevel level)) return;
			boolean raiderWithinChase = entity instanceof Raider && inClaimPlus(level, entity, Balance.GUARD_RAID_CHASE);
			if (!GuardChase.killCounts(inClaim(level, entity), raiderWithinChase)) return;
			WorkXp.award(v, WorkUnit.HOSTILE_KILLED, 1);
			WorkStats.add("guard", "kills", 1);
		});
		// armour wear: villagers do not damage their own armour (hurtArmor is empty); the mod wears it like a player's, the rule of doHurtEquipment
		ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamageTaken, damageTaken, blocked) -> {
			if (!(entity instanceof Villager v) || !Guards.isGuard(v) || blocked || source.is(DamageTypeTags.BYPASSES_ARMOR)) return;
			int amount = Math.max(1, (int) (baseDamageTaken / 4.0F));
			for (EquipmentSlot slot : new EquipmentSlot[] {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
				ItemStack stack = v.getItemBySlot(slot);
				if (stack.isEmpty() || !stack.isDamageableItem() || !stack.canBeHurtBy(source)) continue;
				var eq = stack.get(DataComponents.EQUIPPABLE);
				if (eq == null || !eq.damageOnHurt()) continue;
				stack.hurtAndBreak(amount, v, slot);
			}
		});
	}

	public static void resetForTests() { FIGHTS.clear(); }

	/** True while this guard has a fight target. */
	public static boolean engaged(Villager guard) {
		Fight f = FIGHTS.get(guard.getUUID());
		return f != null && f.target != null;
	}

	/** True while this guard is falling back behind a healthier one (tests, status). */
	public static boolean retreating(UUID guard) {
		Fight f = FIGHTS.get(guard);
		return f != null && f.retreating;
	}

	/** The target's UUID, if any (tests). */
	public static Optional<UUID> targetOf(UUID guard) {
		Fight f = FIGHTS.get(guard);
		return f == null ? Optional.empty() : Optional.ofNullable(f.target);
	}

	static boolean inClaim(ServerLevel level, Entity e) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		return village.isPresent() && ClaimGeometry.inClaim(village.get().heart(), VillageClaims.locate(level, e.blockPosition()));
	}

	private static boolean flier(Mob m) {
		EntityType<?> t = m.getType();
		return t == EntityType.PHANTOM || t == EntityType.VEX || t == EntityType.BLAZE || t == EntityType.GHAST || t == EntityType.BREEZE
				|| m.getNavigation() instanceof net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
	}

	private static GuardTargets.Kind kind(Mob m) {
		if (flier(m)) return GuardTargets.Kind.UNREACHABLE;
		if (m instanceof Creeper) return GuardTargets.Kind.CREEPER;
		if (m instanceof Raider) return GuardTargets.Kind.RAIDER;
		if (m instanceof NeutralMob) return GuardTargets.Kind.NEUTRAL;
		if (m instanceof Enemy) return GuardTargets.Kind.HOSTILE;
		return null; // not a threat
	}

	/** Called every tick by the CORE behaviour. */
	static void tick(ServerLevel level, Villager guard) {
		long now = level.getGameTime();
		Fight f = FIGHTS.computeIfAbsent(guard.getUUID(), k -> new Fight());
		Mob target = f.target == null ? null : level.getEntity(f.target) instanceof Mob m && m.isAlive() && !m.isRemoved() ? m : null;
		if (f.target != null && target == null) drop(guard, f, null, now, false);
		if (target != null && shouldGiveUp(level, guard, f, target, now)) {
			drop(guard, f, target, now, true);
			target = null;
		}
		// a different hostile that hurts the guard while it chases another one takes over (vanilla HurtByTargetGoal): the attacker comes first
		if (target != null && guard.getLastHurtByMob() instanceof Mob hb && hb != target && hb.isAlive()
				&& guard.getLastHurtByMobTimestamp() != f.switchedAt && guard.tickCount - guard.getLastHurtByMobTimestamp() < 100) {
			GuardTargets.Kind k = kind(hb);
			f.switchedAt = guard.getLastHurtByMobTimestamp();
			if (k != null && k != GuardTargets.Kind.CREEPER && k != GuardTargets.Kind.UNREACHABLE) {
				f.ignoreUntil.remove(hb.getUUID());
				target = hb;
				acquire(level, guard, f, target, now, chasing(level, hb));
			}
		}
		if (target == null && (now + guard.getId()) % Balance.GUARD_SCAN_TICKS == 0) {
			target = scan(level, guard, f, now);
			if (target != null) acquire(level, guard, f, target, now, chasing(level, target));
		}
		if (target == null) return;
		fight(level, guard, f, target, now);
	}

	private static boolean shouldGiveUp(ServerLevel level, Villager guard, Fight f, Mob target, long now) {
		boolean duty = guard.getBrain().isActive(GuardActivities.DUTY);
		int margin = f.chase ? Balance.GUARD_RAID_CHASE : 8;
		if (duty && !inClaimPlus(level, target, margin) && !(guard.getLastHurtByMob() == target)) return true;
		double d = guard.distanceTo(target);
		if (d + 1.0 <= f.bestDist) {
			f.bestDist = d;
			f.bestAt = now;
		}
		// the east gates are the way out of the ring; that walk increases the straight-line distance, and it is still a chase
		BlockPos cell = guard.blockPosition();
		boolean cellChanged = f.progressCell == null || !f.progressCell.equals(cell);
		if (GuardChase.detourCounts(f.chase, f.navigating, guard.getNavigation().isInProgress(), cellChanged)) {
			f.progressCell = cell;
			f.bestAt = now;
		}
		return now - f.bestAt > Balance.GUARD_GIVE_UP_TICKS;
	}

	/** Within the claim sphere plus {@code margin} blocks. */
	private static boolean inClaimPlus(ServerLevel level, Entity e, int margin) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return false;
		var h = village.get().heart();
		if (!h.dimension().equals(level.dimension().identifier().toString())) return false;
		double dx = e.getX() - h.x(), dy = e.getY() - h.y(), dz = e.getZ() - h.z(), r = Balance.CLAIM_RADIUS + margin;
		return dx * dx + dy * dy + dz * dz <= r * r;
	}

	private static void drop(Villager guard, Fight f, Mob target, long now, boolean ignore) {
		if (TRACE) dev.villageheart.VillageHeart.LOG.info("[guard] {} @{} drops its target (ignore {})", guard.getId(), now, ignore);
		if (ignore && target != null) f.ignoreUntil.put(target.getUUID(), now + IGNORE_TICKS);
		f.target = null;
		f.retreating = false;
		f.chase = false;
		f.bestDist = Double.MAX_VALUE;
		f.shortPaths = 0;
		f.progressCell = null;
		if (f.navigating) {
			guard.getNavigation().stop();
			guard.getBrain().eraseMemory(MemoryModuleType.PATH);
			f.navigating = false;
		}
	}

	private static Mob scan(ServerLevel level, Villager guard, Fight f, long now) {
		f.ignoreUntil.values().removeIf(t -> t <= now);
		List<Mob> mobs = level.getEntitiesOfClass(Mob.class, guard.getBoundingBox().inflate(Balance.GUARD_SIGHT),
				m -> m.isAlive() && m != guard && !(m instanceof Villager) && !f.ignoreUntil.containsKey(m.getUUID()));
		List<GuardTargets.Candidate> candidates = new ArrayList<>();
		Map<Integer, Mob> byId = new HashMap<>();
		for (Mob m : mobs) addCandidate(level, guard, m, candidates, byId);
		for (Raider raider : dev.villageheart.raid.VillageRaids.chaseTargets(level)) {
			if (byId.containsKey(raider.getId()) || f.ignoreUntil.containsKey(raider.getUUID())) continue;
			addCandidate(level, guard, raider, candidates, byId);
		}
		GuardTargets.Mode mode = guard.getBrain().isActive(GuardActivities.DUTY) ? GuardTargets.Mode.DUTY : GuardTargets.Mode.OFF;
		return GuardTargets.choose(candidates, mode).map(c -> byId.get(c.id())).orElse(null);
	}

	private static void addCandidate(ServerLevel level, Villager guard, Mob m, List<GuardTargets.Candidate> candidates, Map<Integer, Mob> byId) {
		GuardTargets.Kind k = kind(m);
		if (k == null) return;
		boolean hurtMe = guard.getLastHurtByMob() == m && guard.tickCount - guard.getLastHurtByMobTimestamp() < 100;
		boolean targetsVillager = m.getTarget() instanceof Villager;
		candidates.add(new GuardTargets.Candidate(m.getId(), k, guard.distanceToSqr(m), inClaim(level, m), guard.getSensing().hasLineOfSight(m),
				targetsVillager, hurtMe, chasing(level, m)));
		byId.put(m.getId(), m);
	}

	/** A living raider of the tracked raid, still within the claim plus {@link Balance#GUARD_RAID_CHASE}. */
	private static boolean chasing(ServerLevel level, Mob target) {
		if (!(target instanceof Raider)) return false;
		for (Raider raider : dev.villageheart.raid.VillageRaids.chaseTargets(level)) {
			if (raider.getId() == target.getId()) return true;
		}
		return false;
	}

	private static void acquire(ServerLevel level, Villager guard, Fight f, Mob target, long now, boolean chase) {
		if (TRACE) dev.villageheart.VillageHeart.LOG.info("[guard] {} @{} acquires {} at {} (guard hp {}, activities {})", guard.getId(), now, target.getType(), target.blockPosition(), guard.getHealth(), guard.getBrain().getActiveActivities());
		f.target = target.getUUID();
		f.chase = chase;
		f.bestDist = guard.distanceTo(target);
		f.bestAt = now;
		f.lastPathAt = Long.MIN_VALUE / 2;
		f.shortPaths = 0;
		f.progressCell = null;
		if (guard.isSleeping()) guard.stopSleeping();
		// the defend override: a guard that would go to bed (wounded) fights first; it is back in bed when the fight is over
		if (guard.getBrain().isActive(Activity.REST)) guard.getBrain().setActiveActivityIfPossible(GuardActivities.OFF);
	}

	private static void fight(ServerLevel level, Villager guard, Fight f, Mob target, long now) {
		if (guard.isSleeping()) guard.stopSleeping();
		guard.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new EntityTracker(target, true));
		PathNavigation nav = guard.getNavigation();
		// below half health a guard falls back behind another healthy guard (the zombie follows into two swords), eats there and heals, and
		// fights again once it is back to GUARD_HEALED_AT (M18 r3). With nobody to fall back to it fights on.
		double frac = guard.getHealth() / guard.getMaxHealth();
		if (f.retreating && frac >= Balance.GUARD_HEALED_AT) f.retreating = false;
		if (!f.retreating && frac < Balance.GUARD_RETREAT_BELOW) {
			f.retreating = true;
			if (healthyAlly(level, guard) != null) WorkStats.add("guard", "retreats", 1);
		}
		boolean inReach = guard.isWithinMeleeAttackRange(target);
		if (f.retreating) {
			Villager ally = healthyAlly(level, guard);
			if (ally == null) {
				f.retreating = false;
			} else if (guard.distanceToSqr(ally) > 25.0) {
				if (now - f.lastPathAt >= REPATH_TICKS) {
					f.lastPathAt = now;
					nav.moveTo(ally, Balance.GUARD_CHASE_SPEED);
					f.navigating = true;
				}
				return;
			} else {
				GuardRations.bite(level, guard);
				if (!(inReach && guard.getSensing().hasLineOfSight(target))) {
					nav.stop();
					f.navigating = false;
					f.bestAt = now; // holding the line behind the ally is not a stalled chase
					return;
				}
			}
		}
		if (inReach && guard.getSensing().hasLineOfSight(target)) {
			nav.stop();
			f.navigating = false;
			f.bestAt = now; // fighting in reach is progress
			if (now - f.lastHitAt >= Balance.GUARD_ATTACK_COOLDOWN) hit(level, guard, f, target, now);
			return;
		}
		if (now - f.lastPathAt >= REPATH_TICKS) {
			f.lastPathAt = now;
			Path path = nav.createPath(target.blockPosition(), 1, 32);
			boolean near = guard.distanceToSqr(target) <= 9.0; // next to the target a path to its own cell may be empty: not a failure
			if (!f.chase && !near && target.onGround() && (path == null || !path.canReach() && path.getNodeCount() > 0 && path.getEndNode().asBlockPos().distSqr(target.blockPosition()) > 4)) {
				if (++f.shortPaths >= 8) {
					drop(guard, f, target, now, true);
					return;
				}
			} else {
				f.shortPaths = 0;
			}
			if (path != null && nav.moveTo(path, Balance.GUARD_CHASE_SPEED)) {
				guard.getBrain().setMemory(MemoryModuleType.PATH, path);
				f.navigating = true;
			}
			else if (path == null && nav.moveTo(target, Balance.GUARD_CHASE_SPEED)) f.navigating = true; // no planned path: close in directly (the give-up rule still ends a hopeless chase)
		}
	}

	/** The nearest other guard within 32 blocks with at least 70 % health, awake. */
	private static Villager healthyAlly(ServerLevel level, Villager guard) {
		Villager best = null;
		for (Villager v : level.getEntitiesOfClass(Villager.class, guard.getBoundingBox().inflate(32.0), o -> o != guard && o.isAlive() && Guards.isGuard(o)
				&& !o.isSleeping() && o.getHealth() >= o.getMaxHealth() * Balance.GUARD_ALLY_HEALTHY_AT)) {
			if (best == null || v.distanceToSqr(guard) < best.distanceToSqr(guard)) best = v;
		}
		return best;
	}

	private static void hit(ServerLevel level, Villager guard, Fight f, Mob target, long now) {
		f.lastHitAt = now;
		guard.swing(InteractionHand.MAIN_HAND);
		ItemStack weapon = guard.getMainHandItem();
		boolean landed = guard.doHurtTarget(level, target);
		if (TRACE) dev.villageheart.VillageHeart.LOG.info("[guard] {} @{} hits {}: landed {} (target hp {})", guard.getId(), now, target.getType(), landed, target.getHealth());
		level.sendParticles(ParticleTypes.SWEEP_ATTACK, target.getX(), target.getY(0.5), target.getZ(), 1, 0, 0, 0, 0);
		level.playSound(null, target.blockPosition(), SoundEvents.PLAYER_ATTACK_SWEEP, SoundSource.NEUTRAL, 1.0F, 1.0F);
		if (landed && !weapon.isEmpty() && weapon.isDamageableItem()) {
			Weapon w = weapon.get(DataComponents.WEAPON);
			weapon.hurtAndBreak(w == null ? 1 : w.itemDamagePerAttack(), guard, EquipmentSlot.MAINHAND);
		}
	}

	/** Forget everything about a guard that left (fired, unloaded, died). */
	public static void forget(UUID guard) { FIGHTS.remove(guard); }
}
