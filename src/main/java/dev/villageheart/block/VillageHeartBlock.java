package dev.villageheart.block;

import dev.villageheart.core.ClaimRules;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class VillageHeartBlock extends Block {
	public VillageHeartBlock(Properties properties) {
		super(properties);
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel server) || !(placer instanceof Player player)) return;
		ClaimRules.Result result = VillageClaims.claim(server, pos, player);
		player.displayClientMessage(Component.translatable("message.villageheart.claim." + result.name().toLowerCase()), true);
	}
}
