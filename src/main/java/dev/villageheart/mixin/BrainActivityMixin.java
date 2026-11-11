package dev.villageheart.mixin;

import dev.villageheart.worker.shelter.ShelterHold;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.schedule.Activity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps a sheltering unhired villager from being moved back outdoors by vanilla's raid status (see {@link ShelterHold}). */
@Mixin(Brain.class)
abstract class BrainActivityMixin {
	@Inject(method = "setActiveActivityIfPossible", at = @At("HEAD"), cancellable = true)
	private void villageheart$holdShelter(Activity activity, CallbackInfo ci) {
		if (ShelterHold.blocks((Brain<?>) (Object) this, activity)) ci.cancel();
	}
}
