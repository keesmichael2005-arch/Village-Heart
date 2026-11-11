package dev.villageheart.mixin;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.monster.Creeper;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Owner decision 2026-11-11: nobody attacks creepers, iron golems included. Vanilla's nearest-attackable goal already leaves creepers
 * alone, but the hurt-by goal (a creeper that explodes next to the golem) and any other code can still hand a golem a creeper as its
 * target. Refusing it where the target is set covers every route, which a goal of lower priority could not.
 */
@Mixin(Mob.class)
abstract class MobCreeperTargetMixin {
	@Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
	private void villageheart$noCreeperTarget(@Nullable LivingEntity target, CallbackInfo ci) {
		if ((Object) this instanceof IronGolem && target instanceof Creeper) ci.cancel();
	}
}
