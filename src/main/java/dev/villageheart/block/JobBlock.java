package dev.villageheart.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.building.JobBlockType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/** A plain block in M3; M7 registers these as job-block POIs. */
public class JobBlock extends HorizontalDirectionalBlock {
	private static final Codec<JobBlockType> TYPE_CODEC = Codec.STRING.comapFlatMap(
			id -> JobBlockType.byId(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "unknown job block " + id)),
			JobBlockType::id);
	public static final MapCodec<JobBlock> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
			TYPE_CODEC.fieldOf("job_block").forGetter(b -> b.type), propertiesCodec()).apply(i, JobBlock::new));
	private static final VoxelShape STUMP = Block.box(1, 0, 1, 15, 12, 15);

	private final JobBlockType type;

	public JobBlock(JobBlockType type, Properties properties) {
		super(properties);
		this.type = type;
		registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
	}

	public JobBlockType type() { return type; }

	@Override
	protected MapCodec<? extends HorizontalDirectionalBlock> codec() { return CODEC; }

	@Override
	protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
		builder.add(FACING);
	}

	@Override
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
	}

	@Override
	protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
		return type == JobBlockType.CHOPPING_BLOCK ? STUMP : super.getShape(state, level, pos, context);
	}
}
