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

	/** {@code count} mobs of type {@code mob}, 12..radius blocks from the Heart on loaded standing-room ground. */
	public static void nightSpawn(Host ctx, ScenarioAction a) {
		ServerLevel level = ctx.level();
		BlockPos heart = ctx.heart();
		if (heart == null) throw new IllegalStateException("night_spawn needs a claim first");
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(Identifier.parse((String) a.fields().get("mob")));
		int count = num(a, "count", 1), radius = num(a, "radius", 20);
		int placed = 0;
		for (int attempt = 0; attempt < 200 && placed < count; attempt++) {
			double angle = level.getRandom().nextDouble() * Math.PI * 2, dist = 12 + level.getRandom().nextDouble() * Math.max(0, radius - 12);
			int x = heart.getX() + (int) Math.round(Math.cos(angle) * dist), z = heart.getZ() + (int) Math.round(Math.sin(angle) * dist);
			if (!level.isLoaded(new BlockPos(x, heart.getY(), z))) continue;
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
