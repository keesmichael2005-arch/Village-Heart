package dev.villageheart.mixin;

import dev.villageheart.golem.SmithyRepairGoal;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.AbstractGolem;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Every iron golem, including one loaded from disk, can walk to a Smithy. The goal itself decides whether it should. */
@Mixin(IronGolem.class)
abstract class IronGolemGoalsMixin extends AbstractGolem {
	protected IronGolemGoalsMixin(EntityType<? extends AbstractGolem> type, Level level) { super(type, level); }

	@Inject(method = "registerGoals", at = @At("TAIL"))
	private void villageheart$smithyRepair(CallbackInfo ci) {
		this.goalSelector.addGoal(1, new SmithyRepairGoal((IronGolem) (Object) this));
	}
}
