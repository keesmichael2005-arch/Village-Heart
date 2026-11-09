package dev.villageheart.core.gear;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.job.ToolKind;
import org.junit.jupiter.api.Test;

class ToolRecipesTest {
	private static void check(String id, int head, int sticks, String tag) {
		ToolRecipe r = ToolRecipes.byOutput(id).orElseThrow(() -> new AssertionError("no recipe " + id));
		assertEquals(head, r.head(), id + " head");
		assertEquals(sticks, r.sticks(), id + " sticks");
		assertEquals(tag, r.headTag(), id + " tag");
	}

	@Test
	void theEightRecipesArePinned() {
		assertEquals(8, ToolRecipes.ALL.size());
		for (String t : new String[] {"wooden", "stone"}) {
			String tag = t.equals("wooden") ? "#minecraft:planks" : "#minecraft:stone_tool_materials";
			check("minecraft:" + t + "_axe", 3, 2, tag);
			check("minecraft:" + t + "_pickaxe", 3, 2, tag);
			check("minecraft:" + t + "_hoe", 2, 2, tag);
			check("minecraft:" + t + "_sword", 2, 1, tag);
		}
		assertTrue(ToolRecipes.byOutput("minecraft:iron_axe").isEmpty());
		assertTrue(ToolRecipes.byOutput("minecraft:copper_pickaxe").isEmpty());
	}

	@Test
	void conversionsAndTimes() {
		assertEquals(2, ToolRecipes.PLANKS_PER_STICK_BATCH);
		assertEquals(4, ToolRecipes.STICKS_PER_BATCH);
		assertEquals(4, ToolRecipes.PLANKS_PER_LOG);
		assertEquals(2400, Balance.SELF_WOODEN_TICKS);
		assertEquals(4800, Balance.SELF_STONE_TICKS);
		assertTrue(CraftTier.WOODEN.selfTicks() < CraftTier.STONE.selfTicks());
		assertTrue(CraftTier.WOODEN.rank() < CraftTier.STONE.rank());
		assertEquals(GearNeed.SWORD, GearNeed.of(ToolKind.BOW_OR_SWORD));
		assertEquals(ToolKind.BOW_OR_SWORD, GearNeed.SWORD.toolKind());
		assertEquals(ToolRecipes.of(GearNeed.AXE, CraftTier.STONE), ToolRecipes.byOutput("minecraft:stone_axe").orElseThrow());
	}

	@Test
	void plankTable() {
		assertEquals("minecraft:oak_planks", PlankTable.planksOf("minecraft:oak_log").orElseThrow());
		assertEquals("minecraft:oak_planks", PlankTable.planksOf("minecraft:stripped_oak_wood").orElseThrow());
		assertEquals("minecraft:crimson_planks", PlankTable.planksOf("minecraft:crimson_stem").orElseThrow());
		assertEquals("minecraft:dark_oak_planks", PlankTable.planksOf("minecraft:dark_oak_log").orElseThrow());
		assertTrue(PlankTable.planksOf("minecraft:bamboo_block").isEmpty());
		assertTrue(PlankTable.planksOf("minecraft:crimson_log").isEmpty());
		assertTrue(PlankTable.planksOf("minecraft:stone").isEmpty());
		assertEquals(11, PlankTable.woods().size());
	}
}
