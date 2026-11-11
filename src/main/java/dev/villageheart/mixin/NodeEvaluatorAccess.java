package dev.villageheart.mixin;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.pathfinder.NodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads the mob a path-node evaluator is planning for ({@code WalkNodeEvaluatorMixin}). */
@Mixin(NodeEvaluator.class)
public interface NodeEvaluatorAccess {
	@Accessor("mob")
	Mob villageheart$mob();
}
