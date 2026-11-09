package dev.villageheart.world;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.mine.AwayProgress;
import dev.villageheart.core.mine.AwayState;
import dev.villageheart.core.mine.AwayState.AwayTrip;
import dev.villageheart.core.mine.AwayState.PriorFlags;
import dev.villageheart.core.mine.SurfaceReason;
import dev.villageheart.core.room.Pos;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Codec of the away attachment (M16). DFU only, so it unit-tests with JsonOps. Field names are part of the save format. A trip that cannot
 * be read decodes as "no trip" with {@code broken} set: the repair then gives the villager the vanilla flags back (it must never stay
 * invisible, frozen and invulnerable because one field was bad).
 */
public final class AwayCodecs {
	private AwayCodecs() {}

	private static final Codec<Pos> POS = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("x").forGetter(Pos::x),
			Codec.INT.fieldOf("y").forGetter(Pos::y),
			Codec.INT.fieldOf("z").forGetter(Pos::z)
	).apply(i, Pos::new));

	private static final Codec<PriorFlags> PRIOR = RecordCodecBuilder.create(i -> i.group(
			Codec.BOOL.optionalFieldOf("invisible", false).forGetter(PriorFlags::invisible),
			Codec.BOOL.optionalFieldOf("invulnerable", false).forGetter(PriorFlags::invulnerable),
			Codec.BOOL.optionalFieldOf("silent", false).forGetter(PriorFlags::silent),
			Codec.BOOL.optionalFieldOf("no_ai", false).forGetter(PriorFlags::noAi),
			Codec.BOOL.optionalFieldOf("custom_name_visible", false).forGetter(PriorFlags::customNameVisible)
	).apply(i, PriorFlags::new));

	private static final Codec<AwayTrip> TRIP = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("job").forGetter(AwayTrip::jobId),
			VillageCodecs.HEART_LOCATION.fieldOf("site").forGetter(AwayTrip::site),
			POS.fieldOf("surface").forGetter(AwayTrip::surface),
			POS.fieldOf("hold").forGetter(AwayTrip::hold),
			Codec.LONG.fieldOf("since").forGetter(AwayTrip::since),
			PRIOR.fieldOf("prior").forGetter(AwayTrip::prior)
	).apply(i, AwayTrip::new));

	/** A trip, or the marker that a saved one could not be read. */
	private record Slot(Optional<AwayTrip> trip, boolean broken) {
		static final Slot EMPTY = new Slot(Optional.empty(), false);
	}

	private static final Codec<Slot> SLOT = Codec.of(new com.mojang.serialization.Encoder<Slot>() {
		@Override
		public <T> DataResult<T> encode(Slot s, DynamicOps<T> ops, T prefix) {
			return s.trip().isPresent() ? TRIP.encode(s.trip().get(), ops, prefix) : DataResult.success(prefix);
		}
	}, new com.mojang.serialization.Decoder<Slot>() {
		@Override
		public <T> DataResult<Pair<Slot, T>> decode(DynamicOps<T> ops, T input) {
			DataResult<AwayTrip> r = TRIP.parse(ops, input);
			return DataResult.success(Pair.of(r.result().map(t -> new Slot(Optional.of(t), false)).orElse(new Slot(Optional.empty(), true)), input));
		}
	});

	private static final Codec<SurfaceReason> REASON = Codec.STRING.comapFlatMap(s -> {
		try {
			return DataResult.success(SurfaceReason.valueOf(s.toUpperCase(Locale.ROOT)));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Unknown surface reason: " + s);
		}
	}, r -> r.name().toLowerCase(Locale.ROOT));

	private record Raw(Slot slot, double progress, Optional<dev.villageheart.core.HeartLocation> progressShaft, long rolls,
			Map<String, Integer> pending, Optional<SurfaceReason> lastReason, int unknownSteps, long doneDay, int doneToday) {}

	private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
			SLOT.optionalFieldOf("trip", Slot.EMPTY).forGetter(Raw::slot),
			Codec.DOUBLE.optionalFieldOf("progress", 0.0).forGetter(Raw::progress),
			VillageCodecs.HEART_LOCATION.optionalFieldOf("progress_shaft").forGetter(Raw::progressShaft),
			Codec.LONG.optionalFieldOf("rolls", 0L).forGetter(Raw::rolls),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("pending", Map.of()).forGetter(Raw::pending),
			REASON.optionalFieldOf("last_reason").forGetter(Raw::lastReason),
			Codec.INT.optionalFieldOf("unknown_steps", 0).forGetter(Raw::unknownSteps),
			// M17: absent in M16 saves (-1 = never counted); omitted again while at the default, so M16's pinned names are unchanged
			Codec.LONG.optionalFieldOf("done_day", -1L).forGetter(Raw::doneDay),
			Codec.INT.optionalFieldOf("done_today", 0).forGetter(Raw::doneToday)
	).apply(i, Raw::new));

	public static final Codec<AwayState> AWAY_STATE = RAW.flatXmap(r -> {
		try {
			return DataResult.success(new AwayState(r.slot().trip(), new AwayProgress(Math.max(0.0, r.progress())), r.progressShaft(),
					Math.max(0L, r.rolls()), r.pending(), r.lastReason(), Math.max(0, r.unknownSteps()), r.slot().broken(), r.doneDay(), Math.max(0, r.doneToday())));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid away state: " + e.getMessage());
		}
	}, s -> DataResult.success(new Raw(new Slot(s.trip(), false), s.progress().units(), s.progressShaft(), s.rolls(), s.pending(),
			s.lastReason(), s.unknownSteps(), s.doneDay(), s.doneToday())));
}
