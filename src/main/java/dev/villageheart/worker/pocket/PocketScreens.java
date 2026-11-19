package dev.villageheart.worker.pocket;

import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.job.AwayTrips;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;

/** Right-click a hired villager to look at what they are carrying. An unhired villager still opens the trade window. */
public final class PocketScreens {
	private PocketScreens() {}

	public static void register() {
		UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
			if (world.isClientSide() || hand != net.minecraft.world.InteractionHand.MAIN_HAND) return InteractionResult.PASS;
			if (!(entity instanceof Villager v) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
			if (AwayTrips.isAway(v)) return InteractionResult.PASS;
			if (!WorkerAttachments.isWorker(v)) return InteractionResult.PASS;
			PocketMenu.View view = PocketMenu.View.of(world, v);
			sp.openMenu(new ExtendedScreenHandlerFactory<PocketMenu.View>() {
				@Override
				public PocketMenu createMenu(int id, Inventory inventory, Player p) { return new PocketMenu(id, inventory, view); }

				@Override
				public Component getDisplayName() { return Component.literal(view.name()); }

				@Override
				public PocketMenu.View getScreenOpeningData(ServerPlayer player) { return view; }
			});
			return InteractionResult.SUCCESS;
		});
	}

	/** Keeps the menu type referenced. The screen is registered on the client. */
	public static MenuType<?> type() { return dev.villageheart.heart.ModMenus.POCKET; }
}
