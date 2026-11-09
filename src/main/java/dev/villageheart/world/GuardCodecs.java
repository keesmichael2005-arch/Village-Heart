package dev.villageheart.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.guard.GuardData;

/** Codec of the guard attachment (M18). DFU only, so it unit-tests with JsonOps. Field names are part of the save format. */
public final class GuardCodecs {
	private GuardCodecs() {}

	public static final Codec<GuardData> GUARD_DATA = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("wounded", false).forGetter(GuardData::wounded),
			Codec.LONG.optionalFieldOf("training_until", 0L).forGetter(GuardData::trainingUntil),
			Codec.INT.optionalFieldOf("ration", 0).forGetter(GuardData::ration)
	).apply(i, GuardData::new));
}
