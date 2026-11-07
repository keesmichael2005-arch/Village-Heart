package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class JobBlockTypeTest {
	@Test
	void matchesDesign() {
		JobBlockType[] all = JobBlockType.values();
		assertEquals(3, all.length);
		assertEquals("chopping_block", JobBlockType.CHOPPING_BLOCK.id());
		assertEquals("Chopping Block", JobBlockType.CHOPPING_BLOCK.displayName());
		assertEquals(BuildingType.LUMBER_CAMP, JobBlockType.CHOPPING_BLOCK.building());
		assertEquals("builders_table", JobBlockType.BUILDERS_TABLE.id());
		assertEquals("Builder's Table", JobBlockType.BUILDERS_TABLE.displayName());
		assertEquals(BuildingType.BUILDERS_HUT, JobBlockType.BUILDERS_TABLE.building());
		assertEquals("market_stall", JobBlockType.MARKET_STALL.id());
		assertEquals("Market Stall", JobBlockType.MARKET_STALL.displayName());
		assertEquals(BuildingType.MARKET, JobBlockType.MARKET_STALL.building());
	}

	@Test
	void recipes() {
		ShapedSpec c = JobBlockType.CHOPPING_BLOCK.recipe();
		assertEquals(List.of("A", "L"), c.pattern());
		assertEquals("#minecraft:axes", c.key().get('A'));
		assertEquals("#minecraft:logs", c.key().get('L'));
		ShapedSpec b = JobBlockType.BUILDERS_TABLE.recipe();
		assertEquals(List.of(" P ", "BCB"), b.pattern());
		assertEquals("minecraft:paper", b.key().get('P'));
		assertEquals("minecraft:brick", b.key().get('B'));
		assertEquals("minecraft:crafting_table", b.key().get('C'));
		ShapedSpec m = JobBlockType.MARKET_STALL.recipe();
		assertEquals(List.of("WWW", "PEP"), m.pattern());
		assertEquals("#minecraft:wool", m.key().get('W'));
		assertEquals("#minecraft:planks", m.key().get('P'));
		assertEquals("minecraft:emerald", m.key().get('E'));
		for (JobBlockType t : JobBlockType.values()) assertEquals("villageheart:" + t.id(), t.recipe().resultId());
	}
}
