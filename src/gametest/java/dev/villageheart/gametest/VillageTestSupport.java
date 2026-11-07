package dev.villageheart.gametest;

import com.mojang.authlib.GameProfile;
import dev.villageheart.block.ModBlocks;
import dev.villageheart.world.VillageClaims;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.gamerules.GameRules;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Helpers for GameTests that touch the (global, one-per-world) village claim.
 * Every such test must declare its own test environment so vanilla runs it in its own batch,
 * and must call {@link #reset} first and {@link #done} last.
 */
public final class VillageTestSupport {
	private static final List<ServerPlayer> PLAYERS = new ArrayList<>();

	private VillageTestSupport() {}

	/** Clear the claim, leftover players, leftover emeralds, and restore gamerules. */
	public static void reset(GameTestHelper h) {
		MinecraftServer server = h.getLevel().getServer();
		removePlayers(server);
		VillageClaims.data(server).clear();
		h.getLevel().getGameRules().set(GameRules.BLOCK_DROPS, true, server);
		purgePoi(h);
		for (ItemEntity e : h.getEntities(EntityType.ITEM)) {
			if (e.getItem().is(Items.EMERALD)) e.discard();
		}
	}

	/**
	 * Bells (and their POIs) from other tests, including failed ones that never cleaned up, can sit within the
	 * 16-block claim range of a neighbouring test structure. Each claim test runs alone in its own batch, so it is
	 * safe to drop every POI around the test before it starts.
	 */
	private static void purgePoi(GameTestHelper h) {
		var poi = h.getLevel().getPoiManager();
		BlockPos centre = h.absolutePos(BlockPos.ZERO);
		for (var rec : poi.getInRange(t -> true, centre, 48, net.minecraft.world.entity.ai.village.poi.PoiManager.Occupancy.ANY).toList()) {
			BlockPos p = rec.getPos().immutable();
			if (h.getLevel().getBlockState(p).is(net.minecraft.world.level.block.Blocks.BELL)) {
				// vanilla removes the POI itself when the block goes; removing it here too logs "never registered"
				h.getLevel().setBlock(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
			} else {
				poi.remove(p);
			}
		}
	}

	private static void removePlayers(MinecraftServer server) {
		for (ServerPlayer p : PLAYERS) server.getPlayerList().remove(p);
		PLAYERS.clear();
	}

	/** A real ServerPlayer (not the mock helper, which is always creative) with its own name and UUID. */
	public static ServerPlayer player(GameTestHelper h, String name, GameType mode) {
		MinecraftServer server = h.getLevel().getServer();
		GameProfile profile = new GameProfile(UUID.randomUUID(), name);
		ServerPlayer p = new ServerPlayer(server, h.getLevel(), profile, ClientInformation.createDefault());
		Connection conn = new Connection(PacketFlow.SERVERBOUND);
		new EmbeddedChannel(conn);
		server.getPlayerList().placeNewPlayer(conn, p, CommonListenerCookie.createInitial(profile, false));
		p.setGameMode(mode);
		PLAYERS.add(p);
		if (p.isCreative() != (mode == GameType.CREATIVE)) {
			throw new IllegalStateException("player isCreative() does not match requested mode " + mode);
		}
		if (mode == GameType.SURVIVAL && p.hasInfiniteMaterials()) {
			throw new IllegalStateException("survival player has infinite materials");
		}
		return p;
	}

	/** Gives the player one Heart and places it at relBelow.above() (the click hits relBelow's top face... see plan). */
	public static void place(GameTestHelper h, ServerPlayer p, BlockPos relBelow) {
		ItemStack stack = new ItemStack(ModBlocks.VILLAGE_HEART_ITEM);
		p.setItemInHand(InteractionHand.MAIN_HAND, stack);
		h.placeAt(p, stack, relBelow, Direction.UP);
	}

	public static int emeralds(GameTestHelper h, BlockPos relPos, double radius) {
		int n = 0;
		for (ItemEntity e : h.getEntities(EntityType.ITEM, relPos, radius)) {
			if (e.getItem().is(Items.EMERALD)) n += e.getItem().getCount();
		}
		return n;
	}

	public static void done(GameTestHelper h) {
		reset(h);
		h.succeed();
	}
}
