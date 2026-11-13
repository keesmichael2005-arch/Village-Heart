package dev.villageheart.build;

import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildTexts;
import dev.villageheart.item.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * One book of every village plan. Use it in the air to choose a plan. Use it on the ground to place that plan. The book is not used up.
 */
public final class BlueprintBookItem extends Item {
	public BlueprintBookItem(Properties properties) { super(properties); }

	@Override
	public Component getName(ItemStack stack) {
		return BlueprintItem.blueprintOf(stack).map(bp -> {
			var line = BuildTexts.blueprintName(bp);
			return (Component) Component.translatable("item.villageheart.blueprint_book.named",
					Component.translatableWithFallback(line.key(), line.fallback()));
		}).orElseGet(() -> Component.translatable("item.villageheart.blueprint_book"));
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		ItemStack stack = ctx.getItemInHand();
		if (BlueprintItem.blueprintOf(stack).isEmpty()) {
			if (ctx.getLevel().isClientSide() && ctx.getHand() != null) BlueprintBookHooks.openScreen.accept(ctx.getHand());
			return InteractionResult.SUCCESS;
		}
		if (!(ctx.getLevel() instanceof net.minecraft.server.level.ServerLevel level)
				|| !(ctx.getPlayer() instanceof net.minecraft.server.level.ServerPlayer player)) return InteractionResult.SUCCESS;
		return BlueprintUse.click(player, level, stack, ctx.getClickedPos(), ctx.isSecondaryUseActive());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		ItemStack stack = player.getItemInHand(hand);
		boolean turn = player.isSecondaryUseActive() && stack.get(ModComponents.BLUEPRINT_SITE) != null;
		if (turn) {
			if (level instanceof net.minecraft.server.level.ServerLevel server
					&& player instanceof net.minecraft.server.level.ServerPlayer sp) {
				return BlueprintUse.click(sp, server, stack, null, true);
			}
			return InteractionResult.SUCCESS;
		}
		if (level.isClientSide()) BlueprintBookHooks.openScreen.accept(hand);
		return InteractionResult.SUCCESS;
	}

	public static ItemStack empty() { return new ItemStack(ModItems.BLUEPRINT_BOOK); }
}
