package dev.villageheart.core.smith;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.gear.CampNeed;
import dev.villageheart.core.gear.CraftPlan;
import dev.villageheart.core.gear.GearDemand;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ItemCount;
import dev.villageheart.core.gear.MaterialRole;
import dev.villageheart.core.gear.Materials;
import dev.villageheart.core.gear.OpenOrder;
import dev.villageheart.core.gear.OrderKey;
import dev.villageheart.core.gear.SlotPresence;
import dev.villageheart.core.gear.SlotState;
import dev.villageheart.core.gear.StockItem;
import dev.villageheart.core.gear.TierChoice;
import dev.villageheart.core.gear.Urgency;
import dev.villageheart.core.gear.VillageGear;
import dev.villageheart.core.smith.SmithDecision.Hint;
import dev.villageheart.core.smith.SmithFacts.RepairJob;
import dev.villageheart.core.smith.SmithFacts.Trip;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** One case per row of the smith's day, and the row above it winning. */
class SmithPlannerTest {
	private static final HeartLocation CAMP = new HeartLocation("minecraft:overworld", 10, 64, 10);
	private static final HeartLocation F1 = new HeartLocation("minecraft:overworld", 1, 64, 2);
	private static final HeartLocation F2 = new HeartLocation("minecraft:overworld", 2, 64, 2);
	private static final OrderKey AXE = new OrderKey(CAMP, GearNeed.AXE, 0);

	private static GearDemand axe(Urgency u) {
		return new GearDemand(AXE, "lumberjack", u, u == Urgency.U4_WORN_REQUIRED ? SlotState.WORN : SlotState.MISSING, CAMP, OptionalInt.empty(), true);
	}

	private static Materials planks(long n) { return new Materials(List.of(new StockItem("minecraft:oak_planks", MaterialRole.PLANKS, n))); }

	private static SmithDecision decide(SmithFacts f) { return SmithPlanner.decide(f); }

	@Test
	void anInvalidSmithySendsHimHomeBeforeHunger() {
		SmithFacts f = SmithFacts.morning();
		f.smithyValid = false;
		f.speed = 0;
		SmithDecision.Idle idle = (SmithDecision.Idle) decide(f);
		assertEquals(Hint.OK, idle.hint());
		assertTrue(idle.returning());
	}

	@Test
	void hungerBeatsADeposit() {
		SmithFacts f = SmithFacts.morning();
		f.speed = 0;
		f.depositNow = true;
		assertEquals(Hint.TOO_HUNGRY, ((SmithDecision.Idle) decide(f)).hint());
		f.speed = 1;
		assertInstanceOf(SmithDecision.Yield.class, decide(f));
		f.trip = Trip.FULL;
		assertFalse(decide(f) instanceof SmithDecision.Yield);
	}

	@Test
	void aWaitingGolemBeatsACraftADeliveryAndARunningSmelt() {
		SmithFacts f = SmithFacts.morning();
		f.golemWaiting = OptionalInt.of(2);
		f.golemIngotsReady = true;
		f.savedCraft = Optional.of(AXE);
		f.carriedTool = Optional.of(AXE);
		f.currentBatch = Optional.of(new SmithBatch.Step("minecraft:iron_ingot", 8, "minecraft:coal", 1));
		SmithDecision d = decide(f);
		assertInstanceOf(SmithDecision.RepairGolem.class, d);
		assertEquals(2, ((SmithDecision.RepairGolem) d).ingots());
		assertTrue(SmithPlanner.keepsCurrent(d));
		assertEquals(f.currentBatch, SmithPlanner.currentAfter(f, d));
		f.dayTime = Balance.SMITH_DONE_BY;
		assertInstanceOf(SmithDecision.RepairGolem.class, decide(f));
	}

	@Test
	void aFinishedToolIsDeliveredBeforeTheSavedCraft() {
		SmithFacts f = SmithFacts.morning();
		f.carriedTool = Optional.of(AXE);
		f.savedCraft = Optional.of(AXE);
		f.dayTime = Balance.SMITH_DONE_BY;
		assertInstanceOf(SmithDecision.Deliver.class, decide(f));
		f.dayTime = Balance.WORK_END - 1;
		assertInstanceOf(SmithDecision.Deliver.class, decide(f));
		f.dayTime = Balance.WORK_END;
		assertFalse(decide(f) instanceof SmithDecision.Deliver);
	}

