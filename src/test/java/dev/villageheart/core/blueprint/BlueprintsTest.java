package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.room.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

/** T-L1-3 */
class BlueprintsTest {
	private static List<String> lines(Blueprint bp) {
		return bp.materials().lines().stream().map(l -> l.material().id() + " " + l.count()).collect(Collectors.toList());
	}

	@Test
	void smallHouseMaterials() {
		assertEquals(List.of("minecraft:cobblestone 49", "#minecraft:planks 119", "#minecraft:wooden_doors 1", "#minecraft:beds 1",
				"minecraft:torch 1", "villageheart:house_marker 1"), lines(Blueprints.SMALL_HOUSE));
		assertEquals(172, Blueprints.SMALL_HOUSE.placeCount());
		assertEquals(246, Blueprints.SMALL_HOUSE.touchedCount());
	}

	@Test
	void storageShedMaterials() {
		assertEquals(List.of("minecraft:cobblestone 49", "#minecraft:planks 119", "#minecraft:wooden_doors 1", "minecraft:chest 2",
				"minecraft:torch 1", "villageheart:storage_marker 1"), lines(Blueprints.STORAGE_SHED));
		assertEquals(246, Blueprints.STORAGE_SHED.touchedCount());
	}

	@Test
	void farmPlotMaterials() {
		assertEquals(List.of("minecraft:dirt 80", "#minecraft:wooden_fences 39", "#minecraft:fence_gates 1", "minecraft:water_bucket 1"),
				lines(Blueprints.FARM_PLOT));
		Material bucket = Blueprints.FARM_PLOT.materials().lines().stream().map(MaterialList.Line::material)
				.filter(m -> m.id().equals("minecraft:water_bucket")).findFirst().orElseThrow();
		assertEquals("minecraft:bucket", bucket.returns());
		assertEquals(283, Blueprints.FARM_PLOT.touchedCount());
		assertEquals(121, Blueprints.FARM_PLOT.placeCount());
	}

	@Test
	void registryAndInvariants() {
		Set<String> ids = new HashSet<>();
		for (Blueprint bp : Blueprints.all()) {
			boolean defence = bp.kind() == Blueprint.Kind.DEFENCE;
			assertEquals(bp.id().equals("watchtower") || bp.id().equals("smithy") ? Tier.TOWN : Tier.VILLAGE, bp.tier());
			assertEquals(bp.mode() == PlacementMode.LINE, bp.id().equals("wall_segment") || bp.id().equals("gate"));
			if (!defence) assertEquals(PlacementMode.FOOTPRINT, bp.mode());
			assertTrue(ids.add(bp.id()));
			assertTrue(bp.id().matches("[a-z][a-z0-9_]*"));
			assertSame(bp, Blueprints.byId(bp.id()).orElseThrow());
			if (bp.mode() == PlacementMode.FOOTPRINT) assertSame(bp, Blueprints.resolve(bp.id(), BlueprintParams.NONE).orElseThrow());
			List<String> expected = lines(bp);
			for (Rotation4 r : Rotation4.values()) {
				// the material list does not depend on the rotation: it is a property of the blueprint, and the placed cells are a bijection
				Placement p = new Placement(new Pos(0, 0, 0), r);
				int primaries = 0;
				for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
					PaletteEntry e = p.entryAt(bp, p.world(bp, x, y, z));
					if (e.primary()) primaries++;
				}
				assertEquals(bp.placeCount(), primaries);
				assertEquals(expected, lines(bp));
			}
		}
		assertEquals(17, ids.size());
		for (Blueprint bp : Blueprints.all()) assertTrue(bp.materials().lines().size() <= TableSnapshot.MAX_MATERIALS, bp.id());
		assertTrue(Blueprints.byId("nope").isEmpty());
		assertEquals("Needs 12 more planks".substring(0, 0) + "12 planks", Blueprints.SMALL_HOUSE.materials().lines().get(1).material().words(12));
		assertEquals("1 door", Blueprints.SMALL_HOUSE.materials().lines().get(2).material().words(1));
	}
}
