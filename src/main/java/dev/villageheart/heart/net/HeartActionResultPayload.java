package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server to client: the outcome of the last action, shown in the screen's footer. {@code key} and {@code fallback} make a
 * translatable-with-fallback text; {@code args} fill its {@code %s}.
 */
public record HeartActionResultPayload(int containerId, boolean ok, String key, String fallback, List<String> args)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<HeartActionResultPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_action_result"));
	public static final StreamCodec<ByteBuf, HeartActionResultPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, HeartActionResultPayload::containerId,
			ByteBufCodecs.BOOL, HeartActionResultPayload::ok,
			HeartCodecs.KEY, HeartActionResultPayload::key,
			HeartCodecs.TEXT, HeartActionResultPayload::fallback,
			HeartCodecs.ARGS, HeartActionResultPayload::args,
			HeartActionResultPayload::new);

	public HeartActionResultPayload { args = List.copyOf(args); }

	@Override public CustomPacketPayload.Type<HeartActionResultPayload> type() { return TYPE; }
}
