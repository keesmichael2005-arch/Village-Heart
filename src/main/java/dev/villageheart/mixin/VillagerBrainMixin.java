package dev.villageheart.mixin;

import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.WorkerBrain;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A hired (adult) villager gets the mod-built worker brain instead of the vanilla one. Both
 * {@code makeBrain} (construction) and {@code refreshBrain} (profession change, age boundary, load) end in this private method.
 * Unhired villagers fall through to vanilla untouched. (Other mixins: the breeding gate and the make-love hooks of M23, the furnace accessor of M13.)
 */
@Mixin(Villager.class)
abstract class VillagerBrainMixin {
	@Inject(method = "registerBrainGoals", at = @At("HEAD"), cancellable = true)
	private void villageheart$workerBrain(Brain<Villager> brain, CallbackInfo ci) {
		Villager self = (Villager) (Object) this;
		if (!self.isBaby() && WorkerAttachments.isWorker(self)) {
			WorkerBrain.install(self, brain);
			ci.cancel();
		}
	}
}
