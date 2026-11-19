package dev.villageheart.heart.net;

import dev.villageheart.VillageHeart;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** The next guide tasks, for the corner list. At most three. */
public record GuideTasksPayload(List<String> taskIds) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<GuideTasksPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "guide_tasks"));
	public static final StreamCodec<ByteBuf, GuideTasksPayload> CODEC = StreamCodec.composite(
			HeartCodecs.ID.apply(ByteBufCodecs.list(3)), GuideTasksPayload::taskIds, GuideTasksPayload::new);

	public GuideTasksPayload { taskIds = List.copyOf(taskIds); }

	@Override public CustomPacketPayload.Type<GuideTasksPayload> type() { return TYPE; }
}
