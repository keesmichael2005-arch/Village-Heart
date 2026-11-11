package dev.villageheart.gametest.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** GameTest-only read of a mob's goals. Production code does not use this. */
@Mixin(Mob.class)
public interface MobGoalAccessor {
	@Accessor("goalSelector")
	GoalSelector villageheart$goals();
}
