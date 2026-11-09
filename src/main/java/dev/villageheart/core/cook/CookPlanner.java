package dev.villageheart.core.cook;

import dev.villageheart.core.Balance;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * What the Cook does next (M13 plan 3.3). Pure: the glue fills a {@link Stock} from the world and carries the decision out. First match
 * wins; the rows are in {@code docs/plans/M13.md}.
 */
public final class CookPlanner {
	private CookPlanner() {}

	/** Why a cook with nothing to do says so (the glue maps it to a worker status). */
	public enum CookHint { OK, NEEDS_FUEL, NO_WORK, TOO_HUNGRY }

	/** The result of the worker's last deposit trip. */
	public enum TripState { NONE, FULL, UNREACHABLE }

	/** The batch in progress, or a batch to start: {@code units} output items of {@code recipe} (still to make), burning {@code fuelId}. */
	public record Batch(CookRecipe recipe, int units, String fuelId) {}

	/**
	 * Everything the planner reads.
	 *
	 * @param available   item id to what the cook may take from the stockpile (stock minus what others reserved), component-free stacks only
	 * @param carried     item id to what the cook carries now
	 * @param fuelHeat    fuel item id to its heat (filled by the glue from {@link FuelTable} and the log tag)
	 * @param fuelOrder   fuel ids in burning order
	 * @param bank        heat in the smoker's bank
	 * @param dayTime     time of day, 0 to 23999
	 * @param speed       the work-speed multiplier (level x needs); 0 = too hungry or on strike
	 * @param walkTicks   estimated walking ticks of a batch (smoker, Storage, smoker, deposit)
	 * @param craftWalk   extra walking ticks of a CRAFT batch (the crafting table)
	 * @param freeSlots   empty slots in the cook's inventory
	 * @param current     the batch in progress
	 */
	public record Stock(Map<String, Long> available, Map<String, Integer> carried, Map<String, Integer> fuelHeat, List<String> fuelOrder,
			int bank, long dayTime, double speed, boolean kitchenValid, TripState trip, boolean returning, int freeSlots, int walkTicks,
			int craftWalk, Optional<Batch> current) {}

	public sealed interface CookDecision {
		/** A deposit trip runs (the cook's hands are emptied first). */
		record Yield() implements CookDecision {}

		/** Cook {@code units} items of {@code r}: the inputs and the heat (or fuel to load) are in hand. */
		record Cook(CookRecipe r, int units) implements CookDecision {}

		/** Fetch from the stockpile: the extra inputs (id to count) and {@code fuel} items of {@code fuelId}, for a batch of {@code units}. */
		record Fetch(CookRecipe r, int units, Map<String, Integer> inputs, String fuelId, int fuel) implements CookDecision {}

		/** Nothing to start; {@code returning}: hand back what is carried (it is no longer kept). */
		record Idle(CookHint hint, boolean returning) implements CookDecision {}
	}

	private static long avail(Stock s, String id) { return s.available().getOrDefault(id, 0L); }

	private static int carried(Stock s, String id) { return s.carried().getOrDefault(id, 0); }

	private static int carriedFuelHeat(Stock s) {
		int heat = 0;
		for (Map.Entry<String, Integer> e : s.fuelHeat().entrySet()) heat += carried(s, e.getKey()) * e.getValue();
		return heat;
	}

	private static boolean carriesAnyFuel(Stock s) {
		for (String id : s.fuelHeat().keySet()) if (carried(s, id) > 0) return true;
		return false;
	}

	/** The highest-priority recipe the carried items make at least one unit of; the batch in progress first. */
	private static Optional<CookRecipe> carriedRecipe(Stock s) {
		if (s.current().isPresent() && s.current().get().recipe().unitsFrom(s.carried()) >= 1) return Optional.of(s.current().get().recipe());
		for (CookRecipe r : CookRecipes.ALL) if (r.unitsFrom(s.carried()) >= 1) return Optional.of(r);
		return Optional.empty();
	}

	private static boolean carriesInputs(Stock s) {
		for (String id : CookRecipes.inputIds()) if (carried(s, id) > 0) return true;
		return false;
	}

