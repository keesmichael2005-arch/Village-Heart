package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.heart.HeartAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server (M22): set what the market keeps, as the absolute pair (so a double click cannot drift). Any ints decode; the server's
 * gate rejects a value out of range with BAD_RESERVE.
 */
public record SetReservesPayload(int containerId, int foodDays, int itemReserve) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SetReservesPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_set_reserves"));
	public static final StreamCodec<ByteBuf, SetReservesPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SetReservesPayload::containerId,
			ByteBufCodecs.VAR_INT, SetReservesPayload::foodDays,
			ByteBufCodecs.VAR_INT, SetReservesPayload::itemReserve,
			SetReservesPayload::new);

	public HeartAction action() { return new HeartAction.SetReserves(foodDays, itemReserve); }

	@Override public CustomPacketPayload.Type<SetReservesPayload> type() { return TYPE; }
}
