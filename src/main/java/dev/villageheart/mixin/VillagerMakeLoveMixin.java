package dev.villageheart.mixin;

import dev.villageheart.growth.BreedTrace;
import dev.villageheart.growth.Growth;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
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

	@Inject(method = TAKE_BED, at = @At("RETURN"))
	private void villageheart$noBed(ServerLevel level, Villager villager, CallbackInfoReturnable<Optional<BlockPos>> cir) {
		if (cir.getReturnValue().isEmpty()) villageheart$noBed = true;
	}

	@Inject(method = GIVE_BED, at = @At("HEAD"))
	private void villageheart$born(ServerLevel level, Villager child, BlockPos bed, CallbackInfo ci) {
		villageheart$born = true;
		BreedTrace.birth();
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
