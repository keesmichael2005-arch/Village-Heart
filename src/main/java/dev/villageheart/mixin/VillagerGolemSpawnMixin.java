package dev.villageheart.mixin;

import dev.villageheart.golem.GolemGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The only villager-made iron golem path. Gossip and the panic trigger both end here. Cancelling at the head skips vanilla's entity query
 * and the spawn. A carved pumpkin still builds a golem: that is {@code CarvedPumpkinBlock}, not this method.
 */
@Mixin(Villager.class)
abstract class VillagerGolemSpawnMixin {
	@Inject(method = "spawnGolemIfNeeded", at = @At("HEAD"), cancellable = true)
	private void villageheart$noVanillaGolemInClaim(ServerLevel level, long gameTime, int required, CallbackInfo ci) {
		if (GolemGate.block((Villager) (Object) this, gameTime)) ci.cancel();
	}
}
