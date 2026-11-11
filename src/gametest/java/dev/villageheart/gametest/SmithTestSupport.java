package dev.villageheart.gametest;

import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

/** Helpers for the smith GameTests (M19). Own environment per test, {@code yard}, sky access. */
public final class SmithTestSupport {
	private static final Set<Long> FORCED = new HashSet<>();

	private SmithTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:smith_" + name; }

	/** Drop every iron golem in a box around {@code centre}. Earlier tests share the world. */
	public static void sweepGolems(ServerLevel level, BlockPos centre, double radius) {
		AABB box = new AABB(centre).inflate(radius);
		for (IronGolem g : level.getEntities(EntityType.IRON_GOLEM, box, e -> true)) g.discard();
	}

	public static int golemsNear(ServerLevel level, BlockPos centre, double radius) {
		return level.getEntities(EntityType.IRON_GOLEM, new AABB(centre).inflate(radius), IronGolem::isAlive).size();
	}

	/** Load and force every chunk the box touches. Released by {@link #releaseForced} and by {@code VillageTestSupport.reset}. */
	public static void force(ServerLevel level, BlockPos centre, int radius) {
		int x0 = (centre.getX() - radius) >> 4, x1 = (centre.getX() + radius) >> 4;
		int z0 = (centre.getZ() - radius) >> 4, z1 = (centre.getZ() + radius) >> 4;
		for (int cx = x0; cx <= x1; cx++) for (int cz = z0; cz <= z1; cz++) {
			level.getChunk(cx, cz);
			level.setChunkForced(cx, cz, true);
			FORCED.add(((long) cx << 32) | (cz & 0xffffffffL));
		}
	}

	public static void releaseForced(ServerLevel level) {
		for (long key : FORCED) level.setChunkForced((int) (key >> 32), (int) key, false);
		FORCED.clear();
	}

	/** A stone pad with air above it, glass only on the rim, so a golem has somewhere to stand and the villagers stay put. */
	public static void pad(ServerLevel level, BlockPos feet, int radius) {
		int y = feet.getY() - 1;
		for (int dx = -radius; dx <= radius; dx++) for (int dz = -radius; dz <= radius; dz++) {
			BlockPos ground = new BlockPos(feet.getX() + dx, y, feet.getZ() + dz);
			boolean rim = Math.abs(dx) == radius || Math.abs(dz) == radius;
			level.setBlock(ground, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
			for (int up = 1; up <= 5; up++) {
				BlockPos above = ground.above(up);
				boolean wall = rim && up <= 3;
				level.setBlock(above, wall ? Blocks.GLASS.defaultBlockState() : Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
			}
		}
	}

	public static Villager villagerAt(ServerLevel level, BlockPos feet) {
		Villager v = EntityType.VILLAGER.create(level, EntitySpawnReason.COMMAND);
		if (v == null) throw new IllegalStateException("no villager");
		v.snapTo(feet.getX() + 0.5, feet.getY(), feet.getZ() + 0.5, 0, 0);
		level.addFreshEntity(v);
		WorkerTestSupport.track(v);
		return v;
	}
}
