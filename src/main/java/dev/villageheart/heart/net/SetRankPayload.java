package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: give {@code player} the rank with ordinal {@code rank} (any int decodes; the server validates it). */
public record SetRankPayload(int containerId, UUID player, int rank) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SetRankPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_set_rank"));
	public static final StreamCodec<ByteBuf, SetRankPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetRankPayload::containerId,
			UUIDUtil.STREAM_CODEC, SetRankPayload::player,
			ByteBufCodecs.VAR_INT, SetRankPayload::rank,
			SetRankPayload::new);

	@Override public CustomPacketPayload.Type<SetRankPayload> type() { return TYPE; }
}
