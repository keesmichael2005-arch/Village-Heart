package dev.villageheart.worker.cook;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.AbstractFurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The smoker shows the cook's work: while it cooks, the block is {@code LIT} (the smoker block's own {@code animateTick} then spawns the
 * smoke and the crackle on the client). The block entity writes {@code LIT} only when its own burning state changes, so a value set here
 * stays until the cook, the {@link KitchenSystem} sweep or a player's use of the smoker changes it.
 */
public final class KitchenVisuals {
	private KitchenVisuals() {}

	/** Sets {@code LIT} of the smoker at {@code pos} (a loaded chunk only). True when the block changed. */
	public static boolean light(ServerLevel level, BlockPos pos, boolean lit) {
		if (level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4) == null) return false;
		BlockState state = level.getBlockState(pos);
		if (!(state.getBlock() instanceof AbstractFurnaceBlock) || state.getValue(AbstractFurnaceBlock.LIT) == lit) return false;
		level.setBlock(pos, state.setValue(AbstractFurnaceBlock.LIT, lit), 3);
		return true;
	}

	public static boolean isLit(ServerLevel level, BlockPos pos) {
		BlockState state = level.getBlockState(pos);
		return state.getBlock() instanceof AbstractFurnaceBlock && state.getValue(AbstractFurnaceBlock.LIT);
	}
}
