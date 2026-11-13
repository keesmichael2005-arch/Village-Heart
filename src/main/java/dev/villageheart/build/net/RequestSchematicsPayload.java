package dev.villageheart.build.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: send the placed plans so the schematic outlines can be drawn. */
public record RequestSchematicsPayload() implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<RequestSchematicsPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "request_schematics"));
	public static final StreamCodec<ByteBuf, RequestSchematicsPayload> CODEC = StreamCodec.unit(new RequestSchematicsPayload());

	@Override public CustomPacketPayload.Type<RequestSchematicsPayload> type() { return TYPE; }
}
