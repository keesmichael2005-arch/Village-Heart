package dev.villageheart.core.building;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingRules.CheckContext;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** DESIGN section 3.3, hand-written independently of {@link BuildingRequirements}: the two must agree. */
class BuildingRequirementsTest {
	/** One row of the table: floor, minimum contents (each count 1) with the thing a missing key reads as, expected slots. */
	record Row(int floor, LinkedHashMap<String, Thing> needs, int slots) {}

	static final Map<BuildingType, Row> EXPECTED = new EnumMap<>(BuildingType.class);

	static LinkedHashMap<String, Thing> needs(Object... kv) {
		LinkedHashMap<String, Thing> m = new LinkedHashMap<>();
		for (int i = 0; i < kv.length; i += 2) m.put((String) kv[i], (Thing) kv[i + 1]);
		return m;
	}

	static {
		EXPECTED.put(BuildingType.HOUSE, new Row(12, needs("#bed", Thing.BED, "#light", Thing.LIGHT), 1));
		EXPECTED.put(BuildingType.STORAGE, new Row(9, needs("#chest", Thing.STORAGE_CONTAINER, "minecraft:barrel", Thing.STORAGE_CONTAINER), 2));
		EXPECTED.put(BuildingType.FARMHOUSE, new Row(9, needs("minecraft:composter", Thing.COMPOSTER, "#chest", Thing.CHEST), 1));
		EXPECTED.put(BuildingType.LUMBER_CAMP, new Row(9, needs("villageheart:chopping_block", Thing.CHOPPING_BLOCK, "#chest", Thing.CHEST), 1));
		EXPECTED.put(BuildingType.KITCHEN, new Row(9, needs("minecraft:smoker", Thing.SMOKER, "#chest", Thing.CHEST,
				"minecraft:crafting_table", Thing.CRAFTING_TABLE), 1));
		EXPECTED.put(BuildingType.BUILDERS_HUT, new Row(9, needs("villageheart:builders_table", Thing.BUILDERS_TABLE, "#chest", Thing.CHEST), 1));
		EXPECTED.put(BuildingType.MINE, new Row(9, needs("minecraft:stonecutter", Thing.STONECUTTER, "#chest", Thing.CHEST), 1));
		EXPECTED.put(BuildingType.HUNTING_LODGE, new Row(9, needs("minecraft:fletching_table", Thing.FLETCHING_TABLE, "#chest", Thing.CHEST), 1));
		EXPECTED.put(BuildingType.BARRACKS, new Row(16, needs("minecraft:grindstone", Thing.GRINDSTONE, "#bed", Thing.BED, "#chest", Thing.CHEST), 1));
		EXPECTED.put(BuildingType.MARKET, new Row(9, needs("villageheart:market_stall", Thing.MARKET_STALL, "#chest", Thing.CHEST), 1));
		EXPECTED.put(BuildingType.SMITHY, new Row(12, needs("minecraft:blast_furnace", Thing.BLAST_FURNACE, "#anvil", Thing.ANVIL,
				"#chest", Thing.CHEST), 1));
	}

	static final CheckContext TOWN = new CheckContext(Tier.TOWN, null, null, 1);

	static RoomFacts facts(int floor, Map<String, Integer> counts) { return new RoomFacts(true, List.of(), floor, counts); }

	static Map<String, Integer> minimal(Row r) {
		Map<String, Integer> m = new HashMap<>();
		for (String k : r.needs().keySet()) m.put(k, 1);
		return m;
	}

	@Test void tableCoversExactlyTheRoomTypes() {
		for (BuildingType t : BuildingType.values()) {
			if (t.kind() == BuildingKind.ROOM) {
				assertTrue(EXPECTED.containsKey(t), t + " needs a row");
				assertNotNull(BuildingRequirements.of(t));
			} else {
				assertFalse(EXPECTED.containsKey(t));
				assertThrows(IllegalArgumentException.class, () -> BuildingRequirements.of(t), t.toString());
			}
		}
	}

