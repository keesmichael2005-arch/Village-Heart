package dev.villageheart.core.smith;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.cook.Ingredient;
import dev.villageheart.core.gear.CraftTier;
import dev.villageheart.core.gear.GearInfo;
import dev.villageheart.core.gear.GearNeed;
import dev.villageheart.core.gear.ToolRecipe;
import dev.villageheart.core.gear.ToolRecipes;
import dev.villageheart.core.job.ToolTier;
import org.junit.jupiter.api.Test;

/** T-L1-1: the smith's table, pinned literally, sharing M19a's eight tool shapes. */
class SmithRecipesTest {
	@Test
	void woodenAndStoneAreTheSelfCraftObjects() {
		assertEquals(8, ToolRecipes.ALL.size());
		assertTrue(ToolRecipes.byOutput("minecraft:iron_axe").isEmpty());
		int shared = 0;
		for (SmithRecipe r : SmithRecipes.ALL) {
			if (r.kind() != SmithKind.CRAFT || r.need().orElseThrow().isArmour()) continue;
			CraftTier t = r.tier().orElseThrow();
			if (t != CraftTier.WOODEN && t != CraftTier.STONE) continue;
			ToolRecipe shape = r.shape().orElseThrow();
			assertSame(ToolRecipes.byOutput(r.outputId()).orElseThrow(), shape, r.outputId());
			assertEquals(t == CraftTier.WOODEN ? Balance.SMITH_TOOL_WOODEN : Balance.SMITH_TOOL_STONE, r.ticksPerUnit());
			shared++;
		}
		assertEquals(8, shared);
	}

	@Test
	void namesAndRanksSurviveTheAppend() {
		assertEquals(1, CraftTier.WOODEN.rank());
		assertEquals(1, CraftTier.LEATHER.rank());
		assertEquals(2, CraftTier.STONE.rank());
		assertEquals(3, CraftTier.COPPER.rank());
		assertEquals(4, CraftTier.IRON.rank());
		assertEquals(5, CraftTier.DIAMOND.rank());
		assertEquals(CraftTier.WOODEN, CraftTier.valueOf("WOODEN"));
		assertEquals(CraftTier.STONE, CraftTier.valueOf("STONE"));
		assertEquals(0, CraftTier.WOODEN.ordinal());
		assertEquals(1, CraftTier.STONE.ordinal());
		assertThrows(IllegalStateException.class, CraftTier.COPPER::selfTicks);
		assertThrows(IllegalStateException.class, CraftTier.LEATHER::selfTicks);
		assertTrue(CraftTier.IRON.ironOrBetter());
		assertFalse(CraftTier.COPPER.ironOrBetter());
		assertTrue(CraftTier.LEATHER.armourOnly());
		assertTrue(CraftTier.WOODEN.toolOnly());
		assertFalse(CraftTier.IRON.toolOnly());
		assertFalse(CraftTier.IRON.armourOnly());
		assertThrows(IllegalStateException.class, GearNeed.CHEST::toolKind);
		assertTrue(GearNeed.HEAD.isArmour());
		assertEquals(4, GearNeed.tools().size());
	}

