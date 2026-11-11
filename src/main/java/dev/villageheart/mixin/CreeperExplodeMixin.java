package dev.villageheart.mixin;

import dev.villageheart.siege.CreeperBreachGoal;
import dev.villageheart.siege.SiegeDamage;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A creeper that lit itself on a village wall does not leave a vanilla crater (docs/plans/M25.md 3.11). Entities still
 * take the blast. Defence cells take {@link SiegeDamage#blast}. A creeper chasing a player keeps the vanilla explosion.
 */
@Mixin(Creeper.class)
abstract class CreeperExplodeMixin {
	private static final String EXPLODE =
			"Lnet/minecraft/server/level/ServerLevel;explode(Lnet/minecraft/world/entity/Entity;DDDFLnet/minecraft/world/level/Level$ExplosionInteraction;)V";

	@ModifyArg(method = "explodeCreeper", at = @At(value = "INVOKE", target = EXPLODE), index = 5)
	private Level.ExplosionInteraction villageheart$breacherSparesBlocks(Level.ExplosionInteraction interaction) {
		Creeper self = (Creeper) (Object) this;
		return self.getTags().contains(CreeperBreachGoal.BREACHER) ? Level.ExplosionInteraction.NONE : interaction;
	}

	@Inject(method = "explodeCreeper", at = @At(value = "INVOKE", target = EXPLODE, shift = At.Shift.AFTER))
	private void villageheart$blastDefence(CallbackInfo ci) {
		Creeper self = (Creeper) (Object) this;
		if (!self.getTags().contains(CreeperBreachGoal.BREACHER)) return;
		if (!(self.level() instanceof ServerLevel level)) return;
		CreeperBreachGoal.Aim aim = CreeperBreachGoal.takeAim(self.getUUID());
		if (aim == null) return;
		SiegeDamage.blast(level.getServer(), level, aim.pos(), aim.above(), self.isPowered());
	}
}
