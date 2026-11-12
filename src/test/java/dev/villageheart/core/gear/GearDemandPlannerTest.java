package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.job.ToolTier;
import dev.villageheart.core.smith.SmithKind;
import dev.villageheart.core.smith.SmithSettings;
import dev.villageheart.core.worker.JobType;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GearDemandPlannerTest {
	private static final HeartLocation CAMP = new HeartLocation("minecraft:overworld", 10, 64, 10);

	private static CancelCheck run(int start, SlotPresence... seq) {
		CancelCheck c = new CancelCheck(start, Optional.empty());
		for (SlotPresence p : seq) c = GearDemandPlanner.cancel(p, c.consecutiveDefinite());
		return c;
	}

	@Test
	void heldResets() {
		assertEquals(0, run(1, SlotPresence.HELD).consecutiveDefinite());
	}

	@Test
	void twoDefiniteInARow() {
		CancelCheck once = run(0, SlotPresence.COVERED);
		assertEquals(1, once.consecutiveDefinite());
		assertTrue(once.reason().isEmpty());
		assertEquals(CancelReason.COVERED, run(0, SlotPresence.COVERED, SlotPresence.COVERED).reason().orElseThrow());
		assertEquals(CancelReason.HIRED_DROPPED, run(0, SlotPresence.HIRED_DROPPED, SlotPresence.HIRED_DROPPED).reason().orElseThrow());
		assertEquals(CancelReason.BUILDING_UNREGISTERED,
				run(0, SlotPresence.BUILDING_UNREGISTERED, SlotPresence.BUILDING_UNREGISTERED).reason().orElseThrow());
	}

	@Test
	void unknownKeepsTheCount() {
		assertEquals(CancelReason.COVERED, run(0, SlotPresence.COVERED, SlotPresence.UNKNOWN, SlotPresence.COVERED).reason().orElseThrow());
		assertTrue(run(0, SlotPresence.COVERED, SlotPresence.HELD, SlotPresence.COVERED).reason().isEmpty());
		assertTrue(run(0, SlotPresence.UNKNOWN, SlotPresence.UNKNOWN, SlotPresence.UNKNOWN, SlotPresence.UNKNOWN, SlotPresence.UNKNOWN).reason().isEmpty());
	}

	@Test
	void surplusIsTheLeastAdvanced() {
		OrderKey k0 = new OrderKey(CAMP, GearNeed.AXE, 0), k1 = new OrderKey(CAMP, GearNeed.AXE, 1);
		assertEquals(List.of(k0), GearDemandPlanner.surplus(List.of(new OpenOrder(k0, 600, 1), new OpenOrder(k1, 2000, 2)), 1));
		// equal progress: the later start
		assertEquals(List.of(k1), GearDemandPlanner.surplus(List.of(new OpenOrder(k0, 600, 1), new OpenOrder(k1, 600, 9)), 1));
		assertTrue(GearDemandPlanner.surplus(List.of(new OpenOrder(k0, 600, 1)), 1).isEmpty());
	}

	private static final HeartLocation MINE = at(20);
	private static final HeartLocation BARRACKS = at(30);
	private static final HeartLocation STORAGE = at(40);
	private static final UUID A = new UUID(0, 1), B = new UUID(0, 2);
	private static final Set<Urgency> ALL = EnumSet.allOf(Urgency.class);

	private static HeartLocation at(int x) { return new HeartLocation("minecraft:overworld", x, 64, 10); }

	private static StockItem item(String id, MaterialRole role, long n) { return new StockItem(id, role, n); }

	private static Materials mats(StockItem... items) { return new Materials(List.of(items)); }

	private static GearDemand demand(HeartLocation b, GearNeed n, int slot, String job, Urgency u, boolean pace) {
		return new GearDemand(new OrderKey(b, n, slot), job, u, u == Urgency.U4_WORN_REQUIRED ? SlotState.WORN : SlotState.MISSING, b,
				OptionalInt.empty(), pace);
	}

	private static GearDemand axe(Urgency u) { return demand(CAMP, GearNeed.AXE, 0, "lumberjack", u, true); }

	private static Optional<GearOrder> next(List<GearDemand> demands, Materials m, int smelt, VillageGear gear,
			Map<CampNeed, List<OpenOrder>> open) {
		return GearDemandPlanner.next(demands, open, ALL, m, smelt, gear, SmithSettings.defaults());
	}

	private static GearOrder must(List<GearDemand> demands, Materials m, int smelt) {
		return next(demands, m, smelt, VillageGear.none(), Map.of()).orElseThrow();
	}

	private static long smelts(GearOrder o) { return o.prerequisites().stream().filter(r -> r.kind() == SmithKind.SMELT).count(); }

	private static Materials sticksAnd(StockItem... more) {
		List<StockItem> items = new java.util.ArrayList<>();
		items.add(item("minecraft:stick", MaterialRole.STICK, 4));
		items.addAll(List.of(more));
		return new Materials(items);
	}

	@Test
	void noDemandMeansNoOrder() {
		Materials full = sticksAnd(item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 60), item("minecraft:diamond", MaterialRole.DIAMOND, 20),
				item("minecraft:oak_planks", MaterialRole.PLANKS, 20), item("minecraft:cobblestone", MaterialRole.STONE, 20));
		assertTrue(next(List.of(), full, 0, VillageGear.none(), Map.of()).isEmpty());
		assertTrue(ToolDemand.of(CAMP, JobType.LUMBERJACK, List.of(), Set.of(), List.of(), 0).slots().isEmpty());
	}

	@Test
	void fivePlanksMakeAWoodenAxeAndFourDoNot() {
		GearOrder made = must(List.of(axe(Urgency.U1_MISSING_REQUIRED)),
				mats(item("minecraft:oak_planks", MaterialRole.PLANKS, 5)), 0);
		assertEquals("minecraft:wooden_axe", made.recipe().outputId());
		assertEquals(1, made.prerequisites().stream().filter(r -> r.kind() == SmithKind.STICKS).count());
		assertEquals(List.of(new ItemCount("minecraft:oak_planks", 5)), made.plan().take());
		Materials four = mats(item("minecraft:oak_planks", MaterialRole.PLANKS, 4));
		assertTrue(next(List.of(axe(Urgency.U1_MISSING_REQUIRED)), four, 0, VillageGear.none(), Map.of()).isEmpty());
		Deficit d = GearDemandPlanner.deficits(List.of(axe(Urgency.U1_MISSING_REQUIRED)), four).get(0);
		assertEquals("planks", d.cheapestMaterial());
		assertEquals(1, d.short_());
	}

	@Test
	void stoneThenCopperThenIronThenSmeltedIron() {
		Materials stone = sticksAnd(item("minecraft:oak_planks", MaterialRole.PLANKS, 8), item("minecraft:cobblestone", MaterialRole.STONE, 3));
		assertEquals("minecraft:stone_axe", must(List.of(axe(Urgency.U1_MISSING_REQUIRED)), stone, 0).recipe().outputId());
		Materials mixed = sticksAnd(item("minecraft:blackstone", MaterialRole.STONE, 1), item("minecraft:cobbled_deepslate", MaterialRole.STONE, 2));
		GearOrder mixedAxe = must(List.of(axe(Urgency.U1_MISSING_REQUIRED)), mixed, 0);
		assertEquals("minecraft:stone_axe", mixedAxe.recipe().outputId());
		assertTrue(mixedAxe.plan().take().contains(new ItemCount("minecraft:blackstone", 1)));
		assertTrue(mixedAxe.plan().take().contains(new ItemCount("minecraft:cobbled_deepslate", 2)));
		Materials copper = sticksAnd(item("minecraft:cobblestone", MaterialRole.STONE, 3), item("minecraft:copper_ingot", MaterialRole.COPPER_INGOT, 3));
		assertEquals("minecraft:copper_axe", must(List.of(axe(Urgency.U1_MISSING_REQUIRED)), copper, 0).recipe().outputId());
		Materials rawCopper = sticksAnd(item("minecraft:raw_copper", MaterialRole.RAW_COPPER, 3));
		GearOrder smelted = must(List.of(axe(Urgency.U1_MISSING_REQUIRED)), rawCopper, 3);
		assertEquals("minecraft:copper_axe", smelted.recipe().outputId());
		assertEquals(3, smelts(smelted));
		assertTrue(smelted.plan().take().contains(new ItemCount("minecraft:raw_copper", 3)));
		Materials iron = sticksAnd(item("minecraft:copper_ingot", MaterialRole.COPPER_INGOT, 3), item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 3));
		assertEquals("minecraft:iron_axe", must(List.of(axe(Urgency.U1_MISSING_REQUIRED)), iron, 0).recipe().outputId());
		Materials rawIron = sticksAnd(item("minecraft:raw_iron", MaterialRole.RAW_IRON, 3));
		GearOrder fromOre = must(List.of(axe(Urgency.U1_MISSING_REQUIRED)), rawIron, 3);
		assertEquals("minecraft:iron_axe", fromOre.recipe().outputId());
		assertEquals(SmithKind.SMELT, fromOre.prerequisites().get(0).kind());
		assertEquals(3, smelts(fromOre));
		Materials copperFallback = sticksAnd(item("minecraft:raw_iron", MaterialRole.RAW_IRON, 3), item("minecraft:copper_ingot", MaterialRole.COPPER_INGOT, 3));
		assertEquals("minecraft:copper_axe", must(List.of(axe(Urgency.U1_MISSING_REQUIRED)), copperFallback, 0).recipe().outputId());
		Materials stoneFallback = sticksAnd(item("minecraft:raw_iron", MaterialRole.RAW_IRON, 3), item("minecraft:cobblestone", MaterialRole.STONE, 3));
		assertEquals("minecraft:stone_axe", must(List.of(axe(Urgency.U1_MISSING_REQUIRED)), stoneFallback, 0).recipe().outputId());
	}

	@Test
	void aWorkingToolIsNotUpgradedAndAWornOneTakesTheBestWeCanAfford() {
		CampTool fresh = new CampTool(0, ToolTier.STONE, ToolTier.STONE.ordinal(), 131, 131);
		CampDemand covered = ToolDemand.of(MINE, JobType.MINER, List.of(A), Set.of(), List.of(fresh), 0);
		assertTrue(covered.slots().isEmpty());
		Materials rich = sticksAnd(item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 30));
		assertTrue(next(covered.slots(), rich, 0, VillageGear.none(), Map.of()).isEmpty());
		GearDemand worn = demand(MINE, GearNeed.PICKAXE, 0, "miner", Urgency.U4_WORN_REQUIRED, true);
		Materials all = sticksAnd(item("minecraft:oak_planks", MaterialRole.PLANKS, 8), item("minecraft:cobblestone", MaterialRole.STONE, 3),
				item("minecraft:copper_ingot", MaterialRole.COPPER_INGOT, 3), item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 3));
		assertEquals("minecraft:iron_pickaxe", must(List.of(worn), all, 0).recipe().outputId());
		Materials noIron = sticksAnd(item("minecraft:oak_planks", MaterialRole.PLANKS, 8), item("minecraft:cobblestone", MaterialRole.STONE, 3),
				item("minecraft:copper_ingot", MaterialRole.COPPER_INGOT, 3));
		assertEquals("minecraft:copper_pickaxe", must(List.of(worn), noIron, 0).recipe().outputId());
		GearDemand wornAxe = demand(CAMP, GearNeed.AXE, 0, "lumberjack", Urgency.U4_WORN_REQUIRED, true);
		assertEquals("minecraft:stone_axe", must(List.of(wornAxe), sticksAnd(item("minecraft:cobblestone", MaterialRole.STONE, 3)), 0).recipe().outputId());
	}

	@Test
	void diamondsStayInReserveUnlessEveryoneElseIsAlreadyIron() {
		GearDemand miner = demand(MINE, GearNeed.PICKAXE, 0, "miner", Urgency.U1_MISSING_REQUIRED, true);
		Materials tight = sticksAnd(item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 3), item("minecraft:diamond", MaterialRole.DIAMOND, 8));
		// 8 - 3 is not strictly more than the reserve of 5, so the pickaxe stays iron.
		assertEquals("minecraft:iron_pickaxe", must(List.of(miner), tight, 0).recipe().outputId());
		OrderKey other = new OrderKey(CAMP, GearNeed.AXE, 0);
		Materials rich = sticksAnd(item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 3), item("minecraft:diamond", MaterialRole.DIAMOND, 20));
		VillageGear ready = new VillageGear(Map.of(miner.key(), false, other, true), 0);
		assertEquals("minecraft:diamond_pickaxe", next(List.of(miner), rich, 0, ready, Map.of()).orElseThrow().recipe().outputId());
		VillageGear oneShort = new VillageGear(Map.of(miner.key(), true, other, false), 0);
		assertEquals("minecraft:iron_pickaxe", next(List.of(miner), rich, 0, oneShort, Map.of()).orElseThrow().recipe().outputId());
		OrderKey guard = new OrderKey(BARRACKS, GearNeed.SWORD, 0);
		VillageGear unloadedGuard = new VillageGear(Map.of(miner.key(), false, guard, true), 1);
		assertEquals("minecraft:diamond_pickaxe", next(List.of(miner), rich, 0, unloadedGuard, Map.of()).orElseThrow().recipe().outputId());
		VillageGear openIron = new VillageGear(Map.of(miner.key(), false, other, true), 0);
		assertEquals("minecraft:diamond_pickaxe", next(List.of(miner), rich, 0, openIron, Map.of()).orElseThrow().recipe().outputId());
		GearDemand hunter = demand(at(50), GearNeed.SWORD, 0, "hunter", Urgency.U1_MISSING_REQUIRED, false);
		GearDemand farmer = demand(at(60), GearNeed.HOE, 0, "farmer", Urgency.U5_HOE, false);
		VillageGear everyone = new VillageGear(Map.of(hunter.key(), true, farmer.key(), true, other, true), 0);
		assertEquals("minecraft:iron_sword", next(List.of(hunter), rich, 0, everyone, Map.of()).orElseThrow().recipe().outputId());
		assertEquals("minecraft:iron_hoe", next(List.of(farmer), rich, 0, everyone, Map.of()).orElseThrow().recipe().outputId());
		assertEquals("minecraft:iron_pickaxe", next(List.of(miner), rich, 0, new VillageGear(Map.of(other, false), 0), Map.of()).orElseThrow().recipe().outputId());
		assertEquals("minecraft:diamond_pickaxe", next(List.of(miner), rich, 0, new VillageGear(Map.of(other, true), 0), Map.of()).orElseThrow().recipe().outputId());
	}

	@Test
	void aPlayersToolOvertakesASmithOrder() {
		CampTool fresh = new CampTool(0, ToolTier.STONE, ToolTier.STONE.ordinal(), 131, 131);
		CampDemand camp = ToolDemand.of(CAMP, JobType.LUMBERJACK, List.of(A), Set.of(), List.of(fresh), 0);
		OpenOrder smith = new OpenOrder(new OrderKey(CAMP, GearNeed.AXE, 0), 40, 1);
		assertEquals(SlotPresence.COVERED, ToolDemand.presence(camp, List.of(smith)).get(smith.key()));
		OpenOrder guard = new OpenOrder(new OrderKey(BARRACKS, GearNeed.CHEST, 0), 10, 1);
		assertEquals(SlotPresence.COVERED, GuardDemand.presence(BARRACKS, GearNeed.CHEST, 1, 1, 0, List.of(guard)).get(guard.key()));
	}

	@Test
	void oneOrderPerCampAndTheSelfCraftersKeyCounts() {
		GearDemand slot0 = axe(Urgency.U1_MISSING_REQUIRED);
		GearDemand slot1 = demand(CAMP, GearNeed.AXE, 1, "lumberjack", Urgency.U1_MISSING_REQUIRED, true);
		Materials wood = mats(item("minecraft:oak_planks", MaterialRole.PLANKS, 16));
		OpenOrder onZero = new OpenOrder(slot0.key(), 10, 1);
		Map<CampNeed, List<OpenOrder>> open = Map.of(new CampNeed(CAMP, GearNeed.AXE), List.of(onZero));
		assertEquals(1, next(List.of(slot0, slot1), wood, 0, VillageGear.none(), open).orElseThrow().key().slot());
		OpenOrder selfCraft = new OpenOrder(new OrderKey(CAMP, GearNeed.AXE, 1), 10, 1);
		Map<CampNeed, List<OpenOrder>> self = Map.of(new CampNeed(CAMP, GearNeed.AXE), List.of(selfCraft));
		assertTrue(next(List.of(slot0), wood, 0, VillageGear.none(), self).isEmpty());
		assertEquals(0, next(List.of(slot0, slot1), wood, 0, VillageGear.none(), self).orElseThrow().key().slot());
		CampDemand two = ToolDemand.of(CAMP, JobType.LUMBERJACK, List.of(A, B), Set.of(), List.of(), 0);
		assertEquals(List.of(slot0.key()), ToolDemand.freeSlots(two, Set.of(selfCraft.key())));
	}

	@Test
	void aSharedCampReportsTheWornToolsRank() {
		CampTool iron = new CampTool(0, ToolTier.IRON, ToolTier.IRON.ordinal(), 250, 250);
		CampTool wornStone = new CampTool(1, ToolTier.STONE, ToolTier.STONE.ordinal(), 10, 131);
		CampDemand one = ToolDemand.of(CAMP, JobType.LUMBERJACK, List.of(A, B), Set.of(), List.of(iron, wornStone), 0);
		assertEquals(1, one.slots().size());
		assertEquals(SlotState.WORN, one.slots().get(0).state());
		assertEquals(2, one.slots().get(0).currentRank().orElseThrow());
		CampTool wornIron = new CampTool(0, ToolTier.IRON, ToolTier.IRON.ordinal(), 10, 250);
		CampDemand both = ToolDemand.of(CAMP, JobType.LUMBERJACK, List.of(A, B), Set.of(), List.of(wornIron, wornStone), 0);
		assertEquals(2, both.slots().size());
		assertEquals(SlotState.WORN, both.slots().get(0).state());
		assertEquals(SlotState.WORN, both.slots().get(1).state());
		OrderKey advanced = new OrderKey(CAMP, GearNeed.AXE, 0), behind = new OrderKey(CAMP, GearNeed.AXE, 1);
		// Holder identity does not matter: the least progress is cancelled, whether that order is the smith's or a self-craft.
		assertEquals(List.of(behind), GearDemandPlanner.surplus(List.of(new OpenOrder(advanced, 2000, 1), new OpenOrder(behind, 100, 2)), 1));
		assertEquals(List.of(behind), GearDemandPlanner.surplus(List.of(new OpenOrder(behind, 100, 2), new OpenOrder(advanced, 2000, 1)), 1));
	}

	@Test
	void urgencyOrdersTheQueue() {
		GearDemand miner = demand(MINE, GearNeed.PICKAXE, 0, "miner", Urgency.U1_MISSING_REQUIRED, true);
		GearDemand sword = demand(BARRACKS, GearNeed.SWORD, 0, "guard", Urgency.U2_GUARD_WEAPON, false);
		GearDemand chest = demand(BARRACKS, GearNeed.CHEST, 0, "guard", Urgency.U3_GUARD_ARMOUR, false);
		GearDemand worn = demand(CAMP, GearNeed.AXE, 0, "lumberjack", Urgency.U4_WORN_REQUIRED, true);
		GearDemand hoe = demand(at(70), GearNeed.HOE, 0, "farmer", Urgency.U5_HOE, false);
		Materials m = sticksAnd(item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 40), item("minecraft:oak_planks", MaterialRole.PLANKS, 8));
		List<GearDemand> all = List.of(hoe, worn, chest, sword, miner);
		assertEquals(miner.key(), must(all, m, 0).key());
		assertEquals(sword.key(), next(List.of(hoe, worn, chest, sword), m, 0, VillageGear.none(), Map.of()).orElseThrow().key());
		assertEquals(chest.key(), next(List.of(hoe, worn, chest), m, 0, VillageGear.none(), Map.of()).orElseThrow().key());
		assertEquals(worn.key(), next(List.of(hoe, worn), m, 0, VillageGear.none(), Map.of()).orElseThrow().key());
		assertEquals(hoe.key(), next(List.of(hoe), m, 0, VillageGear.none(), Map.of()).orElseThrow().key());
	}

	@Test
	void goldIsNotAToolAndPaceIndependentJobsPreferStone() {
		// Gold has no recipe. A best-tier job's cheapest resort is wood, so the hint is planks (a wooden axe is a real order; the plan text said cobblestone).
		assertTrue(next(List.of(axe(Urgency.U1_MISSING_REQUIRED)), mats(item("minecraft:stick", MaterialRole.STICK, 4)), 0, VillageGear.none(), Map.of()).isEmpty());
		assertEquals("planks", TierChoice.cheapestShort(axe(Urgency.U1_MISSING_REQUIRED), mats(item("minecraft:stick", MaterialRole.STICK, 4))).cheapestMaterial());
		assertEquals("cobblestone", TierChoice.cheapestShort(demand(at(50), GearNeed.SWORD, 0, "hunter", Urgency.U1_MISSING_REQUIRED, false),
				mats(item("minecraft:stick", MaterialRole.STICK, 4))).cheapestMaterial());
		CampTool golden = new CampTool(0, ToolTier.GOLD, ToolTier.GOLD.ordinal(), 32, 32);
		assertTrue(ToolDemand.of(CAMP, JobType.LUMBERJACK, List.of(A), Set.of(), List.of(golden), 0).slots().isEmpty());
		Materials both = sticksAnd(item("minecraft:cobblestone", MaterialRole.STONE, 3), item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 30),
				item("minecraft:oak_planks", MaterialRole.PLANKS, 8));
		GearDemand hoe = demand(at(60), GearNeed.HOE, 0, "farmer", Urgency.U5_HOE, false);
		GearDemand hunter = demand(at(50), GearNeed.SWORD, 0, "hunter", Urgency.U1_MISSING_REQUIRED, false);
		assertEquals("minecraft:stone_hoe", must(List.of(hoe), both, 0).recipe().outputId());
		assertEquals("minecraft:stone_sword", must(List.of(hunter), both, 0).recipe().outputId());
		Materials onlyIron = sticksAnd(item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 30));
		assertEquals("minecraft:iron_hoe", must(List.of(hoe), onlyIron, 0).recipe().outputId());
		assertEquals("minecraft:iron_sword", must(List.of(hunter), onlyIron, 0).recipe().outputId());
	}

	@Test
	void guardsAreArmedBeforeTheyAreArmoured() {
		List<GearDemand> bare = GuardDemand.of(BARRACKS, STORAGE, 2, List.of());
		Materials iron = sticksAnd(item("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 60));
		Map<CampNeed, List<OpenOrder>> open = new HashMap<>();
		GearOrder first = next(bare, iron, 0, VillageGear.none(), open).orElseThrow();
		assertEquals(GearNeed.SWORD, first.key().need());
		assertEquals(0, first.key().slot());
		assertEquals(STORAGE, first.demand().destination());
		open.computeIfAbsent(new CampNeed(BARRACKS, GearNeed.SWORD), k -> new java.util.ArrayList<>()).add(new OpenOrder(first.key(), 0, 1));
		assertEquals(1, next(bare, iron, 0, VillageGear.none(), open).orElseThrow().key().slot());
		open.get(new CampNeed(BARRACKS, GearNeed.SWORD)).add(new OpenOrder(new OrderKey(BARRACKS, GearNeed.SWORD, 1), 0, 2));
		GearOrder chest = next(bare, iron, 0, VillageGear.none(), open).orElseThrow();
		assertEquals(GearNeed.CHEST, chest.key().need());
		assertEquals(0, chest.key().slot());
		GuardDemand.Piece leather = new GuardDemand.Piece(GearNeed.CHEST, GuardDemand.Where.STORAGE, 80, 80, false);
		long chests = GuardDemand.of(BARRACKS, STORAGE, 2, List.of(leather)).stream().filter(d -> d.key().need() == GearNeed.CHEST).count();
		assertEquals(1, chests);
		GearDemand oneChest = demand(BARRACKS, GearNeed.CHEST, 0, "guard", Urgency.U3_GUARD_ARMOUR, false);
		Materials copperAndLeather = mats(item("minecraft:copper_ingot", MaterialRole.COPPER_INGOT, 8), item("minecraft:leather", MaterialRole.LEATHER, 8));
		assertEquals("minecraft:copper_chestplate", must(List.of(oneChest), copperAndLeather, 0).recipe().outputId());
		assertEquals("minecraft:leather_chestplate", must(List.of(oneChest), mats(item("minecraft:leather", MaterialRole.LEATHER, 8)), 0).recipe().outputId());
		Materials raw = mats(item("minecraft:raw_copper", MaterialRole.RAW_COPPER, 8), item("minecraft:leather", MaterialRole.LEATHER, 8));
		assertEquals("minecraft:copper_chestplate", must(List.of(oneChest), raw, 8).recipe().outputId());
		assertEquals("minecraft:leather_chestplate", must(List.of(oneChest), raw, 0).recipe().outputId());
		GuardDemand.Piece inSmithy = new GuardDemand.Piece(GearNeed.CHEST, GuardDemand.Where.SMITHY, 500, 528, false);
		assertEquals(2, GuardDemand.of(BARRACKS, STORAGE, 2, List.of(inSmithy)).stream().filter(d -> d.key().need() == GearNeed.CHEST).count());
	}

	@Test
	void aQueuedRepairCoversAGuardAndACommittedOrderStaysOpen() {
		assertTrue(GuardDemand.usable(new GuardDemand.Piece(GearNeed.HEAD, GuardDemand.Where.STORAGE, 0, 165, true)));
		assertFalse(GuardDemand.usable(new GuardDemand.Piece(GearNeed.HEAD, GuardDemand.Where.BARRACKS, 0, 165, false)));
		OpenOrder committed = new OpenOrder(new OrderKey(BARRACKS, GearNeed.CHEST, 0), 80, 1);
		assertEquals(SlotPresence.HELD, GuardDemand.presence(BARRACKS, GearNeed.CHEST, 1, 0, 1, List.of(committed)).get(committed.key()));
	}

	@Test
	void oneSwordCoversOneDemand() {
		GearDemand hunter = demand(at(50), GearNeed.SWORD, 0, "hunter", Urgency.U1_MISSING_REQUIRED, false);
		GearDemand guard = demand(BARRACKS, GearNeed.SWORD, 0, "guard", Urgency.U2_GUARD_WEAPON, false);
		List<GearDemand> left = OneStack.stillOpen(List.of(hunter, guard), List.of("minecraft:iron_sword"));
		assertEquals(List.of(guard), left);
	}

	@Test
	void deficitsNameTheCheapestMiss() {
		List<Deficit> d = GearDemandPlanner.deficits(List.of(axe(Urgency.U1_MISSING_REQUIRED)), mats());
		assertEquals("planks", d.get(0).cheapestMaterial());
		assertTrue(d.get(0).short_() > 0);
	}
}
