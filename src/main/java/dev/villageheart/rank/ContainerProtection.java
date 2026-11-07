package dev.villageheart.rank;

import dev.villageheart.block.MarkerBlock;
import dev.villageheart.block.ModBlocks;
import dev.villageheart.building.Buildings;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorldClaim;
import dev.villageheart.core.heart.Permission;
import dev.villageheart.core.heart.Permissions;
import dev.villageheart.core.rank.ContainerGuard;
import dev.villageheart.core.room.Pos;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.world.VillageSavedData;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The Visitor rule (M24, DESIGN section 2): a player below Resident can neither open nor break a container inside one of the
 * village's claimed buildings, nor break a registered building marker. Ops get no bypass. Also the Village Heart's break rule.
 * Server thread only. Not grief protection: hoppers, explosions, pistons and golems are out of scope (docs/DESIGN.md).
 */
public final class ContainerProtection {
	private ContainerProtection() {}

	/** At most one action-bar line per player per this many ticks, so holding right-click does not spam. */
	private static final int NOTICE_INTERVAL_TICKS = 20;
	private static final Map<String, Long> LAST_NOTICE = new HashMap<>();

	public static void register() {
		UseBlockCallback.EVENT.register(ContainerProtection::onUse);
		PlayerBlockBreakEvents.BEFORE.register(ContainerProtection::allowBreak);
		PlayerBlockBreakEvents.CANCELED.register(ContainerProtection::onBreakCancelled);
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents.SERVER_STOPPING.register(server -> LAST_NOTICE.clear());
	}

	// ---- open ---------------------------------------------------------------------------------------------------

	private static InteractionResult onUse(Player player, Level level, InteractionHand hand, BlockHitResult hit) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
		// Vanilla skips the block's own use when sneaking with an item in either hand (sneak-place against a chest).
		if (!player.isSpectator() && player.isSecondaryUseActive()
				&& (!player.getMainHandItem().isEmpty() || !player.getOffhandItem().isEmpty())) return InteractionResult.PASS;
		BlockPos pos = hit.getBlockPos();
		BlockState state = server.getBlockState(pos);
		if (!isContainerBlock(server, pos)) return InteractionResult.PASS;
		Optional<VillageState> village = VillageClaims.current(server.getServer());
		if (village.isEmpty()) return InteractionResult.PASS;
		if (Permissions.allows(village.get().rankOf(player.getUUID()), Permission.OPEN_CONTAINERS)) return InteractionResult.PASS;
		if (!isProtected(server, pos, state)) return InteractionResult.PASS;
		notice(sp, server, "message.villageheart.protect.container", village.get().ownerName());
		// A refused use with an item in hand (a disc on a jukebox) may have been predicted on the client.
		sp.containerMenu.sendAllDataToRemote();
		return InteractionResult.FAIL;
	}

	// ---- break --------------------------------------------------------------------------------------------------

	/** Only a rank with BREAK_HEART may break the claimed Heart; below OPEN_CONTAINERS no protected container or marker. */
	public static boolean allowBreak(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!(level instanceof ServerLevel server)) return true;
		if (state.is(ModBlocks.VILLAGE_HEART)) return allowHeartBreak(server, player, pos);
		boolean container = be instanceof Container && !be.isRemoved();
		boolean marker = state.getBlock() instanceof MarkerBlock;
		if (!container && !marker) return true;
		Optional<VillageState> village = VillageClaims.current(server.getServer());
		if (village.isEmpty()) return true;
		if (Permissions.allows(village.get().rankOf(player.getUUID()), Permission.OPEN_CONTAINERS)) return true;
		boolean protectedBlock;
		if (marker) {
			protectedBlock = ContainerGuard.isMarker(dimensionOf(server), new Pos(pos.getX(), pos.getY(), pos.getZ()),
					Buildings.guardAreas(server.getServer()));
		} else {
			protectedBlock = isProtected(server, pos, state);
		}
		if (!protectedBlock) return true;
		if (player instanceof ServerPlayer sp) notice(sp, server, "message.villageheart.protect.break", village.get().ownerName());
		return false;
	}

	private static boolean allowHeartBreak(ServerLevel server, Player player, BlockPos pos) {
		VillageSavedData data = VillageClaims.data(server.getServer());
		if (data.mayBreakHeart(VillageClaims.locate(server, pos), player.getUUID()) != WorldClaim.BreakDecision.DENIED_NOT_OWNER) return true;
		String owner = data.village().map(VillageState::ownerName).orElse("?");
		player.displayClientMessage(Component.translatable("message.villageheart.heart.not_owner", owner), true);
		return false;
	}

	/** The client may have predicted the break of one half of a double chest as a single chest: resend the partner. */
	private static void onBreakCancelled(Level level, Player player, BlockPos pos, BlockState state, @Nullable BlockEntity be) {
		if (!(level instanceof ServerLevel server) || !(player instanceof ServerPlayer sp)) return;
		if (!(state.getBlock() instanceof ChestBlock) || state.getValue(ChestBlock.TYPE) == ChestType.SINGLE) return;
		BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
		sp.connection.send(new ClientboundBlockUpdatePacket(other, server.getBlockState(other)));
		sp.connection.send(new ClientboundBlockUpdatePacket(pos, state));
	}

	// ---- what is protected --------------------------------------------------------------------------------------

	private static String dimensionOf(ServerLevel level) {
		return level.dimension().identifier().toString();
	}

	/** A loaded block entity that is a {@link Container} (the ender chest is personal and is not one). Never loads a chunk. */
	private static boolean isContainerBlock(ServerLevel level, BlockPos pos) {
		LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
		if (chunk == null) return false;
		BlockEntity be = chunk.getBlockEntity(pos);
		return be instanceof Container && !be.isRemoved();
	}

	/** True when the block, or the other half of a double chest, is inside a building of the village. */
	static boolean isProtected(ServerLevel level, BlockPos pos, BlockState state) {
		List<ContainerGuard.GuardArea> areas = Buildings.guardAreas(level.getServer());
		if (areas.isEmpty()) return false;
		String dim = dimensionOf(level);
		if (ContainerGuard.isProtected(dim, new Pos(pos.getX(), pos.getY(), pos.getZ()), areas)) return true;
		if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
			BlockPos other = ChestBlock.getConnectedBlockPos(pos, state);
			return ContainerGuard.isProtected(dim, new Pos(other.getX(), other.getY(), other.getZ()), areas);
		}
		return false;
	}

	private static void notice(ServerPlayer player, ServerLevel level, String key, String owner) {
		long now = level.getGameTime();
		String throttleKey = player.getUUID() + key;
		Long last = LAST_NOTICE.get(throttleKey);
		if (last != null && now - last < NOTICE_INTERVAL_TICKS && now >= last) return;
		LAST_NOTICE.put(throttleKey, now);
		player.displayClientMessage(Component.translatable(key, owner), true);
	}

	/** Tests: forget the notice throttle. */
	public static void resetForTests() { LAST_NOTICE.clear(); }
}
