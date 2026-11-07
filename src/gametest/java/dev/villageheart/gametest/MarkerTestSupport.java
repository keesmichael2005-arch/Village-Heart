package dev.villageheart.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/** Helpers for marker GameTests. Never call {@code placeAt}: it clicks the air in front of the block. */
public final class MarkerTestSupport {
	private MarkerTestSupport() {}

	/** Right-click the given face of the block at {@code relBlock} holding {@code stack} in the main hand. */
	public static InteractionResult clickFace(GameTestHelper h, ServerPlayer player, BlockPos relBlock, Direction face, ItemStack stack) {
		player.setItemInHand(InteractionHand.MAIN_HAND, stack);
		BlockPos abs = h.absolutePos(relBlock);
		Vec3 centre = Vec3.atCenterOf(abs).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
		BlockHitResult hit = new BlockHitResult(centre, face, abs, false);
		return player.getMainHandItem().useOn(new UseOnContext(player, InteractionHand.MAIN_HAND, hit));
	}

	/** Remove only this test's own player (VillageTestSupport.reset would remove every helper player). */
	public static void removePlayer(GameTestHelper h, ServerPlayer player) {
		h.getLevel().getServer().getPlayerList().remove(player);
	}

	/** A real ServerPlayer owned by the calling test only (not tracked by VillageTestSupport). Standing at {@code relPos}. */
	public static ServerPlayer player(GameTestHelper h, String name, GameType mode, BlockPos relPos) {
		var server = h.getLevel().getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile, ClientInformation.createDefault());
		Connection conn = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(conn);
		server.getPlayerList().placeNewPlayer(conn, p, CommonListenerCookie.createInitial(profile, false));
		p.setGameMode(mode);
		Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(relPos));
		p.setPos(at.x, at.y, at.z);
		if (mode == GameType.SURVIVAL && p.hasInfiniteMaterials()) throw new IllegalStateException("survival player has infinite materials");
		return p;
	}

	/** Make the player look along a horizontal direction (pitch 0). */
	public static void look(ServerPlayer p, Direction d) {
		p.setYRot(d.toYRot());
		p.setXRot(0);
		p.setYHeadRot(d.toYRot());
	}
}
