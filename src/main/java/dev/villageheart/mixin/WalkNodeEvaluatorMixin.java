package dev.villageheart.mixin;

import dev.villageheart.defence.GatePaths;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Lets hired workers and guards plan paths through closed fence gates inside the claim. Vanilla types a closed gate {@code PathType.FENCE}
 * (malus -1: a wall), while a closed wooden door is {@code DOOR_WOOD_CLOSED}, which {@code getPathTypeWithinMobBB} turns into
 * {@code WALKABLE_DOOR} for a mob that can open doors (villagers can). For an eligible walker the gate cell is reported as
 * {@code DOOR_WOOD_CLOSED}, so the same rule applies; {@code GateTraffic} opens the gate when the path reaches it. Every other mob still sees a fence.
 */
@Mixin(WalkNodeEvaluator.class)
abstract class WalkNodeEvaluatorMixin {
	/** The first {@code getPathType} call in {@code getPathTypeWithinMobBB} is the per-cell type of the mob's bounding box. */
	@Redirect(method = "getPathTypeWithinMobBB", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/level/pathfinder/WalkNodeEvaluator;getPathType(Lnet/minecraft/world/level/pathfinder/PathfindingContext;III)Lnet/minecraft/world/level/pathfinder/PathType;",
			ordinal = 0))
	private PathType villageheart$gateAsDoor(WalkNodeEvaluator self, PathfindingContext ctx, int x, int y, int z) {
		PathType type = self.getPathType(ctx, x, y, z);
		if (type != PathType.FENCE) return type;
		Mob mob = ((NodeEvaluatorAccess) (Object) self).villageheart$mob();
		if (mob == null) return type;
		BlockPos pos = new BlockPos(x, y, z);
		BlockState state = ctx.getBlockState(pos);
		return GatePaths.mayPass(mob, pos, state) ? PathType.DOOR_WOOD_CLOSED : type;
	}
}
