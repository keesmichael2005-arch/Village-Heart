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

	/** A recorded lamp post or watchtower (M21). */
	public static final Codec<dev.villageheart.core.defence.DefenceSite> DEFENCE_SITE = RecordCodecBuilder.create(i -> i.group(
			UUID_STRING.fieldOf("id").forGetter(dev.villageheart.core.defence.DefenceSite::id),
			Codec.STRING.fieldOf("blueprint").forGetter(dev.villageheart.core.defence.DefenceSite::blueprintId),
			HEART_LOCATION.fieldOf("ground").forGetter(dev.villageheart.core.defence.DefenceSite::ground),
			ROTATION.optionalFieldOf("rotation", Rotation4.NONE).forGetter(dev.villageheart.core.defence.DefenceSite::rotation),
			Codec.LONG.optionalFieldOf("built_at", 0L).forGetter(dev.villageheart.core.defence.DefenceSite::builtAt)
	).apply(i, dev.villageheart.core.defence.DefenceSite::new));

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

	/** Unvalidated shape of VillageState, so that invalid values become DataResult errors instead of exceptions. */
	private record Raw(HeartLocation heart, UUID owner, String ownerName, long claimedAt, java.util.Map<UUID, Rank> ranks,
			java.util.List<KnownPlayer> known, int treasury, Tier tier, java.util.List<BuildingRecord> buildings, java.util.List<WorkerRecord> workers,
			java.util.List<TreeTask> treeTasks, java.util.List<BuildJob> builds,
			java.util.List<dev.villageheart.core.defence.DefenceSite> defences, java.util.List<dev.villageheart.core.gear.SelfCraft> selfCrafts,
			dev.villageheart.core.tier.TierProgress tierProgress) {}

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
			TREE_TASK.listOf().optionalFieldOf("tree_tasks", java.util.List.of()).forGetter(Raw::treeTasks),
			lenientList(BUILD).optionalFieldOf("builds", java.util.List.of()).forGetter(Raw::builds),
			lenientList(DEFENCE_SITE).optionalFieldOf("defences", java.util.List.of()).forGetter(Raw::defences),
			lenientList(SELF_CRAFT).optionalFieldOf("self_crafts", java.util.List.of()).forGetter(Raw::selfCrafts),
			TIER_PROGRESS.lenientOptionalFieldOf("tier_progress", dev.villageheart.core.tier.TierProgress.INITIAL).forGetter(Raw::tierProgress)
	).apply(i, Raw::new));

	public static final Codec<VillageState> VILLAGE_STATE = RAW.flatXmap(r -> {
		try {
			MemberRepair.Result m = MemberRepair.repair(r.owner(), r.ranks(), r.known());
			if (!m.dropped().isEmpty()) LOG.warn("Village save: dropped or demoted players to fit the member limits: {}", m.dropped());
			return DataResult.success(new VillageState(r.heart(), r.owner(), r.ownerName(), r.claimedAt(), m.ranks(), m.known(),
					r.treasury(), r.tier(), dedupe(r.buildings()), r.workers(), dedupeTasks(r.treeTasks()), dedupeBuilds(r.builds()), dedupeDefences(r.defences()), dedupeSelfCrafts(r.selfCrafts()), r.tierProgress()));
		} catch (IllegalArgumentException e) {
			return DataResult.error(() -> "Invalid village: " + e.getMessage());
		}
	}, v -> DataResult.success(new Raw(v.heart(), v.owner(), v.ownerName(), v.claimedAtGameTime(), v.ranks(), v.known(),
			v.treasury(), v.tier(), v.buildings(), v.workers(), v.treeTasks(), v.builds(), v.defences(), v.selfCrafts(), v.tierProgress())));

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
			PAYROLL.lenientOptionalFieldOf("payroll").forGetter(WorldClaim::payroll)
	).apply(i, (schema, village, reserved, pending, dinner, payroll) -> new WorldClaim(village, new java.util.LinkedHashSet<>(reserved),
			new java.util.LinkedHashSet<>(pending), dinner.isPresent() ? java.util.OptionalLong.of(dinner.get()) : java.util.OptionalLong.empty(), payroll)));
}
