package dev.villageheart;

import dev.villageheart.block.ModBlocks;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorldClaim;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSavedData;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VillageHeart implements ModInitializer {
	public static final String MOD_ID = "villageheart";
	public static final Logger LOG = LoggerFactory.getLogger(MOD_ID);

	/** Only the owner may break the claimed Heart (any game mode). Unclaimed Hearts are inert. */
	private static boolean allowBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!state.is(ModBlocks.VILLAGE_HEART) || !(level instanceof ServerLevel server)) return true;
		VillageSavedData data = VillageClaims.data(server.getServer());
		if (data.mayBreakHeart(VillageClaims.locate(server, pos), player.getUUID()) != WorldClaim.BreakDecision.DENIED_NOT_OWNER) return true;
		String owner = data.village().map(VillageState::ownerName).orElse("?");
		player.displayClientMessage(Component.translatable("message.villageheart.heart.not_owner", owner), true);
		return false;
	}

	@Override
	public void onInitialize() {
		ModBlocks.register();
		dev.villageheart.item.ModItems.register();
		dev.villageheart.block.MarkerBlocks.register();
		dev.villageheart.block.JobBlocks.register();
		dev.villageheart.item.ModCreativeTab.register();
		PlayerBlockBreakEvents.BEFORE.register(VillageHeart::allowBreak);
		dev.villageheart.world.VillageTicker.register();
		LOG.info("Village Heart loaded");
	}
}
