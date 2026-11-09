package dev.villageheart.worker.guard;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Balance;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.guard.GuardData;
import dev.villageheart.core.guard.GuardPerks;
import dev.villageheart.core.guard.GuardPhase;
import dev.villageheart.core.guard.GuardSchedule;
import dev.villageheart.core.guard.GuardShift;
import dev.villageheart.core.guard.Shift;
import dev.villageheart.core.job.XpChange;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.WorkGate;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.WorkerLevels;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * The guard facade (M18): shift and phase of a guard, the activity it should be in, level perks. Server thread only.
 *
 * <p>Registers at init: the {@code ATTACK_DAMAGE} attribute on every villager (so vanilla {@code doHurtTarget} works for a guard, M18 3.2 #1;
 * this replaces the villager's attribute supplier), the activities, the attachment and the level listener.
 */
public final class Guards {
	static final Identifier HEALTH_ID = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "guard_health");
	static final Identifier DAMAGE_ID = Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "guard_damage");

	private Guards() {}

	public static void register() {
		GuardActivities.register();
		GuardAttachments.register();
		GuardCombat.register();
		// the finished Lamp Posts and Watchtowers of M21 are patrol points (BACKLOG M21, blocking for M18)
		PatrolPoints.register(dev.villageheart.defence.Defences::patrolPoints);
		FabricDefaultAttributeRegistry.register(EntityType.VILLAGER,
				Villager.createAttributes().add(Attributes.ATTACK_DAMAGE, Balance.GUARD_BASE_DAMAGE));
		// Villager.die releases HOME, which would free the Barracks bed's ticket the mod holds: erase the memory first (it fires before die)
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
			if (entity instanceof Villager v && isGuard(v)) v.getBrain().eraseMemory(MemoryModuleType.HOME);
			return true;
		});
		// a guard that a zombie (or lightning) converts: the gear stays in the world, the result wears nothing and has no perks
		net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents.MOB_CONVERSION.register((previous, converted, context) -> {
			if (!(previous instanceof Villager v) || !isGuard(v) || !(converted.level() instanceof ServerLevel level)) return;
			for (var slot : GuardGear.SLOTS) {
				var stack = converted.getItemBySlot(slot).copy();
				if (stack.isEmpty()) continue;
				converted.setItemSlot(slot, net.minecraft.world.item.ItemStack.EMPTY);
				converted.spawnAtLocation(level, stack);
			}
			removePerks(converted);
		});
		WorkerLevels.register(new WorkerLevels.LevelListener() {
			@Override
			public void onLevelChanged(ServerLevel level, Villager villager, XpChange change) {
				if (isGuard(villager)) applyPerks(villager, true);
			}
		});
	}

	public static boolean isGuard(Villager v) {
		WorkerData d = v.getAttached(WorkerAttachments.WORKER);
		return d != null && d.jobId().equals(JobType.GUARD.id());
	}

	/** The guard's shift (empty for a villager that is no guard of the claimed village). */
	public static Optional<GuardShift> shiftOf(MinecraftServer server, UUID villager) {
		return VillageClaims.current(server).flatMap(v -> v.guardShift(villager));
	}

	/** What this guard does now: the schedule with the raid, wound and work overrides (M18 3.3). */
	public static GuardPhase phaseOf(Villager guard, ServerLevel level) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty()) return GuardPhase.OFF;
		Shift shift = village.get().guardShift(guard.getUUID()).map(GuardShift::shift).orElse(Shift.NIGHT);
		WorkerData data = guard.getAttached(WorkerAttachments.WORKER);
		boolean canWork = data == null || WorkGate.mayWork(data.needs());
		boolean raid = RaidCheck.active(level, village.get());
		return GuardSchedule.phase(shift, level.getDayTime(), raid, GuardAttachments.get(guard).wounded(), canWork);
	}

	/** The activity of this guard's brain right now. */
	public static Activity activityFor(Villager guard, ServerLevel level) {
		return switch (phaseOf(guard, level)) {
			case DUTY -> GuardActivities.DUTY;
			case OFF -> GuardActivities.OFF;
			// DEFEND beats wounded and sleep: a guard that has a target stays awake and out of bed until the fight is over
			case SLEEP -> GuardCombat.engaged(guard) ? GuardActivities.OFF : Activity.REST;
		};
	}

	/** A villager was just hired as a guard: its old bed is let go and its perks are applied. */
	public static void onHired(ServerLevel level, Villager guard) {
		guard.releasePoi(MemoryModuleType.HOME);
		guard.getBrain().eraseMemory(MemoryModuleType.HOME);
		GuardAttachments.set(guard, GuardData.FRESH);
		applyPerks(guard, false);
	}

	/**
	 * Max health and melee damage for the guard's level as permanent attribute modifiers (saved with the entity, so a reload does not clamp the
	 * health). {@code heal}: the guard also heals by the max-health gain (a level-up).
	 */
	public static void applyPerks(Villager guard, boolean heal) {
		WorkerData data = guard.getAttached(WorkerAttachments.WORKER);
		if (data == null) return;
		AttributeInstance hp = guard.getAttribute(Attributes.MAX_HEALTH), dmg = guard.getAttribute(Attributes.ATTACK_DAMAGE);
		if (hp == null || dmg == null) return;
		double before = guard.getMaxHealth();
		double wantHp = GuardPerks.maxHealth(data.level()) - hp.getBaseValue();
		double wantDmg = GuardPerks.bonusDamage(data.level());
		AttributeModifier curHp = hp.getModifier(HEALTH_ID), curDmg = dmg.getModifier(DAMAGE_ID);
		if (wantHp == 0) { if (curHp != null) hp.removeModifier(HEALTH_ID); }
		else if (curHp == null || curHp.amount() != wantHp) hp.addOrReplacePermanentModifier(new AttributeModifier(HEALTH_ID, wantHp, AttributeModifier.Operation.ADD_VALUE));
		if (wantDmg == 0) { if (curDmg != null) dmg.removeModifier(DAMAGE_ID); }
		else if (curDmg == null || curDmg.amount() != wantDmg) dmg.addOrReplacePermanentModifier(new AttributeModifier(DAMAGE_ID, wantDmg, AttributeModifier.Operation.ADD_VALUE));
		double gain = guard.getMaxHealth() - before;
		if (heal && gain > 0) guard.heal((float) gain);
	}

	/**
	 * Everything that makes a villager a guard is taken off (a fire, a conversion, the orphan sweep): the gear goes back (Barracks chest,
	 * else Storage, else the ground), the perks go, HOME is erased WITHOUT releasing it (the mod keeps the Barracks bed's ticket, 3.5).
	 */
	public static void strip(ServerLevel level, Villager guard) {
		GuardGear.returnAll(level, guard);
		removePerks(guard);
		guard.getBrain().eraseMemory(MemoryModuleType.HOME);
		GuardAttachments.set(guard, GuardData.FRESH);
	}

	/** Removes the two perk modifiers (a fired or converted guard is an ordinary villager again). */
	public static void removePerks(net.minecraft.world.entity.LivingEntity e) {
		AttributeInstance hp = e.getAttribute(Attributes.MAX_HEALTH), dmg = e.getAttribute(Attributes.ATTACK_DAMAGE);
		if (hp != null) hp.removeModifier(HEALTH_ID);
		if (dmg != null) dmg.removeModifier(DAMAGE_ID);
		if (e.getHealth() > e.getMaxHealth()) e.setHealth(e.getMaxHealth());
	}
}
