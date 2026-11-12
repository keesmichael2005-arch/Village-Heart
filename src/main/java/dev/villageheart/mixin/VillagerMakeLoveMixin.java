package dev.villageheart.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import dev.villageheart.growth.BreedTrace;
import dev.villageheart.growth.BreedingGate;
import dev.villageheart.growth.Growth;
import dev.villageheart.growth.GrowthStats;
import dev.villageheart.needs.BedPoi;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.VillagerMakeLove;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Hooks into vanilla's breeding behaviour (M23 plan 3.3 and 3.5): the reason a run ended without a baby, the reserved House bed for a
 * licensed pair, the birth, and the walk fix that keeps a licensed pair close enough for the birth tick.
 */
@Mixin(VillagerMakeLove.class)
abstract class VillagerMakeLoveMixin {
	private static final String START = "start(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V";
	private static final String TICK = "tick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V";
	private static final String STOP = "stop(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;J)V";
	private static final String TAKE_BED = "takeVacantBed(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;)Ljava/util/Optional;";
	private static final String GIVE_BED = "giveBedToChild(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/npc/villager/Villager;Lnet/minecraft/core/BlockPos;)V";

	/** A baby was born in this run (set by the bed hand-over, which only follows a successful breed). */
	@Unique private boolean villageheart$born;
	/** The birth tick found no bed in this run. */
	@Unique private boolean villageheart$noBed;

	private static final String LOCK_GAZE = "Lnet/minecraft/world/entity/ai/behavior/BehaviorUtils;lockGazeAndWalkToEachOther"
			+ "(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/LivingEntity;FI)V";

	/** Fix (a), plan 3.5: a licensed pair walks to within 1 (Manhattan) of each other, which on flat ground is always inside the birth tick's sqrt(5). */
	@WrapOperation(method = {START, TICK}, at = @At(value = "INVOKE", target = LOCK_GAZE))
	private void villageheart$closeEnough(LivingEntity self, LivingEntity partner, float speed, int closeEnough, Operation<Void> original) {
		boolean licensed = self instanceof Villager v && BreedingGate.licenceOf(v).isPresent();
		original.call(self, partner, speed, licensed ? 1 : closeEnough);
	}

	/** Fix (b), plan 3.5: vanilla does nothing when the pair stands farther apart than sqrt(5); a licensed pair walks together again. */
	@Inject(method = TICK, at = @At("HEAD"))
	private void villageheart$closeTheGap(ServerLevel level, Villager self, long time, CallbackInfo ci) {
		if (BreedingGate.licenceOf(self).isEmpty()) return;
		self.getBrain().getMemory(MemoryModuleType.BREED_TARGET).ifPresent(p -> {
			if (self.distanceToSqr(p) > 5.0) BehaviorUtils.lockGazeAndWalkToEachOther(self, p, 0.5F, 1);
		});
	}

	/**
	 * Review M23-r2 item 2. Vanilla's "is the partner visible" is {@code NEAREST_VISIBLE_LIVING_ENTITIES.contains(partner)}, and that snapshot
	 * memoises the line-of-sight test per entity until the next sensor scan (up to 20 ticks): one test made while the pair was still walking, or
	 * in the tick the snapshot was taken, keeps a partner who now stands under 2 blocks away "unseen", the make-love run stops with NOT_VISIBLE and
	 * the licence loses a seed (three of them lapse it, MAX_SEEDS). For a licensed pair within the birth distance the test is made again, fresh.
	 */
	@WrapOperation(method = "isBreedingPossible(Lnet/minecraft/world/entity/npc/villager/Villager;)Z", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/ai/behavior/BehaviorUtils;targetIsValid(Lnet/minecraft/world/entity/ai/Brain;"
					+ "Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;Lnet/minecraft/world/entity/EntityType;)Z"))
	private boolean villageheart$freshSight(Brain<?> brain, MemoryModuleType<? extends LivingEntity> memory, EntityType<?> type,
			Operation<Boolean> original, @Local(argsOnly = true) Villager self) {
		boolean valid = original.call(brain, memory, type);
		if (valid || !BreedingGate.holdsLicence(self)) return valid;
		AgeableMob partner = brain.getMemory(MemoryModuleType.BREED_TARGET).orElse(null);
		if (partner instanceof Villager p && p.isAlive() && self.distanceToSqr(p) <= 5.0 && self.hasLineOfSight(p)) {
			GrowthStats.sightRescue();
			return true;
		}
		return false;
	}

	/** A licensed initiator takes the House bed reserved at dawn instead of the first free HOME POI in range (plan 3.2 #6). */
	@Inject(method = TAKE_BED, at = @At("HEAD"), cancellable = true)
	private void villageheart$reservedBed(ServerLevel level, Villager villager, CallbackInfoReturnable<Optional<BlockPos>> cir) {
		Optional<Optional<BlockPos>> bed = Growth.bedForBirth(level, villager);
		if (bed.isPresent()) {
			if (bed.get().isEmpty()) villageheart$noBed = true;
			cir.setReturnValue(bed.get());
		}
	}

	@Inject(method = TAKE_BED, at = @At("RETURN"), cancellable = true)
	private void villageheart$noBed(ServerLevel level, Villager villager, CallbackInfoReturnable<Optional<BlockPos>> cir) {
		Optional<BlockPos> bed = cir.getReturnValue();
		if (bed.isPresent() && Growth.mustDropBed(level, villager, bed.get())) {
			BedPoi.release(level, bed.get());
			cir.setReturnValue(Optional.empty());
			bed = Optional.empty();
		}
		if (bed.isEmpty()) villageheart$noBed = true;
	}

	@Inject(method = GIVE_BED, at = @At("HEAD"))
	private void villageheart$born(ServerLevel level, Villager child, BlockPos bed, CallbackInfo ci) {
		villageheart$born = true;
		BreedTrace.birth();
		Growth.onBirth(level, child, bed);
	}

	@Inject(method = STOP, at = @At("HEAD"))
	private void villageheart$stop(ServerLevel level, Villager self, long time, CallbackInfo ci) {
		boolean born = villageheart$born, noBed = villageheart$noBed;
		villageheart$born = false;
		villageheart$noBed = false;
		BreedTrace.finish(self.getUUID());
		if (born) return;
		AgeableMob partner = self.getBrain().getMemory(MemoryModuleType.BREED_TARGET).orElse(null);
		BreedTrace.Abort reason;
		double d = partner == null ? -1 : self.distanceTo(partner);
		if (noBed) reason = BreedTrace.Abort.NO_BED;
		else if (partner == null || !partner.isAlive()) reason = BreedTrace.Abort.PARTNER_GONE;
		else if (self.distanceToSqr(partner) > 5.0) reason = BreedTrace.Abort.TOO_FAR;
		else if (!BehaviorUtils.entityIsVisible(self.getBrain(), partner)) reason = BreedTrace.Abort.NOT_VISIBLE;
		else if (!self.canBreed() || !partner.canBreed()) reason = BreedTrace.Abort.CANNOT_BREED;
		else reason = BreedTrace.Abort.TIMED_OUT;
		Growth.onRunStopped(level, self, partner, reason, d);
	}
}
