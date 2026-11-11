package dev.villageheart.build;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Rank;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.blueprint.Blueprint;
import dev.villageheart.core.blueprint.Blueprints;
import dev.villageheart.core.blueprint.TableTexts;
import dev.villageheart.core.heart.Permission;
import dev.villageheart.core.heart.Permissions;
import dev.villageheart.heart.HeartActions;
import dev.villageheart.heart.net.HeartActionResultPayload;
import dev.villageheart.heart.net.HeartNetwork;
import dev.villageheart.world.VillageClaims;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/**
 * Server side of the Builder's Table actions (plan 3.7). Every receiver calls one of these: rate limit first (the Heart's bucket), then the
 * open menu and its village, then the rank re-read from the current village ({@link Permission#START_BLUEPRINT}), the tier and the build
 * limit, then the action and a reply the screen shows in its footer. Nothing a client sends is trusted.
 */
public final class BuildersTableActions {
	private BuildersTableActions() {}

	private static final Map<UUID, Long> LAST_TAKE = new HashMap<>();

	public static void reset() { LAST_TAKE.clear(); }

	private static BuildersTableMenu menu(ServerPlayer player, int containerId) {
		return player.containerMenu instanceof BuildersTableMenu m && m.containerId == containerId && m.nearTable(player) && m.epochCurrent() ? m : null;
	}

	private static void reply(ServerPlayer player, int containerId, boolean ok, String suffix, List<String> args) {
		HeartNetwork.send(player, new HeartActionResultPayload(containerId, ok, TableTexts.key(suffix), TableTexts.english(suffix), args));
	}

	public static void take(ServerPlayer player, int containerId, String blueprintId) {
		var server = player.level().getServer();
		long now = server.getTickCount();
		if (!HeartActions.RATE.tryAcquire(player.getUUID(), now)) {
			reply(player, containerId, false, "too_fast", List.of());
			return;
		}
		BuildersTableMenu menu = menu(player, containerId);
		Optional<VillageState> village = VillageClaims.current(server);
		if (menu == null || village.isEmpty()) {
			reply(player, containerId, false, "stale", List.of());
			return;
		}
		Rank rank = village.get().rankOf(player.getUUID());
		if (!Permissions.allows(rank, Permission.START_BLUEPRINT)) {
			reply(player, containerId, false, "no_permission", List.of());
			return;
		}
		Optional<Blueprint> bp = Blueprints.byId(blueprintId);
		if (bp.isEmpty()) {
			reply(player, containerId, false, "unknown", List.of());
			return;
		}
		if (village.get().tier().compareTo(bp.get().tier()) < 0) {
			reply(player, containerId, false, "locked", List.of(bp.get().tier().displayName()));
			return;
		}
		if (village.get().builds().size() >= Balance.MAX_ACTIVE_BUILDS) {
			reply(player, containerId, false, "too_many", List.of());
			return;
		}
		Long last = LAST_TAKE.get(player.getUUID());
		if (last != null && now - last < Balance.BLUEPRINT_TAKE_COOLDOWN) {
			reply(player, containerId, false, "too_fast", List.of());
			return;
		}
		LAST_TAKE.put(player.getUUID(), now);
		ItemStack stack = BlueprintItem.of(bp.get());
		if (!player.getInventory().add(stack)) player.drop(stack, false);
		player.inventoryMenu.broadcastChanges(); // the open table menu has no inventory slots: tell the client now
		reply(player, containerId, true, "taken", List.of(bp.get().name()));
		menu.markDirty();
	}

	public static void cancel(ServerPlayer player, int containerId, UUID buildId) {
		var server = player.level().getServer();
		long now = server.getTickCount();
		if (!HeartActions.RATE.tryAcquire(player.getUUID(), now)) {
			reply(player, containerId, false, "too_fast", List.of());
			return;
		}
		BuildersTableMenu menu = menu(player, containerId);
		Optional<VillageState> village = VillageClaims.current(server);
		if (menu == null || village.isEmpty()) {
			reply(player, containerId, false, "stale", List.of());
			return;
		}
		if (!Permissions.allows(village.get().rankOf(player.getUUID()), Permission.START_BLUEPRINT)) {
			reply(player, containerId, false, "no_permission", List.of());
			return;
		}
		if (!BuildJobs.cancel(server, buildId)) {
			reply(player, containerId, false, "no_such_build", List.of());
			return;
		}
		reply(player, containerId, true, "cancelled", List.of());
		menu.markDirty();
	}

	public static void prioritise(ServerPlayer player, int containerId, UUID buildId) {
		var server = player.level().getServer();
		long now = server.getTickCount();
		if (!HeartActions.RATE.tryAcquire(player.getUUID(), now)) {
			reply(player, containerId, false, "too_fast", List.of());
			return;
		}
		BuildersTableMenu menu = menu(player, containerId);
		Optional<VillageState> village = VillageClaims.current(server);
		if (menu == null || village.isEmpty()) {
			reply(player, containerId, false, "stale", List.of());
			return;
		}
		if (!Permissions.allows(village.get().rankOf(player.getUUID()), Permission.START_BLUEPRINT)) {
			reply(player, containerId, false, "no_permission", List.of());
			return;
		}
		if (!BuildJobs.prioritise(server, buildId)) {
			reply(player, containerId, false, "no_such_build", List.of());
			return;
		}
		reply(player, containerId, true, "prioritised", List.of());
		menu.markDirty();
	}
}
