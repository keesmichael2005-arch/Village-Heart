package dev.villageheart.core.heart;

import dev.villageheart.core.Tier;
import dev.villageheart.core.stock.StorageAlert;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Plain facts about the village that the glue collects from the world; {@link HeartSnapshotBuilder} turns them into a
 * snapshot per viewer. No Minecraft and no worker types in here.
 *
 * @param adults       all adults counted as population, workers that are not loaded included
 * @param babies       baby villagers (never listed as rows)
 * @param beds         homes of the valid Houses
 * @param workerSlots  worker slots over all valid workplaces
 * @param villagers    workers (any order, see {@link VillagerFact#hiringIndex}) and unemployed adults
 * @param extraAlerts  alerts of later milestones (M11, M12, M25), appended after the built-in ones
 * @param members      the owner, every known player and every online player (M24); shown only to viewers who manage ranks
 * @param wageBill     what the next wage run costs: the wages of every registered worker, loaded or not (M12)
 * @param strikers     registered workers that are on strike (M12)
 * @param population   the Overview's population: loaded villagers in the claim plus registered workers that are not loaded there (M14)
 * @param tierReport   the next tier's goals with progress (M14)
 */
public record VillageFacts(String ownerName, Tier tier, int treasury, int adults, int babies, int beds, int workerSlots,
		List<VillagerFact> villagers, List<JobFact> jobs, List<BuildingFact> buildings, StockFacts stock,
		List<AlertLine> extraAlerts, List<MemberFact> members, List<BuildRow> builds, int wageBill, int strikers,
		int population, dev.villageheart.core.tier.TierReport tierReport) {
	/** Without the population and the tier checklist (the shape before M14): population is adults plus babies, the report has no live inputs. */
	public VillageFacts(String ownerName, Tier tier, int treasury, int adults, int babies, int beds, int workerSlots,
			List<VillagerFact> villagers, List<JobFact> jobs, List<BuildingFact> buildings, StockFacts stock,
			List<AlertLine> extraAlerts, List<MemberFact> members, List<BuildRow> builds, int wageBill, int strikers) {
		this(ownerName, tier, treasury, adults, babies, beds, workerSlots, villagers, jobs, buildings, stock, extraAlerts, members, builds, wageBill,
				strikers, adults + babies, dev.villageheart.core.tier.TierGoals.evaluate(tier, dev.villageheart.core.tier.TierInputs.EMPTY,
						dev.villageheart.core.tier.TierProgress.INITIAL));
	}

	/** Without the wage figures (the shape before M12). */
	public VillageFacts(String ownerName, Tier tier, int treasury, int adults, int babies, int beds, int workerSlots,
			List<VillagerFact> villagers, List<JobFact> jobs, List<BuildingFact> buildings, StockFacts stock,
			List<AlertLine> extraAlerts, List<MemberFact> members, List<BuildRow> builds) {
		this(ownerName, tier, treasury, adults, babies, beds, workerSlots, villagers, jobs, buildings, stock, extraAlerts, members, builds, 0, 0);
	}

	/** Without builds (the shape before M20). */
	public VillageFacts(String ownerName, Tier tier, int treasury, int adults, int babies, int beds, int workerSlots,
			List<VillagerFact> villagers, List<JobFact> jobs, List<BuildingFact> buildings, StockFacts stock,
			List<AlertLine> extraAlerts, List<MemberFact> members) {
		this(ownerName, tier, treasury, adults, babies, beds, workerSlots, villagers, jobs, buildings, stock, extraAlerts, members, List.of());
	}

	public VillageFacts {
		Objects.requireNonNull(ownerName, "ownerName");
		Objects.requireNonNull(tier, "tier");
		Objects.requireNonNull(stock, "stock");
		Objects.requireNonNull(tierReport, "tierReport");
		villagers = List.copyOf(villagers);
		jobs = List.copyOf(jobs);
		buildings = List.copyOf(buildings);
		extraAlerts = List.copyOf(extraAlerts);
		members = List.copyOf(members);
		builds = List.copyOf(builds);
	}

	/**
	 * @param customName  "" when the villager has no name tag
	 * @param hiringIndex position in hiring order for a worker, -1 for an unemployed villager
	 * @param jobId       "" for none
	 * @param level       0 when not a worker
	 */
	public record VillagerFact(UUID id, String customName, int hiringIndex, String jobId, String vanillaProfession, int level,
			int xp, NeedsFlags needs, ActivityKind activity, boolean workplaceInvalid, boolean nitwit, boolean hasTraded,
			boolean loaded, GuardShiftView shift) {
		/** A villager that is no guard (the shape before M18). */
		public VillagerFact(UUID id, String customName, int hiringIndex, String jobId, String vanillaProfession, int level, int xp, NeedsFlags needs,
				ActivityKind activity, boolean workplaceInvalid, boolean nitwit, boolean hasTraded, boolean loaded) {
			this(id, customName, hiringIndex, jobId, vanillaProfession, level, xp, needs, activity, workplaceInvalid, nitwit, hasTraded, loaded, GuardShiftView.NONE);
		}

		public VillagerFact {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(customName, "customName");
			Objects.requireNonNull(jobId, "jobId");
			Objects.requireNonNull(vanillaProfession, "vanillaProfession");
			Objects.requireNonNull(needs, "needs");
			Objects.requireNonNull(activity, "activity");
			Objects.requireNonNull(shift, "shift");
		}

		public boolean worker() { return hiringIndex >= 0; }
	}

	/** One player of the Ranks tab: the owner, a known player, or an online player the village has not seen yet. */
	public record MemberFact(UUID id, String name, dev.villageheart.core.Rank rank, boolean online) {
		public MemberFact {
			Objects.requireNonNull(id, "id");
			Objects.requireNonNull(name, "name");
			Objects.requireNonNull(rank, "rank");
		}
	}

	public record JobFact(String jobId, int freeSlots, boolean tierLocked) {
		public JobFact { Objects.requireNonNull(jobId, "jobId"); }
	}

	public record BuildingFact(String typeId, int x, int y, int z, RowStatus status, List<TextLine> reasons, DetailKind detail,
			int detailCount, int workersHere) {
		public BuildingFact {
			Objects.requireNonNull(typeId, "typeId");
			Objects.requireNonNull(status, "status");
			Objects.requireNonNull(detail, "detail");
			reasons = List.copyOf(reasons);
		}
	}

	/** @param items one entry per item id (variants already merged) */
	public record StockFacts(List<StockItemFact> items, int storages, int unknownStorages, int containers, int slots,
			int freeSlots, StorageAlert alert, dev.villageheart.core.market.Reserves reserves, int soldToday, int merchants) {
		public StockFacts {
			items = List.copyOf(items);
			Objects.requireNonNull(alert, "alert");
			Objects.requireNonNull(reserves, "reserves");
		}

		/** Without the market's figures (the shape before M22). */
		public StockFacts(List<StockItemFact> items, int storages, int unknownStorages, int containers, int slots, int freeSlots, StorageAlert alert) {
			this(items, storages, unknownStorages, containers, slots, freeSlots, alert, dev.villageheart.core.market.Reserves.DEFAULT, 0, 0);
		}
	}

	/**
	 * @param foodNutrition the item's nutrition, 0 when it is not food
	 * @param villageFood   false for foods the village does not eat (tag {@code #villageheart:not_village_food})
	 * @param forSale       how many the market may sell now (M22)
	 */
	public record StockItemFact(String itemId, long count, int foodNutrition, boolean villageFood, long forSale) {
		public StockItemFact { Objects.requireNonNull(itemId, "itemId"); }

		/** Without the market's figure (the shape before M22). */
		public StockItemFact(String itemId, long count, int foodNutrition, boolean villageFood) { this(itemId, count, foodNutrition, villageFood, 0); }
	}
}
