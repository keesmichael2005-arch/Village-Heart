package dev.villageheart.core.smith;

import dev.villageheart.core.gear.GearDemand;
import dev.villageheart.core.gear.Materials;
import dev.villageheart.core.gear.OpenOrder;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SlotPresence;
import dev.villageheart.core.gear.VillageGear;
import dev.villageheart.core.gear.CampNeed;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Everything {@link SmithPlanner} reads. Defaults are a valid Smithy on a work morning with nothing to do; a test or the glue overwrites
 * the fields it cares about.
 */
public final class SmithFacts {
	public enum Trip { NONE, RUNNING, FULL, UNREACHABLE }

	/** One damaged piece and the item that repairs it. */
	public record RepairJob(String itemId, int damage, int max, String materialId) {
		public int units() { return RepairRules.units(damage, max); }
	}

	public boolean smithyValid = true;
	public double speed = 1;
	public boolean depositNow;
	public Trip trip = Trip.NONE;
	public long dayTime = 1000;
	/** Empty: no golem is waiting. Otherwise the ingots {@link GolemRules#ingotsFor} asks for. */
	public OptionalInt golemWaiting = OptionalInt.empty();
	/** True when those ingots are in hand or in the stockpile. */
	public boolean golemIngotsReady;
	public Optional<OrderKey> savedCraft = Optional.empty();
	public SlotPresence craftPresence = SlotPresence.HELD;
	/** Definite cancel checks already seen for {@link #savedCraft}, before this decision. */
	public int craftCancelCount;
	public Optional<OrderKey> carriedTool = Optional.empty();
	public Set<OrderKey> deliveryBlocked = Set.of();
	public List<GearDemand> demands = List.of();
	public Map<CampNeed, List<OpenOrder>> openByCamp = Map.of();
	/** Usable tool and armour ids in Storage. Worn, enchanted and named stacks are left out by the caller. */
	public List<String> storageTools = List.of();
	public Materials materials = new Materials(List.of());
	public int smeltableUnits;
	public VillageGear gear = VillageGear.none();
	public SmithSettings settings = SmithSettings.defaults();
	public boolean buildSaved;
	/** False for every smith but the one that owns this Smithy's build today. */
	public boolean buildOwnedByMe = true;
	public boolean golemAllowed;
	public RepairJob chestRepair;
	public List<RepairJob> fetchRepairs = List.of();
	/** Item id to count: ore, fuel is separate, ingots, pumpkin, repair materials. */
	public Map<String, Long> stock = Map.of();
	public Map<String, Integer> fuelCount = Map.of();
	public Map<String, Integer> fuelHeat = Map.of();
	public int furnaceBank;
	public boolean cookHired;
	public int freeSlots = 16;
	public int walkTicks;
	/** The smelt the smith was in the middle of. A golem repair leaves it here. */
	public Optional<SmithBatch.Step> currentBatch = Optional.empty();

	public static SmithFacts morning() { return new SmithFacts(); }
}
