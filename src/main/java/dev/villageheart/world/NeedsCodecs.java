package dev.villageheart.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.needs.Diet;

/** Codec of the diet attachment (DFU only, unit-tested with JsonOps). Field names are part of the save format. */
public final class NeedsCodecs {
	private NeedsCodecs() {}

	private record Raw(long claim, int hungryDays, long lastDinnerDay, int lastNutrition) {}

	private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.optionalFieldOf("claim", 0L).forGetter(Raw::claim),
			Codec.INT.optionalFieldOf("hungry_days", 0).forGetter(Raw::hungryDays),
			Codec.LONG.optionalFieldOf("last_dinner_day", -1L).forGetter(Raw::lastDinnerDay),
			Codec.INT.optionalFieldOf("last_nutrition", 0).forGetter(Raw::lastNutrition)
	).apply(i, Raw::new));

	public static final Codec<Diet> DIET = RAW.flatXmap(r -> {
		try {
			return DataResult.success(new Diet(r.claim(), r.hungryDays(), r.lastDinnerDay(), r.lastNutrition()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid diet: " + e.getMessage());
		}
	}, d -> DataResult.success(new Raw(d.claim(), d.hungryDays(), d.lastDinnerDay(), d.lastNutrition())));
}
