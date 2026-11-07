package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class RingTest {
	@Test
	void patterns() {
		ShapedSpec u = Ring.uniform("minecraft:dirt", "minecraft:wheat").recipe("villageheart:farmhouse_marker", 1);
		assertEquals(List.of("CEC", "E#E", "CEC"), u.pattern());
		assertEquals(3, u.key().size());
		assertEquals("villageheart:blank_marker", u.key().get('#'));
		ShapedSpec t = Ring.withTool("#minecraft:logs", "minecraft:stone_axe", "minecraft:stick").recipe("villageheart:x", 1);
		assertEquals(List.of("CTC", "E#E", "CEC"), t.pattern());
		assertEquals(4, t.key().size());
		assertEquals("minecraft:stone_axe", t.key().get('T'));
		for (BuildingType b : BuildingType.values()) {
			assertEquals("villageheart:blank_marker", b.ring().grid().get(4));
			assertEquals("villageheart:blank_marker", b.ring().recipe("villageheart:y", 1).key().get('#'));
		}
		for (String bad : new String[] {"Planks", "minecraft:", "##x:y", "", "plain"}) {
			assertThrows(IllegalArgumentException.class, () -> Ring.uniform(bad, "minecraft:dirt"), bad);
			assertThrows(IllegalArgumentException.class, () -> Ring.uniform("minecraft:dirt", bad), bad);
		}
	}

	private static List<String> mirror(List<String> g) {
		List<String> m = new ArrayList<>();
		for (int r = 0; r < 3; r++) for (int c = 0; c < 3; c++) m.add(g.get(r * 3 + 2 - c));
		return m;
	}

	@Test
	void allMarkerGridsDistinct() {
		List<List<String>> grids = new ArrayList<>();
		for (BuildingType b : BuildingType.values()) grids.add(b.ring().grid());
		for (JobBlockType j : JobBlockType.values()) grids.add(j.recipe().grid());
		grids.add(new ShapedSpec(List.of("PPP", "PFP", "PPP"), java.util.Map.of('P', "#minecraft:planks", 'F', "minecraft:item_frame"), "villageheart:blank_marker", 1).grid());
		for (int i = 0; i < grids.size(); i++) {
			for (int j = 0; j < grids.size(); j++) {
				if (i == j) continue;
				assertNotEquals(grids.get(i), grids.get(j), i + " vs " + j);
				assertNotEquals(grids.get(i), mirror(grids.get(j)), i + " vs mirror " + j);
			}
		}
	}
}
