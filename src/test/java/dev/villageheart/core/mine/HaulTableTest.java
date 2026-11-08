package dev.villageheart.core.mine;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.job.ToolTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** The tables against literal numbers copied from DESIGN section 5.3 (not derived from the code under test). */
class HaulTableTest {
	private static HaulEntry e(String id, int n, double p) { return new HaulEntry("minecraft:" + id, n, p, Optional.empty()); }

	@Test
	void highTableMatchesDesign() {
		assertEquals(List.of(e("cobblestone", 16, 1), e("coal", 2, 1), e("raw_copper", 1, 1), e("raw_iron", 1, 0.30)),
				HaulTable.entries(DepthBand.HIGH));
	}

	@Test
	void midTableMatchesDesign() {
		assertEquals(List.of(e("cobblestone", 12, 1), e("coal", 2, 1), e("raw_copper", 2, 1), e("raw_iron", 2, 1),
				e("redstone", 4, 0.20), e("lapis_lazuli", 4, 0.10)), HaulTable.entries(DepthBand.MID));
	}

	@Test
	void deepTableMatchesDesign() {
		assertEquals(List.of(e("cobbled_deepslate", 12, 1), e("coal", 1, 1), e("raw_iron", 3, 1), e("redstone", 4, 0.30),
				e("lapis_lazuli", 4, 0.20), e("raw_gold", 1, 0.20),
				new HaulEntry("minecraft:diamond", 1, 0.02, Optional.of(ToolTier.IRON))), HaulTable.entries(DepthBand.DEEP));
	}

	@Test
	void aRollAlwaysHoldsEveryFixedEntryWithItsCount() {
		for (DepthBand b : DepthBand.values()) {
			for (ToolTier t : ToolTier.values()) {
				for (int i = 0; i < 200; i++) {
					Map<String, Integer> roll = HaulTable.roll(b, t, HaulRng.forHaul(7, UUID.randomUUID(), i));
					for (HaulEntry en : HaulTable.entries(b)) {
						if (en.fixed()) assertEquals(en.count(), roll.get(en.itemId()), b + " " + t + " " + en.itemId());
					}
					roll.values().forEach(n -> assertTrue(n > 0, "zero counts are left out"));
				}
			}
		}
	}

	@Test
	void expectedIsFixedPlusChanceTimesCount() {
		Map<String, Double> high = HaulTable.expected(DepthBand.HIGH, ToolTier.STONE);
		assertEquals(Map.of("minecraft:cobblestone", 16.0, "minecraft:coal", 2.0, "minecraft:raw_copper", 1.0, "minecraft:raw_iron", 0.3), high);
		Map<String, Double> deepStone = HaulTable.expected(DepthBand.DEEP, ToolTier.STONE);
		assertEquals(0.0, deepStone.get("minecraft:diamond"));
		assertEquals(1.2, deepStone.get("minecraft:redstone"), 1e-12);
		assertEquals(0.8, deepStone.get("minecraft:lapis_lazuli"), 1e-12);
		assertEquals(0.02, HaulTable.expected(DepthBand.DEEP, ToolTier.IRON).get("minecraft:diamond"), 1e-12);
		assertEquals(0.02, HaulTable.expected(DepthBand.DEEP, ToolTier.NETHERITE).get("minecraft:diamond"), 1e-12);
		assertEquals(0.0, HaulTable.expected(DepthBand.DEEP, ToolTier.GOLD).get("minecraft:diamond"));
	}

	@Test
	void expectedKeepsTheTableOrder() {
		assertEquals(List.of("minecraft:cobblestone", "minecraft:coal", "minecraft:raw_copper", "minecraft:raw_iron"),
				new ArrayList<>(HaulTable.expected(DepthBand.HIGH, ToolTier.WOOD).keySet()));
	}
}
