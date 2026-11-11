package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.blueprint.BuildPlan.Kind;
import dev.villageheart.core.room.Pos;
import org.junit.jupiter.api.Test;

/** Mirror math and decorative cells, which must not change the order of a blueprint that has no decoration. */
class P4CoreTest {
	@Test
	void mirrorFlipsLocalXAndRoundTrips() {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement plain = new Placement(new Pos(10, 5, 10), Rotation4.NONE, false);
		Placement mirror = new Placement(new Pos(10, 5, 10), Rotation4.NONE, true);
		assertNotEquals(plain.world(bp, 0, 0, bp.anchorZ()).x(), mirror.world(bp, 0, 0, bp.anchorZ()).x());
		Placement turned = new Placement(new Pos(10, 5, 10), Rotation4.CLOCKWISE_90, true);
		Pos world = turned.world(bp, 1, 2, 3);
		assertArrayEquals(new int[] {1, 2, 3}, turned.local(bp, world));
	}

	@Test
	void decorationIsPlacedAfterTheMarker() {
		Blueprint bp = BlueprintParser.parse("""
				id decor_hut
				name Decor Hut
				tier VILLAGE
				kind BUILDING
				builds house
				base 0
				anchor 0 0
				front NORTH
				size 1 2 1
				palette
				. any
				_ air
				M villageheart:house_marker <- villageheart:house_marker "marker"
				F minecraft:flower_pot <- minecraft:flower_pot "pot" pass=decor
				layer 0
				M
				layer 1
				F
				end
				""");
		int marker = -1, pot = -1;
		for (BuildPlan.Step s : BuildPlan.of(bp, new Placement(new Pos(0, 0, 0), Rotation4.NONE))) {
			if (s.kind() != Kind.PLACE) continue;
			if (s.entry().blockId().contains("marker")) marker = s.index();
			if (s.entry().blockId().contains("flower_pot")) pot = s.index();
		}
		assertTrue(marker >= 0 && pot > marker, "marker " + marker + " pot " + pot);
	}

	@Test
	void aBlueprintWithoutDecorationKeepsTheMarkerLast() {
		var steps = BuildPlan.of(Blueprints.SMALL_HOUSE, new Placement(new Pos(0, 0, 0), Rotation4.NONE));
		BuildPlan.Step last = null;
		for (BuildPlan.Step s : steps) if (s.kind() == Kind.PLACE) last = s;
		assertNotNull(last);
		assertTrue(Blueprints.SMALL_HOUSE.isMarker(last.entry()), last.entry().state());
	}
}