	public static CookDecision decide(Stock s, boolean depositNow) {
		// 1: a Kitchen that is not valid
		if (!s.kitchenValid()) return new CookDecision.Idle(CookHint.OK, true);
		// 2: too hungry or on strike
		if (s.speed() <= 0) return new CookDecision.Idle(CookHint.TOO_HUNGRY, false);
		boolean storageBlocked = s.trip() != TripState.NONE;
		// 3: something to hand in
		if (depositNow && !storageBlocked) return new CookDecision.Yield();
		boolean inTime = s.dayTime() < Balance.COOK_DONE_BY;
		Optional<CookRecipe> carriedRecipe = carriedRecipe(s);
		int heatInHand = s.bank() + carriedFuelHeat(s);
		if (inTime) {
			// 4: resume what the pockets hold (also after a reload: the inventory is the state)
			if (carriedRecipe.isPresent() && heatInHand >= FuelTable.HEAT_PER_ITEM) {
				int units = Math.min(carriedRecipe.get().unitsFrom(s.carried()), heatInHand / FuelTable.HEAT_PER_ITEM);
				return new CookDecision.Cook(carriedRecipe.get(), units);
			}
		} else if (carriesInputs(s) || carriesAnyFuel(s)) {
			// 4b: dinner is near, everything goes back
			return new CookDecision.Idle(CookHint.OK, true);
		}
		// 5: inputs in hand but no heat
		if (inTime && carriedRecipe.isPresent()) {
			CookRecipe r = carriedRecipe.get();
			int units = Math.min(Balance.COOK_BATCH, r.unitsFrom(s.carried()));
			BatchMath.FuelPick pick = BatchMath.pickFuel(FuelTable.HEAT_PER_ITEM - heatInHand, s.fuelHeat(), s.fuelOrder(), s.available());
			if (pick.items() > 0 && (heatInHand + pick.totalHeat()) / FuelTable.HEAT_PER_ITEM >= 1) {
				int fit = Math.min(units, (heatInHand + pick.totalHeat()) / FuelTable.HEAT_PER_ITEM);
				BatchMath.FuelPick exact = BatchMath.pickFuel(fit * FuelTable.HEAT_PER_ITEM - heatInHand, s.fuelHeat(), s.fuelOrder(), s.available());
				return new CookDecision.Fetch(r, fit, Map.of(), exact.id(), exact.items());
			}
			return new CookDecision.Idle(CookHint.NEEDS_FUEL, true);
		}
		// 6: the last batch could not be stored
		if (storageBlocked) return new CookDecision.Idle(CookHint.OK, false);
		// 7: too late for any unit
		int byTime = BatchMath.unitsByTime(s.dayTime(), s.speed(), s.walkTicks());
		Optional<Batch> batch = nextBatch(s);
		if (byTime == 0) {
			// done for today; the reason does not vanish late in the day
			return new CookDecision.Idle(!cookable(s) ? CookHint.NO_WORK : fuelPossible(s) ? CookHint.OK : CookHint.NEEDS_FUEL, false);
		}
		// 8: start a batch
		if (batch.isPresent()) {
			Batch b = batch.get();
			Map<String, Integer> inputs = new LinkedHashMap<>();
			for (Ingredient i : b.recipe().inputs()) {
				int need = i.count() * b.units() - carried(s, i.itemId());
				if (need > 0) inputs.put(i.itemId(), need);
			}
			int haveFuel = b.fuelId().isEmpty() ? 0 : carried(s, b.fuelId());
			int fetchFuel = 0;
			if (!b.fuelId().isEmpty()) {
				int burn = s.fuelHeat().get(b.fuelId());
				fetchFuel = Math.max(0, BatchMath.fuelToFetch(b.units(), s.bank() + haveFuel * burn, burn));
			}
			return new CookDecision.Fetch(b.recipe(), b.units(), inputs, b.fuelId(), fetchFuel);
		}
		// 9: otherwise
		return new CookDecision.Idle(cookable(s) && !fuelPossible(s) ? CookHint.NEEDS_FUEL : CookHint.NO_WORK, false);
	}

	/** Heat for at least one unit: in the bank, in hand or in the stockpile. */
	private static boolean fuelPossible(Stock s) {
		long heat = s.bank();
		for (Map.Entry<String, Integer> e : s.fuelHeat().entrySet()) heat += (avail(s, e.getKey()) + carried(s, e.getKey())) * e.getValue();
		return heat >= FuelTable.HEAT_PER_ITEM;
	}

	/** Some recipe has at least one unit of inputs on hand or in stock (the fuel is another question). */
	private static boolean cookable(Stock s) {
		for (CookRecipe r : CookRecipes.ALL) {
			if (BatchMath.unitsBySlots(r, s.freeSlots()) < 1) continue;
			int units = Integer.MAX_VALUE;
			for (Ingredient i : r.inputs()) {
				units = (int) Math.min(units, (avail(s, i.itemId()) + carried(s, i.itemId())) / i.count());
			}
			if (units >= 1) return true;
		}
		return false;
	}

	/** The batch to start now: the first recipe with inputs, room and time, sized by inputs, time, slots and fuel; empty without fuel. */
	private static Optional<Batch> nextBatch(Stock s) {
		for (CookRecipe r : CookRecipes.ALL) {
			int units = Math.min(Balance.COOK_BATCH, BatchMath.unitsBySlots(r, s.freeSlots()));
			for (Ingredient i : r.inputs()) {
				units = (int) Math.min(units, (avail(s, i.itemId()) + carried(s, i.itemId())) / i.count());
			}
			int walk = s.walkTicks() + (r.kind() == RecipeKind.CRAFT ? s.craftWalk() : 0);
			units = Math.min(units, BatchMath.unitsByTime(s.dayTime(), s.speed(), walk));
			if (units < 1) continue;
			Map<String, Long> fuelOnHand = new LinkedHashMap<>();
			for (String id : s.fuelHeat().keySet()) fuelOnHand.put(id, avail(s, id) + carried(s, id));
			BatchMath.FuelPick pick = BatchMath.pickFuel(units * FuelTable.HEAT_PER_ITEM - s.bank(), s.fuelHeat(), s.fuelOrder(), fuelOnHand);
			units = Math.min(units, (s.bank() + pick.totalHeat()) / FuelTable.HEAT_PER_ITEM);
			if (units < 1) return Optional.empty(); // the fuel is shared by every recipe
			pick = BatchMath.pickFuel(units * FuelTable.HEAT_PER_ITEM - s.bank(), s.fuelHeat(), s.fuelOrder(), fuelOnHand);
			return Optional.of(new Batch(r, units, pick.items() == 0 ? "" : pick.id()));
		}
		return Optional.empty();
	}
}
