package dev.villageheart.build;

import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.BuildTexts;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.MaterialList;
import dev.villageheart.item.ModItems;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * A blueprint to build (docs/plans/M20.md 3.7): click the ground to see the footprint, sneak-click to rotate, click inside the outline to
 * confirm. The server decides everything ({@link BlueprintUse}); the client draws the outline from the {@code blueprint_site} component.
 */
public final class BlueprintItem extends Item {
	public BlueprintItem(Properties properties) { super(properties); }

	public static ItemStack of(Blueprint bp) {
		ItemStack stack = new ItemStack(ModItems.BLUEPRINT);
		stack.set(ModComponents.BLUEPRINT, bp.id());
		return stack;
	}

	public static Optional<Blueprint> blueprintOf(ItemStack stack) {
		String id = stack.get(ModComponents.BLUEPRINT);
		return id == null ? Optional.empty() : Blueprints.byId(id);
	}

	@Override
	public Component getName(ItemStack stack) {
		return blueprintOf(stack).map(bp -> {
			var line = BuildTexts.blueprintName(bp);
			return (Component) Component.translatable("item.villageheart.blueprint.named", Component.translatableWithFallback(line.key(), line.fallback()));
		}).orElseGet(() -> Component.translatable("item.villageheart.blueprint"));
	}

	@Override
	@SuppressWarnings("deprecation")
	public void appendHoverText(ItemStack stack, Item.TooltipContext context, TooltipDisplay display, Consumer<Component> out, TooltipFlag flag) {
		blueprintOf(stack).ifPresent(bp -> {
			for (MaterialList.Line l : bp.materials().lines()) {
				out.accept(Component.literal(l.material().words(l.count())).withStyle(ChatFormatting.GRAY));
			}
		});
	}

	@Override
	public InteractionResult useOn(UseOnContext ctx) {
		if (!(ctx.getLevel() instanceof ServerLevel level) || !(ctx.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
		return BlueprintUse.click(player, level, ctx.getItemInHand(), ctx.getClickedPos(), ctx.isSecondaryUseActive());
	}

	@Override
	public InteractionResult use(Level level, Player player, InteractionHand hand) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.SUCCESS;
		if (!player.isSecondaryUseActive()) return InteractionResult.PASS;
		return BlueprintUse.click(sp, server, player.getItemInHand(hand), null, true);
	}
}
