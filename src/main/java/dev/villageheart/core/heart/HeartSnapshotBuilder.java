package dev.villageheart.core.heart;

import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.heart.VillageFacts.BuildingFact;
import dev.villageheart.core.heart.VillageFacts.JobFact;
import dev.villageheart.core.heart.VillageFacts.StockFacts;
import dev.villageheart.core.heart.VillageFacts.StockItemFact;
import dev.villageheart.core.heart.VillageFacts.VillagerFact;
import dev.villageheart.core.job.Leveling;
import dev.villageheart.core.job.Progress;
import dev.villageheart.core.stock.StorageAlert;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Turns plain facts into the snapshot one viewer is allowed to see (DESIGN section 2 for the ranks, section 10). */
public final class HeartSnapshotBuilder {
	private HeartSnapshotBuilder() {}

	private static final String ALERT = "gui.villageheart.heart.alert.";

	public static HeartSnapshot build(VillageFacts f, Rank viewer) {
		Stock stock = stock(f.stock());
		boolean details = Permissions.allows(viewer, Permission.VIEW_DETAILS);
		List<VillagerRow> villagers = List.of();
		int moreVillagers = 0;
		List<BuildingRow> buildings = List.of();
		int moreBuildings = 0;
		List<BuildRow> builds = List.of();
		List<JobChoice> choices = List.of();
		if (details) {
			List<VillagerRow> all = villagerRows(f.villagers(), f.tier());
			villagers = all.subList(0, Math.min(all.size(), HeartLimits.MAX_VILLAGERS));
			moreVillagers = all.size() - villagers.size();
			List<BuildingRow> allB = buildingRows(f.buildings());
			buildings = allB.subList(0, Math.min(allB.size(), HeartLimits.MAX_BUILDINGS));
			moreBuildings = allB.size() - buildings.size();
			builds = buildRows(f.builds());
		} else {
			stock = Stock.EMPTY;
		}
		if (Permissions.allows(viewer, Permission.HIRE)) {
			List<JobChoice> c = new ArrayList<>();
			for (JobFact j : f.jobs()) {
				if (c.size() == HeartLimits.MAX_JOB_CHOICES) break;
				c.add(new JobChoice(HeartLimits.cut(j.jobId(), HeartLimits.MAX_JOB_ID), j.freeSlots(), j.tierLocked()));
			}
			choices = c;
		}
		List<MemberRow> members = List.of();
		int moreMembers = 0;
		if (Permissions.allows(viewer, Permission.MANAGE_RANKS)) {
			List<MemberRow> all = memberRows(f.members());
			members = all.subList(0, Math.min(all.size(), HeartLimits.MAX_MEMBERS));
			moreMembers = all.size() - members.size();
		}
		return new HeartSnapshot(viewer, overview(f), villagers, moreVillagers, choices, buildings, moreBuildings, stock, members,
				moreMembers, builds);
	}

	/** At most {@link HeartLimits#MAX_BUILDS} rows, each with at most {@link HeartLimits#MAX_BUILD_MISSING} missing lines and cut names. */
	private static List<BuildRow> buildRows(List<BuildRow> in) {
		List<BuildRow> out = new ArrayList<>();
		for (BuildRow r : in) {
			if (out.size() == HeartLimits.MAX_BUILDS) break;
			out.add(new BuildRow(HeartLimits.cut(r.blueprintId(), HeartLimits.MAX_ID), r.x(), r.y(), r.z(), r.state(), r.placed(), r.total(), r.status(),
					r.missing().subList(0, Math.min(r.missing().size(), HeartLimits.MAX_BUILD_MISSING)), HeartLimits.cut(r.builderName(), HeartLimits.MAX_NAME)));
		}
		return out;
	}

	// ---- overview -----------------------------------------------------------------------------------------------------

