package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** T-L1-4: build plans, rotations and stand points of the defence blueprints. */
class DefencePlanTest {
	private static final Pos GROUND = new Pos(100, 64, 100);
	private static final StandPoints.Standable FLAT = (x, z) -> OptionalInt.of(65);

	private static long count(List<BuildPlan.Step> steps, BuildPlan.Kind k) { return steps.stream().filter(s -> s.kind() == k).count(); }

	@Test
	void wallPlanCountsInEveryRotation() {
		Blueprint wall = DefenceBlueprints.wall(20);
		for (Rotation4 r : Rotation4.values()) {
			List<BuildPlan.Step> steps = BuildPlan.of(wall, new Placement(GROUND, r));
			assertEquals(60, count(steps, BuildPlan.Kind.CLEAR), r.name());
			assertEquals(60, count(steps, BuildPlan.Kind.PLACE), r.name());
			assertEquals(60, count(steps, BuildPlan.Kind.CHECK), r.name());
			assertEquals(180, steps.size());
		}
	}

	@Test
	void gatePlan() {
		Blueprint gate = DefenceBlueprints.gate(7);
		List<BuildPlan.Step> steps = BuildPlan.of(gate, new Placement(GROUND, Rotation4.NONE));
		assertEquals(18, count(steps, BuildPlan.Kind.PLACE));
		assertEquals(3, steps.stream().filter(s -> s.kind() == BuildPlan.Kind.PLACE && s.entry().blockId().equals("minecraft:oak_fence_gate")).count());
		assertTrue(steps.stream().allMatch(s -> s.parts().isEmpty()));
	}

	@Test
	void lampFencesBeforeLantern() {
		for (Rotation4 r : Rotation4.values()) {
			List<BuildPlan.Step> steps = BuildPlan.of(Blueprints.LAMP_POST, new Placement(GROUND, r));
			List<BuildPlan.Step> place = steps.stream().filter(s -> s.kind() == BuildPlan.Kind.PLACE).toList();
			assertEquals(3, place.size());
			assertEquals("minecraft:oak_fence", place.get(0).entry().blockId());
			assertEquals("minecraft:oak_fence", place.get(1).entry().blockId());
			assertEquals("minecraft:lantern", place.get(2).entry().blockId());
			assertEquals(new Pos(100, 67, 100), place.get(2).pos());
		}
	}

	@Test
	void towerLaddersComeAfterEveryMainBlockAndKeepTheirSupport() {
		Blueprint tower = Blueprints.WATCHTOWER;
		for (Rotation4 r : Rotation4.values()) {
			Placement pl = new Placement(GROUND, r);
			List<BuildPlan.Step> steps = BuildPlan.of(tower, pl);
			assertEquals(94, count(steps, BuildPlan.Kind.CLEAR));
			assertEquals(84, count(steps, BuildPlan.Kind.PLACE));
			assertEquals(84, count(steps, BuildPlan.Kind.CHECK));
			int lastMain = -1, firstLadder = Integer.MAX_VALUE;
			for (BuildPlan.Step s : steps) {
				if (s.kind() != BuildPlan.Kind.PLACE) continue;
				if (s.entry().pass() == PaletteEntry.Pass.MAIN) lastMain = s.index();
				else firstLadder = Math.min(firstLadder, s.index());
			}
			assertTrue(lastMain < firstLadder, r.name());
			Dir facing = r.apply(Dir.NORTH);
			int ladders = 0;
			for (BuildPlan.Step s : steps) {
				if (s.kind() != BuildPlan.Kind.PLACE || !s.entry().blockId().equals("minecraft:ladder")) continue;
				ladders++;
				Pos support = s.pos().offset(facing.opposite());
				PaletteEntry e = pl.entryAt(tower, support);
				assertNotNull(e, "support inside the box " + support);
				assertTrue(e.blockId().equals("minecraft:cobblestone") || e.blockId().equals("minecraft:oak_planks"), r + " " + support);
			}
			assertEquals(6, ladders);
		}
	}

	@Test
	void everyTouchedCellHasAStandPointWithinReach() {
		List<Blueprint> all = new java.util.ArrayList<>(List.of(Blueprints.LAMP_POST, Blueprints.WATCHTOWER));
		for (int n : new int[] {1, 2, 5, 20, 32}) all.add(DefenceBlueprints.wall(n));
		for (int n : new int[] {5, 6, 7, 20, 32}) all.add(DefenceBlueprints.gate(n));
		for (Rotation4 r : Rotation4.values()) for (Blueprint bp : all) {
			Placement p = new Placement(GROUND, r);
			List<Pos> ring = StandPoints.ring(p.min(bp), p.max(bp), FLAT);
			for (Pos c : SiteRules.touched(bp, p)) {
				assertFalse(StandPoints.within(c, ring, Balance.BUILD_REACH, c).isEmpty(), bp.id() + " " + r + " " + c);
			}
		}
	}

	@Test
	void theTowersFarthestCellsAreJustInReach() {
		Blueprint tower = Blueprints.WATCHTOWER;
		Placement p = new Placement(GROUND, Rotation4.NONE);
		List<Pos> ring = StandPoints.ring(p.min(tower), p.max(tower), FLAT);
		double worst = 0;
		for (Pos c : SiteRules.touched(tower, p)) {
			double best = Double.MAX_VALUE;
			for (Pos s : ring) best = Math.min(best, StandPoints.distance(s, c));
			worst = Math.max(worst, best);
		}
		assertTrue(worst <= Balance.BUILD_REACH && worst > 6.5, "farthest touched cell " + worst);
		// the top ladder (local 2, 5, 2) is 6.26 from the nearest ring column
		Pos top = p.world(tower, 2, 5, 2);
		double best = Double.MAX_VALUE;
		for (Pos s : ring) best = Math.min(best, StandPoints.distance(s, top));
		assertEquals(Math.sqrt(9 + 5.5 * 5.5), best, 1e-9);
	}
}
