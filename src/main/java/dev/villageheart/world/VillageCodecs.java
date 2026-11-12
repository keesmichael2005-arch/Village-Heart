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
import dev.villageheart.core.blueprint.BlueprintParams;
import dev.villageheart.core.blueprint.BuildJob;
import dev.villageheart.core.blueprint.Rotation4;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.WorldClaim;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.siege.ThreatState;

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
			HEART_LOCATION.fieldOf("job_block").forGetter(WorkerRecord::jobBlock),
			// M12: the mirror of the worker's level; an older save has none and reads as level 1, a bad value (< 1) as 1
			Codec.INT.optionalFieldOf("level", 1).forGetter(WorkerRecord::level)
	).apply(i, (villager, job, building, jobBlock, level) -> new WorkerRecord(villager, job, building, jobBlock, Math.max(1, level))));

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

	public static final Codec<Rotation4> ROTATION = enumCodec(Rotation4.class);

	private static final Codec<BuildJob> RAW_BUILD = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("id").forGetter(BuildJob::id),
			Codec.STRING.fieldOf("blueprint").forGetter(BuildJob::blueprintId),
			Codec.INT.optionalFieldOf("length", 0).forGetter(b -> b.params().length()),
			HEART_LOCATION.fieldOf("ground").forGetter(BuildJob::ground),
			ROTATION.optionalFieldOf("rotation", Rotation4.NONE).forGetter(BuildJob::rotation),
			Codec.LONG.optionalFieldOf("created_at", 0L).forGetter(BuildJob::createdAt),
			UUID_STRING.fieldOf("started_by").forGetter(BuildJob::startedBy),
			Codec.INT.optionalFieldOf("cursor", 0).forGetter(BuildJob::cursor),
			Codec.unboundedMap(Codec.STRING, Codec.INT).optionalFieldOf("carried", java.util.Map.of()).forGetter(BuildJob::carried),
			UUID_STRING.optionalFieldOf("carrier").forGetter(b -> java.util.Optional.ofNullable(b.carrier()))
	).apply(i, (id, bp, length, ground, rot, created, by, cursor, carried, carrier) ->
			new BuildJob(id, bp, new BlueprintParams(Math.max(0, length)), ground, rot, created, by, Math.max(0, cursor), carried, carrier.orElse(null))));

	/** A saved build whose blueprint is unknown or whose cursor is past its last step is dropped (logged), never failing the village. */
	public static final Codec<BuildJob> BUILD = RAW_BUILD.flatXmap(b -> {
		java.util.Optional<dev.villageheart.core.blueprint.Blueprint> bp = dev.villageheart.core.blueprint.Blueprints.resolve(b.blueprintId(), b.params());
		if (bp.isEmpty()) return DataResult.error(() -> "Unknown blueprint " + b.blueprintId());
		int steps = dev.villageheart.core.blueprint.BuildPlan.of(bp.get(), b.placement()).size();
		if (b.cursor() > steps) return DataResult.error(() -> "Build cursor " + b.cursor() + " is past the last step " + steps);
		return DataResult.success(b);
	}, DataResult::success);

	/** The first build wins when two share an id. */
	private static java.util.List<BuildJob> dedupeBuilds(java.util.List<BuildJob> in) {
		java.util.Map<UUID, BuildJob> byId = new java.util.LinkedHashMap<>();
		for (BuildJob b : in) byId.putIfAbsent(b.id(), b);
		return java.util.List.copyOf(byId.values());
	}

	/** A recorded defence (M21, M25). Length, opened and seen are omitted when empty so a pre-M25 site still round-trips. */
	public static final Codec<dev.villageheart.core.defence.DefenceSite> DEFENCE_SITE = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("id").forGetter(dev.villageheart.core.defence.DefenceSite::id),
			Codec.STRING.fieldOf("blueprint").forGetter(dev.villageheart.core.defence.DefenceSite::blueprintId),
			HEART_LOCATION.fieldOf("ground").forGetter(dev.villageheart.core.defence.DefenceSite::ground),
			ROTATION.optionalFieldOf("rotation", Rotation4.NONE).forGetter(dev.villageheart.core.defence.DefenceSite::rotation),
			Codec.LONG.optionalFieldOf("built_at", 0L).forGetter(dev.villageheart.core.defence.DefenceSite::builtAt),
			Codec.INT.optionalFieldOf("length", 0).forGetter(dev.villageheart.core.defence.DefenceSite::length),
			Codec.LONG.listOf().optionalFieldOf("opened", java.util.List.of()).forGetter(s -> ThreatCodecs.sortedSet(s.opened())),
			Codec.LONG.listOf().optionalFieldOf("seen", java.util.List.of()).forGetter(s -> ThreatCodecs.sortedSet(s.seen()))
	).apply(i, (id, bp, ground, rot, built, length, opened, seen) -> new dev.villageheart.core.defence.DefenceSite(id, bp, ground, rot, built,
			Math.max(0, length), ThreatCodecs.setOf(opened), ThreatCodecs.setOf(seen))));

	/** The first site wins when two share an id, and at most {@link dev.villageheart.core.Balance#MAX_DEFENCE_SITES} are kept. */
	private static java.util.List<dev.villageheart.core.defence.DefenceSite> dedupeDefences(java.util.List<dev.villageheart.core.defence.DefenceSite> in) {
		java.util.Map<UUID, dev.villageheart.core.defence.DefenceSite> byId = new java.util.LinkedHashMap<>();
		for (var d : in) byId.putIfAbsent(d.id(), d);
		java.util.List<dev.villageheart.core.defence.DefenceSite> out = new java.util.ArrayList<>(byId.values());
		if (out.size() > dev.villageheart.core.Balance.MAX_DEFENCE_SITES) {
			LOG.warn("Village save: {} defence sites, keeping the first {}", out.size(), dev.villageheart.core.Balance.MAX_DEFENCE_SITES);
			out = new java.util.ArrayList<>(out.subList(0, dev.villageheart.core.Balance.MAX_DEFENCE_SITES));
		}
		return java.util.List.copyOf(out);
	}

	private static final Codec<dev.villageheart.core.gear.ItemCount> ITEM_COUNT = RecordCodecBuilder.create(i -> i.group(
			Codec.STRING.fieldOf("id").forGetter(dev.villageheart.core.gear.ItemCount::id),
			Codec.INT.fieldOf("count").forGetter(dev.villageheart.core.gear.ItemCount::count)
	).apply(i, (id, n) -> new dev.villageheart.core.gear.ItemCount(id, Math.max(0, n))));

	private record RawSelfCraft(UUID worker, String recipe, HeartLocation building, String need, int slot, int progress,
			java.util.List<dev.villageheart.core.gear.ItemCount> committed, boolean refundPending) {}

	private static final Codec<RawSelfCraft> RAW_SELF_CRAFT = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("worker").forGetter(RawSelfCraft::worker),
			Codec.STRING.fieldOf("recipe").forGetter(RawSelfCraft::recipe),
			HEART_LOCATION.fieldOf("building").forGetter(RawSelfCraft::building),
			Codec.STRING.fieldOf("need").forGetter(RawSelfCraft::need),
			Codec.INT.optionalFieldOf("slot", 0).forGetter(RawSelfCraft::slot),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(RawSelfCraft::progress),
			ITEM_COUNT.listOf().optionalFieldOf("committed", java.util.List.of()).forGetter(RawSelfCraft::committed),
			Codec.BOOL.optionalFieldOf("refund_pending", false).forGetter(RawSelfCraft::refundPending)
	).apply(i, RawSelfCraft::new));

	/**
	 * A saved self-craft (M19a). Progress above the recipe's ticks is clamped; a negative progress, an unknown recipe or an unknown need loads
	 * with {@code refund_pending} so the committed items are refunded, never silently dropped.
	 */
	public static final Codec<dev.villageheart.core.gear.SelfCraft> SELF_CRAFT = RAW_SELF_CRAFT.xmap(r -> {
		java.util.Optional<dev.villageheart.core.gear.ToolRecipe> recipe = dev.villageheart.core.gear.ToolRecipes.byOutput(r.recipe());
		dev.villageheart.core.gear.GearNeed need = null;
		for (dev.villageheart.core.gear.GearNeed n : dev.villageheart.core.gear.GearNeed.values()) if (n.name().equalsIgnoreCase(r.need())) need = n;
		boolean bad = recipe.isEmpty() || need == null || r.progress() < 0 || r.slot() < 0;
		if (bad) LOG.warn("Village save: bad self-craft of {} ({} / {} / progress {}): refunding it", r.worker(), r.recipe(), r.need(), r.progress());
		int progress = recipe.isPresent() ? Math.max(0, Math.min(r.progress(), recipe.get().tier().selfTicks())) : 0;
		return new dev.villageheart.core.gear.SelfCraft(r.worker(), new dev.villageheart.core.gear.GearCraft(r.recipe(),
				new dev.villageheart.core.gear.OrderKey(r.building(), need == null ? dev.villageheart.core.gear.GearNeed.AXE : need, Math.max(0, r.slot())),
				progress, r.committed()), r.refundPending() || bad);
	}, s -> new RawSelfCraft(s.worker(), s.craft().recipeId(), s.craft().key().building(), s.craft().key().need().name().toLowerCase(Locale.ROOT),
			s.craft().key().slot(), s.craft().progress(), s.craft().committed(), s.refundPending()));

	/** The first record wins when two share a worker. */
	private static java.util.List<dev.villageheart.core.gear.SelfCraft> dedupeSelfCrafts(java.util.List<dev.villageheart.core.gear.SelfCraft> in) {
		java.util.Map<UUID, dev.villageheart.core.gear.SelfCraft> byWorker = new java.util.LinkedHashMap<>();
		for (var c : in) byWorker.putIfAbsent(c.worker(), c);
		return java.util.List.copyOf(byWorker.values());
	}

	/** The heat banked at one smoker (M13); a negative heat is an error for that entry only (the lenient list drops it). */
	private record SmokerHeat(HeartLocation smoker, int heat) {}

	private static final Codec<SmokerHeat> SMOKER_HEAT = RecordCodecBuilder.<SmokerHeat>create(i -> i.group(
			HEART_LOCATION.fieldOf("smoker").forGetter(SmokerHeat::smoker),
			Codec.INT.fieldOf("heat").forGetter(SmokerHeat::heat)
	).apply(i, SmokerHeat::new)).flatXmap(h -> h.heat() < 0 ? DataResult.<SmokerHeat>error(() -> "Negative smoker heat " + h.heat()) : DataResult.success(h),
			DataResult::success);

	private static java.util.List<SmokerHeat> heatList(java.util.Map<HeartLocation, Integer> heat) {
		return heat.entrySet().stream().map(e -> new SmokerHeat(e.getKey(), e.getValue()))
				.sorted(java.util.Comparator.comparing(SmokerHeat::smoker, VillageState.locationOrder())).toList();
	}

	private static java.util.Map<HeartLocation, Integer> heatMap(java.util.List<SmokerHeat> list) {
		java.util.Map<HeartLocation, Integer> out = new java.util.HashMap<>();
		for (SmokerHeat h : list) if (h.heat() > 0) out.putIfAbsent(h.smoker(), h.heat());
		return out;
	}

	/** A saved golem build. Progress above the build time is clamped; a negative progress drops the entry (the lenient list). */
	private record GolemBuild(HeartLocation smithy, int progress) {}

	private static final Codec<GolemBuild> GOLEM_BUILD = RecordCodecBuilder.<GolemBuild>create(i -> i.group(
			HEART_LOCATION.fieldOf("smithy").forGetter(GolemBuild::smithy),
			Codec.INT.fieldOf("progress").forGetter(GolemBuild::progress)
	).apply(i, GolemBuild::new)).flatXmap(g -> {
		if (g.progress() < 0) return DataResult.error(() -> "Negative golem progress " + g.progress() + " at " + g.smithy());
		int progress = Math.min(g.progress(), dev.villageheart.core.Balance.GOLEM_BUILD_TICKS);
		if (progress != g.progress()) LOG.warn("Village save: golem build at {} progress {} clamped to {}", g.smithy(), g.progress(), progress);
		return DataResult.success(new GolemBuild(g.smithy(), progress));
	}, DataResult::success);

	private static java.util.List<GolemBuild> golemList(java.util.Map<HeartLocation, Integer> builds) {
		return builds.entrySet().stream().map(e -> new GolemBuild(e.getKey(), e.getValue()))
				.sorted(java.util.Comparator.comparing(GolemBuild::smithy, VillageState.locationOrder())).toList();
	}

	private static java.util.Map<HeartLocation, Integer> golemMap(java.util.List<GolemBuild> list) {
		java.util.Map<HeartLocation, Integer> out = new java.util.LinkedHashMap<>();
		for (GolemBuild g : list) out.putIfAbsent(g.smithy(), g.progress());
		return out;
	}

	private record RawSmithCraft(HeartLocation furnace, String recipe, HeartLocation building, String need, int slot, HeartLocation destination,
			int progress, java.util.List<dev.villageheart.core.gear.ItemCount> committed, boolean refundPending) {}

	private record SavedSmith(HeartLocation furnace, dev.villageheart.core.smith.SmithCraft craft) {}

	/**
	 * A saved smith craft. Progress above the recipe's ticks is clamped. A negative progress, an unknown recipe or an unknown need keeps the
	 * committed items and sets {@code refund_pending}.
	 */
	private static final Codec<SavedSmith> SAVED_SMITH = RecordCodecBuilder.<RawSmithCraft>create(i -> i.group(
			HEART_LOCATION.fieldOf("furnace").forGetter(RawSmithCraft::furnace),
			Codec.STRING.fieldOf("recipe").forGetter(RawSmithCraft::recipe),
			HEART_LOCATION.fieldOf("building").forGetter(RawSmithCraft::building),
			Codec.STRING.fieldOf("need").forGetter(RawSmithCraft::need),
			Codec.INT.optionalFieldOf("slot", 0).forGetter(RawSmithCraft::slot),
			HEART_LOCATION.fieldOf("destination").forGetter(RawSmithCraft::destination),
			Codec.INT.optionalFieldOf("progress", 0).forGetter(RawSmithCraft::progress),
			ITEM_COUNT.listOf().optionalFieldOf("committed", java.util.List.of()).forGetter(RawSmithCraft::committed),
			Codec.BOOL.optionalFieldOf("refund_pending", false).forGetter(RawSmithCraft::refundPending)
	).apply(i, RawSmithCraft::new)).xmap(r -> {
		java.util.Optional<dev.villageheart.core.smith.SmithRecipe> recipe = dev.villageheart.core.smith.SmithRecipes.byOutput(r.recipe());
		dev.villageheart.core.gear.GearNeed need = null;
		for (dev.villageheart.core.gear.GearNeed n : dev.villageheart.core.gear.GearNeed.values()) if (n.name().equalsIgnoreCase(r.need())) need = n;
		boolean bad = recipe.isEmpty() || need == null || r.progress() < 0 || r.slot() < 0;
		if (bad) LOG.warn("Village save: bad smith craft at {} ({} / {} / progress {}): refunding it", r.furnace(), r.recipe(), r.need(), r.progress());
		int cap = recipe.map(dev.villageheart.core.smith.SmithRecipe::ticksPerUnit).orElse(0);
		int progress = bad ? 0 : Math.min(r.progress(), cap);
		var craft = new dev.villageheart.core.smith.SmithCraft(new dev.villageheart.core.gear.GearCraft(r.recipe(),
				new dev.villageheart.core.gear.OrderKey(r.building(), need == null ? dev.villageheart.core.gear.GearNeed.AXE : need, Math.max(0, r.slot())),
				progress, r.committed()), r.destination(), r.refundPending() || bad);
		return new SavedSmith(r.furnace(), craft);
	}, s -> new RawSmithCraft(s.furnace(), s.craft().craft().recipeId(), s.craft().craft().key().building(),
			s.craft().craft().key().need().name().toLowerCase(Locale.ROOT), s.craft().craft().key().slot(), s.craft().destination(),
			s.craft().craft().progress(), s.craft().craft().committed(), s.craft().refundPending()));

	private static java.util.List<SavedSmith> smithList(java.util.Map<HeartLocation, dev.villageheart.core.smith.SmithCraft> crafts) {
		return crafts.entrySet().stream().map(e -> new SavedSmith(e.getKey(), e.getValue()))
				.sorted(java.util.Comparator.comparing(SavedSmith::furnace, VillageState.locationOrder())).toList();
	}

	private static java.util.Map<HeartLocation, dev.villageheart.core.smith.SmithCraft> smithMap(java.util.List<SavedSmith> list) {
		java.util.Map<HeartLocation, dev.villageheart.core.smith.SmithCraft> out = new java.util.LinkedHashMap<>();
		for (SavedSmith s : list) out.putIfAbsent(s.furnace(), s.craft());
		return out;
	}

	/** Unvalidated shape of VillageState, so that invalid values become DataResult errors instead of exceptions. */
	private record Raw(HeartLocation heart, UUID owner, String ownerName, long claimedAt, java.util.Map<UUID, Rank> ranks,
			java.util.List<KnownPlayer> known, int treasury, Tier tier, java.util.List<BuildingRecord> buildings, java.util.List<WorkerRecord> workers,
			java.util.List<TreeTask> treeTasks, java.util.List<BuildJob> builds,
			java.util.List<dev.villageheart.core.defence.DefenceSite> defences, java.util.List<dev.villageheart.core.gear.SelfCraft> selfCrafts,
			dev.villageheart.core.tier.TierProgress tierProgress,
			java.util.List<SmokerHeat> smokerHeat, java.util.List<GuardShiftEntry> guardShifts,
			java.util.List<GolemBuild> golemBuilds, java.util.List<SavedSmith> smithCrafts,
			dev.villageheart.core.death.DeathLog deaths, ThreatState threats) {}

	/** One saved guard shift (M18): the villager, the shift id and whether it is pinned. */
	public record GuardShiftEntry(UUID villager, dev.villageheart.core.guard.Shift shift, boolean pinned) {}

	private static final Codec<dev.villageheart.core.guard.Shift> SHIFT = Codec.STRING.comapFlatMap(
			id -> dev.villageheart.core.guard.Shift.byId(id).map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Unknown guard shift " + id)),
			dev.villageheart.core.guard.Shift::id);

	public static final Codec<GuardShiftEntry> GUARD_SHIFT_ENTRY = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("villager").forGetter(GuardShiftEntry::villager),
			SHIFT.fieldOf("shift").forGetter(GuardShiftEntry::shift),
			Codec.BOOL.optionalFieldOf("pinned", false).forGetter(GuardShiftEntry::pinned)
	).apply(i, GuardShiftEntry::new));

	/** One saved death (M25). Strings are cut by {@link dev.villageheart.core.death.DeathEntry}. */
	public static final Codec<dev.villageheart.core.death.DeathEntry> DEATH_ENTRY = RecordCodecBuilder.create(i -> i.group(
			Codec.LONG.fieldOf("seq").forGetter(dev.villageheart.core.death.DeathEntry::seq),
			UUID_STRING.fieldOf("villager").forGetter(dev.villageheart.core.death.DeathEntry::villager),
			Codec.STRING.optionalFieldOf("name", "").forGetter(dev.villageheart.core.death.DeathEntry::name),
			Codec.STRING.optionalFieldOf("job", "").forGetter(dev.villageheart.core.death.DeathEntry::jobId),
			Codec.STRING.fieldOf("cause").forGetter(dev.villageheart.core.death.DeathEntry::cause),
			Codec.BOOL.optionalFieldOf("by_mob", false).forGetter(dev.villageheart.core.death.DeathEntry::byMob),
			Codec.LONG.fieldOf("game_time").forGetter(dev.villageheart.core.death.DeathEntry::gameTime),
			Codec.INT.optionalFieldOf("day", 1).forGetter(dev.villageheart.core.death.DeathEntry::day),
			Codec.BOOL.optionalFieldOf("in_claim", true).forGetter(dev.villageheart.core.death.DeathEntry::inClaim),
			Codec.BOOL.optionalFieldOf("during_raid", false).forGetter(dev.villageheart.core.death.DeathEntry::duringRaid),
			Codec.BOOL.optionalFieldOf("during_siege", false).forGetter(dev.villageheart.core.death.DeathEntry::duringSiege)
	).apply(i, dev.villageheart.core.death.DeathEntry::new));

	private record RawDeathLog(java.util.List<dev.villageheart.core.death.DeathEntry> entries, long nextSeq, long inClaimLogged) {}

	private static final Codec<RawDeathLog> RAW_DEATH_LOG = RecordCodecBuilder.create(i -> i.group(
			lenientList(DEATH_ENTRY).optionalFieldOf("entries", java.util.List.of()).forGetter(RawDeathLog::entries),
			Codec.LONG.optionalFieldOf("next_seq", 0L).forGetter(RawDeathLog::nextSeq),
			Codec.LONG.optionalFieldOf("in_claim_logged", 0L).forGetter(RawDeathLog::inClaimLogged)
	).apply(i, RawDeathLog::new));

	/**
	 * The death log (M25). A value that will not parse becomes an empty log (one WARN) and the village is kept.
	 * More than {@link dev.villageheart.core.Balance#DEATH_LOG_MAX} entries keeps the newest.
	 */
	public static final Codec<dev.villageheart.core.death.DeathLog> DEATH_LOG = new Codec<>() {
		@Override public <T> DataResult<Pair<dev.villageheart.core.death.DeathLog, T>> decode(DynamicOps<T> ops, T input) {
			DataResult<Pair<RawDeathLog, T>> parsed = RAW_DEATH_LOG.decode(ops, input);
			if (parsed.result().isEmpty()) {
				LOG.warn("Village save: unreadable death_log dropped ({})", parsed.error().map(e -> e.message()).orElse("?"));
				return DataResult.success(Pair.of(dev.villageheart.core.death.DeathLog.EMPTY, input));
			}
			RawDeathLog d = parsed.result().get().getFirst();
			if (d.entries().size() > dev.villageheart.core.Balance.DEATH_LOG_MAX) {
				LOG.warn("Village save: death log had {} entries, keeping the newest {}", d.entries().size(), dev.villageheart.core.Balance.DEATH_LOG_MAX);
			}
			return DataResult.success(Pair.of(new dev.villageheart.core.death.DeathLog(d.entries(), d.nextSeq(), d.inClaimLogged()),
					parsed.result().get().getSecond()));
		}

		@Override public <T> DataResult<T> encode(dev.villageheart.core.death.DeathLog input, DynamicOps<T> ops, T prefix) {
			return RAW_DEATH_LOG.encode(new RawDeathLog(input.entries(), input.nextSeq(), input.inClaimLogged()), ops, prefix);
		}
	};

	private record RawProgress(int fedStreak, long lastDinnerDay, int bestDawnTreasury) {}

	/**
	 * Tier progress (M14). Lenient: a malformed or missing field reads as its default and a negative number as 0, so a bad value never
	 * drops the village. {@code last_dinner_day} is written only when a dinner was counted.
	 */
	public static final Codec<dev.villageheart.core.tier.TierProgress> TIER_PROGRESS = RecordCodecBuilder.<RawProgress>create(i -> i.group(
			Codec.INT.lenientOptionalFieldOf("fed_streak", 0).forGetter(RawProgress::fedStreak),
			Codec.LONG.lenientOptionalFieldOf("last_dinner_day", dev.villageheart.core.tier.TierProgress.NO_DAY).forGetter(RawProgress::lastDinnerDay),
			Codec.INT.lenientOptionalFieldOf("best_dawn_treasury", 0).forGetter(RawProgress::bestDawnTreasury)
	).apply(i, RawProgress::new)).xmap(r -> {
		if (r.fedStreak() < 0 || r.bestDawnTreasury() < 0) LOG.warn("Village save: negative tier progress {} clamped to 0", r);
		return new dev.villageheart.core.tier.TierProgress(Math.max(0, r.fedStreak()), r.lastDinnerDay(), Math.max(0, r.bestDawnTreasury()));
	}, p -> new RawProgress(p.fedStreak(), p.lastDinnerDay(), p.bestDawnTreasury()));

	// RecordCodecBuilder.group takes at most 16 fields. The village has 19 components (M25 adds death_log and threats on RAW_B).
	// The keys are read by two map codecs joined with Codec.mapPair: both read the same flat compound, so the save format stays flat.
	private record RawA(HeartLocation heart, UUID owner, String ownerName, long claimedAt, java.util.Map<UUID, Rank> ranks,
			java.util.List<KnownPlayer> known, int treasury, Tier tier, java.util.List<BuildingRecord> buildings, java.util.List<WorkerRecord> workers) {}

	private record RawB(java.util.List<TreeTask> treeTasks, java.util.List<BuildJob> builds,
			java.util.List<dev.villageheart.core.defence.DefenceSite> defences, java.util.List<dev.villageheart.core.gear.SelfCraft> selfCrafts,
			dev.villageheart.core.tier.TierProgress tierProgress, java.util.List<SmokerHeat> smokerHeat, java.util.List<GuardShiftEntry> guardShifts,
			java.util.List<GolemBuild> golemBuilds, java.util.List<SavedSmith> smithCrafts,
			dev.villageheart.core.death.DeathLog deaths, ThreatState threats) {}

	private static final com.mojang.serialization.MapCodec<RawA> RAW_A = RecordCodecBuilder.mapCodec(i -> i.group(
			HEART_LOCATION.fieldOf("heart").forGetter(RawA::heart),
			UUID_STRING.fieldOf("owner").forGetter(RawA::owner),
			Codec.STRING.fieldOf("owner_name").forGetter(RawA::ownerName),
			Codec.LONG.fieldOf("claimed_at").forGetter(RawA::claimedAt),
			Codec.unboundedMap(UUID_STRING, RANK).optionalFieldOf("ranks", java.util.Map.of()).forGetter(RawA::ranks),
			lenientList(KNOWN_PLAYER).optionalFieldOf("known_players", java.util.List.of()).forGetter(RawA::known),
			Codec.INT.optionalFieldOf("treasury", 0).forGetter(RawA::treasury),
			TIER.optionalFieldOf("tier", Tier.HAMLET).forGetter(RawA::tier),
			BUILDING.listOf().optionalFieldOf("buildings", java.util.List.of()).forGetter(RawA::buildings),
			WORKER.listOf().optionalFieldOf("workers", java.util.List.of()).forGetter(RawA::workers)
	).apply(i, RawA::new));

	private static final com.mojang.serialization.MapCodec<RawB> RAW_B = RecordCodecBuilder.mapCodec(i -> i.group(
			TREE_TASK.listOf().optionalFieldOf("tree_tasks", java.util.List.of()).forGetter(RawB::treeTasks),
			lenientList(BUILD).optionalFieldOf("builds", java.util.List.of()).forGetter(RawB::builds),
			lenientList(DEFENCE_SITE).optionalFieldOf("defences", java.util.List.of()).forGetter(RawB::defences),
			lenientList(SELF_CRAFT).optionalFieldOf("self_crafts", java.util.List.of()).forGetter(RawB::selfCrafts),
			TIER_PROGRESS.lenientOptionalFieldOf("tier_progress", dev.villageheart.core.tier.TierProgress.INITIAL).forGetter(RawB::tierProgress),
			lenientList(SMOKER_HEAT).optionalFieldOf("smoker_heat", java.util.List.of()).forGetter(RawB::smokerHeat),
			lenientList(GUARD_SHIFT_ENTRY).optionalFieldOf("guard_shifts", java.util.List.of()).forGetter(RawB::guardShifts),
			lenientList(GOLEM_BUILD).optionalFieldOf("golem_builds", java.util.List.of()).forGetter(RawB::golemBuilds),
			lenientList(SAVED_SMITH).optionalFieldOf("smith_crafts", java.util.List.of()).forGetter(RawB::smithCrafts),
			DEATH_LOG.optionalFieldOf("death_log", dev.villageheart.core.death.DeathLog.EMPTY).forGetter(RawB::deaths),
			ThreatCodecs.lenient(ThreatCodecs.THREATS, "threats", ThreatState.EMPTY).forGetter(RawB::threats)
	).apply(i, RawB::new));

	private static final Codec<Raw> RAW = Codec.mapPair(RAW_A, RAW_B).codec().xmap(
			pair -> new Raw(pair.getFirst().heart(), pair.getFirst().owner(), pair.getFirst().ownerName(), pair.getFirst().claimedAt(), pair.getFirst().ranks(),
					pair.getFirst().known(), pair.getFirst().treasury(), pair.getFirst().tier(), pair.getFirst().buildings(), pair.getFirst().workers(),
					pair.getSecond().treeTasks(), pair.getSecond().builds(), pair.getSecond().defences(), pair.getSecond().selfCrafts(),
					pair.getSecond().tierProgress(), pair.getSecond().smokerHeat(), pair.getSecond().guardShifts(), pair.getSecond().golemBuilds(), pair.getSecond().smithCrafts(),
					pair.getSecond().deaths(), pair.getSecond().threats()),
			r -> com.mojang.datafixers.util.Pair.of(new RawA(r.heart(), r.owner(), r.ownerName(), r.claimedAt(), r.ranks(), r.known(), r.treasury(), r.tier(),
					r.buildings(), r.workers()),
					new RawB(r.treeTasks(), r.builds(), r.defences(), r.selfCrafts(), r.tierProgress(), r.smokerHeat(), r.guardShifts(), r.golemBuilds(), r.smithCrafts(),
							r.deaths(), r.threats())));

	public static final Codec<VillageState> VILLAGE_STATE = RAW.flatXmap(r -> {
		try {
			MemberRepair.Result m = MemberRepair.repair(r.owner(), r.ranks(), r.known());
			if (!m.dropped().isEmpty()) LOG.warn("Village save: dropped or demoted players to fit the member limits: {}", m.dropped());
			java.util.Map<UUID, dev.villageheart.core.guard.GuardShift> saved = new java.util.LinkedHashMap<>();
			for (GuardShiftEntry e : r.guardShifts()) saved.putIfAbsent(e.villager(), new dev.villageheart.core.guard.GuardShift(e.shift(), e.pinned()));
			java.util.List<UUID> guards = new java.util.ArrayList<>();
			for (WorkerRecord w : r.workers()) if (dev.villageheart.core.worker.JobType.GUARD.id().equals(w.jobId())) guards.add(w.villager());
			// a map that does not match the records is brought in line once, here
			var shifts = dev.villageheart.core.guard.ShiftBalancer.rebalance(guards, saved);
			return DataResult.success(new VillageState(r.heart(), r.owner(), r.ownerName(), r.claimedAt(), m.ranks(), m.known(),
					r.treasury(), r.tier(), dedupe(r.buildings()), r.workers(), dedupeTasks(r.treeTasks()), dedupeBuilds(r.builds()), dedupeDefences(r.defences()), dedupeSelfCrafts(r.selfCrafts()), r.tierProgress(), heatMap(r.smokerHeat()), shifts, golemMap(r.golemBuilds()), smithMap(r.smithCrafts()), r.deaths(), r.threats()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid village: " + e.getMessage());
		}
	}, v -> DataResult.success(new Raw(v.heart(), v.owner(), v.ownerName(), v.claimedAtGameTime(), v.ranks(), v.known(),
			v.treasury(), v.tier(), v.buildings(), v.workers(), v.treeTasks(), v.builds(), v.defences(), v.selfCrafts(), v.tierProgress(), heatList(v.furnaceHeat()),
			v.guardShifts().entrySet().stream().map(e -> new GuardShiftEntry(e.getKey(), e.getValue().shift(), e.getValue().pinned())).toList(),
			golemList(v.golemBuilds()), smithList(v.smithCrafts()),
			v.deaths(), v.threats())));

	/** A striker and the wage it was charged at dawn (M12). */
	public static final Codec<dev.villageheart.core.economy.Payee> PAYEE = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("villager").forGetter(dev.villageheart.core.economy.Payee::villager),
			Codec.INT.fieldOf("wage").forGetter(dev.villageheart.core.economy.Payee::wage)
	).apply(i, dev.villageheart.core.economy.Payee::new));

	/**
	 * The payroll (M12). Each list drops an unreadable entry (one WARN); a payroll that is still invalid (a villager both paid and unpaid, a
	 * duplicate) is read as absent by the lenient field below, so a broken payroll is re-initialised and never loses the claim.
	 */
	public static final Codec<dev.villageheart.core.economy.Payroll> PAYROLL = RecordCodecBuilder.<Raw3>create(i -> i.group(
			Codec.LONG.fieldOf("last_wage_day").forGetter(Raw3::day),
			lenientList(UUID_STRING).optionalFieldOf("paid_today", java.util.List.of()).forGetter(Raw3::paid),
			lenientList(PAYEE).optionalFieldOf("unpaid", java.util.List.of()).forGetter(Raw3::unpaid)
	).apply(i, Raw3::new)).flatXmap(r -> {
		try {
			// a repeated entry keeps its first place; a striker also listed as paid counts as paid
			java.util.Set<UUID> paid = new java.util.LinkedHashSet<>(r.paid());
			java.util.Map<UUID, dev.villageheart.core.economy.Payee> unpaid = new java.util.LinkedHashMap<>();
			for (dev.villageheart.core.economy.Payee p : r.unpaid()) if (!paid.contains(p.villager())) unpaid.putIfAbsent(p.villager(), p);
			return DataResult.success(new dev.villageheart.core.economy.Payroll(r.day(), paid, new ArrayList<>(unpaid.values())));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid payroll: " + e.getMessage());
		}
	}, p -> DataResult.success(new Raw3(p.lastWageDay(), java.util.List.copyOf(p.paidToday()), p.unpaid())));

	private record Raw3(long day, java.util.List<UUID> paid, java.util.List<dev.villageheart.core.economy.Payee> unpaid) {}

	private record SoldBy(UUID villager, int sold) {}

	private record SoldAt(HeartLocation stall, int sold) {}

	private record RawLedger(long day, java.util.List<SoldBy> byMerchant, java.util.List<SoldAt> byStall) {}

	/** Today's sales (M22). Each list drops an unreadable entry; a repeated key keeps its first. */
	public static final Codec<dev.villageheart.core.market.SalesLedger> SALES_LEDGER = RecordCodecBuilder.<RawLedger>create(i -> i.group(
			Codec.LONG.fieldOf("day").forGetter(RawLedger::day),
			lenientList(RecordCodecBuilder.<SoldBy>create(j -> j.group(
					UUID_STRING.fieldOf("villager").forGetter(SoldBy::villager),
					Codec.intRange(0, 1_000_000).fieldOf("sold").forGetter(SoldBy::sold)).apply(j, SoldBy::new)))
					.optionalFieldOf("by_merchant", java.util.List.of()).forGetter(RawLedger::byMerchant),
			lenientList(RecordCodecBuilder.<SoldAt>create(j -> j.group(
					HEART_LOCATION.fieldOf("stall").forGetter(SoldAt::stall),
					Codec.intRange(0, 1_000_000).fieldOf("sold").forGetter(SoldAt::sold)).apply(j, SoldAt::new)))
					.optionalFieldOf("by_stall", java.util.List.of()).forGetter(RawLedger::byStall)
	).apply(i, RawLedger::new)).xmap(r -> {
		java.util.Map<UUID, Integer> m = new java.util.LinkedHashMap<>();
		for (SoldBy e : r.byMerchant()) m.putIfAbsent(e.villager(), e.sold());
		java.util.Map<HeartLocation, Integer> s = new java.util.LinkedHashMap<>();
		for (SoldAt e : r.byStall()) s.putIfAbsent(e.stall(), e.sold());
		return new dev.villageheart.core.market.SalesLedger(r.day(), m, s);
	}, l -> {
		java.util.List<SoldBy> m = new ArrayList<>();
		l.byMerchant().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey()).forEach(e -> m.add(new SoldBy(e.getKey(), e.getValue())));
		java.util.List<SoldAt> s = new ArrayList<>();
		l.byStall().entrySet().stream().sorted(java.util.Map.Entry.comparingByKey(VillageState.locationOrder())).forEach(e -> s.add(new SoldAt(e.getKey(), e.getValue())));
		return new RawLedger(l.day(), m, s);
	});

	private record RawMarket(int foodDays, int itemReserve, dev.villageheart.core.market.SalesLedger ledger) {}

	/**
	 * The market (M22): reserves and ledger. Reserves out of range are clamped into range on read (one WARN), never rejected; the field is read
	 * leniently by {@link #WORLD_CLAIM}, so a broken market reads as absent and the claim survives.
	 */
	public static final Codec<dev.villageheart.core.market.MarketState> MARKET = RecordCodecBuilder.<RawMarket>create(i -> i.group(
			Codec.INT.optionalFieldOf("food_reserve_days", dev.villageheart.core.Balance.FOOD_RESERVE_DAYS).forGetter(RawMarket::foodDays),
			Codec.INT.optionalFieldOf("item_reserve", dev.villageheart.core.Balance.ITEM_RESERVE).forGetter(RawMarket::itemReserve),
			SALES_LEDGER.optionalFieldOf("ledger", dev.villageheart.core.market.SalesLedger.empty(-1)).forGetter(RawMarket::ledger)
	).apply(i, RawMarket::new)).xmap(r -> {
		if (!dev.villageheart.core.market.Reserves.valid(r.foodDays(), r.itemReserve())) LOG.warn("Village save: market reserves {}/{} out of range, clamped", r.foodDays(), r.itemReserve());
		return new dev.villageheart.core.market.MarketState(dev.villageheart.core.market.Reserves.clamped(r.foodDays(), r.itemReserve()), r.ledger());
	}, m -> new RawMarket(m.reserves().foodDays(), m.reserves().itemReserve(), m.ledger()));

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
			HEART_LOCATION.listOf().optionalFieldOf("pending_ticket_releases", java.util.List.of()).forGetter(VillageCodecs::sortedPending),
			Codec.LONG.optionalFieldOf("last_dinner_day").forGetter(w -> w.lastDinnerDay().isPresent()
					? Optional.of(w.lastDinnerDay().getAsLong()) : Optional.empty()),
			PAYROLL.lenientOptionalFieldOf("payroll").forGetter(WorldClaim::payroll),
			// written only when it differs from the defaults, so a save of a village with no market activity reads as before M22
			MARKET.lenientOptionalFieldOf("market").forGetter(w -> w.market().equals(dev.villageheart.core.market.MarketState.initial()) ? Optional.empty() : Optional.of(w.market())),
			// M23: nested on the world claim (not in RAW); written only when there is something to remember
			GrowthCodecs.GROWTH.lenientOptionalFieldOf("growth").forGetter(w -> w.growth().equals(dev.villageheart.core.growth.GrowthState.EMPTY) ? Optional.empty() : Optional.of(w.growth()))
	).apply(i, (schema, village, reserved, pending, dinner, payroll, market, growth) -> new WorldClaim(village, new java.util.LinkedHashSet<>(reserved),
			new java.util.LinkedHashSet<>(pending), dinner.isPresent() ? java.util.OptionalLong.of(dinner.get()) : java.util.OptionalLong.empty(), payroll,
			market.orElse(dev.villageheart.core.market.MarketState.initial()), growth.orElse(dev.villageheart.core.growth.GrowthState.EMPTY))));
}
