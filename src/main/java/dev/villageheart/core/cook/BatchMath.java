package dev.villageheart.core.cook;

import dev.villageheart.core.Balance;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.TreeMap;
import java.util.function.Predicate;

/** The arithmetic of a batch (M13 plan 3.2 #6): how many units the inputs, the time, the slots and the fuel allow, and which fuel to take. */
public final class BatchMath {
	private BatchMath() {}

	/** One planned batch: {@code units} of {@code recipe}, burning {@code fuelItems} of {@code fuelId} (none: 0) on top of the bank. */
	public record Step(CookRecipe recipe, int units, String fuelId, int fuelItems) {}

	/** The fuel chosen for a batch: {@code items} of {@code id} worth {@code heat} each. */
	public record FuelPick(String id, int items, int heat) {
		public static final FuelPick NONE = new FuelPick("", 0, 0);
		public int totalHeat() { return items * heat; }
	}

	/** What {@link #simulate} leaves behind. */
	public record Run(List<Step> steps, Map<String, Long> left, int bank) {}

	/**
	 * Units of work that can still end by {@link Balance#COOK_DONE_BY}: {@code floor((done - dayTime - walk) * speed / 125)}, never negative.
	 *
	 * @param dayTime the time of day, 0 to 23999
	 * @param walk    ticks the batch spends walking
	 */
	public static int unitsByTime(long dayTime, double speed, int walk) {
		if (speed <= 0) return 0;
		double ticks = Balance.COOK_DONE_BY - dayTime - walk;
		if (ticks <= 0) return 0;
		return (int) Math.floor(ticks * speed / Balance.COOK_TICKS_PER_ITEM + 1e-9);
	}

	/** Fuel items to fetch for {@code units} output items when the bank already holds {@code bank} heat; each worth {@code burn}. */
	public static int fuelToFetch(int units, int bank, int burn) {
		int need = units * FuelTable.HEAT_PER_ITEM - bank;
		return need <= 0 ? 0 : (need + burn - 1) / burn;
	}

	/** The stack size of an output (beetroot soup does not stack). */
	public static int stackSize(String itemId) { return itemId.equals("minecraft:beetroot_soup") ? 1 : 64; }

	/** The most outputs a batch of {@code r} can hold with {@code freeSlots} empty slots: the inputs, one fuel slot and the outputs must fit. */
	public static int unitsBySlots(CookRecipe r, int freeSlots) {
		int inputSlots = 0;
		for (Ingredient i : r.inputs()) inputSlots += (i.count() * Balance.COOK_BATCH + 63) / 64;
		int room = freeSlots - inputSlots - 1;
		return room <= 0 ? 0 : room * stackSize(r.outputId());
	}

	/**
	 * Which fuel for a batch that needs {@code needHeat} more heat than the bank holds: the first kind in {@code order} that covers all of it,
	 * else the kind that covers the most (the batch then shrinks to it).
	 *
	 * @param heat  fuel id to heat per item
	 * @param count fuel id to items on hand
	 */
	public static FuelPick pickFuel(int needHeat, Map<String, Integer> heat, List<String> order, Map<String, ? extends Number> count) {
		if (needHeat <= 0) return FuelPick.NONE;
		FuelPick best = FuelPick.NONE;
		for (String id : order) {
			Integer h = heat.get(id);
			Number c = count.get(id);
			if (h == null || h <= 0 || c == null || c.longValue() <= 0) continue;
			long have = Math.min(c.longValue(), Integer.MAX_VALUE / 4);
			if (have * h >= needHeat) return new FuelPick(id, (needHeat + h - 1) / h, h);
			if (have * h > best.totalHeat()) best = new FuelPick(id, (int) have, h);
		}
		return best;
	}

	/** The fuel order: the configured fuels first, then every other fuel by id (the logs). */
	public static List<String> fuelOrder(java.util.Collection<String> fuelIds) {
		List<String> order = new ArrayList<>();
		for (String id : Balance.COOK_FUEL_ORDER) if (fuelIds.contains(id)) order.add(id);
		fuelIds.stream().filter(id -> !order.contains(id)).sorted().forEach(order::add);
		return order;
	}

	/**
	 * The L1 oracle of the L2 counts: batch after batch, ignoring walking, time and slots. Fuel ids are the stock entries that are coal,
	 * charcoal or (by {@code burnableLog}) logs.
	 */
	public static Run simulate(Map<String, Long> stock, int bank, Predicate<String> burnableLog) {
		Map<String, Long> left = new TreeMap<>(stock);
		Map<String, Integer> heat = new HashMap<>();
		for (String id : stock.keySet()) {
			OptionalInt h = FuelTable.heat(id, burnableLog.test(id));
			if (h.isPresent()) heat.put(id, h.getAsInt());
		}
		List<String> order = fuelOrder(heat.keySet());
		List<Step> steps = new ArrayList<>();
		int heatBank = bank;
		for (int guard = 0; guard < 1000; guard++) {
			Step step = null;
			for (CookRecipe r : CookRecipes.ALL) {
				int n = Math.min(Balance.COOK_BATCH, r.unitsFrom(left));
				if (n < 1) continue;
				FuelPick pick = pickFuel(n * FuelTable.HEAT_PER_ITEM - heatBank, heat, order, left);
				n = Math.min(n, (heatBank + pick.totalHeat()) / FuelTable.HEAT_PER_ITEM);
				if (n < 1) continue; // no fuel for any unit of this recipe, nor for the others (the fuel is shared)
				// the fuel may now be more than the shrunken batch needs
				pick = pickFuel(n * FuelTable.HEAT_PER_ITEM - heatBank, heat, order, left);
				step = new Step(r, n, pick.items() == 0 ? "" : pick.id(), pick.items());
				heatBank += pick.totalHeat() - n * FuelTable.HEAT_PER_ITEM;
				break;
			}
			if (step == null) break;
			for (Ingredient i : step.recipe().inputs()) left.merge(i.itemId(), -(long) i.count() * step.units(), Long::sum);
			if (step.fuelItems() > 0) left.merge(step.fuelId(), -(long) step.fuelItems(), Long::sum);
			steps.add(step);
		}
		left.values().removeIf(v -> v <= 0);
		return new Run(List.copyOf(steps), left, heatBank);
	}
}
