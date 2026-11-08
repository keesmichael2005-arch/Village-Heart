package dev.villageheart.build;

import dev.villageheart.core.ClaimGeometry;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.TableSnapshot;
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

/** Opens the Builder's Table screen. Anyone inside the claim may open it and read it; writes are checked per action. */
public final class BuildersTableScreens {
	private BuildersTableScreens() {}

	public static void open(ServerPlayer player, ServerLevel level, BlockPos table) {
		Optional<VillageState> village = VillageClaims.current(level.getServer());
		if (village.isEmpty() || !ClaimGeometry.inClaim(village.get().heart(), VillageClaims.locate(level, table))) {
			player.sendSystemMessage(Component.translatable("build.villageheart.msg.not_in_village"));
			return;
		}
		Optional<TableSnapshot> opening = BuildersTableFacts.snapshot(level.getServer(), player);
		if (opening.isEmpty()) return;
		int epoch = VillageClaims.data(level.getServer()).epoch();
		BuildersTableOpenData data = new BuildersTableOpenData(table, opening.get());
		player.openMenu(new ExtendedScreenHandlerFactory<BuildersTableOpenData>() {
			@Override
			public BuildersTableOpenData getScreenOpeningData(ServerPlayer p) { return data; }

			@Override
			public Component getDisplayName() { return Component.translatable("container.villageheart.builders_table"); }

			@Nullable
			@Override
			public AbstractContainerMenu createMenu(int id, Inventory inventory, Player p) {
				return new BuildersTableMenu(id, inventory, level, player, table, epoch, data.snapshot());
			}
		});
	}
}
