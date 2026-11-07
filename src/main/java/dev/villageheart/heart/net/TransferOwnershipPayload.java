package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: give the village to {@code newOwner}; {@code expectedRank} is the rank the sender's screen showed for them. */
public record TransferOwnershipPayload(int containerId, UUID newOwner, int expectedRank) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TransferOwnershipPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_transfer_ownership"));
	public static final StreamCodec<ByteBuf, TransferOwnershipPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TransferOwnershipPayload::containerId,
			UUIDUtil.STREAM_CODEC, TransferOwnershipPayload::newOwner,
			ByteBufCodecs.VAR_INT, TransferOwnershipPayload::expectedRank,
			TransferOwnershipPayload::new);

	@Override public CustomPacketPayload.Type<TransferOwnershipPayload> type() { return TYPE; }
}
