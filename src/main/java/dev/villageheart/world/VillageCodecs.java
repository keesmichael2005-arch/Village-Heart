package dev.villageheart.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.WorldClaim;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Codecs for the village save format. Only DFU types here (no net.minecraft), so they unit-test with JsonOps.
 * Field names are part of the save format: do not rename.
 */
public final class VillageCodecs {
	public static final int SCHEMA = 1;

	private VillageCodecs() {}

	public static final Codec<UUID> UUID_STRING = Codec.STRING.comapFlatMap(s -> {
		try {
			return DataResult.success(UUID.fromString(s));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Not a UUID: " + s);
		}
	}, UUID::toString);

	private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
		Function<String, DataResult<E>> parse = s -> {
			for (E e : type.getEnumConstants()) {
				if (e.name().toLowerCase(Locale.ROOT).equals(s)) return DataResult.success(e);
			}
			return DataResult.error(() -> "Unknown " + type.getSimpleName() + ": " + s);
		};
		return Codec.STRING.comapFlatMap(parse, e -> e.name().toLowerCase(Locale.ROOT));
	}

	public static final Codec<Tier> TIER = enumCodec(Tier.class);
	public static final Codec<Rank> RANK = enumCodec(Rank.class);

	public static final Codec<HeartLocation> HEART_LOCATION = RecordCodecBuilder.<HeartLocation>create(i -> i.group(
			Codec.STRING.fieldOf("dim").forGetter(HeartLocation::dimension),
			Codec.INT.fieldOf("x").forGetter(HeartLocation::x),
			Codec.INT.fieldOf("y").forGetter(HeartLocation::y),
			Codec.INT.fieldOf("z").forGetter(HeartLocation::z)
	).apply(i, HeartLocation::new)).validate(l -> l.dimension().isBlank()
			? DataResult.error(() -> "Blank dimension") : DataResult.success(l));

	public static final Codec<BuildingRecord> BUILDING = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("type").forGetter(BuildingRecord::typeId),
			HEART_LOCATION.fieldOf("marker").forGetter(BuildingRecord::marker)
	).apply(i, BuildingRecord::new));

	public static final Codec<WorkerRecord> WORKER = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("villager").forGetter(WorkerRecord::villager),
			Codec.STRING.fieldOf("job").forGetter(WorkerRecord::jobId)
	).apply(i, WorkerRecord::new));

	/** Unvalidated shape of VillageState, so that invalid values become DataResult errors instead of exceptions. */
	private record Raw(HeartLocation heart, UUID owner, String ownerName, long claimedAt, java.util.Map<UUID, Rank> ranks,
			int treasury, Tier tier, java.util.List<BuildingRecord> buildings, java.util.List<WorkerRecord> workers) {}

	private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
			HEART_LOCATION.fieldOf("heart").forGetter(Raw::heart),
			UUID_STRING.fieldOf("owner").forGetter(Raw::owner),
			Codec.STRING.fieldOf("owner_name").forGetter(Raw::ownerName),
			Codec.LONG.fieldOf("claimed_at").forGetter(Raw::claimedAt),
			Codec.unboundedMap(UUID_STRING, RANK).optionalFieldOf("ranks", java.util.Map.of()).forGetter(Raw::ranks),
			Codec.INT.optionalFieldOf("treasury", 0).forGetter(Raw::treasury),
			TIER.optionalFieldOf("tier", Tier.HAMLET).forGetter(Raw::tier),
			BUILDING.listOf().optionalFieldOf("buildings", java.util.List.of()).forGetter(Raw::buildings),
			WORKER.listOf().optionalFieldOf("workers", java.util.List.of()).forGetter(Raw::workers)
	).apply(i, Raw::new));

	public static final Codec<VillageState> VILLAGE_STATE = RAW.flatXmap(r -> {
		try {
			return DataResult.success(new VillageState(r.heart(), r.owner(), r.ownerName(), r.claimedAt(), r.ranks(),
					r.treasury(), r.tier(), r.buildings(), r.workers()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid village: " + e.getMessage());
		}
	}, v -> DataResult.success(new Raw(v.heart(), v.owner(), v.ownerName(), v.claimedAtGameTime(), v.ranks(),
			v.treasury(), v.tier(), v.buildings(), v.workers())));

	public static final Codec<WorldClaim> WORLD_CLAIM = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("schema").forGetter(w -> SCHEMA),
			VILLAGE_STATE.optionalFieldOf("village").forGetter(WorldClaim::village)
	).apply(i, (schema, village) -> new WorldClaim(village)));
}
