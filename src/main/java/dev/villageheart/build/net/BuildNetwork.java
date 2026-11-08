package dev.villageheart.build.net;

import dev.villageheart.build.BuildersTableActions;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;

/** Payload registration and the server-side receivers of the Builder's Table (common init). */
public final class BuildNetwork {
	private BuildNetwork() {}

	public static void register() {
		PayloadTypeRegistry.playS2C().register(BuildersTableSnapshotPayload.TYPE, BuildersTableSnapshotPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(TakeBlueprintPayload.TYPE, TakeBlueprintPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(CancelBuildPayload.TYPE, CancelBuildPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(TakeBlueprintPayload.TYPE,
				(p, ctx) -> BuildersTableActions.take(ctx.player(), p.containerId(), p.blueprintId()));
		ServerPlayNetworking.registerGlobalReceiver(CancelBuildPayload.TYPE,
				(p, ctx) -> BuildersTableActions.cancel(ctx.player(), p.containerId(), p.buildId()));
	}
}
