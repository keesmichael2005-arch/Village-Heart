package dev.villageheart.heart;

import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.jetbrains.annotations.Nullable;

public final class HeartScreens {
	private HeartScreens() {}

	/** Opens the Heart screen for {@code player} (server thread). The caller has checked that this Heart holds the village. */
	public static void open(ServerPlayer player, ServerLevel level, BlockPos heart) {
		Optional<HeartSnapshot> opening = HeartMenu.build(level.getServer(), player);
		if (opening.isEmpty()) return;
		int epoch = VillageClaims.data(level.getServer()).epoch();
		HeartOpenData data = new HeartOpenData(heart, opening.get());
		player.openMenu(new ExtendedScreenHandlerFactory<HeartOpenData>() {
			@Override
			public HeartOpenData getScreenOpeningData(ServerPlayer p) { return data; }

			@Override
			public Component getDisplayName() { return Component.translatable("container.villageheart.heart"); }

			@Nullable
			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				return new HeartMenu(id, inventory, level, player, heart, epoch, data.snapshot());
			}
		});
	}
}
