package dev.villageheart.gametest;

import dev.villageheart.build.BuildJobs;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;

/** Helpers of the defence GameTests (M21): line builds by command, materials, and the "every cell equals the blueprint" check. */
public final class DefenceTestSupport {
	private DefenceTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:defence_" + name; }

	/** {@code /villageheart build line <blueprint> <from> <to>} between two test-relative ground blocks. */
	public static void startLine(GameTestHelper h, String blueprintId, BlockPos fromRel, BlockPos toRel) {
		BlockPos a = h.absolutePos(fromRel), b = h.absolutePos(toRel);
		BuildTestSupport.command(h, "villageheart build line " + blueprintId + " " + a.getX() + " " + a.getY() + " " + a.getZ() + " "
				+ b.getX() + " " + b.getY() + " " + b.getZ());
	}

	public static String blockId(BlockState s) { return BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString(); }

	/** Every touched cell of the blueprint at the placement holds the blueprint's block (or air). */
	public static void assertBuilt(GameTestHelper h, Blueprint bp, Placement p, String what) {
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			PaletteEntry e = bp.entry(x, y, z);
			if (!e.touched()) continue;
			Pos w = p.world(bp, x, y, z);
			BlockPos rel = BuildTestSupport.rel(h, w);
			BlockState s = h.getBlockState(rel);
			if (e.kind() == PaletteEntry.Kind.AIR) {
				h.assertTrue(s.isAir(), what + ": air expected at " + rel + " (local " + x + "," + y + "," + z + "), got " + s);
			} else if (e.partOf() == null) {
				h.assertTrue(blockId(s).equals(e.blockId()), what + ": " + e.blockId() + " expected at " + rel + " (local " + x + "," + y + "," + z + "), got " + s);
			}
		}
	}

	public static void advanceAll(GameTestHelper h) {
		for (BuildJob j : new ArrayList<>(BuildTestSupport.builds(h))) {
			BuildJobs.Advanced a = BuildJobs.advance(h.getLevel().getServer(), j.id(), Integer.MAX_VALUE);
			h.assertTrue(a.finished(), "build " + j.blueprintId() + " finished: " + a);
		}
	}

	public static ItemStack[] wallMaterials(int cobblestone) { return new ItemStack[] {new ItemStack(Items.COBBLESTONE, cobblestone)}; }

	public static ItemStack[] lampMaterials(int copies) {
		return new ItemStack[] {new ItemStack(Items.OAK_FENCE, 2 * copies), new ItemStack(Items.LANTERN, copies)};
	}

	public static ItemStack[] towerMaterials(int copies) {
		return new ItemStack[] {new ItemStack(Items.COBBLESTONE, 38 * copies), new ItemStack(Items.OAK_PLANKS, 24 * copies),
				new ItemStack(Items.OAK_FENCE, 16 * copies), new ItemStack(Items.LADDER, 6 * copies)};
	}

	public static ItemStack[] concat(ItemStack[]... parts) {
		List<ItemStack> out = new ArrayList<>();
		for (ItemStack[] p : parts) out.addAll(List.of(p));
		return out.toArray(new ItemStack[0]);
	}

	/** The row of ground blocks under a line: a hash that stays equal when the build does not touch the ground. */
	public static int groundHash(GameTestHelper h, Blueprint bp, Placement p) {
		int hash = 1;
		for (int x = 0; x < bp.width(); x++) {
			for (int z = 0; z < bp.depth(); z++) {
				Pos w = p.world(bp, x, 0, z);
				hash = 31 * hash + net.minecraft.world.level.block.Block.getId(h.getLevel().getBlockState(new BlockPos(w.x(), w.y() - 1, w.z())));
			}
		}
		return hash;
	}

	public static Rotation4[] rotations() { return Rotation4.values(); }
}
