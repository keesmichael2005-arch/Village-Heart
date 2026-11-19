package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.Tier;
import dev.villageheart.core.building.AreaCounter;
import dev.villageheart.core.building.BuildingRequirements;
import dev.villageheart.core.building.BuildingRules;
import dev.villageheart.core.building.BuildingType;
import dev.villageheart.core.building.RoomFacts;
import dev.villageheart.core.building.Verdict;
import dev.villageheart.core.room.BlockView;
import dev.villageheart.core.room.Cell;
import dev.villageheart.core.room.Pos;
import dev.villageheart.core.room.RoomScan;
import dev.villageheart.core.room.RoomScanner;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-L1-4 */
class BlueprintRoomTest {
	private static final BuildingRules.CheckContext CTX = new BuildingRules.CheckContext(Tier.VILLAGE, null, null, 0);
	/** Town, with a linked outdoor site, so the Mine, the lodge, and the Smithy can pass. */
	private static final BuildingRules.CheckContext LINKED = new BuildingRules.CheckContext(Tier.TOWN, null, new Pos(0, 64, 0), 1);

	private static Pos marker(Blueprint bp, Placement p) {
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			if (bp.isMarker(bp.entry(x, y, z))) return p.world(bp, x, y, z);
		}
		throw new AssertionError("no marker");
	}

	private static Verdict verdict(Blueprint bp, BuildingType type, BlockView view, Placement p) {
		RoomScan scan = RoomScanner.scanFromMarker(view, marker(bp, p), p.front(bp));
		assertTrue(scan.enclosed(), bp.id() + " " + p.rotation() + " " + scan.problems());
		assertEquals(25, scan.floorArea());
		return BuildingRules.checkRoom(type, RoomFacts.of(scan), CTX);
	}

	@Test
	void houseAndShedAreValidRoomsInEveryRotation() {
		for (Rotation4 r : Rotation4.values()) {
			Placement p = new Placement(new Pos(40, 70, -12), r);
			Verdict house = verdict(Blueprints.SMALL_HOUSE, BuildingType.HOUSE, new BlueprintView(Blueprints.SMALL_HOUSE, p), p);
			assertTrue(house.reasons().isEmpty(), r + " " + house.reasons());
			assertEquals(1, house.slots());
			Verdict shed = verdict(Blueprints.STORAGE_SHED, BuildingType.STORAGE, new BlueprintView(Blueprints.STORAGE_SHED, p), p);
			assertTrue(shed.reasons().isEmpty(), r + " " + shed.reasons());
			assertEquals(2, shed.slots());
		}
	}

	/** The same view with one cell replaced. */
	private record Patched(BlockView inner, Pos at, Cell replacement) implements BlockView {
		@Override
		public Cell cell(int x, int y, int z) { return at.x() == x && at.y() == y && at.z() == z ? replacement : inner.cell(x, y, z); }

		@Override
		public boolean openToSky(int x, int y, int z) { return inner.openToSky(x, y, z); }
	}

	@Test
	void aMissingBedOrChestIsReported() {
		for (Rotation4 r : Rotation4.values()) {
			Placement p = new Placement(new Pos(0, 10, 0), r);
			Blueprint house = Blueprints.SMALL_HOUSE;
			BlockView view = new BlueprintView(house, p);
			// the bed head carries the #bed content
			Pos head = p.world(house, 1, 1, 6);
			Verdict v = verdict(house, BuildingType.HOUSE, new Patched(view, head, Cell.SOLID), p);
			assertEquals(1, v.reasons().size(), r + " " + v.reasons());
			assertEquals("Needs 1 more bed", v.reasons().get(0).message());

			Blueprint shed = Blueprints.STORAGE_SHED;
			BlockView sview = new BlueprintView(shed, p);
			Pos chest = p.world(shed, 5, 1, 6);
			Verdict sv = verdict(shed, BuildingType.STORAGE, new Patched(sview, chest, Cell.SOLID), p);
			assertEquals(1, sv.reasons().size(), r + " " + sv.reasons());
			assertEquals("Needs 1 more chest", sv.reasons().get(0).message());
		}
	}

	@Test
	void farmWaterIsWithinFourOfEveryFarmland() {
		Blueprint bp = Blueprints.FARM_PLOT;
		int farmland = 0, water = 0;
		int wx = -1, wz = -1;
		for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			PaletteEntry e = bp.entry(x, 0, z);
			if (e.blockId().equals("minecraft:water")) { water++; wx = x; wz = z; }
		}
		assertEquals(1, water);
		for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			PaletteEntry e = bp.entry(x, 0, z);
			if (!e.blockId().equals("minecraft:farmland")) continue;
			farmland++;
			assertTrue(Math.abs(x - wx) <= 4 && Math.abs(z - wz) <= 4, "farmland (" + x + ", " + z + ") is too far from the water");
		}
		assertEquals(80, farmland);
	}

	private static Pos find(Blueprint bp, Placement p, char symbol) {
		for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
			if (bp.entry(x, y, z).symbol() == symbol) return p.world(bp, x, y, z);
		}
		throw new AssertionError(bp.id() + " has no '" + symbol + "'");
	}

	@Test
	void everyWorkplaceRoomIsValid() {
		for (Blueprint bp : List.of(WorkplaceBlueprints.FARMHOUSE, WorkplaceBlueprints.LUMBER_CAMP, WorkplaceBlueprints.KITCHEN,
				WorkplaceBlueprints.BUILDERS_HUT, WorkplaceBlueprints.MINE, WorkplaceBlueprints.HUNTING_LODGE, WorkplaceBlueprints.BARRACKS,
				WorkplaceBlueprints.MARKET, WorkplaceBlueprints.SMITHY)) {
			BuildingType type = BuildingType.byId(bp.buildsTypeId()).orElseThrow();
			for (Rotation4 r : Rotation4.values()) {
				Placement p = new Placement(new Pos(40, 70, -12), r);
				RoomScan scan = RoomScanner.scanFromMarker(new BlueprintView(bp, p), marker(bp, p), p.front(bp));
				assertTrue(scan.enclosed(), bp.id() + " " + r + " " + scan.problems());
				assertTrue(scan.floorArea() >= BuildingRequirements.of(type).minFloor(), bp.id() + " floor " + scan.floorArea());
				assertTrue(bp.materials().count("minecraft:oak_stairs") > 0, bp.id());
				Verdict v = BuildingRules.checkRoom(type, RoomFacts.of(scan), LINKED);
				assertTrue(v.reasons().isEmpty(), bp.id() + " " + r + " " + v.reasons());
			}
		}
	}

	@Test
	void barracksYardIsOutsideTheRoomAndHasTwoTargets() {
		Blueprint bp = WorkplaceBlueprints.BARRACKS;
		for (Rotation4 r : Rotation4.values()) {
			Placement p = new Placement(new Pos(10, 64, 10), r);
			BlockView view = new BlueprintView(bp, p);
			RoomScan scan = RoomScanner.scanFromMarker(view, marker(bp, p), p.front(bp));
			Pos yard = find(bp, p, 'Y');
			assertFalse(scan.occupies(yard), r + " yard is inside the barracks");
			assertTrue(AreaCounter.count(view, yard, Balance.TRAINING_YARD_RADIUS, BuildingRequirements.TARGET).n()
					>= Balance.TRAINING_YARD_MIN_DUMMIES, r.toString());
		}
	}

	@Test
	void huntingGroveHasEnoughLogsAndLeaves() {
		Blueprint bp = WorkplaceBlueprints.HUNTING_GROVE;
		for (Rotation4 r : Rotation4.values()) {
			Placement p = new Placement(new Pos(0, 70, 0), r);
			BlockView view = new BlueprintView(bp, p);
			int n = AreaCounter.count(view, find(bp, p, 'M'), Balance.HUNTING_GROUND_RADIUS, BuildingRequirements.NATURAL).n();
			assertTrue(n >= Balance.HUNTING_GROUND_MIN_NATURAL, r + " " + n);
		}
	}
}
