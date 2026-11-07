package dev.villageheart.gametest;

import static dev.villageheart.gametest.BuildingTestSupport.rightClickAbs;

import dev.villageheart.core.Rank;
import dev.villageheart.gametest.HeartTestSupport.OverlayPlayer;
import dev.villageheart.rank.ContainerProtection;
import dev.villageheart.world.VillageClaims;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;

/** Helpers of the rank and container-protection GameTests (M24). */
public final class RankTestSupport {
	private RankTestSupport() {}

	public static String env(String name) { return "villageheart-gametest:ranks_" + name; }

	/** A survival player that records its action bar, standing next to the Heart, with the given rank (Visitor: none). */
	public static OverlayPlayer ranked(GameTestHelper h, String name, Rank rank) {
		OverlayPlayer p = (OverlayPlayer) VillageTestSupport.player(h, name, GameType.SURVIVAL, OverlayPlayer::new);
		if (rank != Rank.VISITOR && rank != Rank.OWNER) VillageClaims.setRank(h.getLevel().getServer(), p.getUUID(), rank);
		HeartTestSupport.standNearHeart(h, p);
		return p;
	}

	/** Right-click the top face of the block at {@code rel} with an empty hand (the real {@code useItemOn} path). */
	public static InteractionResult use(GameTestHelper h, ServerPlayer p, BlockPos rel) {
		return use(h, p, rel, ItemStack.EMPTY);
	}

	public static InteractionResult use(GameTestHelper h, ServerPlayer p, BlockPos rel, ItemStack stack) {
		ContainerProtection.resetForTests();
		return rightClickAbs(h.getLevel(), p, h.absolutePos(rel), Direction.UP, stack);
	}

	/**
	 * A fresh {@link ServerPlayer} with a chosen UUID and name, joined through the real {@code placeNewPlayer} path (so Fabric's JOIN
	 * event fires). Not tracked by {@code VillageTestSupport}: the test removes it with {@link #leave}.
	 */
	public static ServerPlayer joinAs(GameTestHelper h, java.util.UUID id, String name) {
		var server = h.getLevel().getServer();
		var profile = new com.mojang.authlib.GameProfile(id, name);
		ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile, net.minecraft.server.level.ClientInformation.createDefault());
		var conn = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
		new io.netty.channel.embedded.EmbeddedChannel(conn);
		server.getPlayerList().placeNewPlayer(conn, p, net.minecraft.server.network.CommonListenerCookie.createInitial(profile, false));
		p.setGameMode(GameType.SURVIVAL);
		return p;
	}

	public static void leave(GameTestHelper h, ServerPlayer p) { h.getLevel().getServer().getPlayerList().remove(p); }

	/** Runs a command line as the server console (permission level 4) and collects what the command says. */
	public static final class Console implements net.minecraft.commands.CommandSource {
		public final java.util.List<Component> messages = new java.util.ArrayList<>();

		@Override public void sendSystemMessage(Component message) { messages.add(message); }

		@Override public boolean acceptsSuccess() { return true; }

		@Override public boolean acceptsFailure() { return true; }

		@Override public boolean shouldInformAdmins() { return false; }

		public String lastKey() {
			if (messages.isEmpty()) return "<nothing>";
			return keyOf(messages.get(messages.size() - 1));
		}

		/** The translation key of a message or of its first translatable part (commands wrap failures in a red empty component). */
		private static String keyOf(Component c) {
			if (c.getContents() instanceof TranslatableContents t) return t.getKey();
			for (Component sibling : c.getSiblings()) {
				String k = keyOf(sibling);
				if (k != null) return k;
			}
			return c.getSiblings().isEmpty() ? c.getString() : null;
		}

		/** Run as the console. */
		public void run(GameTestHelper h, String command) {
			var server = h.getLevel().getServer();
			server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withSource(this), command);
		}

		/** Run as {@code player} (whatever permission they have). */
		public void runAs(ServerPlayer player, String command) {
			var server = player.level().getServer();
			server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withSource(this), command);
		}
	}

	public static boolean hasOverlay(OverlayPlayer p, String key) {
		return p.overlays.stream().anyMatch(c -> isKey(c, key));
	}

	private static boolean isKey(Component c, String key) {
		return c.getContents() instanceof TranslatableContents t && t.getKey().equals(key);
	}
}
