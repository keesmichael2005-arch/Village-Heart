package dev.villageheart.core.guard;

import static dev.villageheart.core.guard.GearItem.Source.*;
import static dev.villageheart.core.guard.GearSlot.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

/** T-L1-5 (ranking) and T-L1-6 (planner). Armour numbers are the vanilla chestplate values. */
class GearTest {
	private static GearItem chest(String id, double armor, double tough, int damage, int max, GearItem.Source src, int order) {
		return new GearItem(id, CHEST, armor, tough, 0, damage, max, src, order);
	}

	private static GearItem chest(String id, double armor, double tough) { return chest(id, armor, tough, 0, 100, STOCKPILE, 0); }

	private static GearItem sword(String id, double dmg, int damage, int max, GearItem.Source src, int order) {
		return new GearItem(id, WEAPON, 0, 0, dmg, damage, max, src, order);
	}

	private static boolean better(GearItem a, GearItem b) { return GearRanking.compare(a, b) > 0; }

	@Test
	void chestplatesRankByArmourThenToughness() {
		GearItem leather = chest("leather", 3, 0), copper = chest("copper", 4, 0), chain = chest("chain", 5, 0), gold = chest("gold", 5, 0);
		GearItem iron = chest("iron", 6, 0), diamond = chest("diamond", 8, 2), netherite = chest("netherite", 8, 3);
		assertTrue(better(copper, leather));
		assertTrue(better(chain, copper));
		assertEquals(0, GearRanking.compareStats(chain, gold), "chainmail and gold tie on the numbers");
		assertTrue(better(iron, gold));
		assertTrue(better(diamond, iron));
		assertTrue(better(netherite, diamond));
	}

	@Test
	void tieGoesToChestOrderThenSource() {
		GearItem a = chest("chain", 5, 0, 0, 100, STOCKPILE, 1), b = chest("gold", 5, 0, 0, 100, STOCKPILE, 2);
		assertTrue(better(a, b), "lower order wins");
		assertTrue(better(chest("g", 5, 0, 0, 100, WORN, 9), chest("g", 5, 0, 0, 100, BARRACKS, 0)));
		assertTrue(better(chest("g", 5, 0, 0, 100, BARRACKS, 9), chest("g", 5, 0, 0, 100, STOCKPILE, 0)));
	}

	@Test
	void durabilityDecidesOnlyBetweenEquals() {
		GearItem damagedDiamond = chest("diamond", 8, 2, 50, 100, STOCKPILE, 0), freshIron = chest("iron", 6, 0);
		assertTrue(better(damagedDiamond, freshIron));
		assertTrue(better(chest("iron", 6, 0, 10, 100, STOCKPILE, 0), chest("iron", 6, 0, 60, 100, STOCKPILE, 0)));
	}

	@Test
	void nearlyBrokenAndZeroArmourAreUnusable() {
		GearItem worn = chest("iron", 6, 0, 95, 100, STOCKPILE, 0);
		assertFalse(worn.usable(0.1));
		assertTrue(GearRanking.best(List.of(worn)).isEmpty());
		assertTrue(chest("iron", 6, 0, 90, 100, STOCKPILE, 0).usable(0.1), "exactly 10 % left is usable");
		assertFalse(chest("pumpkin", 0, 0).usable(0.1));
	}

	@Test
	void swordsRankByAttackDamage() {
		GearItem wood = sword("wood", 3, 0, 59, STOCKPILE, 0), stone = sword("stone", 4, 0, 131, STOCKPILE, 0);
		GearItem iron = sword("iron", 5, 0, 250, STOCKPILE, 0), diamond = sword("diamond", 6, 0, 1561, STOCKPILE, 0);
		assertTrue(better(stone, wood));
		assertTrue(better(iron, stone));
		assertTrue(better(diamond, iron));
		assertTrue(better(sword("iron", 5, 0, 250, WORN, 3), sword("iron", 5, 0, 250, BARRACKS, 0)), "equal swords: worn first");
	}

	@Test
	void sharpnessDoesNotChangeTheRank() {
		// an enchantment is no attribute modifier, so the glue reads the same numbers for both (documented v1 rule)
		GearItem plain = sword("iron_sword", 5, 0, 250, STOCKPILE, 0), sharp = sword("iron_sword", 5, 0, 250, STOCKPILE, 1);
		assertEquals(0, GearRanking.compareStats(plain, sharp));
		assertTrue(better(plain, sharp), "order decides");
	}

	@Test
	void plannerTakesHelmetAndSwordNotTheWorseChest() {
		GearItem wornIron = chest("iron_chestplate", 6, 0, 0, 100, WORN, 0);
		GearItem leatherChest = chest("leather_chestplate", 3, 0, 0, 100, BARRACKS, 0);
		GearItem ironHelmet = new GearItem("iron_helmet", HEAD, 2, 0, 0, 0, 100, STOCKPILE, 0);
		GearItem ironSword = sword("iron_sword", 5, 0, 250, BARRACKS, 1);
		List<GearItem> plan = GearPlanner.plan(List.of(wornIron), List.of(leatherChest, ironHelmet, ironSword));
		assertEquals(2, plan.size());
		assertTrue(plan.contains(ironHelmet));
		assertTrue(plan.contains(ironSword));
		assertFalse(plan.contains(leatherChest));
	}

	@Test
	void equalItemNeverUpgrades() {
		GearItem worn = chest("iron_chestplate", 6, 0, 0, 100, WORN, 0);
		assertTrue(GearPlanner.plan(List.of(worn), List.of(chest("iron_chestplate", 6, 0, 0, 100, BARRACKS, 0))).isEmpty());
	}

	@Test
	void onePickPerSlotTheBestOne() {
		List<GearItem> plan = GearPlanner.plan(List.of(), List.of(chest("leather", 3, 0), chest("iron", 6, 0), chest("chain", 5, 0)));
		assertEquals(1, plan.size());
		assertEquals("iron", plan.get(0).id());
	}

	@Test
	void anItemMissingFromAvailableIsNeverPlanned() {
		// reserved by another guard: the glue leaves it out of `available`
		assertTrue(GearPlanner.plan(List.of(), List.of()).isEmpty());
	}

	@Test
	void aNearlyBrokenWornPieceIsReplacedByAnyUsableOne() {
		GearItem broken = chest("iron", 6, 0, 99, 100, WORN, 0);
		List<GearItem> plan = GearPlanner.plan(List.of(broken), List.of(chest("leather", 3, 0)));
		assertEquals(1, plan.size());
	}
}