	private static Overview overview(VillageFacts f) {
		long nutrition = 0;
		for (StockItemFact i : f.stock().items()) nutrition += i.count() * nutritionEach(i);
		int workers = 0;
		int invalidWorkplaces = 0;
		for (VillagerFact v : f.villagers()) {
			if (!v.worker()) continue;
			workers++;
			if (v.workplaceInvalid()) invalidWorkplaces++;
		}
		int invalidBuildings = 0;
		for (BuildingFact b : f.buildings()) if (b.status() == RowStatus.INVALID) invalidBuildings++;

		List<AlertLine> alerts = new ArrayList<>();
		StockFacts s = f.stock();
		if (s.alert() == StorageAlert.NO_STORAGE) {
			alerts.add(new AlertLine(AlertCode.NO_STORAGE, Severity.WARNING,
					new TextLine(ALERT + "no_storage", "No Storage: items have nowhere to go")));
		} else if (s.alert() == StorageAlert.FULL) {
			TextLine text = s.unknownStorages() > 0
					? new TextLine(ALERT + "storage_full_unchecked",
							"Storage full: no empty slot in any readable Storage (%s Storage not checked yet)",
							List.of(Integer.toString(s.unknownStorages())))
					: new TextLine(ALERT + "storage_full", "Storage full: no empty slot in any readable Storage");
			alerts.add(new AlertLine(AlertCode.STORAGE_FULL, Severity.WARNING, text));
		}
		if (invalidBuildings > 0) {
			alerts.add(new AlertLine(AlertCode.INVALID_BUILDINGS, Severity.WARNING, invalidBuildings == 1
					? new TextLine(ALERT + "invalid_building", "1 building needs attention")
					: new TextLine(ALERT + "invalid_buildings", "%s buildings need attention",
							List.of(Integer.toString(invalidBuildings)))));
		}
		if (invalidWorkplaces > 0) {
			alerts.add(new AlertLine(AlertCode.WORKPLACE_INVALID, Severity.DANGER, invalidWorkplaces == 1
					? new TextLine(ALERT + "workplace_invalid_one", "1 worker has no valid workplace")
					: new TextLine(ALERT + "workplace_invalid", "%s workers have no valid workplace",
							List.of(Integer.toString(invalidWorkplaces)))));
		}
		alerts.addAll(dev.villageheart.core.economy.WageAlerts.of(f.treasury(), f.wageBill(), f.strikers()));
		alerts.addAll(f.extraAlerts());
		// A raid is first, then a siege, then damaged walls (M25). Everything else stays severity-first (M12): the cap of
		// MAX_ALERTS must cut a warning, never a strike or an invalid workplace.
		alerts.sort(Comparator.comparingInt(HeartSnapshotBuilder::alertRank)
				.thenComparingInt(a -> a.severity() == Severity.DANGER ? 0 : 1));
		int more = Math.max(0, alerts.size() - HeartLimits.MAX_ALERTS);
		List<AlertLine> shown = alerts.subList(0, alerts.size() - more);

		return new Overview(HeartLimits.cut(f.ownerName(), HeartLimits.MAX_NAME), f.tier(), f.tierReport().next(), goals(f.tierReport()),
				f.population(), f.adults(), f.babies(), f.beds(), workers, f.workerSlots(),
				FoodDays.tenths(nutrition, f.adults(), f.babies()), f.treasury(), f.wageBill(), shown, more, deathRows(f),
				Math.max(0, f.deathsLogged()));
	}

	/** The newest death rows, re-cut, and never more than the screen shows. */
	private static List<DeathRow> deathRows(VillageFacts f) {
		List<DeathRow> out = new ArrayList<>();
		for (DeathRow d : f.recentDeaths()) {
			if (out.size() == HeartLimits.MAX_DEATH_ROWS) break;
			out.add(new DeathRow(d.name(), d.jobId(), d.cause(), d.day(), d.duringRaid()));
		}
		return List.copyOf(out);
	}

	/** Raid, then siege, then damaged walls, then the alerts that were already here. */
	private static int alertRank(AlertLine alert) {
		return switch (alert.code()) {
			case RAID -> 0;
			case SIEGE -> 1;
			case DEFENCE_DAMAGED -> 2;
			default -> 3;
		};
	}

	/** One line per goal of the next tier, in checklist order (M14). Two arguments at most. */
	private static List<GoalLine> goals(dev.villageheart.core.tier.TierReport report) {
		List<GoalLine> out = new ArrayList<>();
		for (dev.villageheart.core.tier.Goal g : report.goals()) {
			String key = HeartTexts.PREFIX + "goal." + g.id().name().toLowerCase(java.util.Locale.ROOT);
			String fallback = HeartTexts.EN.get(key);
			TextLine label = switch (g.id()) {
				case STORAGE, FARMHOUSE, KITCHEN -> new TextLine(key, fallback);
				case DAWN_TREASURY -> new TextLine(key, fallback, List.of(Integer.toString(Math.min(g.have(), g.need())), Integer.toString(g.need())));
				default -> new TextLine(key, fallback, List.of(Integer.toString(g.have()), Integer.toString(g.need())));
			};
			out.add(new GoalLine(label, g.have(), g.need(), g.met()));
		}
		return out;
	}

	private static int nutritionEach(StockItemFact i) { return i.villageFood() ? Math.max(0, i.foodNutrition()) : 0; }

	// ---- villagers ----------------------------------------------------------------------------------------------------

	private static String nameOf(VillagerFact v) {
		String n = v.customName().isBlank() ? VillagerNames.of(v.id()) : v.customName();
		return HeartLimits.cut(n, HeartLimits.MAX_NAME);
	}

