package dev.villageheart.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.building.BuildingType;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A wall-mounted building marker plaque (like a wall sign). FACING is the direction the plaque's front faces,
 * i.e. out of the wall; the supporting wall block is at {@code pos.relative(FACING.getOpposite())}.
 */
public class MarkerBlock extends HorizontalDirectionalBlock {
	public static final EnumProperty<MarkerStatus> STATUS = EnumProperty.create("status", MarkerStatus.class);
	private static final Codec<BuildingType> BUILDING_TYPE_CODEC = Codec.STRING.comapFlatMap(
			id -> BuildingType.byId(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "unknown building " + id)),
			BuildingType::id);
	public static final MapCodec<MarkerBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			BUILDING_TYPE_CODEC.fieldOf("building").forGetter(b -> b.type), propertiesCodec()).apply(i, MarkerBlock::new));
	private static final Map<Direction, VoxelShape> SHAPES = Shapes.rotateHorizontal(Block.boxZ(14, 1, 15, 15, 16));

	private final BuildingType type;

	public MarkerBlock(BuildingType type, Properties properties) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(STATUS, MarkerStatus.UNCHECKED));
	}

	public BuildingType type() { return type; }

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING, STATUS);
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return SHAPES.get(state.getValue(FACING));
	}

	@Override
	protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
		return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).isSolid();
	}

	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		LevelReader level = context.getLevel();
		BlockPos pos = context.getClickedPos();
		BlockState base = defaultBlockState().setValue(STATUS, MarkerStatus.UNCHECKED);
		Direction clicked = context.getClickedFace();
		if (clicked.getAxis().isHorizontal()) {
			BlockState s = base.setValue(FACING, clicked);
			if (s.canSurvive(level, pos)) return s;
		}
		for (Direction d : context.getNearestLookingDirections()) {
			if (d.getAxis().isHorizontal()) {
				BlockState s = base.setValue(FACING, d.getOpposite());
				if (s.canSurvive(level, pos)) return s;
			}
		}
		return null;
	}

	@Override
	protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
			Direction direction, BlockPos neighbourPos, BlockState neighbour, RandomSource random) {
		return direction.getOpposite() == state.getValue(FACING) && !state.canSurvive(level, pos)
				? Blocks.AIR.defaultBlockState()
				: super.updateShape(state, level, ticks, pos, direction, neighbourPos, neighbour, random);
	}
}
