package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The guide's next task changed. An empty id means the book is finished. */
public record GuideToastPayload(String taskId) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GuideToastPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "guide_toast"));
	public static final StreamCodec<ByteBuf, GuideToastPayload> CODEC = StreamCodec.composite(
			HeartCodecs.ID, GuideToastPayload::taskId, GuideToastPayload::new);

	@Override public CustomPacketPayload.Type<GuideToastPayload> type() { return TYPE; }
}
