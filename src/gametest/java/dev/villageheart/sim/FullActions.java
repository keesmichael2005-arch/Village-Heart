package dev.villageheart.sim;

import dev.villageheart.VillageHeart;
import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.sim.ScenarioAction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The outdoor sites of the {@code full} scenario (M26): a Mineshaft and a Hunting Ground, placed the way {@code training_yard} places its marker
 * (site-relative {@code at}, an oak fence post, the marker hanging on the post's south face).
 */
final class FullActions {
	private FullActions() {}

	private static void set(ServerLevel level, BlockPos p, BlockState s) { level.setBlock(p, s, Block.UPDATE_CLIENTS); }

	private static void marker(ServerLevel level, BuildingType type, BlockPos post, String what) {
		set(level, post, Blocks.OAK_FENCE.defaultBlockState());
		BlockPos marker = post.south();
		BlockState m = MarkerBlocks.BLOCKS.get(type).defaultBlockState().setValue(MarkerBlock.FACING, Direction.SOUTH);
		if (!m.canSurvive(level, marker)) throw new IllegalStateException(what + ": the marker has no support at " + marker.toShortString());
		set(level, marker, m);
		VillageHeart.LOG.info("[sim] {} at {}", what, marker.toShortString());
	}

	private static BlockPos at(Host ctx, ScenarioAction a, String what) {
		BlockPos site = ctx.site();
		if (site == null) throw new IllegalStateException(what + " needs a site action first");
		var at = (java.util.List<?>) a.fields().get("at");
		return new BlockPos(site.getX() + ((Number) at.get(0)).intValue(), site.getY(), site.getZ() + ((Number) at.get(1)).intValue());
	}

	/** {@code hunting_ground}: an oak fence post with the Hunting Ground marker; the grass around it is what the survey counts. */
	static void huntingGround(Host ctx, ScenarioAction a) {
		BlockPos g = at(ctx, a, "hunting_ground");
		marker(ctx.level(), BuildingType.HUNTING_GROUND, g.above(), "hunting ground");
	}

	/**
	 * {@code mineshaft}: a one-block shaft from the surface down to a 3 x 3 floor two blocks high, {@code depth} (default 14) below the marker. Eight rails and a
	 * torch lie on the floor, which rests on stone (rails need support). The geometry of M16's tests, scaled to the depth.
	 */
	static void mineshaft(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos g = at(ctx, a, "mineshaft");
		int depth = a.fields().get("depth") instanceof Number n ? n.intValue() : 14;
		int markerY = g.getY() + 1;
		int lowest = markerY - depth;
		BlockState air = Blocks.AIR.defaultBlockState();
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
			set(level, new BlockPos(g.getX() + x, lowest - 1, g.getZ() + z), Blocks.STONE.defaultBlockState());
			for (int y = lowest; y <= lowest + 1; y++) set(level, new BlockPos(g.getX() + x, y, g.getZ() + z), air);
			// a one-block collar of stone around the shaft above the floor keeps the walls solid where the ground is soft
			if (x != 0 || z != 0) for (int y = lowest + 2; y <= g.getY(); y++) {
				BlockPos p = new BlockPos(g.getX() + x, y, g.getZ() + z);
				if (y < g.getY() && level.getBlockState(p).isAir()) set(level, p, Blocks.STONE.defaultBlockState());
			}
		}
		for (int y = lowest + 2; y <= g.getY(); y++) set(level, new BlockPos(g.getX(), y, g.getZ()), air);
		int n = 0;
		for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
			set(level, new BlockPos(g.getX() + x, lowest, g.getZ() + z), n < 8 ? Blocks.RAIL.defaultBlockState() : Blocks.TORCH.defaultBlockState());
			n++;
		}
		marker(level, BuildingType.MINESHAFT, new BlockPos(g.getX(), markerY, g.getZ() - 1), "mineshaft");
	}
}
