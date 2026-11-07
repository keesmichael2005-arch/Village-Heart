package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.heart.HeartSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the latest snapshot for the open Heart screen {@code containerId}. */
public record HeartSnapshotPayload(int containerId, HeartSnapshot snapshot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HeartSnapshotPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_snapshot"));
	public static final StreamCodec<ByteBuf, HeartSnapshotPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HeartSnapshotPayload::containerId,
			HeartCodecs.SNAPSHOT, HeartSnapshotPayload::snapshot,
			HeartSnapshotPayload::new);

	@Override public CustomPacketPayload.Type<HeartSnapshotPayload> type() { return TYPE; }
}
