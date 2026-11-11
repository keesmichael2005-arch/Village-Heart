package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: pin a guard to a shift (0 auto, 1 day, 2 night, M18). The gate rejects any other int with BAD_SHIFT. */
public record SetGuardShiftPayload(int containerId, UUID villager, int choice) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SetGuardShiftPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_set_guard_shift"));
	public static final StreamCodec<ByteBuf, SetGuardShiftPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetGuardShiftPayload::containerId,
			UUIDUtil.STREAM_CODEC, SetGuardShiftPayload::villager,
			ByteBufCodecs.VAR_INT, SetGuardShiftPayload::choice,
			SetGuardShiftPayload::new);

	@Override public CustomPacketPayload.Type<SetGuardShiftPayload> type() { return TYPE; }
}