	@Test
	void aCraftCancelsOnTheSecondDefiniteCheckAndAnUnknownSlotDoesNot() {
		SmithFacts f = SmithFacts.morning();
		f.savedCraft = Optional.of(AXE);
		f.craftPresence = SlotPresence.COVERED;
		f.craftCancelCount = 1;
		assertInstanceOf(SmithDecision.CancelCraft.class, decide(f));
		f.craftCancelCount = 0;
		assertInstanceOf(SmithDecision.Craft.class, decide(f));
		f.craftPresence = SlotPresence.UNKNOWN;
		f.stock = Map.of("minecraft:raw_iron", 8L);
		f.fuelCount = Map.of("minecraft:coal", 1);
		f.fuelHeat = Map.of("minecraft:coal", 1600);
		assertInstanceOf(SmithDecision.Smelt.class, decide(f));
		f.craftPresence = SlotPresence.HELD;
		f.dayTime = Balance.WORK_END - 1;
		assertInstanceOf(SmithDecision.Craft.class, decide(f));
		f.dayTime = Balance.WORK_END;
		assertFalse(decide(f) instanceof SmithDecision.Craft);
	}

	@Test
	void aMissingToolPreemptsAGolemAndAWornToolDoesNot() {
		SmithFacts f = SmithFacts.morning();
		f.buildSaved = true;
		f.demands = List.of(axe(Urgency.U1_MISSING_REQUIRED));
		f.materials = planks(5);
		assertInstanceOf(SmithDecision.StartCraft.class, decide(f));
		f.demands = List.of(axe(Urgency.U4_WORN_REQUIRED));
		assertInstanceOf(SmithDecision.BuildGolem.class, decide(f));
		f.savedCraft = Optional.of(AXE);
		f.demands = List.of(axe(Urgency.U1_MISSING_REQUIRED));
		assertInstanceOf(SmithDecision.Craft.class, decide(f));
	}

	@Test
	void storageBeatsANewCraftAndABlockedChestDoesNeither() {
		SmithFacts f = SmithFacts.morning();
		f.demands = List.of(axe(Urgency.U1_MISSING_REQUIRED));
		f.materials = planks(5);
		f.storageTools = List.of("minecraft:iron_axe");
		SmithDecision fetched = decide(f);
		assertInstanceOf(SmithDecision.FetchAndDeliver.class, fetched);
		assertEquals("minecraft:iron_axe", ((SmithDecision.FetchAndDeliver) fetched).itemId());
		f.storageTools = List.of();
		assertInstanceOf(SmithDecision.StartCraft.class, decide(f));
		f.storageTools = List.of("minecraft:iron_axe");
		f.deliveryBlocked = Set.of(AXE);
		SmithDecision blocked = decide(f);
		assertFalse(blocked instanceof SmithDecision.FetchAndDeliver);
		assertFalse(blocked instanceof SmithDecision.StartCraft);
		// The planks were spent making the axe that bounced off a full chest. Storage still holds it, so it is carried, not abandoned.
		f.deliveryBlocked = Set.of();
		f.materials = planks(0);
		f.storageTools = List.of("minecraft:wooden_axe");
		SmithDecision spent = decide(f);
		assertInstanceOf(SmithDecision.FetchAndDeliver.class, spent);
		assertEquals("minecraft:wooden_axe", ((SmithDecision.FetchAndDeliver) spent).itemId());
	}

