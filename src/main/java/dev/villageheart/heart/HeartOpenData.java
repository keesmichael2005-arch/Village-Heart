package dev.villageheart.heart;

import dev.villageheart.core.heart.HeartSnapshot;
import dev.villageheart.heart.net.HeartCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.StreamCodec;

/** The opening data of the Heart screen: which Heart, and the first snapshot, so the first frame is complete. */
public record HeartOpenData(BlockPos heart, HeartSnapshot snapshot) {
	public static final StreamCodec<ByteBuf, HeartOpenData> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, HeartOpenData::heart,
			HeartCodecs.SNAPSHOT, HeartOpenData::snapshot,
			HeartOpenData::new);
}
