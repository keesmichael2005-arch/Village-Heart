package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.blueprint.BuildPlan.Kind;
import dev.villageheart.core.blueprint.BuildPlan.Step;
import dev.villageheart.core.room.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T-L1-5 */
class BuildPlanTest {
	private static final Pos GROUND = new Pos(10, 64, 20);

	private static List<Step> plan(Blueprint bp, Rotation4 r) { return BuildPlan.of(bp, new Placement(GROUND, r)); }

	private static List<Step> kind(List<Step> steps, Kind k) { return steps.stream().filter(s -> s.kind() == k).toList(); }

	@Test
	void smallHouseOrder() {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement p = new Placement(GROUND, Rotation4.NONE);
		List<Step> steps = BuildPlan.of(bp, p);
		List<Step> clear = kind(steps, Kind.CLEAR);
		assertEquals(246, clear.size());
		// top layer first
		int prevY = Integer.MAX_VALUE;
		for (Step s : clear) {
			assertTrue(s.pos().y() <= prevY, "clear order must go down");
			prevY = s.pos().y();
		}
		assertEquals(172, BuildPlan.placeCount(steps));
		assertEquals(171, kind(steps, Kind.CHECK).size());
		// the order of kinds: all CLEAR, then PLACE, then CHECK, then the marker PLACE
		for (int i = 0; i < steps.size(); i++) assertEquals(i, steps.get(i).index());
		assertEquals(Kind.CLEAR, steps.get(0).kind());
		assertEquals(Kind.CLEAR, steps.get(245).kind());
		assertEquals(Kind.PLACE, steps.get(246).kind());
		Step last = steps.get(steps.size() - 1);
		assertEquals(Kind.PLACE, last.kind());
		assertTrue(bp.isMarker(last.entry()));
		assertEquals(p.world(bp, 3, 3, 0), last.pos());
		assertEquals(Kind.CHECK, steps.get(steps.size() - 2).kind());
		// no other PLACE step is the marker
		assertEquals(1, kind(steps, Kind.PLACE).stream().filter(s -> bp.isMarker(s.entry())).count());

		// PLACE: MAIN pass layer by layer, then attached (torch), the marker excepted
		List<Step> place = kind(steps, Kind.PLACE);
		int layerOfPrev = -1;
		int firstAttached = -1;
		for (int i = 0; i < place.size() - 1; i++) {
			Step s = place.get(i);
			int layer = s.pos().y() - GROUND.y() - bp.base();
			if (s.entry().pass() == PaletteEntry.Pass.MAIN) {
				assertTrue(layer >= layerOfPrev, "layers ascend");
				layerOfPrev = layer;
				assertEquals(-1, firstAttached, "no main step after an attached one");
			} else if (firstAttached < 0) {
				firstAttached = i;
			}
		}
		assertEquals(170, firstAttached);
		assertEquals("minecraft:wall_torch", place.get(170).entry().blockId());
		// door and bed are one step each, with one part each
		List<Step> units = place.stream().filter(s -> !s.parts().isEmpty()).toList();
		assertEquals(2, units.size());
		for (Step u : units) assertEquals(1, u.parts().size());
		Step door = units.stream().filter(s -> s.entry().blockId().endsWith("_door")).findFirst().orElseThrow();
		assertEquals(door.pos().above(), door.parts().get(0).pos());
		assertEquals("upper", door.parts().get(0).entry().property("half"));
		// every PLACE cell is also CHECKed (except the marker), in the same order
		List<Step> checks = kind(steps, Kind.CHECK);
		for (int i = 0; i < checks.size(); i++) assertEquals(place.get(i).pos(), checks.get(i).pos());
		// CLEAR covers exactly the touched cells
		Set<Pos> cleared = new HashSet<>();
		for (Step s : clear) cleared.add(s.pos());
		assertEquals(new HashSet<>(SiteRules.touched(bp, p)), cleared);
	}

	@Test
	void layersWalkRoundOnce() {
		// within one layer the cells go round the box by angle: the angle never decreases
		Blueprint bp = Blueprints.SMALL_HOUSE;
		Placement p = new Placement(GROUND, Rotation4.NONE);
		Pos min = p.min(bp), max = p.max(bp);
		double cx = (min.x() + max.x() + 1) / 2.0, cz = (min.z() + max.z() + 1) / 2.0;
		double prev = -1;
		for (Step s : kind(BuildPlan.of(bp, p), Kind.PLACE)) {
			if (s.pos().y() != GROUND.y() + 1 || s.entry().pass() != PaletteEntry.Pass.MAIN) continue;
			double a = Math.atan2(s.pos().z() + 0.5 - cz, s.pos().x() + 0.5 - cx);
			if (a < 0) a += 2 * Math.PI;
			assertTrue(a >= prev - 1e-12, "angle " + a + " after " + prev);
			prev = a;
		}
		assertTrue(prev > 0);
	}

	@Test
	void farmWaterComesAfterAllFarmland() {
		List<Step> place = kind(plan(Blueprints.FARM_PLOT, Rotation4.NONE), Kind.PLACE);
		assertEquals(121, place.size());
		int water = -1, lastFarmland = -1, lastFence = -1;
		for (int i = 0; i < place.size(); i++) {
			String id = place.get(i).entry().blockId();
			if (id.equals("minecraft:water")) water = i;
			if (id.equals("minecraft:farmland")) lastFarmland = i;
			if (id.contains("fence")) lastFence = i;
		}
		assertEquals(place.size() - 1, water);
		assertTrue(lastFarmland < water && lastFence < water);
	}

	@Test
	void sameStepCountInEveryRotationAndDeterministic() {
		for (Blueprint bp : Blueprints.all()) {
			int n = plan(bp, Rotation4.NONE).size();
			for (Rotation4 r : Rotation4.values()) {
				List<Step> a = plan(bp, r), b = plan(bp, r);
				assertEquals(n, a.size(), bp.id() + " " + r);
				assertEquals(a, b);
				assertEquals(bp.placeCount(), BuildPlan.placeCount(a));
			}
		}
	}

	@Test
	void progress() {
		List<Step> steps = plan(Blueprints.SMALL_HOUSE, Rotation4.NONE);
		assertEquals(0, BuildPlan.percent(steps, 0));
		assertEquals(0, BuildPlan.percent(steps, 246));
		assertEquals(50, BuildPlan.percent(steps, 246 + 86));
		assertEquals(100, BuildPlan.percent(steps, steps.size()));
		assertEquals(86, BuildPlan.placedBefore(steps, 246 + 86));
	}
}
