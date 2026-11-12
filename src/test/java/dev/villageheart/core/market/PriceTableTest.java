package dev.villageheart.core.market;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class PriceTableTest {
	private static int lot(String id) { return PriceTable.lot("minecraft:" + id).orElse(-1); }

	@Test
	void designExamples() {
		for (String wood : List.of("oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak")) assertEquals(10, lot(wood + "_log"), wood); // M26 H2: 16 -> 10
		assertEquals(20, lot("wheat"));
		assertEquals(20, lot("cobblestone")); // M26 H2: 32 -> 20
		assertEquals(20, lot("cobbled_deepslate"));
		assertEquals(4, lot("iron_ingot"));
		for (String m : List.of("beef", "porkchop", "mutton", "chicken", "rabbit", "cod", "salmon")) assertEquals(10, lot("cooked_" + m), m);
	}

	@Test
	void theWholeTable() {
		assertEquals(22, lot("carrot"));
		assertEquals(26, lot("potato"));
		assertEquals(15, lot("beetroot"));
		assertEquals(15, lot("coal"));
		assertEquals(8, lot("raw_iron"));
		assertEquals(20, lot("raw_copper"));
		assertEquals(6, lot("leather"));
		assertEquals(27, PriceTable.entries().size(), "9 logs, wheat, 3 crops, 2 stones, coal, 2 raw ores, ingot, leather, 7 meats");
	}

	@Test
	void notSold() {
		for (String id : List.of("bread", "diamond", "beef", "stone", "gold_ingot", "lapis_lazuli", "redstone", "oak_sapling", "wheat_seeds", "iron_pickaxe", "porkchop"))
			assertTrue(PriceTable.lot("minecraft:" + id).isEmpty(), id);
	}

	@Test
	void idsUniqueLotsFitAStackAndOrderIsTheTableOrder() {
		Set<String> seen = new HashSet<>();
		int i = 0;
		for (PriceEntry e : PriceTable.entries()) {
			assertTrue(seen.add(e.itemId()), e.itemId());
			assertTrue(e.lot() >= 1 && e.lot() <= 64);
			assertEquals(i++, PriceTable.order(e.itemId()));
		}
		assertEquals(Integer.MAX_VALUE, PriceTable.order("minecraft:stone"));
	}
}
