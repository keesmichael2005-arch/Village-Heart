package dev.villageheart.heart;

import dev.villageheart.building.Buildings;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.VillageState;
import dev.villageheart.core.WorkerRecord;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingStatus;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.Requirements;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.heart.ActivityKind;
import dev.villageheart.core.heart.DetailKind;
import dev.villageheart.core.heart.NeedsFlags;
import dev.villageheart.core.heart.RowStatus;
import dev.villageheart.core.heart.TextLine;
import dev.villageheart.core.heart.VillageFacts;
import dev.villageheart.core.heart.VillageFacts.BuildingFact;
import dev.villageheart.core.heart.VillageFacts.JobFact;
import dev.villageheart.core.heart.VillageFacts.StockFacts;
import dev.villageheart.core.heart.VillageFacts.StockItemFact;
import dev.villageheart.core.heart.VillageFacts.VillagerFact;
import dev.villageheart.core.stock.StockSnapshot;
import dev.villageheart.core.worker.JobSlots;
import dev.villageheart.core.worker.JobType;
import dev.villageheart.core.worker.SiteView;
import dev.villageheart.core.worker.WorkerData;
import dev.villageheart.core.worker.WorkerStatus;
import dev.villageheart.stock.StackKey;
import dev.villageheart.stock.Stockpiles;
import dev.villageheart.world.VillageCensus;
import dev.villageheart.world.VillageClaims;
import dev.villageheart.worker.WorkerAttachments;
import dev.villageheart.worker.WorkerBrain;
import dev.villageheart.worker.Workers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.npc.villager.Villager;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/**
 * Collects the plain facts the Heart screen shows (server thread, never loads chunks). One game world holds one village, so
 * the cache is keyed by the tick and the claim epoch; {@link #invalidate} drops it after an action.
 */
public final class HeartFacts {
	private HeartFacts() {}

	public static final TagKey<Item> NOT_VILLAGE_FOOD = TagKey.create(Registries.ITEM,
			Identifier.fromNamespaceAndPath("villageheart", "not_village_food"));

	/** Test hook: how many real collects ran. */
	public static int collectCount;

	private static MinecraftServer cachedServer;
	private static int cachedTick = -1;
	private static int cachedEpoch = -1;
	private static Optional<VillageFacts> cached = Optional.empty();

	/** Facts for this tick, collected at most once however many viewers ask. */
	public static Optional<VillageFacts> cached(MinecraftServer server) {
		int tick = server.getTickCount();
		int epoch = VillageClaims.data(server).epoch();
		if (cachedServer != server || cachedTick != tick || cachedEpoch != epoch) {
			cached = collect(server);
			cachedServer = server;
			cachedTick = tick;
			cachedEpoch = epoch;
		}
		return cached;
	}

	/** Forget everything (server stopping: the integrated server is reused between worlds). */
	public static void reset() {
		invalidate();
		cachedEpoch = -1;
	}

	public static void invalidate() {
		cachedServer = null;
		cachedTick = -1;
		cached = Optional.empty();
	}

	public static Optional<VillageFacts> collect(MinecraftServer server) {
		Optional<VillageState> opt = VillageClaims.current(server);
		if (opt.isEmpty()) return Optional.empty();
		collectCount++;
		VillageState state = opt.get();

		List<Villager> census = VillageCensus.of(server, state);
		Map<UUID, Villager> loaded = new HashMap<>();
		int babies = 0;
		for (Villager v : census) {
			loaded.put(v.getUUID(), v);
			if (v.isBaby()) babies++;
		}

		List<VillagerFact> villagers = new ArrayList<>();
		Set<UUID> workerIds = new HashSet<>();
		int adults = 0;
		int index = 0;
		for (WorkerRecord rec : state.workers()) {
			workerIds.add(rec.villager());
			Villager v = loaded.get(rec.villager());
			if (v == null) v = Workers.findLoaded(server, rec.villager()).orElse(null);
			WorkerStatus status = Workers.status(server, rec);
			if (v == null) {
				villagers.add(new VillagerFact(rec.villager(), "", index++, rec.jobId(), "", 0, 0, NeedsFlags.UNKNOWN,
						ActivityKind.AWAY, status == WorkerStatus.WORKPLACE_INVALID, false, false, false));
				if (!loaded.containsKey(rec.villager())) adults++;
				continue;
			}
			Optional<WorkerData> data = WorkerAttachments.get(v);
			int level = data.map(WorkerData::level).orElse(1);
			int xp = data.map(WorkerData::xp).orElse(0);
			NeedsFlags needs = data.map(d -> NeedsFlags.of(d.needs().fed(), d.needs().housed(), d.needs().paid()))
					.orElse(NeedsFlags.UNKNOWN);
			ActivityKind activity = switch (status) {
				case STORAGE_FULL -> ActivityKind.STORAGE_FULL;
				case STORAGE_UNREACHABLE -> ActivityKind.STORAGE_UNREACHABLE;
				default -> activityOf(v);
			};
			villagers.add(new VillagerFact(v.getUUID(), name(v), index++, rec.jobId(), professionOf(v), level, xp, needs, activity,
					status == WorkerStatus.WORKPLACE_INVALID, false, false, true));
			if (!v.isBaby()) adults++;
		}
		for (Villager v : census) {
			if (v.isBaby() || workerIds.contains(v.getUUID())) continue;
			adults++;
			boolean nitwit = v.getVillagerData().profession().is(VillagerProfession.NITWIT);
			boolean none = v.getVillagerData().profession().is(VillagerProfession.NONE);
			villagers.add(new VillagerFact(v.getUUID(), name(v), -1, "", professionOf(v), 0, 0, NeedsFlags.UNKNOWN, activityOf(v),
					false, nitwit, v.getVillagerXp() > 0 || (!none && !nitwit), true));
		}

		// Buildings, beds
		List<BuildingFact> buildings = new ArrayList<>();
		int beds = 0;
		for (Buildings.BuildingView b : Buildings.all(server)) {
			HeartLocation loc = VillageClaims.locate(b.level(), b.marker());
			Optional<Verdict> verdict = b.verdict();
			RowStatus status;
			if (verdict.isPresent() && verdict.get().deferred()) status = RowStatus.DEFERRED;
			else status = switch (verdict.map(Verdict::status).orElse(b.status())) {
				case VALID -> RowStatus.VALID;
				case INVALID -> RowStatus.INVALID;
				case UNCHECKED -> RowStatus.UNCHECKED;
			};
			List<TextLine> reasons = new ArrayList<>();
			verdict.ifPresent(vd -> vd.reasons().forEach(r -> reasons.add(new TextLine(r.translationKey(), r.message()))));
			Requirements.DetailLine line = BuildingRequirements.of(b.type()).detail();
			DetailKind detail = switch (line) {
				case WORKER_SLOTS -> DetailKind.WORKER_SLOTS;
				case HOMES -> DetailKind.HOMES;
				case CONTAINERS -> DetailKind.CONTAINERS;
			};
			int count = verdict.map(Verdict::slots).orElse(0);
			if (status == RowStatus.VALID && b.type() == BuildingType.HOUSE) beds += count;
			buildings.add(new BuildingFact(b.type().id(), loc.x(), loc.y(), loc.z(), status, reasons, detail, count,
					state.workersAt(loc).size()));
		}

		// Jobs and slots
		List<JobFact> jobs = new ArrayList<>();
		int workerSlots = 0;
		for (JobType job : JobType.values()) {
			List<SiteView> sites = Workers.jobSites().sites(server, job);
			for (SiteView s : sites) workerSlots += s.slots();
			jobs.add(new JobFact(job.id(), JobSlots.freeSlots(sites, state.workers()).size(),
					job.tier().ordinal() > state.tier().ordinal()));
		}

		return Optional.of(new VillageFacts(state.ownerName(), state.tier(), state.treasury(), adults, babies, beds, workerSlots,
				villagers, jobs, buildings, stock(server), List.of(), members(server, state)));
	}

