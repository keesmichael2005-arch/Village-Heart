package dev.villageheart.world;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.growth.BreedingPair;
import dev.villageheart.core.growth.FailedPair;
import dev.villageheart.core.growth.GrowthState;
import dev.villageheart.core.growth.LapseReason;
import dev.villageheart.core.growth.LicenceState;
import dev.villageheart.core.growth.License;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Codec of the growth state kept on the world claim (M23 plan 3.7). Field names are part of the save format. Enums are saved by name (never by
 * ordinal). A licence that fails validation is dropped with one WARN and the rest of the state is kept; every other field is read leniently.
 */
public final class GrowthCodecs {
	private static final Logger LOG = LoggerFactory.getLogger("villageheart");

	private GrowthCodecs() {}

	/** Own copy of the UUID codec: VillageCodecs refers to this class while it initialises. */
	private static final Codec<UUID> UUID_STRING = Codec.STRING.comapFlatMap(s -> {
		try {
			return DataResult.success(UUID.fromString(s));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Not a UUID: " + s);
		}
	}, UUID::toString);

	private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
		return Codec.STRING.comapFlatMap(s -> {
			try {
				return DataResult.success(Enum.valueOf(type, s));
			} catch (IllegalArgumentException e) {
				return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + s);
			}
		}, Enum::name);
	}

	private record RawLicence(long day, UUID a, UUID b, LicenceState state, int seeds, int aborts, Optional<Long> bed, boolean handedOver,
			int priorFoodA, int priorFoodB, long approachTicks, Optional<UUID> child, Optional<LapseReason> lapse) {}

	private static final Codec<RawLicence> RAW_LICENCE = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(RawLicence::day),
			UUID_STRING.fieldOf("a").forGetter(RawLicence::a),
			UUID_STRING.fieldOf("b").forGetter(RawLicence::b),
			enumCodec(LicenceState.class).fieldOf("state").forGetter(RawLicence::state),
			Codec.INT.optionalFieldOf("seeds", 0).forGetter(RawLicence::seeds),
			Codec.INT.optionalFieldOf("aborts", 0).forGetter(RawLicence::aborts),
			Codec.LONG.optionalFieldOf("bed").forGetter(RawLicence::bed),
			Codec.BOOL.optionalFieldOf("bed_handed_over", false).forGetter(RawLicence::handedOver),
			Codec.INT.optionalFieldOf("prior_food_a", 0).forGetter(RawLicence::priorFoodA),
			Codec.INT.optionalFieldOf("prior_food_b", 0).forGetter(RawLicence::priorFoodB),
			Codec.LONG.optionalFieldOf("approach_ticks", 0L).forGetter(RawLicence::approachTicks),
			UUID_STRING.optionalFieldOf("child").forGetter(RawLicence::child),
			enumCodec(LapseReason.class).optionalFieldOf("lapse").forGetter(RawLicence::lapse)
	).apply(i, RawLicence::new));

	/** Strict: an inconsistent licence is an error. */
	public static final Codec<License> LICENCE = RAW_LICENCE.flatXmap(r -> {
		try {
			return DataResult.success(new License(r.day(), BreedingPair.of(r.a(), r.b()), r.state(), r.seeds(), r.aborts(),
					r.bed().map(OptionalLong::of).orElse(OptionalLong.empty()), r.handedOver(), r.priorFoodA(), r.priorFoodB(),
					r.approachTicks(), r.child(), r.lapse()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid licence: " + e.getMessage());
		}
	}, l -> DataResult.success(new RawLicence(l.day(), l.pair().a(), l.pair().b(), l.state(), l.seeds(), l.aborts(),
			l.bed().isPresent() ? Optional.of(l.bed().getAsLong()) : Optional.empty(), l.handedOver(), l.priorFoodA(), l.priorFoodB(),
			l.approachTicks(), l.child(), l.lapse())));

	/** Lenient: a licence that cannot be read is dropped (one WARN), never an error. */
	private static final Codec<Optional<License>> LENIENT_LICENCE = new Codec<>() {
		@Override
		public <T> DataResult<Pair<Optional<License>, T>> decode(DynamicOps<T> ops, T input) {
			DataResult<Pair<License, T>> r = LICENCE.decode(ops, input);
			if (r.error().isPresent()) {
				LOG.warn("Village save: growth licence dropped: {}", r.error().get().message());
				return DataResult.success(Pair.of(Optional.empty(), input));
			}
			Pair<License, T> p = r.result().orElseThrow();
			return DataResult.success(Pair.of(Optional.of(p.getFirst()), p.getSecond()));
		}

		@Override
		public <T> DataResult<T> encode(Optional<License> input, DynamicOps<T> ops, T prefix) {
			return input.isPresent() ? LICENCE.encode(input.get(), ops, prefix) : DataResult.success(prefix);
		}
	};

	private record RawFailed(UUID a, UUID b, long day) {}

	private static final Codec<RawFailed> RAW_FAILED = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("a").forGetter(RawFailed::a),
			UUID_STRING.fieldOf("b").forGetter(RawFailed::b),
			Codec.LONG.fieldOf("day").forGetter(RawFailed::day)
	).apply(i, RawFailed::new));

	private static final Codec<FailedPair> FAILED = RAW_FAILED.xmap(r -> new FailedPair(BreedingPair.of(r.a(), r.b()), r.day()),
			f -> new RawFailed(f.pair().a(), f.pair().b(), f.day()));

	private record RawGrowth(Optional<License> licence, Optional<Long> lastBirthDay, long birthsTotal, List<FailedPair> failed) {}

	private static final Codec<RawGrowth> RAW_GROWTH = RecordCodecBuilder.create(i -> i.group(
			LENIENT_LICENCE.optionalFieldOf("licence", Optional.empty()).forGetter(RawGrowth::licence),
			Codec.LONG.lenientOptionalFieldOf("last_birth_day").forGetter(RawGrowth::lastBirthDay),
			Codec.LONG.lenientOptionalFieldOf("births_total", 0L).forGetter(RawGrowth::birthsTotal),
			FAILED.listOf().lenientOptionalFieldOf("failed_pairs", List.of()).forGetter(RawGrowth::failed)
	).apply(i, RawGrowth::new));

	public static final Codec<GrowthState> GROWTH = RAW_GROWTH.xmap(
			r -> new GrowthState(r.licence(), r.lastBirthDay().map(OptionalLong::of).orElse(OptionalLong.empty()), Math.max(0, r.birthsTotal()), r.failed()),
			g -> new RawGrowth(g.licence(), g.lastBirthDay().isPresent() ? Optional.of(g.lastBirthDay().getAsLong()) : Optional.empty(), g.birthsTotal(),
					g.failedPairs()));
}
