package dev.villageheart.build;

import dev.villageheart.VillageHeart;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.ItemStack;

/**
 * A flat creative dimension entered from the Builder's Table. The survival inventory is put back on the way out, and anything gathered in
 * the plot is destroyed.
 */
public final class DesignWorld {
	public static final ResourceKey<Level> DIMENSION = ResourceKey.create(Registries.DIMENSION, Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "design"));

	private static final Map<UUID, Held> HELD = new HashMap<>();

	private record Held(ResourceKey<Level> dimension, BlockPos pos, GameType mode, ItemStack[] items) {}

	private DesignWorld() {}

	public static boolean available(net.minecraft.server.MinecraftServer server) {
		return server.getLevel(DIMENSION) != null;
	}

	/** @return false when the dimension is not there */
	public static boolean enter(ServerPlayer player) {
		ServerLevel plot = player.level().getServer().getLevel(DIMENSION);
		if (plot == null) return false;
		UUID id = player.getUUID();
		ItemStack[] items = new ItemStack[player.getInventory().getContainerSize()];
		for (int i = 0; i < items.length; i++) items[i] = player.getInventory().getItem(i).copy();
		HELD.put(id, new Held(player.level().dimension(), player.blockPosition(), player.gameMode.getGameModeForPlayer(), items));
		player.getInventory().clearContent();
		player.setGameMode(GameType.CREATIVE);
		int slot = Math.floorMod(id.hashCode(), 64);
		player.teleportTo(plot, slot * 64 + 0.5, 4, 0.5, java.util.Set.of(), 0, 0, true);
		return true;
	}

	public static boolean leave(ServerPlayer player) {
		Held held = HELD.remove(player.getUUID());
		if (held == null) return false;
		player.getInventory().clearContent();
		for (int i = 0; i < held.items.length; i++) player.getInventory().setItem(i, held.items[i].copy());
		player.setGameMode(held.mode);
		ServerLevel back = player.level().getServer().getLevel(held.dimension);
		if (back != null) player.teleportTo(back, held.pos.getX() + 0.5, held.pos.getY(), held.pos.getZ() + 0.5, java.util.Set.of(), player.getYRot(), player.getXRot(), true);
		return true;
	}
}
