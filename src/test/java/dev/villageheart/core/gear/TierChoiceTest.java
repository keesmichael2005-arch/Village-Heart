package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TierChoiceTest {
	private static StockItem planks(String wood, long n) { return new StockItem("minecraft:" + wood + "_planks", MaterialRole.PLANKS, n); }
	private static StockItem logs(String wood, long n) { return new StockItem("minecraft:" + wood + "_log", MaterialRole.LOGS, n); }
	private static StockItem stone(String id, long n) { return new StockItem("minecraft:" + id, MaterialRole.STONE, n); }
	private static StockItem sticks(long n) { return new StockItem("minecraft:stick", MaterialRole.STICK, n); }

	private static Materials mats(StockItem... items) { return new Materials(List.of(items)); }

	private static CraftPlan pick(GearNeed n, StockItem... items) {
		return TierChoice.selfMade(n, mats(items)).orElseThrow(() -> new AssertionError("expected a plan"));
	}

	private static void takes(CraftPlan p, ItemCount... expected) { assertEquals(List.of(expected), p.take()); }

	@Test
	void stoneBeatsWoodenWhenBothAreAffordable() {
		// Armour needs (M19) are not self-made; this loop stays the four tool needs.
		for (GearNeed n : GearNeed.tools()) {
			CraftPlan p = pick(n, stone("cobblestone", 3), sticks(2), planks("oak", 10), logs("oak", 5));
			assertEquals(CraftTier.STONE, p.recipe().tier(), n.name());
			// stone from planks for the sticks, again with wood to spare
			CraftPlan q = pick(n, stone("cobblestone", 3), planks("oak", 10));
			assertEquals(CraftTier.STONE, q.recipe().tier(), n.name());
		}
		assertEquals(CraftTier.WOODEN, pick(GearNeed.AXE, stone("cobblestone", 2), planks("oak", 10)).recipe().tier(), "stone short: wooden");
	}

	@Test
	void stoneWithSticksInStorage() {
		CraftPlan p = pick(GearNeed.PICKAXE, stone("cobblestone", 3), sticks(2));
		assertEquals("minecraft:stone_pickaxe", p.recipe().outputId());
		takes(p, new ItemCount("minecraft:cobblestone", 3), new ItemCount("minecraft:stick", 2));
		assertEquals(0, p.stickBatches());
		assertTrue(p.leftovers().isEmpty());
	}

	@Test
	void stoneWithAStickBatch() {
		CraftPlan p = pick(GearNeed.PICKAXE, stone("cobblestone", 3), planks("oak", 2));
		assertEquals("minecraft:stone_pickaxe", p.recipe().outputId());
		takes(p, new ItemCount("minecraft:cobblestone", 3), new ItemCount("minecraft:oak_planks", 2));
		assertEquals(1, p.stickBatches());
		assertEquals(List.of(new ItemCount("minecraft:stick", 2)), p.leftovers());
	}

	@Test
	void woodenFromPlanks() {
		CraftPlan p = pick(GearNeed.PICKAXE, planks("oak", 5));
		assertEquals("minecraft:wooden_pickaxe", p.recipe().outputId());
		takes(p, new ItemCount("minecraft:oak_planks", 5));
		assertEquals(List.of(new ItemCount("minecraft:stick", 2)), p.leftovers());
		assertTrue(TierChoice.selfMade(GearNeed.PICKAXE, mats(planks("oak", 4))).isEmpty());
		Deficit d = TierChoice.deficit(GearNeed.PICKAXE, mats(planks("oak", 4)));
		assertEquals("wood", d.cheapestMaterial());
		assertEquals(1, d.short_());
		assertEquals(0, TierChoice.deficit(GearNeed.PICKAXE, mats(planks("oak", 5))).short_());
	}

	@Test
	void woodenFromLogs() {
		CraftPlan p = pick(GearNeed.AXE, logs("oak", 2));
		assertEquals("minecraft:wooden_axe", p.recipe().outputId());
		takes(p, new ItemCount("minecraft:oak_log", 2));
		assertEquals(2, p.logs());
		assertEquals(List.of(new ItemCount("minecraft:stick", 2), new ItemCount("minecraft:oak_planks", 3)), p.leftovers());
		CraftPlan q = pick(GearNeed.AXE, planks("oak", 1), logs("oak", 1));
		takes(q, new ItemCount("minecraft:oak_planks", 1), new ItemCount("minecraft:oak_log", 1));
		assertEquals(List.of(new ItemCount("minecraft:stick", 2)), q.leftovers());
	}

	@Test
	void logsOnlyWhenPlanksDoNotSuffice() {
		CraftPlan p = pick(GearNeed.AXE, planks("oak", 5), logs("oak", 9));
		takes(p, new ItemCount("minecraft:oak_planks", 5));
		assertEquals(0, p.logs());
	}

	@Test
	void stoneShortFallsBackToWooden() {
		CraftPlan p = pick(GearNeed.PICKAXE, stone("cobblestone", 2), planks("oak", 5));
		assertEquals("minecraft:wooden_pickaxe", p.recipe().outputId());
	}

	@Test
	void swordAndHoe() {
		assertEquals("minecraft:stone_sword", pick(GearNeed.SWORD, stone("cobblestone", 2), sticks(1)).recipe().outputId());
		assertEquals("minecraft:wooden_sword", pick(GearNeed.SWORD, planks("oak", 2), sticks(1)).recipe().outputId());
		assertEquals("minecraft:stone_hoe", pick(GearNeed.HOE, stone("cobblestone", 2), sticks(2)).recipe().outputId());
		// a sword needs one stick: a batch leaves 3
		CraftPlan p = pick(GearNeed.SWORD, stone("cobblestone", 2), planks("oak", 2));
		assertEquals(List.of(new ItemCount("minecraft:stick", 3)), p.leftovers());
	}

	@Test
	void availableIsAlreadyNetOfReservations() {
		// 3 cobblestone of which 1 is reserved by another: the caller passes 2
		CraftPlan p = pick(GearNeed.PICKAXE, stone("cobblestone", 2), sticks(2), planks("oak", 5));
		assertEquals("minecraft:wooden_pickaxe", p.recipe().outputId());
	}

	@Test
	void blackstoneAndDeepslateCountAsStone() {
		CraftPlan p = pick(GearNeed.AXE, stone("blackstone", 1), stone("cobbled_deepslate", 2), sticks(2));
		assertEquals("minecraft:stone_axe", p.recipe().outputId());
		takes(p, new ItemCount("minecraft:blackstone", 1), new ItemCount("minecraft:cobbled_deepslate", 2), new ItemCount("minecraft:stick", 2));
	}

	@Test
	void neverAboveStone() {
		assertTrue(TierChoice.selfMade(GearNeed.PICKAXE, mats()).isEmpty());
		// iron, diamond and copper have no role, so the stockpile reads as empty to the choice
		List<StockItem> none = new ArrayList<>();
		assertEquals(Optional.empty(), TierChoice.selfMade(GearNeed.AXE, new Materials(none)));
	}

	@Test
	void aSingleStickIsLeftAlone() {
		CraftPlan p = pick(GearNeed.PICKAXE, sticks(1), stone("cobblestone", 3), planks("oak", 2));
		takes(p, new ItemCount("minecraft:cobblestone", 3), new ItemCount("minecraft:oak_planks", 2));
		assertEquals(1, p.stickBatches());
	}

	@Test
	void mixedPlanksNameBothKeys() {
		CraftPlan p = pick(GearNeed.PICKAXE, planks("oak", 2), planks("spruce", 1), sticks(2));
		assertEquals("minecraft:wooden_pickaxe", p.recipe().outputId());
		takes(p, new ItemCount("minecraft:stick", 2), new ItemCount("minecraft:oak_planks", 2), new ItemCount("minecraft:spruce_planks", 1));
	}

	private static GearDemand axe() {
		return new GearDemand(new OrderKey(new dev.villageheart.core.HeartLocation("minecraft:overworld", 1, 64, 1), GearNeed.AXE, 0),
				"lumberjack", Urgency.U1_MISSING_REQUIRED, SlotState.MISSING,
				new dev.villageheart.core.HeartLocation("minecraft:overworld", 1, 64, 1), java.util.OptionalInt.empty(), true);
	}

	private static GearOrder best(GearDemand d, Materials m, int smelt) {
		return TierChoice.best(d, m, smelt, VillageGear.none(), dev.villageheart.core.smith.SmithSettings.defaults()).orElseThrow();
	}

	@Test
	void smithAgreesWithSelfMadeWhenThereAreNoLogs() {
		Materials m = mats(stone("cobblestone", 3), sticks(2), planks("oak", 5));
		assertEquals(TierChoice.selfMade(GearNeed.AXE, m).orElseThrow().take(), best(axe(), m, 0).plan().take());
		Materials logsOnly = mats(logs("oak", 2));
		assertTrue(TierChoice.best(axe(), logsOnly, 0, VillageGear.none(), dev.villageheart.core.smith.SmithSettings.defaults()).isEmpty());
		assertEquals("planks", TierChoice.cheapestShort(axe(), logsOnly).cheapestMaterial());
		assertEquals("minecraft:wooden_axe", TierChoice.selfMade(GearNeed.AXE, logsOnly).orElseThrow().recipe().outputId());
	}

	@Test
	void copperIronAndArmourPlans() {
		GearOrder copper = best(axe(), mats(new StockItem("minecraft:copper_ingot", MaterialRole.COPPER_INGOT, 3), sticks(2)), 0);
		assertEquals("minecraft:copper_axe", copper.recipe().outputId());
		takes(copper.plan(), new ItemCount("minecraft:copper_ingot", 3), new ItemCount("minecraft:stick", 2));
		Materials ore = mats(new StockItem("minecraft:raw_iron", MaterialRole.RAW_IRON, 3), sticks(2), stone("cobblestone", 3));
		GearOrder iron = best(axe(), ore, 3);
		assertEquals("minecraft:iron_axe", iron.recipe().outputId());
		assertEquals(3, iron.prerequisites().stream().filter(r -> r.kind() == dev.villageheart.core.smith.SmithKind.SMELT).count());
		assertEquals("minecraft:stone_axe", best(axe(), ore, 2).recipe().outputId());
		GearOrder mixed = best(axe(), mats(planks("oak", 2), planks("spruce", 1), sticks(2)), 0);
		assertTrue(mixed.plan().take().contains(new ItemCount("minecraft:oak_planks", 2)));
		assertTrue(mixed.plan().take().contains(new ItemCount("minecraft:spruce_planks", 1)));
		var barracks = new dev.villageheart.core.HeartLocation("minecraft:overworld", 3, 64, 3);
		GearDemand chest = new GearDemand(new OrderKey(barracks, GearNeed.CHEST, 0), "guard", Urgency.U3_GUARD_ARMOUR, SlotState.MISSING, barracks,
				java.util.OptionalInt.empty(), false);
		GearOrder plate = best(chest, mats(new StockItem("minecraft:iron_ingot", MaterialRole.IRON_INGOT, 8)), 0);
		assertEquals("minecraft:iron_chestplate", plate.recipe().outputId());
		assertEquals(0, plate.plan().stickBatches());
		assertTrue(plate.prerequisites().isEmpty());
		takes(plate.plan(), new ItemCount("minecraft:iron_ingot", 8));
	}
}
