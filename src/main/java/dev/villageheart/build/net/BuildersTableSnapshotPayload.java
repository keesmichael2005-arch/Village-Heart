package dev.villageheart.build.net;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.blueprint.TableSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the latest snapshot for the open Builder's Table screen {@code containerId}. */
public record BuildersTableSnapshotPayload(int containerId, TableSnapshot snapshot) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<BuildersTableSnapshotPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "builders_table_snapshot"));
	public static final StreamCodec<ByteBuf, BuildersTableSnapshotPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, BuildersTableSnapshotPayload::containerId,
			TableCodecs.SNAPSHOT, BuildersTableSnapshotPayload::snapshot,
			BuildersTableSnapshotPayload::new);

	@Override public CustomPacketPayload.Type<BuildersTableSnapshotPayload> type() { return TYPE; }
}
