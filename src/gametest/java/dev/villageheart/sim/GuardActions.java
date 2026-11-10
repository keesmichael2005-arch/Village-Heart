package dev.villageheart.sim;

import dev.villageheart.VillageHeart;
import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.MarkerBlocks;
import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.sim.ScenarioAction;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The scenario actions M18 adds (plan 3.10): {@code tier} sets the village tier (no tier command exists), {@code training_yard} builds a
 * Training Yard (an oak fence post with the marker and two target blocks), {@code night_spawn} places a few hostile mobs inside the claim at a
 * given tick (the scripted threat of the {@code defended} scenario: it makes "at most one mob death" mean something).
 */
public final class GuardActions {
	private GuardActions() {}

	private static int num(ScenarioAction a, String k, int dflt) { return a.fields().get(k) instanceof Number n ? n.intValue() : dflt; }

	private static void set(ServerLevel level, BlockPos p, BlockState s) { level.setBlock(p, s, Block.UPDATE_CLIENTS); }

	public static void tier(Host ctx, ScenarioAction a) {
		Tier t = Tier.valueOf(((String) a.fields().get("tier")).toUpperCase(java.util.Locale.ROOT));
		VillageClaims.data(ctx.server()).update(v -> v.withTier(t));
		VillageHeart.LOG.info("[sim] tier set to {}", t);
	}

	public static void trainingYard(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = ctx.site();
		if (site == null) throw new IllegalStateException("training_yard needs a site action first");
		var at = (java.util.List<?>) a.fields().get("at");
		BlockPos post = new BlockPos(site.getX() + ((Number) at.get(0)).intValue(), site.getY() + 1, site.getZ() + ((Number) at.get(1)).intValue());
		set(level, post, Blocks.OAK_FENCE.defaultBlockState());
		set(level, post.east(2).south(), Blocks.TARGET.defaultBlockState());
		set(level, post.south(3), Blocks.TARGET.defaultBlockState());
		BlockPos marker = post.south();
		BlockState m = MarkerBlocks.BLOCKS.get(BuildingType.TRAINING_YARD).defaultBlockState().setValue(MarkerBlock.FACING, Direction.SOUTH);
		if (!m.canSurvive(level, marker)) throw new IllegalStateException("training_yard: the marker has no support at " + marker.toShortString());
		set(level, marker, m);
		VillageHeart.LOG.info("[sim] training yard at {}", marker.toShortString());
	}

	/**
	 * {@code wall_ring} (the {@code defended_walled} scenario): a two-high cobblestone wall on the edge of the site ({@code size} [w, d], the same as the
	 * {@code site} action) with ONE oak fence gate in the south edge at x offset {@code gate} (default w / 2), the cell above the gate left open for the
	 * walker. Solid cobblestone, not wall blocks (an unconnected wall block is a 4-pixel post a zombie walks between). A torch on top every 6 blocks.
	 */
	public static void wallRing(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = ctx.site();
		if (site == null) throw new IllegalStateException("wall_ring needs a site action first");
		var size = (java.util.List<?>) a.fields().get("size");
		int w = ((Number) size.get(0)).intValue(), d = ((Number) size.get(1)).intValue();
		int gateX = num(a, "gate", w / 2);
		int y = site.getY();
		int cells = 0;
		for (int x = 0; x < w; x++) {
			for (int z = 0; z < d; z++) {
				if (x != 0 && x != w - 1 && z != 0 && z != d - 1) continue;
				boolean gate = z == d - 1 && x == gateX;
				BlockPos base = new BlockPos(site.getX() + x, y, site.getZ() + z);
				set(level, base, Blocks.COBBLESTONE.defaultBlockState());
				if (gate) {
					set(level, base.above(), Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(net.minecraft.world.level.block.FenceGateBlock.FACING, Direction.NORTH)
							.setValue(net.minecraft.world.level.block.FenceGateBlock.OPEN, false));
					set(level, base.above(2), Blocks.AIR.defaultBlockState());
					set(level, base.above(3), Blocks.AIR.defaultBlockState());
				} else {
					set(level, base.above(), Blocks.COBBLESTONE.defaultBlockState());
					set(level, base.above(2), Blocks.COBBLESTONE.defaultBlockState());
					if ((x + z) % 6 == 0) set(level, base.above(3), Blocks.TORCH.defaultBlockState());
				}
				cells++;
			}
		}
		VillageHeart.LOG.info("[sim] wall ring {}x{} at {}: {} cells, gate at x+{}", w, d, site.toShortString(), cells, gateX);
	}

