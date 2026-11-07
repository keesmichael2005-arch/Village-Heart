package dev.villageheart.heart.net;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.heart.HeartAction;
import dev.villageheart.heart.HeartActions;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.heart.HeartFacts;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/** Payload registration and the server-side receivers of the Heart screen (common init). */
public final class HeartNetwork {
	private HeartNetwork() {}

	/** Where server-to-client payloads go. GameTests swap in a recorder. */
	@FunctionalInterface
	public interface Sender {
		void send(ServerPlayer player, CustomPacketPayload payload);
	}

	private static final Sender DEFAULT_SENDER = ServerPlayNetworking::send;
	private static Sender sender = DEFAULT_SENDER;

	public static void send(ServerPlayer player, CustomPacketPayload payload) { sender.send(player, payload); }

	/** Test hook: install a sender; pass null to restore the real one. Returns the previous one. */
	public static Sender setSenderForTest(Sender s) {
		Sender previous = sender;
		sender = s == null ? DEFAULT_SENDER : s;
		return previous;
	}

	public static void register() {
		ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
			HeartFacts.reset();
			HeartActions.reset();
			sender = DEFAULT_SENDER;
		});
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> HeartActions.forget(handler.getPlayer().getUUID()));
		PayloadTypeRegistry.playS2C().register(HeartSnapshotPayload.TYPE, HeartSnapshotPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(HeartActionResultPayload.TYPE, HeartActionResultPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(AssignJobPayload.TYPE, AssignJobPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(FireWorkerPayload.TYPE, FireWorkerPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(RecheckBuildingPayload.TYPE, RecheckBuildingPayload.CODEC);

		ServerPlayNetworking.registerGlobalReceiver(AssignJobPayload.TYPE, (p, ctx) -> HeartActions.handle(ctx.player(),
				p.containerId(), new HeartAction.Assign(p.villager(), p.jobId(), p.building().map(b -> location(ctx.player(), b)))));
		ServerPlayNetworking.registerGlobalReceiver(FireWorkerPayload.TYPE, (p, ctx) -> HeartActions.handle(ctx.player(),
				p.containerId(), new HeartAction.Fire(p.villager())));
		ServerPlayNetworking.registerGlobalReceiver(RecheckBuildingPayload.TYPE, (p, ctx) -> HeartActions.handle(ctx.player(),
				p.containerId(), new HeartAction.Recheck(location(ctx.player(), p.marker()))));
	}

	/**
	 * A position a client named, as a village location. Positions always mean "in the Heart's dimension": the client has no say
	 * about the dimension (the player may even stand in another one).
	 */
	static HeartLocation location(ServerPlayer player, BlockPos pos) {
		String dimension = VillageClaims.current(player.level().getServer()).map(VillageState::heart).map(HeartLocation::dimension)
				.orElseGet(() -> VillageClaims.locate(player.level(), pos).dimension());
		return new HeartLocation(dimension, pos.getX(), pos.getY(), pos.getZ());
	}
}
