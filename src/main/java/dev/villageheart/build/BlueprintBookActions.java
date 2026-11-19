package dev.villageheart.build;

import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.BookTexts;
import dev.villageheart.core.blueprint.BuildTexts;
import dev.villageheart.core.heart.Permission;
import dev.villageheart.core.heart.Permissions;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** Arms the Blueprint Book the player is holding. The server checks the rank and the tier again. */
public final class BlueprintBookActions {
	private BlueprintBookActions() {}

	public static void select(ServerPlayer player, int handId, String blueprintId) {
		InteractionHand hand = handId == InteractionHand.OFF_HAND.ordinal() ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
		ItemStack stack = player.getItemInHand(hand);
		if (!stack.is(dev.villageheart.item.ModItems.BLUEPRINT_BOOK)) {
			say(player, BookTexts.key("no_book"), BookTexts.english("no_book"));
			return;
		}
		Optional<VillageState> village = VillageClaims.current(player.level().getServer());
		if (village.isEmpty()) {
			player.displayClientMessage(Component.translatable("build.villageheart.msg.no_village"), true);
			return;
		}
		Optional<Blueprint> found = Blueprints.byId(blueprintId);
		if (found.isEmpty()) {
			say(player, BookTexts.key("unknown"), BookTexts.english("unknown"));
			return;
		}
		Blueprint bp = found.get();
		Rank rank = village.get().rankOf(player.getUUID());
		if (!Permissions.allows(rank, Permission.START_BLUEPRINT)) {
			say(player, BookTexts.key("no_permission"), BookTexts.english("no_permission"));
			return;
		}
		if (village.get().tier().compareTo(bp.tier()) < 0) {
			player.displayClientMessage(Component.translatable(BookTexts.key("locked"), bp.tier().displayName()), true);
			return;
		}
		stack.set(ModComponents.BLUEPRINT, bp.id());
		stack.remove(ModComponents.BLUEPRINT_SITE);
		var line = BuildTexts.blueprintName(bp);
		player.displayClientMessage(Component.translatable(BookTexts.key("armed"),
				Component.translatableWithFallback(line.key(), line.fallback())), true);
	}

	private static void say(ServerPlayer player, String key, String english) {
		player.displayClientMessage(Component.translatableWithFallback(key, english), true);
	}
}
