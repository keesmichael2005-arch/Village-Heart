package dev.villageheart.build;

import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

/** Dirt under a floor, a one-block skirt, and scaffold posts on a tall build. */
public final class SiteGround {
	private SiteGround() {}

	/** Fills the foundation and, once the dirt is there, raises scaffold on a tall build. @return dirt blocks still missing */
	public static int prepare(ServerLevel level, Blueprint bp, BuildJob job) {
		int missing = dirt(level, bp, job);
		if (missing > 0) return missing;
		BuildRuntime rt = BuildRuntime.get(level.getServer());
		if (rt.prepared.add(job.id()) && bp.height() >= 6) scaffold(level, bp, job);
		return 0;
	}

	/** Fills air under the floor from Storage. Does not place scaffold. @return dirt blocks still missing */
	public static int dirt(ServerLevel level, Blueprint bp, BuildJob job) {
		BuildRuntime rt = BuildRuntime.get(level.getServer());
		if (rt.grounded.contains(job.id())) return 0;
		if (bp.kind() != Blueprint.Kind.BUILDING) {
			rt.grounded.add(job.id());
			rt.dirtShort.remove(job.id());
			return 0;
		}
		int missing = fill(level, bp, job.placement());
		if (missing == 0) {
			rt.grounded.add(job.id());
			rt.dirtShort.remove(job.id());
		} else {
			rt.dirtShort.put(job.id(), missing);
		}
		return missing;
	}

	public static void clearScaffold(ServerLevel level, java.util.UUID build) {
		List<BlockPos> posts = BuildRuntime.get(level.getServer()).scaffold.remove(build);
		if (posts == null) return;
		for (BlockPos p : posts) if (level.getBlockState(p).is(ModBlocks.BUILDER_SCAFFOLD)) level.setBlock(p, Blocks.AIR.defaultBlockState(), 3);
	}

	private static int fill(ServerLevel level, Blueprint bp, Placement placement) {
		int missing = 0;
		boolean out = false;
		for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			PaletteEntry floor = null;
			int layer = -1;
			for (int y = 0; y < bp.height(); y++) {
				PaletteEntry e = bp.entry(x, y, z);
				if (e.kind() == PaletteEntry.Kind.BLOCK && e.primary()) { floor = e; layer = y; break; }
			}
			if (floor == null) continue;
			Pos cell = placement.world(bp, x, layer, z);
			int floorY = placement.ground().y() + bp.base();
			BlockPos at = new BlockPos(cell.x(), cell.y(), cell.z());
			for (int d = 1; d <= 4; d++) {
				// air inside the blueprint (under a marker in the front opening) is not foundation
				if (cell.y() - d >= floorY) continue;
				BlockPos below = at.below(d);
				if (!level.getBlockState(below).isAir()) break;
				if (out) { missing++; continue; }
				if (dev.villageheart.stock.Stockpiles.withdraw(level, at, dev.villageheart.stock.StackKey.of(net.minecraft.world.item.Items.DIRT), 1, null).isEmpty()) {
					out = true;
					missing++;
					continue;
				}
				level.setBlock(below, Blocks.DIRT.defaultBlockState(), 3);
			}
		}
		return missing;
	}

	private static void scaffold(ServerLevel level, Blueprint bp, BuildJob job) {
		Placement p = job.placement();
		Pos min = p.min(bp), max = p.max(bp);
		List<BlockPos> placed = new ArrayList<>();
		int[][] corners = {{min.x() - 1, min.z() - 1}, {max.x() + 1, min.z() - 1}, {min.x() - 1, max.z() + 1}, {max.x() + 1, max.z() + 1}};
		int top = max.y();
		for (int[] c : corners) {
			for (int y = min.y(); y <= top; y++) {
				BlockPos pos = new BlockPos(c[0], y, c[1]);
				if (!level.getBlockState(pos).isAir()) continue;
				level.setBlock(pos, ModBlocks.BUILDER_SCAFFOLD.defaultBlockState(), 3);
				placed.add(pos);
			}
		}
		BuildRuntime.get(level.getServer()).scaffold.put(job.id(), placed);
	}
}
