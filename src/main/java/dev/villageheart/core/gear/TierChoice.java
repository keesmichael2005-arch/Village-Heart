package dev.villageheart.core.gear;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Which tier a worker makes (M19a plan 3.2 #5). M19 adds {@code best(...)} for the smith. */
public final class TierChoice {
	private TierChoice() {}

	/** Stone if affordable, else wooden, else empty. Never copper or better, whatever the stockpile holds. */
	public static Optional<CraftPlan> selfMade(GearNeed need, Materials m) {
		Optional<CraftPlan> stone = plan(ToolRecipes.of(need, CraftTier.STONE), m);
		if (stone.isPresent()) return stone;
		return plan(ToolRecipes.of(need, CraftTier.WOODEN), m);
	}

	/** How many planks (counting a log as 4) the cheapest, wooden, recipe is short of; zero when it is affordable. */
	public static Deficit deficit(GearNeed need, Materials m) {
		ToolRecipe r = ToolRecipes.of(need, CraftTier.WOODEN);
		int batches = m.total(MaterialRole.STICK) >= r.sticks() ? 0 : batchesFor(r.sticks());
		long planksNeeded = r.head() + (long) batches * ToolRecipes.PLANKS_PER_STICK_BATCH;
		long have = m.total(MaterialRole.PLANKS) + m.total(MaterialRole.LOGS) * ToolRecipes.PLANKS_PER_LOG;
		return new Deficit(need, "wood", (int) Math.max(0, planksNeeded - have));
	}

	private static int batchesFor(int sticks) { return (sticks + ToolRecipes.STICKS_PER_BATCH - 1) / ToolRecipes.STICKS_PER_BATCH; }

	/** Takes {@code n} from the items of a role in list order; empty when fewer are available. */
	private static Optional<List<ItemCount>> takeFrom(Materials m, MaterialRole role, long n) {
		List<ItemCount> out = new ArrayList<>();
		long left = n;
		for (StockItem i : m.items()) {
			if (left <= 0) break;
			if (i.role() != role || i.available() <= 0) continue;
			int t = (int) Math.min(left, i.available());
			out.add(new ItemCount(i.id(), t));
			left -= t;
		}
		return left > 0 ? Optional.empty() : Optional.of(out);
	}

	private static Optional<CraftPlan> plan(ToolRecipe r, Materials m) {
		boolean wooden = r.tier() == CraftTier.WOODEN;
		List<ItemCount> take = new ArrayList<>();
		List<ItemCount> leftovers = new ArrayList<>();
		// the head
		long planksNeeded = 0;
		if (wooden) planksNeeded += r.head();
		else {
			Optional<List<ItemCount>> head = takeFrom(m, MaterialRole.STONE, r.head());
			if (head.isEmpty()) return Optional.empty();
			take.addAll(head.get());
		}
		// the sticks: from Storage if it has enough, else a conversion of planks, never a mix
		int batches = 0;
		if (m.total(MaterialRole.STICK) >= r.sticks()) {
			take.addAll(takeFrom(m, MaterialRole.STICK, r.sticks()).orElseThrow());
		} else {
			batches = batchesFor(r.sticks());
			planksNeeded += (long) batches * ToolRecipes.PLANKS_PER_STICK_BATCH;
			int made = batches * ToolRecipes.STICKS_PER_BATCH;
			if (made > r.sticks()) leftovers.add(new ItemCount(ToolRecipes.STICK, made - r.sticks()));
		}
		// the planks, then logs for the rest
		int logs = 0;
		if (planksNeeded > 0) {
			long fromPlanks = Math.min(planksNeeded, m.total(MaterialRole.PLANKS));
			if (fromPlanks > 0) take.addAll(takeFrom(m, MaterialRole.PLANKS, fromPlanks).orElseThrow());
			long missing = planksNeeded - fromPlanks;
			if (missing > 0) {
				long logsNeeded = (missing + ToolRecipes.PLANKS_PER_LOG - 1) / ToolRecipes.PLANKS_PER_LOG;
				Optional<List<ItemCount>> logTake = takeFrom(m, MaterialRole.LOGS, logsNeeded);
				if (logTake.isEmpty()) return Optional.empty();
				take.addAll(logTake.get());
				logs = (int) logsNeeded;
				long extra = logsNeeded * ToolRecipes.PLANKS_PER_LOG - missing;
				if (extra > 0) {
					String last = logTake.get().get(logTake.get().size() - 1).id();
					Optional<String> planks = PlankTable.planksOf(last);
					if (planks.isEmpty()) return Optional.empty();
					leftovers.add(new ItemCount(planks.get(), (int) extra));
				}
			}
		}
		return Optional.of(new CraftPlan(r, take, batches, logs, leftovers));
	}
}