	@Test
	void theTableIsPinned() {
		int crafts = 0;
		int smelts = 0;
		for (SmithRecipe r : SmithRecipes.ALL) {
			String id = r.outputId();
			assertFalse(id.contains("shovel") || id.contains("spear") || id.contains("golden_") || id.contains("chainmail")
					|| id.contains("netherite"), id);
			for (Ingredient in : r.inputs()) assertFalse(in.itemId().contains("{"), "no components: " + in.itemId());
			if (r.kind() == SmithKind.CRAFT) {
				crafts++;
				assertTrue(r.need().isPresent() && r.tier().isPresent() && r.shape().isPresent(), id);
				ToolRecipe shape = r.shape().orElseThrow();
				assertEquals(id, shape.outputId());
				if (r.need().orElseThrow().isArmour()) {
					assertEquals(0, shape.sticks());
					int pieces = switch (r.need().orElseThrow()) {
						case HEAD -> 5;
						case CHEST -> 8;
						case LEGS -> 7;
						case FEET -> 4;
						default -> -1;
					};
					assertEquals(pieces, shape.head(), id);
					assertEquals(SmithRecipes.armourTicks(r.tier().orElseThrow()), r.ticksPerUnit());
				} else {
					int head = r.need().orElseThrow() == GearNeed.AXE || r.need().orElseThrow() == GearNeed.PICKAXE ? 3 : 2;
					int sticks = r.need().orElseThrow() == GearNeed.SWORD ? 1 : 2;
					assertEquals(head, shape.head(), id);
					assertEquals(sticks, shape.sticks(), id);
					assertEquals(SmithRecipes.toolTicks(r.tier().orElseThrow()), r.ticksPerUnit());
				}
			}
			if (r.kind() == SmithKind.SMELT) smelts++;
		}
		assertEquals(20 + 16, crafts, "5 tool tiers x 4 and 4 armour tiers x 4");
		assertEquals(3, smelts);
		assertMaterial(CraftTier.WOODEN, GearNeed.AXE, "#minecraft:planks");
		assertMaterial(CraftTier.STONE, GearNeed.PICKAXE, "#minecraft:stone_tool_materials");
		assertMaterial(CraftTier.COPPER, GearNeed.HOE, "#minecraft:copper_tool_materials");
		assertMaterial(CraftTier.IRON, GearNeed.SWORD, "#minecraft:iron_tool_materials");
		assertMaterial(CraftTier.DIAMOND, GearNeed.AXE, "#minecraft:diamond_tool_materials");
		assertMaterial(CraftTier.LEATHER, GearNeed.HEAD, "minecraft:leather");
		assertMaterial(CraftTier.COPPER, GearNeed.CHEST, "minecraft:copper_ingot");
		assertMaterial(CraftTier.IRON, GearNeed.LEGS, "minecraft:iron_ingot");
		assertMaterial(CraftTier.DIAMOND, GearNeed.FEET, "minecraft:diamond");
		SmithRecipe sticks = SmithRecipes.byOutput("minecraft:stick").orElseThrow();
		assertEquals(SmithKind.STICKS, sticks.kind());
		assertEquals(2, sticks.inputs().get(0).count());
		assertEquals(4, sticks.outputCount());
		assertEquals(20, sticks.ticksPerUnit());
		SmithRecipe golem = SmithRecipes.byOutput("minecraft:iron_golem").orElseThrow();
		assertEquals(SmithKind.GOLEM, golem.kind());
		assertEquals(36, golem.inputs().get(0).count());
		assertEquals("minecraft:carved_pumpkin", golem.inputs().get(1).itemId());
		assertEquals(8000, golem.ticksPerUnit());
		assertEquals(SetOfRaw(), SmithRecipes.rawOres());
		assertEquals(100, SmithRecipes.byOutput("minecraft:iron_ingot").orElseThrow().ticksPerUnit());
		assertEquals("minecraft:raw_copper", SmithRecipes.byOutput("minecraft:copper_ingot").orElseThrow().inputs().get(0).itemId());
		assertEquals("minecraft:raw_gold", SmithRecipes.byOutput("minecraft:gold_ingot").orElseThrow().inputs().get(0).itemId());
	}

	@Test
	void gearInfoCoversWhatTheSmithDoesNotMake() {
		assertEquals(1, GearInfo.of("minecraft:wooden_axe").orElseThrow().rank());
		assertEquals(1, GearInfo.of("minecraft:leather_boots").orElseThrow().rank());
		assertEquals(2, GearInfo.of("minecraft:stone_pickaxe").orElseThrow().rank());
		assertEquals(3, GearInfo.of("minecraft:golden_sword").orElseThrow().rank());
		assertEquals(3, GearInfo.of("minecraft:copper_hoe").orElseThrow().rank());
		assertEquals(3, GearInfo.of("minecraft:chainmail_chestplate").orElseThrow().rank());
		assertEquals(4, GearInfo.of("minecraft:iron_helmet").orElseThrow().rank());
		assertEquals(5, GearInfo.of("minecraft:diamond_leggings").orElseThrow().rank());
		assertEquals(6, GearInfo.of("minecraft:netherite_axe").orElseThrow().rank());
		assertFalse(GearInfo.of("minecraft:golden_axe").orElseThrow().ironOrBetter());
		assertTrue(GearInfo.of("minecraft:netherite_pickaxe").orElseThrow().ironOrBetter());
		assertTrue(GearInfo.of("minecraft:iron_sword").orElseThrow().ironOrBetter());
		assertEquals(GearNeed.HEAD, GearInfo.of("minecraft:iron_helmet").orElseThrow().need());
		assertTrue(GearInfo.of("minecraft:iron_shovel").isEmpty());
		assertTrue(GearInfo.of("minecraft:iron_spear").isEmpty());
		assertTrue(GearInfo.of("minecraft:carved_pumpkin").isEmpty());
		assertEquals(2, GearInfo.rankOf(ToolTier.STONE));
		assertEquals(3, GearInfo.rankOf(ToolTier.GOLD));
		assertEquals(6, GearInfo.rankOf(ToolTier.NETHERITE));
	}

	private static void assertMaterial(CraftTier t, GearNeed n, String tag) {
		SmithRecipe r = SmithRecipes.craft(n, t).orElseThrow();
		assertEquals(tag, r.shape().orElseThrow().headTag(), r.outputId());
	}

	private static java.util.Set<String> SetOfRaw() {
		return java.util.Set.of("minecraft:raw_iron", "minecraft:raw_copper", "minecraft:raw_gold");
	}
}