	@Test
	void anOrderableToolBlocksANewGolemAndSmeltingLosesToIt() {
		SmithFacts ready = golemReady();
		ready.demands = List.of(axe(Urgency.U4_WORN_REQUIRED));
		ready.materials = planks(5);
		assertInstanceOf(SmithDecision.StartCraft.class, decide(ready));
		SmithFacts unordered = golemReady();
		unordered.demands = List.of(axe(Urgency.U1_MISSING_REQUIRED));
		assertInstanceOf(SmithDecision.StartGolem.class, decide(unordered));
		SmithFacts ordered = golemReady();
		ordered.demands = List.of(axe(Urgency.U1_MISSING_REQUIRED));
		ordered.materials = planks(5);
		ordered.openByCamp = Map.of(new CampNeed(CAMP, GearNeed.AXE), List.of(new OpenOrder(AXE, 10, 1)));
		assertInstanceOf(SmithDecision.StartGolem.class, decide(ordered));
		SmithFacts smelt = golemReady();
		smelt.stock = new java.util.HashMap<>(smelt.stock);
		smelt.stock.put("minecraft:raw_iron", 10L);
		smelt.fuelCount = Map.of("minecraft:coal", 1);
		smelt.fuelHeat = Map.of("minecraft:coal", 1600);
		assertInstanceOf(SmithDecision.StartGolem.class, decide(smelt));
	}

	@Test
	void repairsThenAWornToolThenAGolemThenASmelt() {
		SmithFacts f = SmithFacts.morning();
		f.chestRepair = new RepairJob("minecraft:iron_sword", 62, 250, "minecraft:iron_ingot");
		f.fetchRepairs = List.of(new RepairJob("minecraft:iron_chestplate", 60, 240, "minecraft:iron_ingot"));
		f.stock = Map.of("minecraft:iron_ingot", 16L);
		assertInstanceOf(SmithDecision.RepairInPlace.class, decide(f));
		f.chestRepair = null;
		assertInstanceOf(SmithDecision.FetchForRepair.class, decide(f));
		f.fetchRepairs = List.of();
		f.demands = List.of(axe(Urgency.U4_WORN_REQUIRED));
		f.materials = planks(5);
		assertInstanceOf(SmithDecision.StartCraft.class, decide(f));
		f.demands = List.of();
		SmithFacts golem = golemReady();
		assertInstanceOf(SmithDecision.StartGolem.class, decide(golem));
		golem.golemAllowed = false;
		golem.stock = Map.of("minecraft:raw_iron", 10L);
		golem.fuelCount = Map.of("minecraft:coal", 1);
		golem.fuelHeat = Map.of("minecraft:coal", 1600);
		assertInstanceOf(SmithDecision.Smelt.class, decide(golem));
		SmithFacts idle = SmithFacts.morning();
		assertEquals(Hint.NO_WORK, ((SmithDecision.Idle) decide(idle)).hint());
	}

	@Test
	void timeGatesStopNewWorkAndNotACommittedCraft() {
		SmithFacts smelt = SmithFacts.morning();
		smelt.dayTime = Balance.SMITH_DONE_BY;
		smelt.stock = Map.of("minecraft:raw_iron", 10L);
		smelt.fuelCount = Map.of("minecraft:coal", 1);
		smelt.fuelHeat = Map.of("minecraft:coal", 1600);
		assertFalse(decide(smelt) instanceof SmithDecision.Smelt);
		SmithFacts repair = SmithFacts.morning();
		repair.dayTime = Balance.SMITH_DONE_BY;
		repair.chestRepair = new RepairJob("minecraft:iron_sword", 62, 250, "minecraft:iron_ingot");
		repair.stock = Map.of("minecraft:iron_ingot", 4L);
		assertFalse(decide(repair) instanceof SmithDecision.RepairInPlace);
		SmithFacts lateCraft = SmithFacts.morning();
		lateCraft.dayTime = Balance.SMITH_DONE_BY - 50;
		lateCraft.demands = List.of(axe(Urgency.U1_MISSING_REQUIRED));
		lateCraft.materials = new Materials(List.of(new StockItem("minecraft:raw_iron", MaterialRole.RAW_IRON, 3),
				new StockItem("minecraft:stick", MaterialRole.STICK, 2)));
		lateCraft.smeltableUnits = 3;
		assertFalse(decide(lateCraft) instanceof SmithDecision.StartCraft);
		SmithFacts craft = SmithFacts.morning();
		craft.savedCraft = Optional.of(AXE);
		craft.dayTime = Balance.WORK_END - 1;
		assertInstanceOf(SmithDecision.Craft.class, decide(craft));
		SmithFacts build = SmithFacts.morning();
		build.buildSaved = true;
		build.dayTime = Balance.WORK_END - 100;
		assertInstanceOf(SmithDecision.BuildGolem.class, decide(build));
		build.dayTime = Balance.WORK_END;
		assertFalse(decide(build) instanceof SmithDecision.BuildGolem);
		SmithFacts late = golemReady();
		assertInstanceOf(SmithDecision.StartGolem.class, decide(at(late, Balance.GOLEM_LAST_START)));
		assertFalse(decide(at(golemReady(), Balance.GOLEM_LAST_START + 1)) instanceof SmithDecision.StartGolem);
	}

