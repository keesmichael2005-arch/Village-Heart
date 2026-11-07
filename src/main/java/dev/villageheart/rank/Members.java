package dev.villageheart.rank;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.Rejection;
import dev.villageheart.core.rank.RankRules;
import dev.villageheart.heart.HeartFacts;
import dev.villageheart.heart.HeartMenu;
import dev.villageheart.world.VillageClaims;
import java.util.Optional;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.CompoundContainer;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

/**
 * The glue of ranks (M24): the village's name book is kept up to date on join and leave, and rank changes and ownership transfers
 * (from the Heart screen and from the op commands alike) all go through here: validated with {@link RankRules}, applied in one
 * {@code update}, then caches are invalidated, open screens refreshed and the target told. Server thread only.
 */
public final class Members {
	private Members() {}

	/** What happened to a requested change. */
	public record Outcome(Optional<Rejection> rejected, boolean changed, String targetName, Rank newRank) {
		public boolean ok() { return rejected.isEmpty(); }

		static Outcome rejected(Rejection r) { return new Outcome(Optional.of(r), false, "", Rank.VISITOR); }
	}

	public static void register() {
		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(server, handler.getPlayer()));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onJoin(server, handler.getPlayer()));
	}

	/** While a claim exists, remember the player's current name and when they were last seen (joins and leaves). */
	public static void onJoin(MinecraftServer server, ServerPlayer player) {
		if (VillageClaims.current(server).isEmpty()) return;
		long now = server.overworld().getGameTime();
		VillageClaims.data(server).update(v -> v.seen(player.getUUID(), player.getGameProfile().name(), now));
	}

	// ---- names -----------------------------------------------------------------------------------

	@Nullable
	public static ServerPlayer online(MinecraftServer server, UUID id) {
		return server.getPlayerList().getPlayer(id);
	}

	/** The online profile name, else the village's last known name, else the first 8 hex digits of the UUID. */
	public static String displayName(MinecraftServer server, VillageState state, UUID id) {
		ServerPlayer p = online(server, id);
		if (p != null) return p.getGameProfile().name();
		String known = state.name(id);
		return known.isEmpty() ? id.toString().substring(0, 8) : known;
	}

	public static String rankLabel(Rank rank) {
		return switch (rank) {
			case OWNER -> "Owner";
			case STEWARD -> "Steward";
			case RESIDENT -> "Resident";
			case VISITOR -> "Visitor";
		};
	}

	static Component rankComponent(Rank rank) {
		return Component.translatable("gui.villageheart.heart.rank." + rank.name().toLowerCase(java.util.Locale.ROOT));
	}

	// ---- changes ---------------------------------------------------------------------------------

	/** Give {@code target} a rank (never OWNER). Validated, applied, announced. */
	public static Outcome setRank(MinecraftServer server, String actorName, UUID target, Rank rank) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return Outcome.rejected(Rejection.NO_VILLAGE);
		VillageState state = village.get();
		ServerPlayer targetPlayer = online(server, target);
		Optional<Rejection> rejected = RankRules.setRank(state, target, rank, targetPlayer != null);
		if (rejected.isPresent()) return Outcome.rejected(rejected.get());
		String name = displayName(server, state, target);
		if (state.rankOf(target) == rank) return new Outcome(Optional.empty(), false, name, rank);
		long now = server.overworld().getGameTime();
		String profileName = targetPlayer == null ? "" : targetPlayer.getGameProfile().name();
		VillageClaims.data(server).update(v -> v.withRank(target, profileName, rank, now));
		changed(server);
		VillageHeart.LOG.info("{} made {} a {}", actorName, name, rank);
		if (targetPlayer != null) {
			targetPlayer.sendSystemMessage(Component.translatable("message.villageheart.rank.you_are", actorName, rankComponent(rank)));
			if (rank == Rank.VISITOR && readsProtectedContainer(targetPlayer)) targetPlayer.closeContainer();
		}
		return new Outcome(Optional.empty(), true, name, rank);
	}

	/**
	 * Give the village to {@code target}, a Steward or Resident ({@code expected}: the rank the actor's screen showed). With
	 * {@code promoteVisitor} (the op command) a Visitor is made a Resident first, in the same update.
	 */
	public static Outcome transfer(MinecraftServer server, String actorName, UUID actor, UUID target, Optional<Rank> expected,
			boolean promoteVisitor) {
		Optional<VillageState> village = VillageClaims.current(server);
		if (village.isEmpty()) return Outcome.rejected(Rejection.NO_VILLAGE);
		VillageState state = village.get();
		ServerPlayer targetPlayer = online(server, target);
		String name = displayName(server, state, target);
		String profileName = targetPlayer == null ? "" : targetPlayer.getGameProfile().name();
		long now = server.overworld().getGameTime();
		VillageState prepared = state;
		if (promoteVisitor && state.rankOf(target) == Rank.VISITOR) {
			Optional<Rejection> promote = RankRules.setRank(state, target, Rank.RESIDENT, targetPlayer != null);
			if (promote.isPresent()) return Outcome.rejected(promote.get());
			prepared = state.withRank(target, profileName, Rank.RESIDENT, now);
		}
		Optional<Rejection> rejected = RankRules.transfer(prepared, actor, target, expected);
		if (rejected.isPresent()) return Outcome.rejected(rejected.get());
		UUID oldOwner = state.owner();
		String oldName = state.ownerName();
		boolean promote = prepared != state;
		VillageClaims.data(server).update(v -> {
			VillageState base = promote ? v.withRank(target, profileName, Rank.RESIDENT, now) : v;
			return base.transferOwnership(target, now);
		});
		changed(server);
		VillageHeart.LOG.info("{} gave the village of {} to {}", actorName, oldName, name);
		if (targetPlayer != null) {
			targetPlayer.sendSystemMessage(Component.translatable("message.villageheart.rank.you_own", actorName));
		}
		ServerPlayer old = online(server, oldOwner);
		if (old != null && old != targetPlayer && !oldOwner.equals(actor)) {
			old.sendSystemMessage(Component.translatable("message.villageheart.rank.you_were_given", name));
		}
		return new Outcome(Optional.empty(), true, name, Rank.OWNER);
	}

	private static void changed(MinecraftServer server) {
		HeartFacts.invalidate();
		HeartMenu.markAllDirty(server);
	}

	// ---- closing a protected container after a demotion -----------------------------------------------

	/**
	 * True when the player's open menu reads a container inside one of the village's buildings: some slot's container is a block
	 * entity at a protected position, or a double chest one of whose halves is. A crafting table or a villager trade does not.
	 */
	static boolean readsProtectedContainer(ServerPlayer player) {
		if (player.containerMenu == player.inventoryMenu || !(player.level() instanceof ServerLevel level)) return false;
		for (Slot slot : player.containerMenu.slots) {
			Container c = slot.container;
			if (c == player.getInventory()) continue;
			if (c instanceof BlockEntity be) {
				if (ContainerProtection.isProtected(level, be.getBlockPos(), be.getBlockState())) return true;
			} else if (c instanceof CompoundContainer cc) {
				if (compoundIsProtected(level, player.blockPosition(), cc)) return true;
			}
		}
		return false;
	}

	/** Finds the halves of a double chest among the chests near the player (rare: only on a demotion). */
	private static boolean compoundIsProtected(ServerLevel level, BlockPos around, CompoundContainer cc) {
		int r = 9;
		for (int dx = -r; dx <= r; dx++) for (int dy = -r; dy <= r; dy++) for (int dz = -r; dz <= r; dz++) {
			BlockPos p = around.offset(dx, dy, dz);
			LevelChunk chunk = level.getChunkSource().getChunkNow(p.getX() >> 4, p.getZ() >> 4);
			if (chunk == null) continue;
			if (chunk.getBlockEntity(p) instanceof ChestBlockEntity chest && cc.contains(chest)
					&& ContainerProtection.isProtected(level, p, chest.getBlockState())) return true;
		}
		return false;
	}
}
