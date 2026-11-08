package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.heart.HeartAction;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client to server: deposit every emerald the player carries ({@code withdraw == false}, the amount is ignored) or take {@code amount}
 * emeralds out of the treasury. Any int decodes; the server's gate rejects an amount outside 1..64 with BAD_AMOUNT.
 */
public record TreasuryActionPayload(int containerId, boolean withdraw, int amount) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<TreasuryActionPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_treasury"));
	public static final StreamCodec<ByteBuf, TreasuryActionPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, TreasuryActionPayload::containerId,
			ByteBufCodecs.BOOL, TreasuryActionPayload::withdraw,
			ByteBufCodecs.VAR_INT, TreasuryActionPayload::amount,
			TreasuryActionPayload::new);

	public HeartAction action() { return withdraw ? new HeartAction.Withdraw(amount) : new HeartAction.Deposit(); }

	@Override public CustomPacketPayload.Type<TreasuryActionPayload> type() { return TYPE; }
}
