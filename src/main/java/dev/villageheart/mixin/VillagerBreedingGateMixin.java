package dev.villageheart.mixin;

import dev.villageheart.growth.BreedingGate;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.npc.villager.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * No unlicensed villager breeding inside the claim (M23 plan 3.2 #7). {@code canBreed} is what vanilla's {@code InteractWith} filters and
 * {@code VillagerMakeLove.isBreedingPossible} read; {@code getBreedOffspring} is the one place a villager baby is made, so a caller that
 * skipped those checks (another mod, a command) still gets no baby.
 */
@Mixin(Villager.class)
abstract class VillagerBreedingGateMixin {
	@Inject(method = "canBreed()Z", at = @At("RETURN"), cancellable = true)
	private void villageheart$gate(CallbackInfoReturnable<Boolean> cir) {
		if (cir.getReturnValueZ() && BreedingGate.blocks((Villager) (Object) this)) cir.setReturnValue(false);
	}

	@Inject(method = "getBreedOffspring(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/entity/AgeableMob;)Lnet/minecraft/world/entity/npc/villager/Villager;",
			at = @At("HEAD"), cancellable = true)
	private void villageheart$noUnlicensedBaby(ServerLevel level, AgeableMob partner, CallbackInfoReturnable<Villager> cir) {
		Villager self = (Villager) (Object) this;
		if (BreedingGate.blocks(self) || (partner instanceof Villager p && BreedingGate.blocks(p))) cir.setReturnValue(null);
	}
}
