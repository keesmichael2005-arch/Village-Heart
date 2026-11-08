package dev.villageheart.build;

import dev.villageheart.core.blueprint.SiteRules.Dependent;
import dev.villageheart.core.room.Pos;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;

/**
 * What would be left without support if a natural cell were cleared (the review of M20 round 1): clearing breaks the block with
 * {@code destroyBlock}, which shape-updates the six neighbours, so a torch, ladder, door, sign, rail or lantern that rests on the cell, and an
 * item frame or painting hung on it, pops even though it stands outside the site. Never changes the world: each neighbour is asked
 * {@code canSurvive} against a view of the level in which the cell is air.
 */
public final class SiteSupport {
	private static final org.slf4j.Logger LOGGER = org.slf4j.LoggerFactory.getLogger("villageheart");

	private SiteSupport() {}

	/** The first thing outside {@code touched} that is not natural and rests on the cell {@code c}, or empty. Only for a loaded cell. */
	public static Optional<Dependent> dependent(ServerLevel level, Pos c, Set<Long> touched) {
		BlockPos hole = BuildExecutor.at(c);
		LevelReader ghost = ghost(level, hole);
		for (Direction d : Direction.values()) {
			BlockPos np = hole.relative(d);
			if (touched.contains(Pos.pack(np.getX(), np.getY(), np.getZ()))) continue;
			BlockState ns = BuildExecutor.read(level, new Pos(np.getX(), np.getY(), np.getZ()));
			if (ns == null || ns.isAir() || !ns.getFluidState().isEmpty() || LevelSiteView.clearable(ns)) continue;
			boolean survives;
			try {
				survives = ns.canSurvive(ghost, np);
			} catch (RuntimeException e) {
				survives = false; // cannot tell: treat it as dependent (the safe side)
				LOGGER.warn("canSurvive of {} at {} threw; treating it as resting on {}", ns, np, hole, e);
			}
			// an ascending rail is removed by its neighbour update when the block on its raised side is gone (not part of canSurvive)
			if (survives && ns.getBlock() instanceof BaseRailBlock rail && raisedSide(np, ns.getValue(rail.getShapeProperty())).equals(hole)) survives = false;
			if (!survives) {
				return Optional.of(new Dependent(BuiltInRegistries.BLOCK.getKey(ns.getBlock()).toString(), new Pos(np.getX(), np.getY(), np.getZ())));
			}
		}
		for (HangingEntity h : level.getEntitiesOfClass(HangingEntity.class, new AABB(hole).inflate(1.5))) {
			Direction dir = h.getDirection();
			AABB support = h.getBoundingBox().move(dir.getStepX() * -0.5, dir.getStepY() * -0.5, dir.getStepZ() * -0.5).deflate(1.0E-7);
			for (BlockPos q : BlockPos.betweenClosed(BlockPos.containing(support.minX, support.minY, support.minZ),
					BlockPos.containing(support.maxX, support.maxY, support.maxZ))) {
				if (q.equals(hole)) {
					BlockPos hp = h.blockPosition();
					return Optional.of(new Dependent(BuiltInRegistries.ENTITY_TYPE.getKey(h.getType()).toString(), new Pos(hp.getX(), hp.getY(), hp.getZ())));
				}
			}
		}
		return Optional.empty();
	}

	/** The cell an ascending rail leans on (its own cell for a flat rail, which no cleared neighbour is). */
	private static BlockPos raisedSide(BlockPos rail, RailShape shape) {
		return switch (shape) {
			case ASCENDING_EAST -> rail.east();
			case ASCENDING_WEST -> rail.west();
			case ASCENDING_NORTH -> rail.north();
			case ASCENDING_SOUTH -> rail.south();
			default -> rail;
		};
	}

	/**
	 * The level as it would be with {@code hole} cleared: air and no fluid there, everything else answered by the real level. The two questions
	 * are told apart by their types (a position in, a block state or a fluid state out), so the names of the methods do not matter.
	 */
	private static LevelReader ghost(ServerLevel level, BlockPos hole) {
		return (LevelReader) Proxy.newProxyInstance(LevelReader.class.getClassLoader(), new Class<?>[] {LevelReader.class}, (proxy, m, args) -> {
			if (args != null && args.length == 1 && hole.equals(args[0])) {
				if (m.getReturnType() == BlockState.class) return Blocks.AIR.defaultBlockState();
				if (m.getReturnType() == FluidState.class) return Fluids.EMPTY.defaultFluidState();
			}
			// a default method (isEmptyBlock for a carpet, isWaterAt, ...) must see the hole too: run its body on the proxy, not on the level
			if (m.isDefault()) return InvocationHandler.invokeDefault(proxy, m, args);
			try {
				return m.invoke(level, args);
			} catch (InvocationTargetException e) {
				throw e.getCause();
			}
		});
	}
}
