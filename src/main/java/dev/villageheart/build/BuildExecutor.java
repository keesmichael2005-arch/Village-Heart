package dev.villageheart.build;

import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildPlan;
import dev.villageheart.core.blueprint.BuildPlan.Step;
import dev.villageheart.core.blueprint.BuilderPlanner.CellState;
import dev.villageheart.core.blueprint.PaletteEntry;
import dev.villageheart.core.blueprint.Placement;
import dev.villageheart.core.blueprint.SiteRules.SiteCell;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;

/**
 * Clears and places blocks for the builder and for the instant-build commands: the one piece of code that changes the world for a build
 * (plan 3.2 #8). Every read first checks the chunk is loaded.
 */
public final class BuildExecutor {
	private BuildExecutor() {}

	/** The stone pickaxe the builder's drops are computed with: stone gives cobblestone, ores drop. */
	private static final ItemStack BASIC_TOOL = new ItemStack(Items.STONE_PICKAXE);

	public static BlockPos at(Pos p) { return new BlockPos(p.x(), p.y(), p.z()); }

	/** The state in the cell, or null when its chunk is not loaded. */
	@Nullable
	public static BlockState read(ServerLevel level, Pos p) {
		LevelChunk c = level.getChunkSource().getChunkNow(p.x() >> 4, p.z() >> 4);
		return c == null ? null : c.getBlockState(at(p));
	}

	/** What the planner needs to know about the cell of a step. */
	public static CellState cellState(ServerLevel level, Step step, Set<Long> touched) {
		BlockState st = read(level, step.pos());
		if (st == null) return CellState.UNLOADED;
		if (step.kind() == BuildPlan.Kind.CLEAR) {
			if (st.isAir()) return CellState.ALREADY_RIGHT;
			if (LevelSiteView.classify(st) != SiteCell.CLEARABLE) return CellState.BLOCKED;
			return supports(level, step.pos(), touched) ? CellState.BLOCKED : CellState.CLEARABLE;
		}
		if (PaletteStates.holds(st, step.entry())) return CellState.ALREADY_RIGHT;
		if (st.isAir()) return entityIn(level, step) ? CellState.ENTITY : CellState.FREE;
		if (LevelSiteView.classify(st) == SiteCell.CLEARABLE) return supports(level, step.pos(), touched) ? CellState.BLOCKED : CellState.CLEARABLE;
		// water or lava (a fluid block, not a waterlogged block of a player) in a cell the check pass finds wrong (a farmland flooded through a gap): put the block back
		if (step.kind() == BuildPlan.Kind.CHECK && st.getBlock() instanceof LiquidBlock) return CellState.FREE;
		// a check pass leaves a different block of a player alone
		return step.kind() == BuildPlan.Kind.CHECK ? CellState.ALREADY_RIGHT : CellState.BLOCKED;
	}

	/** True when a block or hanging thing of a player outside the site rests on the cell: clearing it would break that (see {@link SiteSupport}). */
	private static boolean supports(ServerLevel level, Pos p, Set<Long> touched) {
		return SiteSupport.dependent(level, p, touched).isPresent();
	}

	private static boolean entityIn(ServerLevel level, Step step) {
		BlockState target = PaletteStates.template(step.entry().state());
		if (!level.isUnobstructed(target, at(step.pos()), CollisionContext.empty())) return true;
		for (BuildPlan.Part part : step.parts()) {
			if (!level.isUnobstructed(PaletteStates.template(part.entry().state()), at(part.pos()), CollisionContext.empty())) return true;
		}
		return false;
	}

	/** Breaks the natural block in a cell and returns its drops (nothing when the {@code block_drops} gamerule is off). */
	public static List<ItemStack> clear(ServerLevel level, Pos p, @Nullable Entity actor) {
		BlockPos pos = at(p);
		BlockState st = level.getBlockState(pos);
		List<ItemStack> drops = new ArrayList<>();
		if (level.getGameRules().get(GameRules.BLOCK_DROPS)) {
			for (ItemStack d : Block.getDrops(st, level, pos, null, actor, BASIC_TOOL)) drops.add(d.copy());
		}
		level.destroyBlock(pos, false, actor);
		return drops;
	}

	/**
	 * Places the block of a PLACE/CHECK step with {@code item} as its material: a tag material places the block of the item. A two-block unit
	 * puts its parts in the same call (door lower then upper, bed foot then head). The cell must be air or replaceable.
	 */
	public static void place(ServerLevel level, Blueprint bp, Placement pl, Step step, Item item, @Nullable Entity actor) {
		PaletteEntry e = step.entry();
		Block substitute = PaletteStates.substituteFor(e, item);
		BlockState state = PaletteStates.resolve(e, pl.rotation(), substitute);
		BlockPos pos = at(step.pos());
		level.setBlock(pos, state, Block.UPDATE_ALL);
		if (step.parts().isEmpty()) {
			// fences and panes connect to what stands next to them
			BlockState settled = Block.updateFromNeighbourShapes(level.getBlockState(pos), level, pos);
			if (settled != level.getBlockState(pos) && !settled.isAir()) level.setBlock(pos, settled, Block.UPDATE_CLIENTS);
		}
		Block primary = level.getBlockState(pos).getBlock();
		for (BuildPlan.Part part : step.parts()) {
			level.setBlock(at(part.pos()), PaletteStates.resolve(part.entry(), pl.rotation(), e.material() != null && e.material().isTag() ? primary : null),
					Block.UPDATE_ALL);
		}
		var sound = level.getBlockState(pos).getSoundType().getPlaceSound();
		level.playSound(null, pos, sound, SoundSource.BLOCKS, 0.8F, 1.0F);
	}
}
