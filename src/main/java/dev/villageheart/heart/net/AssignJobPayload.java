package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client to server: hire {@code villager} into {@code jobId}, optionally at one building (its marker position). */
public record AssignJobPayload(int containerId, UUID villager, String jobId, Optional<BlockPos> building)
		implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<AssignJobPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "heart_assign_job"));
	public static final StreamCodec<ByteBuf, AssignJobPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, AssignJobPayload::containerId,
			UUIDUtil.STREAM_CODEC, AssignJobPayload::villager,
			HeartCodecs.JOB_ID, AssignJobPayload::jobId,
			ByteBufCodecs.optional(BlockPos.STREAM_CODEC), AssignJobPayload::building,
			AssignJobPayload::new);

	@Override public CustomPacketPayload.Type<AssignJobPayload> type() { return TYPE; }
}
