package dev.villageheart.core.raid;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.villageheart.core.raid.ShelterPlanner.Kind;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T-L1-5. A worker shelters in its own bed, a free House bed, or a room, and never in someone else's bed. */
class ShelterPlannerTest {
	private static final Pos ME = new Pos(0, 64, 0);

	private static long pack(int x, int y, int z) { return new Pos(x, y, z).pack(); }

	@Test void ownHouseBedBeatsANearerBed() {
		Pos home = new Pos(10, 64, 0);
		var chosen = ShelterPlanner.choose(Optional.of(home), Set.of(home.pack(), pack(1, 64, 0)), Set.of(), List.of(new Pos(0, 64, 1)), ME);
		assertEquals(new ShelterPlanner.ShelterTarget(home, Kind.OWN_BED), chosen.orElseThrow());
	}

	@Test void aTakenBedIsRefusedExceptTheWorkersOwn() {
		Pos home = new Pos(8, 64, 0);
		Pos free = new Pos(3, 64, 0);
		Pos taken = new Pos(1, 64, 0);
		var chosen = ShelterPlanner.choose(Optional.of(home), Set.of(home.pack(), free.pack(), taken.pack()), Set.of(home.pack(), taken.pack()),
				List.of(), ME);
		assertEquals(Kind.OWN_BED, chosen.orElseThrow().kind());
		var noHome = ShelterPlanner.choose(Optional.empty(), Set.of(free.pack(), taken.pack()), Set.of(taken.pack()), List.of(), ME);
		assertEquals(new ShelterPlanner.ShelterTarget(free, Kind.HOUSE_BED), noHome.orElseThrow());
	}

	@Test void noBedsUsesTheNearestRoomDoorAndNothingIsEmpty() {
		Pos far = new Pos(6, 64, 0);
		Pos near = new Pos(0, 64, 2);
		var door = ShelterPlanner.choose(Optional.empty(), Set.of(), Set.of(), List.of(far, near), ME);
		assertEquals(new ShelterPlanner.ShelterTarget(near, Kind.ROOM), door.orElseThrow());
		assertTrue(ShelterPlanner.choose(Optional.empty(), Set.of(), Set.of(), List.of(), ME).isEmpty());
		assertTrue(ShelterPlanner.choose(Optional.of(new Pos(4, 64, 0)), Set.of(pack(4, 64, 0)), Set.of(pack(4, 64, 0)), List.of(), ME).isPresent(),
				"the worker's own bed is still a shelter when every bed is taken");
	}

	@Test void tiesBreakByXThenZThenY() {
		Pos lowX = new Pos(0, 64, 2);
		Pos highX = new Pos(2, 64, 0);
		var byX = ShelterPlanner.choose(Optional.empty(), Set.of(lowX.pack(), highX.pack()), Set.of(), List.of(), ME);
		assertEquals(lowX, byX.orElseThrow().pos());
		Pos lowZ = new Pos(2, 68, 0);
		Pos highZ = new Pos(2, 64, 4);
		var byZ = ShelterPlanner.choose(Optional.empty(), Set.of(), Set.of(), List.of(lowZ, highZ), ME);
		assertEquals(lowZ, byZ.orElseThrow().pos(), "equal x: the smaller z wins, even when it is higher");
		Pos lowY = new Pos(2, 60, 4);
		Pos highY = new Pos(2, 68, 4);
		var byY = ShelterPlanner.choose(Optional.empty(), Set.of(), Set.of(), List.of(highY, lowY), ME);
		assertEquals(lowY, byY.orElseThrow().pos());
	}
}
