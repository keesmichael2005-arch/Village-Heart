package dev.villageheart.block;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.ClaimRules;
import dev.villageheart.economy.Treasury;
import dev.villageheart.heart.HeartScreens;
import dev.villageheart.world.VillageClaims;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class VillageHeartBlock extends Block {
	public VillageHeartBlock(Properties properties) {
		super(properties);
	}

	/**
	 * Server: refuses (returns null, so vanilla does not place the block or consume the item) unless the claim
	 * would succeed. Client: always allows; the server's block update removes the predicted block on refusal.
	 */
	@Override
	@Nullable
	public BlockState getStateForPlacement(BlockPlaceContext context) {
		if (!(context.getLevel() instanceof ServerLevel level)) return defaultBlockState();
		Player player = context.getPlayer();
		ClaimRules.Result result = VillageClaims.checkPlacement(level, context.getClickedPos(), player);
		if (result == ClaimRules.Result.CLAIMED) return defaultBlockState();
		if (player != null) {
			player.displayClientMessage(VillageClaims.refusalMessage(result, player), true);
			// Vanilla does not resync the client's predicted item decrement after a refused placement.
			if (player instanceof ServerPlayer sp) sp.containerMenu.sendAllDataToRemote();
		}
		return null;
	}

	@Override
	public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
		super.setPlacedBy(level, pos, state, placer, stack);
		if (!(level instanceof ServerLevel server) || !(placer instanceof Player player)) return;
		ClaimRules.Result result = VillageClaims.claim(server, pos, player);
		if (result != ClaimRules.Result.CLAIMED) {
			VillageHeart.LOG.warn("Village Heart placed at {} but claim was {} (logic bug)", pos, result);
		}
		player.displayClientMessage(VillageClaims.refusalMessage(result, player), true);
	}

	/**
	 * Right-click with emeralds in the main hand puts the whole stack into the treasury (M12; Residents and above, as much as fits). Anything else,
	 * a refused Visitor included, falls through to {@link #useWithoutItem}, so the screen still opens. Sneaking with an item skips both (vanilla).
	 */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand,
			BlockHitResult hit) {
		if (hand != InteractionHand.MAIN_HAND || !stack.is(Items.EMERALD)) return InteractionResult.TRY_WITH_EMPTY_HAND;
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		boolean ours = VillageClaims.current(server.getServer()).filter(v -> v.isHeartAt(VillageClaims.locate(server, pos))).isPresent();
		if (!ours) return InteractionResult.TRY_WITH_EMPTY_HAND;
		return Treasury.depositFromHand(sp, hand) == Treasury.HandResult.CONSUMED ? InteractionResult.SUCCESS : InteractionResult.TRY_WITH_EMPTY_HAND;
	}

	/**
	 * Right-click opens the Heart screen when this Heart holds the village (every rank; Visitors see the Overview only). Holding an
	 * item does not matter, except sneaking with one, which vanilla keeps for placing blocks.
	 */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
		if (level instanceof ServerLevel server && player instanceof ServerPlayer sp) {
			boolean ours = VillageClaims.current(server.getServer())
					.filter(v -> v.isHeartAt(VillageClaims.locate(server, pos))).isPresent();
			if (ours) HeartScreens.open(sp, server, pos);
			else player.displayClientMessage(Component.translatable("message.villageheart.heart.not_this_village"), true);
		}
		return InteractionResult.SUCCESS;
	}

	/** Any removal with neighbour updates (owner break, commands, ...) releases the claim if this is the claimed Heart. */
	@Override
	protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
		super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
		VillageClaims.releaseAt(level, pos);
	}
}
