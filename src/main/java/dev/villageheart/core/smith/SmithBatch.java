package dev.villageheart.core.smith;

import dev.villageheart.core.Balance;
import dev.villageheart.core.cook.BatchMath;
import dev.villageheart.core.cook.FuelTable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** How many ingots one smelt batch makes, and which fuel it burns. The cook's reserve is left untouched. */
public final class SmithBatch {
	private SmithBatch() {}

	/** {@code units} ingots of {@code outputId}, burning {@code fuelItems} of {@code fuelId} (empty when the bank covers it). */
	public record Step(String outputId, int units, String fuelId, int fuelItems) {}

	/** A planned batch, or no batch. {@code needsFuel} is set only when raw ore is waiting and fuel is the reason it waits. */
	public record Plan(Optional<Step> step, boolean needsFuel) {}

	/** Heat the smith may burn. A hired cook keeps {@link Balance#SMITH_FUEL_RESERVE}. */
	public static int availableHeat(Map<String, Integer> fuelCount, Map<String, Integer> heatEach, boolean cookHired) {
		int heat = 0;
		for (var e : fuelCount.entrySet()) heat += e.getValue() * heatEach.getOrDefault(e.getKey(), 0);
		if (cookHired) heat = Math.max(0, heat - Balance.SMITH_FUEL_RESERVE);
		return heat;
	}

	/**
	 * The next smelt batch. {@code freeSlots} caps it (a full inventory smelts nothing). Time left before {@link Balance#SMITH_DONE_BY}
	 * caps it too. Fuel is charcoal, then coal, then anything else by id.
	 */
	public static Plan plan(Map<String, Long> available, Map<String, Integer> fuelCount, Map<String, Integer> heatEach, int bank,
			boolean cookHired, int freeSlots, long dayTime, double speed, int walkTicks) {
		String output = null;
		long oreCount = 0;
		for (SmithRecipe r : SmithRecipes.ALL) {
			if (r.kind() != SmithKind.SMELT) continue;
			long have = available.getOrDefault(r.inputs().get(0).itemId(), 0L);
			if (have > oreCount) {
				oreCount = have;
				output = r.outputId();
			}
		}
		if (output == null || oreCount <= 0) return new Plan(Optional.empty(), false);
		if (freeSlots <= 0 || speed <= 0 || dayTime >= Balance.SMITH_DONE_BY) return new Plan(Optional.empty(), false);
		int bankHeat = Math.max(0, bank);
		int byHeat = (availableHeat(fuelCount, heatEach, cookHired) + bankHeat) / FuelTable.HEAT_PER_ITEM;
		int byTime = (int) Math.floor((Balance.SMITH_DONE_BY - dayTime - walkTicks) * speed / Balance.SMITH_SMELT_TICKS);
		if (byTime < 0) byTime = 0;
		int units = (int) Math.min(oreCount, Math.min(Balance.SMITH_BATCH, Math.min(freeSlots, Math.min(byHeat, byTime))));
		if (units < 1) return new Plan(Optional.empty(), byHeat < 1 && byTime > 0);
		List<String> order = BatchMath.fuelOrder(heatEach.keySet());
		int need = units * FuelTable.HEAT_PER_ITEM - Math.min(bankHeat, units * FuelTable.HEAT_PER_ITEM);
		BatchMath.FuelPick pick = BatchMath.pickFuel(need, heatEach, order, fuelCount);
		if (need > 0 && pick.totalHeat() < need) {
			units = Math.min(units, (bankHeat + pick.totalHeat()) / FuelTable.HEAT_PER_ITEM);
			if (units < 1) return new Plan(Optional.empty(), true);
			need = units * FuelTable.HEAT_PER_ITEM - Math.min(bankHeat, units * FuelTable.HEAT_PER_ITEM);
			pick = BatchMath.pickFuel(need, heatEach, order, fuelCount);
		}
		String fuelId = need > 0 ? pick.id() : "";
		int fuelItems = need > 0 ? pick.items() : 0;
		return new Plan(Optional.of(new Step(output, units, fuelId, fuelItems)), false);
	}
}
