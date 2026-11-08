package dev.villageheart.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.villageheart.core.BuildingRecord;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.KnownPlayer;
import dev.villageheart.core.MemberRepair;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.TreeTask;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.WorldClaim;
import dev.villageheart.core.building.BuildingStatus;

import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.DynamicOps;
import java.util.ArrayList;
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

	private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger("villageheart");

	private VillageCodecs() {}

	/**
	 * Decodes a list element by element and drops the ones that fail (one WARN each), so a single bad entry never loses
	 * the village. Encodes like {@code element.listOf()}.
	 */
	public static <E> Codec<java.util.List<E>> lenientList(Codec<E> element) {
		Codec<java.util.List<E>> strict = element.listOf();
		return new Codec<>() {
			@Override public <T> DataResult<Pair<java.util.List<E>, T>> decode(DynamicOps<T> ops, T input) {
				return ops.getList(input).map(each -> {
					java.util.List<E> out = new ArrayList<>();
					each.accept(item -> {
						DataResult<E> r = element.parse(ops, item);
						if (r.result().isPresent()) out.add(r.result().get());
						else LOG.warn("Dropping an unreadable list entry: {}", r.error().map(e -> e.message()).orElse("?"));
					});
					return Pair.of(out, input);
				});
			}

			@Override public <T> DataResult<T> encode(java.util.List<E> input, DynamicOps<T> ops, T prefix) {
				return strict.encode(input, ops, prefix);
			}
		};
	}

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

	public static final Codec<BuildingStatus> BUILDING_STATUS = enumCodec(BuildingStatus.class);

	public static final Codec<BuildingRecord> BUILDING = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("type").forGetter(BuildingRecord::typeId),
			HEART_LOCATION.fieldOf("marker").forGetter(BuildingRecord::marker),
			BUILDING_STATUS.optionalFieldOf("status", BuildingStatus.UNCHECKED).forGetter(BuildingRecord::status),
			HEART_LOCATION.optionalFieldOf("link").forGetter(b -> java.util.Optional.ofNullable(b.link()))
	).apply(i, (type, marker, status, link) -> new BuildingRecord(type, marker, status, link.orElse(null))));

	/** First record per marker wins: one bad entry must not lose the whole village. */
	private static java.util.List<BuildingRecord> dedupe(java.util.List<BuildingRecord> in) {
		java.util.Map<HeartLocation, BuildingRecord> byMarker = new java.util.LinkedHashMap<>();
		for (BuildingRecord b : in) byMarker.putIfAbsent(b.marker(), b);
		return java.util.List.copyOf(byMarker.values());
	}

	public static final Codec<WorkerRecord> WORKER = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("villager").forGetter(WorkerRecord::villager),
			Codec.STRING.fieldOf("job").forGetter(WorkerRecord::jobId),
			HEART_LOCATION.fieldOf("building").forGetter(WorkerRecord::building),
			HEART_LOCATION.fieldOf("job_block").forGetter(WorkerRecord::jobBlock)
	).apply(i, WorkerRecord::new));

	public static final Codec<KnownPlayer> KNOWN_PLAYER = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("id").forGetter(KnownPlayer::id),
			Codec.STRING.optionalFieldOf("name", "").forGetter(KnownPlayer::name),
			Codec.LONG.optionalFieldOf("last_seen", 0L).forGetter(KnownPlayer::lastSeen)
	).apply(i, KnownPlayer::new));

	public static final Codec<Pos> POS = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("x").forGetter(Pos::x),
			Codec.INT.fieldOf("y").forGetter(Pos::y),
			Codec.INT.fieldOf("z").forGetter(Pos::z)
	).apply(i, Pos::new));

	public static final Codec<TreeTask.Stage> TREE_STAGE = enumCodec(TreeTask.Stage.class);

	private record RawTask(HeartLocation building, TreeTask.Stage stage, java.util.List<HeartLocation> base, String sapling, long since,
			Pos boxMin, Pos boxMax) {}

	private static final Codec<RawTask> RAW_TASK = RecordCodecBuilder.create(i -> i.group(
			HEART_LOCATION.fieldOf("building").forGetter(RawTask::building),
			TREE_STAGE.fieldOf("stage").forGetter(RawTask::stage),
			HEART_LOCATION.listOf().fieldOf("base").forGetter(RawTask::base),
			Codec.STRING.fieldOf("sapling").forGetter(RawTask::sapling),
			Codec.LONG.optionalFieldOf("since", 0L).forGetter(RawTask::since),
			POS.fieldOf("box_min").forGetter(RawTask::boxMin),
			POS.fieldOf("box_max").forGetter(RawTask::boxMax)
	).apply(i, RawTask::new));

	public static final Codec<TreeTask> TREE_TASK = RAW_TASK.flatXmap(r -> {
		try {
			return DataResult.success(new TreeTask(r.building(), r.stage(), r.base(), r.sapling(), r.since(), r.boxMin(), r.boxMax()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid tree task: " + e.getMessage());
		}
	}, t -> DataResult.success(new RawTask(t.building(), t.stage(), t.base(), t.sapling(), t.since(), t.boxMin(), t.boxMax())));

	/** The first task wins when two share a base cell: one bad entry must not stop the village from loading. */
	private static java.util.List<TreeTask> dedupeTasks(java.util.List<TreeTask> in) {
		java.util.List<TreeTask> kept = new java.util.ArrayList<>();
		for (TreeTask t : in) {
			boolean clash = false;
			for (TreeTask k : kept) if (k.sharesCellWith(t)) clash = true;
			if (clash) System.getLogger("villageheart").log(System.Logger.Level.WARNING, "Dropped a saved tree task that shares a cell with another: {0}", t.key());
			else kept.add(t);
		}
		return java.util.List.copyOf(kept);
	}

	/** Unvalidated shape of VillageState, so that invalid values become DataResult errors instead of exceptions. */
	private record Raw(HeartLocation heart, UUID owner, String ownerName, long claimedAt, java.util.Map<UUID, Rank> ranks,
			java.util.List<KnownPlayer> known, int treasury, Tier tier, java.util.List<BuildingRecord> buildings, java.util.List<WorkerRecord> workers,
			java.util.List<TreeTask> treeTasks) {}

	private static final Codec<Raw> RAW = RecordCodecBuilder.create(i -> i.group(
			HEART_LOCATION.fieldOf("heart").forGetter(Raw::heart),
			UUID_STRING.fieldOf("owner").forGetter(Raw::owner),
			Codec.STRING.fieldOf("owner_name").forGetter(Raw::ownerName),
			Codec.LONG.fieldOf("claimed_at").forGetter(Raw::claimedAt),
			Codec.unboundedMap(UUID_STRING, RANK).optionalFieldOf("ranks", java.util.Map.of()).forGetter(Raw::ranks),
			lenientList(KNOWN_PLAYER).optionalFieldOf("known_players", java.util.List.of()).forGetter(Raw::known),
			Codec.INT.optionalFieldOf("treasury", 0).forGetter(Raw::treasury),
			TIER.optionalFieldOf("tier", Tier.HAMLET).forGetter(Raw::tier),
			BUILDING.listOf().optionalFieldOf("buildings", java.util.List.of()).forGetter(Raw::buildings),
			WORKER.listOf().optionalFieldOf("workers", java.util.List.of()).forGetter(Raw::workers),
			TREE_TASK.listOf().optionalFieldOf("tree_tasks", java.util.List.of()).forGetter(Raw::treeTasks)
	).apply(i, Raw::new));

	public static final Codec<VillageState> VILLAGE_STATE = RAW.flatXmap(r -> {
		try {
			MemberRepair.Result m = MemberRepair.repair(r.owner(), r.ranks(), r.known());
			if (!m.dropped().isEmpty()) LOG.warn("Village save: dropped or demoted players to fit the member limits: {}", m.dropped());
			return DataResult.success(new VillageState(r.heart(), r.owner(), r.ownerName(), r.claimedAt(), m.ranks(), m.known(),
					r.treasury(), r.tier(), dedupe(r.buildings()), r.workers(), dedupeTasks(r.treeTasks())));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid village: " + e.getMessage());
		}
	}, v -> DataResult.success(new Raw(v.heart(), v.owner(), v.ownerName(), v.claimedAtGameTime(), v.ranks(), v.known(),
			v.treasury(), v.tier(), v.buildings(), v.workers(), v.treeTasks())));

	/** Job blocks the mod holds the POI ticket of (M7), sorted so the saved list is stable. */
	private static java.util.List<HeartLocation> sortedReserved(WorldClaim w) {
		java.util.List<HeartLocation> l = new java.util.ArrayList<>(w.reservedJobBlocks());
		l.sort(VillageState.locationOrder());
		return l;
	}

	private static java.util.List<HeartLocation> sortedPending(WorldClaim w) {
		java.util.List<HeartLocation> l = new java.util.ArrayList<>(w.pendingTicketReleases());
		l.sort(VillageState.locationOrder());
		return l;
	}

	public static final Codec<WorldClaim> WORLD_CLAIM = RecordCodecBuilder.create(i -> i.group(
			Codec.INT.fieldOf("schema").forGetter(w -> SCHEMA),
			VILLAGE_STATE.optionalFieldOf("village").forGetter(WorldClaim::village),
			HEART_LOCATION.listOf().optionalFieldOf("reserved_job_blocks", java.util.List.of()).forGetter(VillageCodecs::sortedReserved),
			HEART_LOCATION.listOf().optionalFieldOf("pending_ticket_releases", java.util.List.of()).forGetter(VillageCodecs::sortedPending)
	).apply(i, (schema, village, reserved, pending) -> new WorldClaim(village, new java.util.LinkedHashSet<>(reserved),
			new java.util.LinkedHashSet<>(pending))));
}
