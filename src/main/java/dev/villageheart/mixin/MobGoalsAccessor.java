package dev.villageheart.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** {@code Mob.goalSelector} is protected. The siege installer adds goals through this. */
@Mixin(Mob.class)
public interface MobGoalsAccessor {
	@Accessor("goalSelector")
	GoalSelector villageheart$goalSelector();
}
