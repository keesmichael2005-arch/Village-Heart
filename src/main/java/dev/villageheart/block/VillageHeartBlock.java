package dev.villageheart.block;

import dev.villageheart.core.ClaimRules;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public class VillageHeartBlock extends Block {
	public VillageHeartBlock(Properties properties) {
		super(properties);
	}

	/** Server-side claim check. Public so in-world tests can call it directly. */
	public static ClaimRules.Result tryClaim(ServerLevel level, BlockPos pos) {
		Optional<BlockPos> bell = level.getPoiManager().findClosest(
				type -> type.is(PoiTypes.MEETING), pos, ClaimRules.MAX_BELL_DISTANCE, PoiManager.Occupancy.ANY);
		double distance = bell.map(b -> Math.sqrt(b.distSqr(pos))).orElse(-1.0);
		// TODO(M1): persist the claim per world so a second village is refused.
		return ClaimRules.evaluate(distance, false);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel server) || !(placer instanceof Player player)) return;
		ClaimRules.Result result = tryClaim(server, pos);
		player.displayClientMessage(Component.translatable("message.villageheart.claim." + result.name().toLowerCase()), true);
	}
}
