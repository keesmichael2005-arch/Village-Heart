package dev.villageheart.build.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: build this one next (higher priority than every other build). */
public record PrioritiseBuildPayload(int containerId, UUID buildId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<PrioritiseBuildPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "prioritise_build"));
	public static final StreamCodec<ByteBuf, PrioritiseBuildPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, PrioritiseBuildPayload::containerId,
			UUIDUtil.STREAM_CODEC, PrioritiseBuildPayload::buildId,
			PrioritiseBuildPayload::new);

	@Override public CustomPacketPayload.Type<PrioritiseBuildPayload> type() { return TYPE; }
}