	/**
	 * {@code lamps}: fence posts with a torch on top (3 high, light 14) on a grid of {@code spacing} blocks over the site, on bare grass or dirt only
	 * (never on a building, a field or a path), so the open ground inside the wall is lit and nothing spawns there.
	 */
	public static void lamps(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos site = ctx.site();
		if (site == null) throw new IllegalStateException("lamps needs a site action first");
		var size = (java.util.List<?>) a.fields().get("size");
		int w = ((Number) size.get(0)).intValue(), d = ((Number) size.get(1)).intValue();
		int spacing = num(a, "spacing", 7);
		int placed = 0;
		for (int x = spacing / 2 + 1; x < w - 1; x += spacing) {
			for (int z = spacing / 2 + 1; z < d - 1; z += spacing) {
				BlockPos ground = new BlockPos(site.getX() + x, site.getY(), site.getZ() + z);
				var below = level.getBlockState(ground).getBlock();
				if (below != Blocks.GRASS_BLOCK && below != Blocks.DIRT) continue;
				if (!level.getBlockState(ground.above()).isAir() || !level.getBlockState(ground.above(2)).isAir() || !level.getBlockState(ground.above(3)).isAir()) continue;
				set(level, ground.above(), Blocks.OAK_FENCE.defaultBlockState());
				set(level, ground.above(2), Blocks.OAK_FENCE.defaultBlockState());
				set(level, ground.above(3), Blocks.TORCH.defaultBlockState());
				placed++;
			}
		}
		VillageHeart.LOG.info("[sim] lamps: {} posts at spacing {}", placed, spacing);
	}

	/** {@code count} mobs of type {@code mob}, 12..radius blocks from the Heart on loaded standing-room ground. */
	public static void nightSpawn(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos heart = ctx.heart();
		if (heart == null) throw new IllegalStateException("night_spawn needs a claim first");
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse((String) a.fields().get("mob")));
		int count = num(a, "count", 1), radius = num(a, "radius", 20);
		int placed = 0;
		int[] outside = a.fields().get("outside") instanceof java.util.List<?> o ? new int[] {((Number) o.get(0)).intValue(), ((Number) o.get(1)).intValue()} : null;
		for (int attempt = 0; attempt < 400 && placed < count; attempt++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2, dist = 12 + level.getRandom().nextDouble() * Math.max(0, radius - 12);
			int x = heart.getX() + (int) Math.round(Math.cos(angle) * dist), z = heart.getZ() + (int) Math.round(Math.sin(angle) * dist);
			if (!level.isLoaded(new BlockPos(x, heart.getY(), z))) continue;
			if (outside != null && ctx.site() != null && x >= ctx.site().getX() - 2 && x <= ctx.site().getX() + outside[0] + 1
					&& z >= ctx.site().getZ() - 2 && z <= ctx.site().getZ() + outside[1] + 1) continue; // inside or on the wall: not outside it
			int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
			BlockPos feet = new BlockPos(x, y, z);
			if (!level.getBlockState(feet.below()).isSolidRender() || !level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()) continue;
			if (Math.abs(y - heart.getY()) > 6) continue;
			if (!(type.create(level, EntitySpawnReason.COMMAND) instanceof Mob mob)) throw new IllegalStateException("night_spawn: not a mob: " + type);
			mob.snapTo(x + 0.5, y, z + 0.5, level.getRandom().nextFloat() * 360.0F, 0.0F);
			mob.setPersistenceRequired();
			if (mob instanceof Zombie zom) zom.setBaby(false);
			level.addFreshEntity(mob);
			placed++;
		}
		if (placed < count) throw new IllegalStateException("night_spawn: only " + placed + " of " + count + " could be placed");
		VillageHeart.LOG.info("[sim] night_spawn: {} x {}", placed, type);
	}
}
