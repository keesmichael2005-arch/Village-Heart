package dev.villageheart.build.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: the swap-hands key, while a blueprint is held, mirrors the site instead of swapping hands. */
public record MirrorBlueprintPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<MirrorBlueprintPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "mirror_blueprint"));
	public static final StreamCodec<ByteBuf, MirrorBlueprintPayload> CODEC = StreamCodec.unit(new MirrorBlueprintPayload());

	@Override public CustomPacketPayload.Type<MirrorBlueprintPayload> type() { return TYPE; }
}