	/** The owner, every known player and every online player the village has not seen yet (online Visitors are always rankable). */
	private static List<VillageFacts.MemberFact> members(MinecraftServer server, VillageState state) {
		List<VillageFacts.MemberFact> out = new ArrayList<>();
		Set<UUID> seen = new HashSet<>();
		out.add(member(server, state, state.owner()));
		seen.add(state.owner());
		for (dev.villageheart.core.KnownPlayer k : state.known()) {
			if (seen.add(k.id())) out.add(member(server, state, k.id()));
		}
		for (net.minecraft.server.level.ServerPlayer p : server.getPlayerList().getPlayers()) {
			if (seen.add(p.getUUID())) out.add(member(server, state, p.getUUID()));
		}
		return out;
	}

	private static VillageFacts.MemberFact member(MinecraftServer server, VillageState state, UUID id) {
		return new VillageFacts.MemberFact(id, dev.villageheart.rank.Members.displayName(server, state, id), state.rankOf(id),
				dev.villageheart.rank.Members.online(server, id) != null);
	}

	private static StockFacts stock(MinecraftServer server) {
		StockSnapshot<StackKey> snap = Stockpiles.snapshot(server);
		List<StockItemFact> items = new ArrayList<>();
		for (Map.Entry<String, Long> e : snap.totalsBy(StackKey::id).entrySet()) {
			Item item = BuiltInRegistries.ITEM.getValue(Identifier.parse(e.getKey()));
			FoodProperties food = item.components().get(DataComponents.FOOD);
			boolean village = !item.builtInRegistryHolder().is(NOT_VILLAGE_FOOD);
			items.add(new StockItemFact(e.getKey(), e.getValue(), food == null ? 0 : food.nutrition(), village));
		}
		return new StockFacts(items, snap.sites(), snap.unknownSites(), snap.containers(), snap.slots(), snap.freeSlots(),
				Stockpiles.alert(server));
	}

	private static String name(Villager v) {
		return v.hasCustomName() ? v.getCustomName().getString() : "";
	}

	private static String professionOf(Villager v) {
		return v.getVillagerData().profession().unwrapKey().map(k -> k.identifier().toString()).orElse("");
	}

	static ActivityKind activityOf(Villager v) {
		Optional<Activity> act = v.getBrain().getActiveNonCoreActivity();
		if (act.isEmpty()) return ActivityKind.OTHER;
		Activity a = act.get();
		if (a == Activity.WORK) return ActivityKind.WORKING;
		if (a == Activity.IDLE) return ActivityKind.IDLE;
		if (a == WorkerBrain.GO_HOME) return ActivityKind.GOING_HOME;
		if (a == Activity.REST) return v.isSleeping() ? ActivityKind.SLEEPING : ActivityKind.RESTING;
		if (a == Activity.PANIC) return ActivityKind.PANICKING;
		if (a == Activity.HIDE || a == Activity.RAID || a == Activity.PRE_RAID) return ActivityKind.HIDING;
		if (a == Activity.MEET) return ActivityKind.MEETING;
		return ActivityKind.OTHER;
	}
}
