package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Tier;
import dev.villageheart.core.building.BuildingRules;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.RoomFacts;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScanner;
import org.junit.jupiter.api.Test;

/** Each authored job room passes its building check (the link, for Mine and the lodge, is the one reason left). */
class JobBlueprintRoomTest {
	@Test
	void eachJobRoomPasses() {
		check(JobBlueprints.KITCHEN, BuildingType.KITCHEN, Tier.VILLAGE, true);
		check(JobBlueprints.BARRACKS, BuildingType.BARRACKS, Tier.VILLAGE, true);
		check(JobBlueprints.LUMBER_CAMP, BuildingType.LUMBER_CAMP, Tier.VILLAGE, true);
		check(JobBlueprints.FARMHOUSE, BuildingType.FARMHOUSE, Tier.VILLAGE, true);
		check(JobBlueprints.MARKET, BuildingType.MARKET, Tier.VILLAGE, true);
		check(JobBlueprints.STORAGE, BuildingType.STORAGE, Tier.VILLAGE, true);
		check(JobBlueprints.BUILDERS_HUT, BuildingType.BUILDERS_HUT, Tier.VILLAGE, true);
		check(JobBlueprints.SMITHY, BuildingType.SMITHY, Tier.TOWN, true);
		check(JobBlueprints.MINE, BuildingType.MINE, Tier.VILLAGE, false);
		check(JobBlueprints.HUNTING_LODGE, BuildingType.HUNTING_LODGE, Tier.VILLAGE, false);
	}

	private static void check(Blueprint bp, BuildingType type, Tier tier, boolean clear) {
		for (Rotation4 r : Rotation4.values()) {
			Placement p = new Placement(new Pos(0, 64, 0), r);
			var scan = RoomScanner.scanFromMarker(new BlueprintView(bp, p), marker(bp, p), p.front(bp));
			assertTrue(scan.enclosed(), bp.id() + " " + r + " " + scan.problems());
			int min = type == BuildingType.BARRACKS ? 16 : type == BuildingType.SMITHY ? 12 : 9;
			assertTrue(scan.floorArea() >= min, bp.id() + " floor " + scan.floorArea());
			var verdict = BuildingRules.checkRoom(type, RoomFacts.of(scan), new BuildingRules.CheckContext(tier, null, null, 0));
			if (clear) assertTrue(verdict.reasons().isEmpty(), bp.id() + " " + r + " " + verdict.reasons());
			else {
				assertEquals(1, verdict.reasons().size(), bp.id() + " " + verdict.reasons());
				assertTrue(verdict.reasons().get(0).message().toLowerCase(java.util.Locale.ROOT).contains("needs"), verdict.reasons().get(0).message());
			}
		}
	}

	private static Pos marker(Blueprint bp, Placement p) {
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			if (bp.isMarker(bp.entry(x, y, z))) return p.world(bp, x, y, z);
		}
		throw new AssertionError(bp.id());
	}
}
