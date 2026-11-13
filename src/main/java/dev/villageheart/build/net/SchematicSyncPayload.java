package dev.villageheart.build.net;

import dev.villageheart.VillageHeart;
import dev.villageheart.core.heart.HeartLimits;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server to client: the placed plans to draw while schematics are on. At most {@link #MAX} sites. */
public record SchematicSyncPayload(List<SchematicSyncPayload.Site> sites) implements CustomPacketPayload {
	public static final int MAX = 256;

	public record Site(String blueprintId, int x, int y, int z, int rotation, int length) {}

	public static final CustomPacketPayload.Type<SchematicSyncPayload> TYPE = new CustomPacketPayload.Type<>(
			Identifier.fromNamespaceAndPath(VillageHeart.MOD_ID, "schematic_sync"));
	private static final StreamCodec<ByteBuf, Site> SITE = StreamCodec.composite(
			ByteBufCodecs.stringUtf8(HeartLimits.MAX_ID), Site::blueprintId,
			ByteBufCodecs.VAR_INT, Site::x,
			ByteBufCodecs.VAR_INT, Site::y,
			ByteBufCodecs.VAR_INT, Site::z,
			ByteBufCodecs.VAR_INT, Site::rotation,
			ByteBufCodecs.VAR_INT, Site::length,
			Site::new);
	public static final StreamCodec<ByteBuf, SchematicSyncPayload> CODEC = StreamCodec.composite(
			SITE.apply(ByteBufCodecs.list(MAX)), SchematicSyncPayload::sites,
			SchematicSyncPayload::new);

	public SchematicSyncPayload {
		sites = List.copyOf(sites);
	}

	@Override public CustomPacketPayload.Type<SchematicSyncPayload> type() { return TYPE; }
}
