package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BuildingTypeTest {
	/** id, name, tier, kind, corner, top, left, right, bottom, parent. Copied from DESIGN section 3.3, not derived from the enum. */
	private static final Object[][] TABLE = {
		{"house", "House", Tier.HAMLET, BuildingKind.ROOM, "#minecraft:planks", "minecraft:white_wool", "minecraft:white_wool", "minecraft:white_wool", "minecraft:white_wool", null},
		{"storage", "Storage", Tier.HAMLET, BuildingKind.ROOM, "#minecraft:planks", "minecraft:chest", "minecraft:chest", "minecraft:chest", "minecraft:chest", null},
		{"farmhouse", "Farmhouse", Tier.HAMLET, BuildingKind.ROOM, "minecraft:dirt", "minecraft:wheat", "minecraft:wheat", "minecraft:wheat", "minecraft:wheat", null},
		{"lumber_camp", "Lumber Camp", Tier.HAMLET, BuildingKind.ROOM, "#minecraft:logs", "minecraft:stone_axe", "minecraft:stick", "minecraft:stick", "minecraft:stick", null},
		{"kitchen", "Kitchen", Tier.HAMLET, BuildingKind.ROOM, "minecraft:cobblestone", "minecraft:coal", "minecraft:coal", "minecraft:coal", "minecraft:coal", null},
		{"builders_hut", "Builder's Hut", Tier.VILLAGE, BuildingKind.ROOM, "minecraft:brick", "minecraft:ladder", "minecraft:ladder", "minecraft:ladder", "minecraft:ladder", null},
		{"mine", "Mine", Tier.VILLAGE, BuildingKind.ROOM, "minecraft:cobblestone", "minecraft:stone_pickaxe", "minecraft:rail", "minecraft:rail", "minecraft:rail", null},
		{"hunting_lodge", "Hunting Lodge", Tier.VILLAGE, BuildingKind.ROOM, "minecraft:leather", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow", "minecraft:arrow", null},
		{"barracks", "Barracks", Tier.VILLAGE, BuildingKind.ROOM, "minecraft:cobblestone", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", "minecraft:iron_ingot", null},
		{"market", "Market", Tier.VILLAGE, BuildingKind.ROOM, "minecraft:white_wool", "minecraft:emerald", "minecraft:emerald", "minecraft:emerald", "minecraft:emerald", null},
		{"smithy", "Smithy", Tier.TOWN, BuildingKind.ROOM, "minecraft:iron_ingot", "minecraft:smooth_stone", "minecraft:smooth_stone", "minecraft:smooth_stone", "minecraft:smooth_stone", null},
		{"mineshaft", "Mineshaft", Tier.VILLAGE, BuildingKind.OUTDOOR_SITE, "minecraft:rail", "minecraft:torch", "minecraft:torch", "minecraft:torch", "minecraft:torch", "mine"},
		{"hunting_ground", "Hunting Ground", Tier.VILLAGE, BuildingKind.OUTDOOR_SITE, "minecraft:stick", "minecraft:feather", "minecraft:feather", "minecraft:feather", "minecraft:feather", "hunting_lodge"},
		{"training_yard", "Training Yard", Tier.VILLAGE, BuildingKind.OUTDOOR_SITE, "minecraft:hay_block", "minecraft:wooden_sword", "minecraft:wooden_sword", "minecraft:wooden_sword", "minecraft:wooden_sword", "barracks"},
	};

	@Test
	void matchesDesignTable() {
		BuildingType[] all = BuildingType.values();
		assertEquals(14, all.length);
		assertEquals(TABLE.length, all.length);
		for (int i = 0; i < TABLE.length; i++) {
			Object[] row = TABLE[i];
			BuildingType t = all[i];
			String why = "row " + row[0];
			assertEquals(row[0], t.id(), why);
			assertEquals(row[1], t.displayName(), why);
			assertEquals(row[2], t.tier(), why);
			assertEquals(row[3], t.kind(), why);
			assertEquals(row[4], t.ring().corner(), why);
			assertEquals(row[5], t.ring().top(), why);
			assertEquals(row[6], t.ring().left(), why);
			assertEquals(row[7], t.ring().right(), why);
			assertEquals(row[8], t.ring().bottom(), why);
			assertEquals(row[9] == null ? java.util.Optional.empty() : BuildingType.byId((String) row[9]), t.parent(), why);
		}
		assertEquals(11, java.util.Arrays.stream(all).filter(t -> t.kind() == BuildingKind.ROOM).count());
		assertEquals(3, java.util.Arrays.stream(all).filter(t -> t.kind() == BuildingKind.OUTDOOR_SITE).count());
	}

	@Test
	void idsAreStableAndUnique() {
		Set<String> ids = new HashSet<>(), markers = new HashSet<>();
		for (BuildingType t : BuildingType.values()) {
			assertTrue(ids.add(t.id()), "duplicate id " + t.id());
			assertTrue(markers.add(t.markerId()), "duplicate marker id " + t.markerId());
			assertTrue(t.id().matches("[a-z][a-z0-9_]*"), t.id());
			assertEquals(t.id() + "_marker", t.markerId());
			assertEquals(t, BuildingType.byId(t.id()).orElseThrow());
			if (t.kind() == BuildingKind.OUTDOOR_SITE) {
				BuildingType p = t.parent().orElseThrow();
				assertEquals(BuildingKind.ROOM, p.kind());
				assertEquals(t.tier(), p.tier());
			} else {
				assertTrue(t.parent().isEmpty());
			}
		}
		assertTrue(BuildingType.byId("nope").isEmpty());
	}
}
