package dev.villageheart.build;

import dev.villageheart.build.net.TableCodecs;
import dev.villageheart.core.blueprint.TableSnapshot;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/** The opening data of the Builder's Table screen: which table, and the first snapshot. */
public record BuildersTableOpenData(BlockPos table, TableSnapshot snapshot) {
	public static final StreamCodec<ByteBuf, BuildersTableOpenData> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, BuildersTableOpenData::table,
			TableCodecs.SNAPSHOT, BuildersTableOpenData::snapshot,
			BuildersTableOpenData::new);
}