	@Test void minimumIsValidAndEachShortfallIsExactlyOneReason() {
		for (Map.Entry<BuildingType, Row> e : EXPECTED.entrySet()) {
			BuildingType t = e.getKey();
			Row r = e.getValue();
			Verdict ok = BuildingRules.checkRoom(t, facts(r.floor(), minimal(r)), TOWN);
			assertEquals(List.of(), ok.reasons(), t + " minimal");
			assertEquals(BuildingStatus.VALID, ok.status(), t.toString());
			assertEquals(r.slots(), ok.slots(), t + " slots");

			Verdict small = BuildingRules.checkRoom(t, facts(r.floor() - 1, minimal(r)), TOWN);
			assertEquals(List.of(Reason.floorTooSmall(r.floor() - 1, r.floor())), small.reasons(), t + " floor");

			for (Map.Entry<String, Thing> need : r.needs().entrySet()) {
				Map<String, Integer> counts = minimal(r);
				counts.put(need.getKey(), 0);
				Verdict v = BuildingRules.checkRoom(t, facts(r.floor(), counts), TOWN);
				assertEquals(List.of(Reason.needsMore(1, need.getValue())), v.reasons(), t + " without " + need.getKey());
				assertEquals(BuildingStatus.INVALID, v.status());
			}

			if (t == BuildingType.MINE || t == BuildingType.HUNTING_LODGE) {
				Verdict v = BuildingRules.checkRoom(t, facts(r.floor(), minimal(r)), new CheckContext(Tier.TOWN, null, null, 0));
				BuildingType site = t == BuildingType.MINE ? BuildingType.MINESHAFT : BuildingType.HUNTING_GROUND;
				assertEquals(List.of(Reason.needsLinkedSite(site)), v.reasons(), t.toString());
			} else {
				Verdict v = BuildingRules.checkRoom(t, facts(r.floor(), minimal(r)), new CheckContext(Tier.TOWN, null, null, 0));
				assertEquals(BuildingStatus.VALID, v.status(), t + " needs no site");
			}
		}
	}

	@Test void storageCountsChestsAndBarrels() {
		RoomFacts oneEach = facts(9, Map.of("#chest", 1, "minecraft:barrel", 1));
		assertEquals(BuildingStatus.VALID, BuildingRules.checkRoom(BuildingType.STORAGE, oneEach, TOWN).status());
		Verdict one = BuildingRules.checkRoom(BuildingType.STORAGE, facts(9, Map.of("#chest", 1)), TOWN);
		assertEquals("Needs 1 more chest", one.reasons().get(0).message());
		Verdict none = BuildingRules.checkRoom(BuildingType.STORAGE, facts(9, Map.of()), TOWN);
		assertEquals(List.of("Needs 2 more chests"), none.reasons().stream().map(Reason::message).toList());
	}

	@Test void barracksNeedsOneBedPerGrindstone() {
		Verdict v = BuildingRules.checkRoom(BuildingType.BARRACKS,
				facts(16, Map.of("minecraft:grindstone", 2, "#bed", 1, "#chest", 1)), TOWN);
		assertEquals(List.of("Needs 1 more bed"), v.reasons().stream().map(Reason::message).toList());
		assertEquals(1, v.slots(), "slots = min(grindstones, beds)");
		Verdict ok = BuildingRules.checkRoom(BuildingType.BARRACKS,
				facts(16, Map.of("minecraft:grindstone", 2, "#bed", 3, "#chest", 1)), TOWN);
		assertEquals(BuildingStatus.VALID, ok.status());
		assertEquals(2, ok.slots());
	}

	@Test void houseSlotsAreBeds() {
		Verdict v = BuildingRules.checkRoom(BuildingType.HOUSE, facts(12, Map.of("#bed", 3, "#light", 1)), TOWN);
		assertEquals(3, v.slots());
	}

	@Test void smithyAcceptsAnyAnvilKey() {
		Verdict v = BuildingRules.checkRoom(BuildingType.SMITHY,
				facts(12, Map.of("minecraft:blast_furnace", 1, "#anvil", 1, "#chest", 1)), TOWN);
		assertEquals(BuildingStatus.VALID, v.status());
	}

	@Test void jobKeysFollowTheJobBlockIds() {
		for (JobBlockType j : JobBlockType.values()) {
			Requirements r = BuildingRequirements.of(j.building());
			assertEquals("villageheart:" + j.id(), r.jobKey(), j.toString());
		}
	}
}