	@Test
	void anOrphanedCraftIsAdoptedAndASecondSmithLeavesTheGolem() {
		assertEquals(F1, SmithPlanner.adoptFurnace(F2, false, List.of(F1)).orElseThrow());
		assertTrue(SmithPlanner.adoptFurnace(F2, true, List.of(F1)).isEmpty());
		SmithFacts adopted = SmithFacts.morning();
		adopted.savedCraft = Optional.of(AXE);
		assertInstanceOf(SmithDecision.Craft.class, decide(adopted));
		SmithFacts other = SmithFacts.morning();
		other.buildSaved = true;
		other.buildOwnedByMe = false;
		assertFalse(decide(other) instanceof SmithDecision.BuildGolem);
		other.buildOwnedByMe = true;
		assertInstanceOf(SmithDecision.BuildGolem.class, decide(other));
	}

	@Test
	void aShortFetchIsNotCommitted() {
		GearDemand d = axe(Urgency.U1_MISSING_REQUIRED);
		Materials m = new Materials(List.of(new StockItem("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 3),
				new StockItem("minecraft:stick", MaterialRole.STICK, 2)));
		CraftPlan plan = TierChoice.best(d, m, 0, VillageGear.none(), SmithSettings.defaults()).orElseThrow().plan();
		assertFalse(SmithPlanner.readyToCommit(plan, Map.of("minecraft:iron_ingot", 2, "minecraft:stick", 2)));
		assertTrue(SmithPlanner.readyToCommit(plan, Map.of("minecraft:iron_ingot", 3, "minecraft:stick", 2)));
	}

	@Test
	void hintsSayWhatIsActuallyMissing() {
		SmithFacts fuel = SmithFacts.morning();
		fuel.stock = Map.of("minecraft:raw_iron", 4L);
		fuel.fuelHeat = Map.of("minecraft:coal", 1600);
		assertEquals(Hint.NEEDS_FUEL, ((SmithDecision.Idle) decide(fuel)).hint());
		fuel.demands = List.of(axe(Urgency.U1_MISSING_REQUIRED));
		SmithDecision.Idle both = (SmithDecision.Idle) decide(fuel);
		assertEquals(Hint.NEEDS_MATERIAL, both.hint());
		assertEquals("planks", both.material());
		SmithFacts cap = golemReady();
		cap.golemAllowed = false;
		assertEquals(Hint.NEEDS_VILLAGERS, ((SmithDecision.Idle) decide(cap)).hint());
	}

	@Test
	void keepDropsEverythingAtHomeExceptADelivery() {
		assertEquals(0, SmithKeep.keep("minecraft:iron_ingot", 3, Set.of("minecraft:iron_ingot"), true, false, false));
		assertEquals(0, SmithKeep.keep("minecraft:iron_ingot", 3, Set.of("minecraft:iron_ingot"), false, true, false));
		assertEquals(1, SmithKeep.keep("minecraft:iron_axe", 1, Set.of("minecraft:iron_axe"), false, true, true));
		assertEquals(3, SmithKeep.keep("minecraft:iron_ingot", 3, Set.of("minecraft:iron_ingot"), false, false, false));
	}

	// ---- M19 review round 1, items 2, 3 and 6 ----------------------------------------------------------

	private static final OrderKey GUARD_SWORD = new OrderKey(CAMP, GearNeed.SWORD, 0);

	private static SmithFacts urgentOrderableWithSavedCraft(SlotPresence presence) {
		SmithFacts f = SmithFacts.morning();
		f.savedCraft = Optional.of(AXE);
		f.craftPresence = presence;
		f.demands = List.of(new GearDemand(GUARD_SWORD, "guard", Urgency.U2_GUARD_WEAPON, SlotState.MISSING, CAMP, OptionalInt.empty(), true));
		f.materials = new Materials(List.of(new StockItem("minecraft:oak_planks", MaterialRole.PLANKS, 5)));
		return f;
	}

	@Test
	void aSavedCraftWhoseSlotReadsUnknownPausesAndNeverFreesTheFurnace() {
		SmithFacts f = urgentOrderableWithSavedCraft(SlotPresence.UNKNOWN);
		// Control: the same demand with a free furnace is crafted, so the facts below really are orderable.
		SmithFacts free = urgentOrderableWithSavedCraft(SlotPresence.UNKNOWN);
		free.savedCraft = Optional.empty();
		assertInstanceOf(SmithDecision.StartCraft.class, decide(free));
		assertFalse(decide(f) instanceof SmithDecision.StartCraft, "a second craft was started over the saved one: " + decide(f));
		assertFalse(decide(f) instanceof SmithDecision.Craft, "an unknown slot is not resumed either: " + decide(f));
		f.craftCancelCount = 5;
		assertFalse(decide(f) instanceof SmithDecision.StartCraft);
	}

	@Test
	void aCraftWithAPendingRefundIsNeverResumedAndNeverReplaced() {
		SmithFacts f = urgentOrderableWithSavedCraft(SlotPresence.HELD);
		f.craftRefundPending = true;
		SmithDecision d = decide(f);
		assertFalse(d instanceof SmithDecision.Craft, "a refund-pending craft was resumed: " + d);
		assertFalse(d instanceof SmithDecision.CancelCraft, "the refund system gives the items back, not a cancel: " + d);
		assertFalse(d instanceof SmithDecision.StartCraft, "a new craft would overwrite the record: " + d);
		f.craftRefundPending = false;
		assertInstanceOf(SmithDecision.Craft.class, decide(f));
	}

	@Test
	void aSecondSmithNeverStartsAGolemInASmithyThatHasABuildOrAFetch() {
		SmithFacts f = golemReady();
		assertInstanceOf(SmithDecision.StartGolem.class, decide(f));
		f.buildSaved = true;
		f.buildOwnedByMe = false;
		assertFalse(decide(f) instanceof SmithDecision.StartGolem, "the second smith started another build: " + decide(f));
		SmithFacts fetching = golemReady();
		fetching.golemPending = true;
		assertFalse(decide(fetching) instanceof SmithDecision.StartGolem, "a build is already being fetched here: " + decide(fetching));
		fetching.golemPending = false;
		assertInstanceOf(SmithDecision.StartGolem.class, decide(fetching));
	}

	@Test
	void aSmithWithNoFreeSlotStartsNoRepairTrip() {
		SmithFacts f = SmithFacts.morning();
		f.chestRepair = new RepairJob("minecraft:iron_sword", 150, 250, "minecraft:iron_ingot");
		f.fetchRepairs = List.of(new RepairJob("minecraft:iron_chestplate", 60, 240, "minecraft:iron_ingot"));
		f.stock = Map.of("minecraft:iron_ingot", 16L);
		assertInstanceOf(SmithDecision.RepairInPlace.class, decide(f));
		f.freeSlots = 0;
		assertFalse(decide(f) instanceof SmithDecision.RepairInPlace, "repair with no room for the ingot: " + decide(f));
		f.chestRepair = null;
		assertFalse(decide(f) instanceof SmithDecision.FetchForRepair, "a fetch with no room to carry the gear: " + decide(f));
		f.freeSlots = 1;
		assertInstanceOf(SmithDecision.FetchForRepair.class, decide(f));
	}

	private static SmithFacts golemReady() {
		SmithFacts f = SmithFacts.morning();
		f.golemAllowed = true;
		f.stock = new java.util.HashMap<>(Map.of("minecraft:iron_ingot", 36L, "minecraft:carved_pumpkin", 1L));
		return f;
	}

	private static SmithFacts at(SmithFacts f, long day) {
		f.dayTime = day;
		return f;
	}
}
