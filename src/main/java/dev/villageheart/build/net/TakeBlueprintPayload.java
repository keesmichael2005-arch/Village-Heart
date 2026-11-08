package dev.villageheart.build.net;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.heart.HeartLimits;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: take a Blueprint item of {@code blueprintId} at the open Builder's Table. */
public record TakeBlueprintPayload(int containerId, String blueprintId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TakeBlueprintPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "take_blueprint"));
	public static final StreamCodec<ByteBuf, TakeBlueprintPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TakeBlueprintPayload::containerId,
			ByteBufCodecs.stringUtf8(HeartLimits.MAX_ID), TakeBlueprintPayload::blueprintId,
			TakeBlueprintPayload::new);

	@Override public CustomPacketPayload.Type<TakeBlueprintPayload> type() { return TYPE; }
}
