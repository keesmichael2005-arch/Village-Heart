package dev.villageheart.build.net;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.TableSnapshot;
import dev.villageheart.core.blueprint.TableSnapshot.TableBlueprint;
import dev.villageheart.core.blueprint.TableSnapshot.TableBuild;
import dev.villageheart.core.blueprint.TableSnapshot.TableMaterial;
import dev.villageheart.core.heart.HeartLimits;
import dev.villageheart.heart.net.HeartCodecs;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** The wire format of the Builder's Table snapshot: bounded strings and lists, like the Heart snapshot. */
public final class TableCodecs {
	private TableCodecs() {}

	private static <T> StreamCodec<ByteBuf, T> codec(Function<ByteBuf, T> decode, BiConsumer<ByteBuf, T> encode) {
		return new StreamCodec<>() {
			@Override public T decode(ByteBuf buf) { return decode.apply(buf); }

			@Override public void encode(ByteBuf buf, T value) { encode.accept(buf, value); }
		};
	}

	private static <E extends Enum<E>> StreamCodec<ByteBuf, E> enumCodec(Class<E> type) {
		E[] values = type.getEnumConstants();
		return codec(buf -> {
			int i = ByteBufCodecs.VAR_INT.decode(buf);
			if (i < 0 || i >= values.length) throw new DecoderException("bad " + type.getSimpleName() + " ordinal " + i);
			return values[i];
		}, (buf, e) -> ByteBufCodecs.VAR_INT.encode(buf, e.ordinal()));
	}

	private static final StreamCodec<ByteBuf, Rank> RANK = enumCodec(Rank.class);
	private static final StreamCodec<ByteBuf, Tier> TIER = enumCodec(Tier.class);

	public static final StreamCodec<ByteBuf, TableMaterial> MATERIAL = codec(
			buf -> new TableMaterial(HeartCodecs.NAME.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf)),
			(buf, m) -> {
				HeartCodecs.NAME.encode(buf, m.name());
				ByteBufCodecs.VAR_INT.encode(buf, m.needed());
				ByteBufCodecs.VAR_LONG.encode(buf, m.have());
			});

	private static final StreamCodec<ByteBuf, List<TableMaterial>> MATERIALS = MATERIAL.apply(ByteBufCodecs.list(TableSnapshot.MAX_MATERIALS));

	public static final StreamCodec<ByteBuf, TableBlueprint> BLUEPRINT = codec(
			buf -> new TableBlueprint(HeartCodecs.ID.decode(buf), ByteBufCodecs.BOOL.decode(buf), ByteBufCodecs.VAR_INT.decode(buf),
					ByteBufCodecs.VAR_INT.decode(buf), ByteBufCodecs.VAR_INT.decode(buf), MATERIALS.decode(buf)),
			(buf, b) -> {
				HeartCodecs.ID.encode(buf, b.id());
				ByteBufCodecs.BOOL.encode(buf, b.locked());
				ByteBufCodecs.VAR_INT.encode(buf, b.width());
				ByteBufCodecs.VAR_INT.encode(buf, b.height());
				ByteBufCodecs.VAR_INT.encode(buf, b.depth());
				MATERIALS.encode(buf, b.materials());
			});

	public static final StreamCodec<ByteBuf, TableBuild> BUILD = codec(
			buf -> new TableBuild(UUIDUtil.STREAM_CODEC.decode(buf), HeartCodecs.BUILD_ROW.decode(buf)),
			(buf, b) -> {
				UUIDUtil.STREAM_CODEC.encode(buf, b.id());
				HeartCodecs.BUILD_ROW.encode(buf, b.row());
			});

	private static final StreamCodec<ByteBuf, List<TableBlueprint>> BLUEPRINTS = BLUEPRINT.apply(ByteBufCodecs.list(TableSnapshot.MAX_BLUEPRINTS));
	private static final StreamCodec<ByteBuf, List<TableBuild>> BUILDS = BUILD.apply(ByteBufCodecs.list(HeartLimits.MAX_BUILDS));

	public static final StreamCodec<ByteBuf, TableSnapshot> SNAPSHOT = codec(
			buf -> new TableSnapshot(RANK.decode(buf), TIER.decode(buf), BLUEPRINTS.decode(buf), BUILDS.decode(buf)),
			(buf, s) -> {
				RANK.encode(buf, s.viewerRank());
				TIER.encode(buf, s.tier());
				BLUEPRINTS.encode(buf, s.blueprints());
				BUILDS.encode(buf, s.builds());
			});
}
