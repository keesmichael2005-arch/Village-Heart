package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: check the building at {@code marker} now. */
public record RecheckBuildingPayload(int containerId, BlockPos marker) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RecheckBuildingPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_recheck_building"));
	public static final StreamCodec<ByteBuf, RecheckBuildingPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RecheckBuildingPayload::containerId,
			BlockPos.STREAM_CODEC, RecheckBuildingPayload::marker,
			RecheckBuildingPayload::new);

	@Override public CustomPacketPayload.Type<RecheckBuildingPayload> type() { return TYPE; }
}