	private static List<VillagerRow> villagerRows(List<VillagerFact> facts, Tier tier) {
		List<VillagerFact> sorted = new ArrayList<>(facts);
		Comparator<VillagerFact> order = Comparator
				.comparingInt((VillagerFact v) -> v.worker() ? 0 : v.nitwit() ? 2 : 1)
				.thenComparingInt(v -> v.worker() ? v.hiringIndex() : 0)
				.thenComparing(HeartSnapshotBuilder::nameOf)
				.thenComparing(VillagerFact::id);
		sorted.sort(order);
		List<VillagerRow> rows = new ArrayList<>(sorted.size());
		for (VillagerFact v : sorted) {
			boolean worker = v.worker();
			int level = worker ? Math.max(1, v.level()) : 0;
			rows.add(new VillagerRow(v.id(), nameOf(v), worker ? HeartLimits.cut(v.jobId(), HeartLimits.MAX_ID) : "",
					HeartLimits.cut(v.vanillaProfession(), HeartLimits.MAX_ID), level, worker ? v.xp() : 0,
					worker ? JobLevels.xpForNext(level) : 0, worker ? v.needs() : NeedsFlags.UNKNOWN, v.activity(),
					v.workplaceInvalid(), v.nitwit(), v.hasTraded(), v.loaded(),
					worker && Leveling.capped(new Progress(Math.min(3, level), v.xp()), tier), worker ? v.shift() : GuardShiftView.NONE));
		}
		return rows;
	}

	// ---- buildings ----------------------------------------------------------------------------------------------------

	private static int group(RowStatus s) {
		return switch (s) {
			case INVALID -> 0;
			case UNCHECKED, DEFERRED -> 1;
			case VALID -> 2;
		};
	}

	private static int typeOrder(String typeId) {
		return BuildingType.byId(typeId).map(Enum::ordinal).orElse(Integer.MAX_VALUE);
	}

	private static List<BuildingRow> buildingRows(List<BuildingFact> facts) {
		List<BuildingFact> sorted = new ArrayList<>(facts);
		sorted.sort(Comparator.comparingInt((BuildingFact b) -> group(b.status()))
				.thenComparingInt(b -> typeOrder(b.typeId())).thenComparing(BuildingFact::typeId)
				.thenComparingInt(BuildingFact::x).thenComparingInt(BuildingFact::y).thenComparingInt(BuildingFact::z));
		List<BuildingRow> rows = new ArrayList<>(sorted.size());
		for (BuildingFact b : sorted) {
			int shown = Math.min(b.reasons().size(), HeartLimits.MAX_REASONS);
			rows.add(new BuildingRow(HeartLimits.cut(b.typeId(), HeartLimits.MAX_ID), b.x(), b.y(), b.z(), b.status(),
					b.reasons().subList(0, shown), b.reasons().size() - shown, b.detail(), b.detailCount(), b.workersHere()));
		}
		return rows;
	}

	// ---- members ------------------------------------------------------------------------------------------------------

	/** Owner, Stewards, Residents, Visitors; within a rank online first, then name (case-insensitive), then id. */
	private static List<MemberRow> memberRows(List<VillageFacts.MemberFact> members) {
		List<VillageFacts.MemberFact> sorted = new ArrayList<>(members);
		sorted.sort(Comparator.comparingInt((VillageFacts.MemberFact m) -> m.rank().ordinal())
				.thenComparing((VillageFacts.MemberFact m) -> !m.online())
				.thenComparing(VillageFacts.MemberFact::name, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(VillageFacts.MemberFact::id));
		List<MemberRow> rows = new ArrayList<>(sorted.size());
		for (VillageFacts.MemberFact m : sorted) {
			rows.add(new MemberRow(m.id(), HeartLimits.cut(m.name(), HeartLimits.MAX_NAME), m.rank(), m.online()));
		}
		return rows;
	}

	// ---- stock --------------------------------------------------------------------------------------------------------

	private static Stock stock(StockFacts s) {
		List<StockItemFact> sorted = new ArrayList<>(s.items());
		sorted.sort(Comparator.comparingLong(StockItemFact::count).reversed().thenComparing(StockItemFact::itemId));
		long total = 0;
		long nutrition = 0;
		for (StockItemFact i : sorted) {
			total += i.count();
			nutrition += i.count() * nutritionEach(i);
		}
		int shown = Math.min(sorted.size(), HeartLimits.MAX_STOCK_KINDS);
		List<StockLine> lines = new ArrayList<>(shown);
		for (int k = 0; k < shown; k++) {
			StockItemFact i = sorted.get(k);
			lines.add(new StockLine(HeartLimits.cut(i.itemId(), HeartLimits.MAX_ID), i.count(), nutritionEach(i), i.forSale()));
		}
		return new Stock(lines, sorted.size() - shown, total, nutrition, s.storages(), s.unknownStorages(), s.containers(),
				s.freeSlots(), s.slots(), s.alert(), s.reserves(), s.soldToday(), s.merchants());
	}
}
