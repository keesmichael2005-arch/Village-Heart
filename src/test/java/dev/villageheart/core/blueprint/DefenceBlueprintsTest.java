package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** T-L1-2 */
class DefenceBlueprintsTest {
	private static List<String> lines(Blueprint bp) {
		return bp.materials().lines().stream().map(l -> l.material().id() + " " + l.count()).collect(Collectors.toList());
	}

	@Test
	void walls() {
		for (int n : new int[] {1, 20, 32}) {
			Blueprint w = DefenceBlueprints.wall(n);
			assertEquals(List.of("minecraft:cobblestone " + 3 * n), lines(w));
			assertEquals(n, w.width());
			assertEquals(3, w.height());
			assertEquals(1, w.depth());
			assertEquals(Blueprint.Kind.DEFENCE, w.kind());
			assertEquals(PlacementMode.LINE, w.mode());
			assertEquals(Tier.VILLAGE, w.tier());
			assertNull(w.markerEntry());
			assertEquals(3 * n, w.placeCount());
		}
	}

	@Test
	void gates() {
		for (int n : new int[] {5, 6, 7, 32}) {
			Blueprint g = DefenceBlueprints.gate(n);
			assertEquals(List.of("minecraft:cobblestone " + (3 * n - 6), "#minecraft:fence_gates 3"), lines(g));
			int o = (n - 3) / 2;
			for (int x = 0; x < n; x++) {
				boolean opening = x >= o && x < o + 3;
				PaletteEntry low = g.entry(x, 0, 0), mid = g.entry(x, 1, 0), top = g.entry(x, 2, 0);
				assertEquals(opening ? "minecraft:oak_fence_gate" : "minecraft:cobblestone", low.blockId());
				if (opening) {
					assertEquals("north", low.property("facing"));
					assertEquals("false", low.property("open"));
					assertEquals(PaletteEntry.Kind.AIR, mid.kind());
				} else {
					assertEquals("minecraft:cobblestone", mid.blockId());
				}
				assertEquals("minecraft:cobblestone", top.blockId());
			}
		}
		assertEquals(1, (5 - 3) / 2);
		assertEquals(1, (6 - 3) / 2);
		assertEquals(2, (7 - 3) / 2);
	}

	@Test
	void lampPost() {
		Blueprint b = Blueprints.LAMP_POST;
		assertEquals(List.of("#minecraft:wooden_fences 2", "minecraft:lantern 1"), lines(b));
		PaletteEntry lantern = b.entry(0, 2, 0);
		assertEquals(PaletteEntry.Pass.ATTACHED, lantern.pass());
		assertEquals("false", lantern.property("hanging"));
		assertEquals(1, b.width());
		assertEquals(3, b.height());
	}

	@Test
	void watchtower() {
		Blueprint b = Blueprints.WATCHTOWER;
		assertEquals(List.of("minecraft:cobblestone 38", "#minecraft:planks 24", "#minecraft:wooden_fences 16", "minecraft:ladder 6"), lines(b));
		assertEquals(Tier.TOWN, b.tier());
		assertEquals(5, b.width());
		assertEquals(7, b.height());
		assertEquals(5, b.depth());
		int ladders = 0;
		for (int y = 0; y < b.height(); y++) for (int z = 0; z < b.depth(); z++) for (int x = 0; x < b.width(); x++) {
			PaletteEntry e = b.entry(x, y, z);
			if (!e.blockId().equals("minecraft:ladder")) continue;
			ladders++;
			assertEquals("north", e.property("facing"));
			PaletteEntry support = b.entry(x, y, z + 1); // facing north hangs on the cell to its south
			assertTrue(support.blockId().equals("minecraft:cobblestone") || support.blockId().equals("minecraft:oak_planks"));
		}
		assertEquals(6, ladders);
		for (int y = 0; y < 2; y++) assertEquals(PaletteEntry.Kind.AIR, b.entry(2, y, 1).kind()); // the entrance, two high
		assertEquals(94, b.touchedCount()); // the rail's centre cell is ANY: 7.16 from the ring, out of reach (plan said 95)
		assertEquals(84, b.placeCount());
	}

	@Test
	void menuOrder() {
		assertEquals(List.of("farm_plot", "kitchen", "barracks", "lumber_camp", "farmhouse", "mine", "hunting_lodge", "market",
				"training_yard", "smithy", "storage_room", "builders_hut", "posted_wall", "battlement", "walkway", "tower_piece", "gate_piece",
				"wall_segment", "gate", "lamp_post", "watchtower"), Blueprints.all().stream().map(Blueprint::id).toList());
	}
}
