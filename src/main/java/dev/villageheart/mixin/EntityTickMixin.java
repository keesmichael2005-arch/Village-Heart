package dev.villageheart.mixin;

import dev.villageheart.world.VillageTicker;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Times the overworld's entity tick (docs/plans/M25.md 3.17, {@code entity_ms}). One sample per server tick is the
 * time spent inside {@code tickNonPassenger}, committed with the village timer.
 */
@Mixin(ServerLevel.class)
abstract class EntityTickMixin {
	private static int depth;
	private static long started;

	@Inject(method = "tickNonPassenger", at = @At("HEAD"))
	private void villageheart$entityStart(Entity entity, CallbackInfo ci) {
		if (((ServerLevel) (Object) this).dimension() != Level.OVERWORLD) return;
		if (depth++ == 0) started = System.nanoTime();
	}

	@Inject(method = "tickNonPassenger", at = @At("RETURN"))
	private void villageheart$entityEnd(Entity entity, CallbackInfo ci) {
		if (((ServerLevel) (Object) this).dimension() != Level.OVERWORLD) return;
		if (depth == 0) return;
		if (--depth == 0) VillageTicker.ENTITIES.add(Math.max(0, System.nanoTime() - started));
	}
}
