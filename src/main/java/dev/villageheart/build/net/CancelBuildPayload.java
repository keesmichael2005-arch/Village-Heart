package dev.villageheart.build.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: cancel the build {@code buildId} (its blueprint is not refunded, placed blocks stay). */
public record CancelBuildPayload(int containerId, UUID buildId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<CancelBuildPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "cancel_build"));
	public static final StreamCodec<ByteBuf, CancelBuildPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, CancelBuildPayload::containerId,
			UUIDUtil.STREAM_CODEC, CancelBuildPayload::buildId,
			CancelBuildPayload::new);

	@Override public CustomPacketPayload.Type<CancelBuildPayload> type() { return TYPE; }
}
