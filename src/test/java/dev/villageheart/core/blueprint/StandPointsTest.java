package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** T-L1-6 */
class StandPointsTest {
	private static final Pos GROUND = new Pos(0, 64, 0);
	private static final StandPoints.Standable FLAT = (x, z) -> OptionalInt.of(65);

	@Test
	void ringIsTheBoxGrownByOne() {
		Pos min = new Pos(0, 65, 0), max = new Pos(6, 69, 7);
		List<Pos> ring = StandPoints.ring(min, max, FLAT);
		assertEquals(2 * (9 + 10) - 4, ring.size());
		for (Pos p : ring) {
			assertTrue(p.x() == -1 || p.x() == 7 || p.z() == -1 || p.z() == 8, p.toString());
			assertEquals(65, p.y());
		}
		assertTrue(StandPoints.ring(min, max, (x, z) -> OptionalInt.empty()).isEmpty());
		List<Pos> noWest = StandPoints.ring(min, max, (x, z) -> x < 0 ? OptionalInt.empty() : OptionalInt.of(65));
		assertTrue(noWest.stream().noneMatch(p -> p.x() < 0));
		assertFalse(noWest.isEmpty());
	}

	@Test
	void reachIsEnoughForTheRoofCentreAndTheFarmWater() {
		for (Rotation4 r : Rotation4.values()) {
			for (Blueprint bp : Blueprints.all()) {
				Placement p = new Placement(GROUND, r);
				List<Pos> ring = StandPoints.ring(p.min(bp), p.max(bp), FLAT);
				// every touched cell has a stand point within reach
				for (Pos c : SiteRules.touched(bp, p)) {
					assertFalse(StandPoints.within(c, ring, Balance.BUILD_REACH, c).isEmpty(), bp.id() + " " + r + " " + c);
				}
			}
			Placement p = new Placement(GROUND, r);
			Pos roofCentre = p.world(Blueprints.SMALL_HOUSE, 3, 4, 4);
			assertFalse(StandPoints.within(roofCentre, StandPoints.ring(p.min(Blueprints.SMALL_HOUSE), p.max(Blueprints.SMALL_HOUSE), FLAT),
					Balance.BUILD_REACH, roofCentre).isEmpty());
		}
	}

	@Test
	void nearestFirstWithinReach() {
		Pos cell = new Pos(0, 66, 0);
		List<Pos> points = List.of(new Pos(20, 65, 0), new Pos(3, 65, 0), new Pos(-2, 65, 0), new Pos(0, 65, 6));
		List<Pos> near = StandPoints.within(cell, points, 7.0, new Pos(-5, 65, 0));
		assertEquals(List.of(new Pos(-2, 65, 0), new Pos(0, 65, 6), new Pos(3, 65, 0)), near);
		assertTrue(StandPoints.within(cell, points, 1.0, cell).isEmpty());
	}

	@Test
	void aWalkAcrossTheBoxGoesViaACorner() {
		Pos min = new Pos(0, 65, 0), max = new Pos(6, 69, 7);
		List<Pos> ring = StandPoints.ring(min, max, FLAT);
		Pos west = new Pos(-1, 65, 3), east = new Pos(7, 65, 3);
		assertTrue(StandPoints.crosses(min, max, west.x() + 0.5, west.z() + 0.5, east.x() + 0.5, east.z() + 0.5));
		Pos way = StandPoints.waypoint(min, max, ring, west, east).orElseThrow();
		assertTrue(way.x() == -1 && (way.z() == -1 || way.z() == 8), way.toString());
		// from that corner the target is in the clear only along the side: the next waypoint is the far corner on the same side or none
		java.util.Optional<Pos> next = StandPoints.waypoint(min, max, ring, way, east);
		assertTrue(next.isPresent() && next.get().x() == 7 && next.get().z() == way.z(), "the second leg goes to the other corner of that side: " + next);
		assertTrue(StandPoints.waypoint(min, max, ring, next.get(), east).isEmpty(), "and from there the target is in the clear");
		// along the west side there is nothing to avoid
		assertTrue(StandPoints.waypoint(min, max, ring, new Pos(-1, 65, 0), new Pos(-1, 65, 8)).isEmpty());
		// a corner that is not a stand point is not offered
		List<Pos> noCorners = ring.stream().filter(p -> (p.x() != -1 && p.x() != 7) || (p.z() != -1 && p.z() != 8)).toList();
		assertTrue(StandPoints.waypoint(min, max, noCorners, west, east).isEmpty());
		assertTrue(StandPoints.inside(min, max, 3.5, 3.5, 0.3));
		assertFalse(StandPoints.inside(min, max, -0.5, 3.5, 0.3));
	}
}
