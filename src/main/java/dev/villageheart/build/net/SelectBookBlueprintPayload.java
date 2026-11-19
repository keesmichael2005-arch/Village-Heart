package dev.villageheart.build.net;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.heart.HeartLimits;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: arm the Blueprint Book in {@code hand} (0 main, 1 off) with {@code blueprintId}. */
public record SelectBookBlueprintPayload(int hand, String blueprintId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SelectBookBlueprintPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "select_book_blueprint"));
	public static final StreamCodec<ByteBuf, SelectBookBlueprintPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SelectBookBlueprintPayload::hand,
			ByteBufCodecs.stringUtf8(HeartLimits.MAX_ID), SelectBookBlueprintPayload::blueprintId,
			SelectBookBlueprintPayload::new);

	@Override public CustomPacketPayload.Type<SelectBookBlueprintPayload> type() { return TYPE; }
}
