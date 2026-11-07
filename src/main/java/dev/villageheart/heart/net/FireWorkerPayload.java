package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: fire the worker {@code villager}. */
public record FireWorkerPayload(int containerId, UUID villager) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<FireWorkerPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_fire_worker"));
	public static final StreamCodec<ByteBuf, FireWorkerPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FireWorkerPayload::containerId,
			UUIDUtil.STREAM_CODEC, FireWorkerPayload::villager,
			FireWorkerPayload::new);

	@Override public CustomPacketPayload.Type<FireWorkerPayload> type() { return TYPE; }
}
