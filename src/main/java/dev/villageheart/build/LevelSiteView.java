package dev.villageheart.build;

import dev.villageheart.core.blueprint.SiteRules;
import dev.villageheart.core.blueprint.SiteRules.SiteCell;
import dev.villageheart.core.room.Pos;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * The world as the site rules and the builder see it (plan 3.2 #2): a block may be cleared only when it is natural, i.e. in the data tag
 * {@code #villageheart:builder_clearable} and, for leaves, not persistent (a player placed it), and it holds no fluid and no block entity.
 * Everything else is "player-placed" and is never broken. Never loads a chunk.
 */
public final class LevelSiteView implements SiteRules.SiteView {
	public static final TagKey<Block> CLEARABLE = TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("villageheart", "builder_clearable"));

	private final ServerLevel level;
	private int chunkX = Integer.MIN_VALUE, chunkZ = Integer.MIN_VALUE;
	private LevelChunk chunk;

	public LevelSiteView(ServerLevel level) { this.level = level; }

	/** True when the builder may break this block. */
	public static boolean clearable(BlockState s) {
		if (!s.is(CLEARABLE)) return false;
		if (s.is(Blocks.LIGHT) || s.is(Blocks.STRUCTURE_VOID)) return false;
		if (s.hasBlockEntity()) return false;
		return !(s.getBlock() instanceof LeavesBlock) || !s.getValue(LeavesBlock.PERSISTENT);
	}

	public static SiteCell classify(BlockState s) {
		if (s.isAir()) return SiteCell.AIR;
		if (!s.getFluidState().isEmpty()) return SiteCell.FLUID;
		if (s.is(BlockTags.OVERWORLD_NATURAL_LOGS)) return SiteCell.LOG;
		return clearable(s) ? SiteCell.CLEARABLE : SiteCell.BLOCKED;
	}

	private LevelChunk chunk(int x, int z) {
		int cx = x >> 4, cz = z >> 4;
		if (chunk != null && cx == chunkX && cz == chunkZ) return chunk;
		LevelChunk c = level.getChunkSource().getChunkNow(cx, cz);
		if (c != null) {
			chunk = c;
			chunkX = cx;
			chunkZ = cz;
		}
		return c;
	}

	/** The state at a position, or null when its chunk is not loaded. Outside the world height: air above, a block below. */
	public BlockState state(Pos p) {
		if (p.y() > level.getMaxY()) return Blocks.AIR.defaultBlockState();
		if (p.y() < level.getMinY()) return Blocks.BEDROCK.defaultBlockState();
		LevelChunk c = chunk(p.x(), p.z());
		return c == null ? null : c.getBlockState(new BlockPos(p.x(), p.y(), p.z()));
	}

	@Override
	public SiteCell cell(Pos p) {
		BlockState s = state(p);
		return s == null ? SiteCell.UNLOADED : classify(s);
	}

	@Override
	public String blockId(Pos p) {
		BlockState s = state(p);
		return s == null ? "minecraft:air" : BuiltInRegistries.BLOCK.getKey(s.getBlock()).toString();
	}
}
