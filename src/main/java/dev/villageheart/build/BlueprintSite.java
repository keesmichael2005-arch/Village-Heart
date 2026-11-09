package dev.villageheart.build;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.heart.net.HeartCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The site a Blueprint item shows: the ground block, the rotation (an ordinal of {@code Rotation4}), whether the server's check passed and
 * why not. A data component of the stack, so the client draws the outline from it (green when ok, red otherwise).
 */
public record BlueprintSite(BlockPos ground, int rotation, boolean ok, TextLine reason, int length) {
	/** A footprint site (M20): {@code length} 0. For a LINE blueprint, {@code ground} is the start and {@code length} 0 means only the start was clicked. */
	public BlueprintSite(BlockPos ground, int rotation, boolean ok, TextLine reason) {
		this(ground, rotation, ok, reason, 0);
	}

	private static final Codec<TextLine> TEXT_LINE = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("key").forGetter(TextLine::key),
			Codec.STRING.fieldOf("fallback").forGetter(TextLine::fallback),
			Codec.STRING.listOf().optionalFieldOf("args", java.util.List.of()).forGetter(TextLine::args)
	).apply(i, TextLine::new));

	public static final Codec<BlueprintSite> CODEC = RecordCodecBuilder.create(i -> i.group(
			BlockPos.CODEC.fieldOf("ground").forGetter(BlueprintSite::ground),
			Codec.intRange(0, 3).fieldOf("rotation").forGetter(BlueprintSite::rotation),
			Codec.BOOL.fieldOf("ok").forGetter(BlueprintSite::ok),
			TEXT_LINE.fieldOf("reason").forGetter(BlueprintSite::reason),
			Codec.intRange(0, 32).optionalFieldOf("length", 0).forGetter(BlueprintSite::length)
	).apply(i, BlueprintSite::new));

	public static final StreamCodec<ByteBuf, BlueprintSite> STREAM_CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, BlueprintSite::ground,
			ByteBufCodecs.VAR_INT, BlueprintSite::rotation,
			ByteBufCodecs.BOOL, BlueprintSite::ok,
			HeartCodecs.BUILD_TEXT_LINE, BlueprintSite::reason,
			ByteBufCodecs.VAR_INT, BlueprintSite::length,
			BlueprintSite::new);

	public dev.villageheart.core.blueprint.Rotation4 rotationEnum() { return dev.villageheart.core.blueprint.Rotation4.values()[rotation]; }
}
