package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.HeartLocation;
import dev.villageheart.core.Rank;
import dev.villageheart.core.Tier;
import dev.villageheart.core.blueprint.SiteRules.Box;
import dev.villageheart.core.blueprint.SiteRules.Code;
import dev.villageheart.core.blueprint.SiteRules.SiteCell;
import dev.villageheart.core.blueprint.SiteRules.SiteFacts;
import dev.villageheart.core.room.Pos;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** T-L1-5: FOOTING (rule 11c), rule 8 for defences, the explicit UNCHECKED override set, the stand ring of a sandwiched wall. */
class DefenceSiteRulesTest {
	private static final HeartLocation HEART = new HeartLocation("minecraft:overworld", 0, 0, 0);
	private static final String DIM = "minecraft:overworld";
	private static final Pos GROUND = new Pos(10, 0, 30);
	private static final StandPoints.Standable FLAT = (x, z) -> OptionalInt.of(1);

	/** Solid ground (BLOCKED-free: a stone block counts as CLEARABLE-free "LOG"? no: plain AIR cells with a solid floor row). */
	private static final class World implements SiteRules.SiteView {
		final Map<Pos, SiteCell> cells = new HashMap<>();
		/** Everything at or below this y is solid. */
		int floorY = 0;
		final Map<Pos, Boolean> solidOverride = new HashMap<>();

		@Override
		public SiteCell cell(Pos p) { return cells.getOrDefault(p, p.y() <= floorY ? SiteCell.BLOCKED : SiteCell.AIR); }

		@Override
		public String blockId(Pos p) { return "minecraft:stone"; }

		@Override
		public boolean solid(Pos p) { return solidOverride.getOrDefault(p, p.y() <= floorY); }
	}

	private static SiteFacts facts(Blueprint bp, Pos ground, Rotation4 r, World w, List<Box> others, Tier tier, StandPoints.Standable stand) {
		return new SiteFacts(bp, new Placement(ground, r), HEART, DIM, tier, Rank.STEWARD, -64, 320, w, List.of(), others, others.size(), stand);
	}

	private static Code code(SiteFacts f) { return SiteRules.check(f).map(SiteRules.SiteProblem::code).orElse(null); }

	private static World ground() {
		World w = new World();
		// the ground block row itself (y 0) is natural: the rules treat a BLOCKED floor as "stone in the way" only inside the box
		return w;
	}

	@Test
	void aWallOnSolidGroundPasses() {
		Blueprint wall = DefenceBlueprints.wall(10);
		assertNull(code(facts(wall, GROUND, Rotation4.NONE, ground(), List.of(), Tier.VILLAGE, FLAT)));
	}

	@Test
	void footingNamesTheCellOverTheDip() {
		Blueprint wall = DefenceBlueprints.wall(10);
		World w = ground();
		w.solidOverride.put(new Pos(10 + 6, 0, 30), false); // the 7th column (index 6) has air below layer 0
		var p = SiteRules.check(facts(wall, GROUND, Rotation4.NONE, w, List.of(), Tier.VILLAGE, FLAT)).orElseThrow();
		assertEquals(Code.FOOTING, p.code());
		assertEquals(new Pos(16, 1, 30), p.at());
		assertEquals("No ground under (16, 1, 30): defences need level ground", p.english());
		// a solid footing that is merely clearable (fake view says solid) passes
		w.solidOverride.put(new Pos(16, 0, 30), true);
		assertNull(code(facts(wall, GROUND, Rotation4.NONE, w, List.of(), Tier.VILLAGE, FLAT)));
	}

	@Test
	void aHouseStillBridgesADip() {
		World w = ground();
		w.solidOverride.put(new Pos(12, 0, 32), false);
		assertNull(code(facts(Blueprints.SMALL_HOUSE, GROUND, Rotation4.NONE, w, List.of(), Tier.VILLAGE, FLAT)));
	}

	@Test
	void footingOrder() {
		// SUPPORT comes before FOOTING; FOOTING before NO_STAND
		Blueprint wall = DefenceBlueprints.wall(10);
		World dip = ground();
		dip.solidOverride.put(new Pos(12, 0, 30), false);
		assertEquals(Code.FOOTING, code(facts(wall, GROUND, Rotation4.NONE, dip, List.of(), Tier.VILLAGE, (x, z) -> OptionalInt.empty())));
		World both = ground();
		both.solidOverride.put(new Pos(12, 0, 30), false);
		both.cells.put(new Pos(11, 1, 30), SiteCell.CLEARABLE);
		SiteRules.SiteView withDependent = new SiteRules.SiteView() {
			@Override public SiteCell cell(Pos p) { return both.cell(p); }
			@Override public String blockId(Pos p) { return "minecraft:stone"; }
			@Override public boolean solid(Pos p) { return both.solid(p); }
			@Override public java.util.Optional<SiteRules.Dependent> dependent(Pos cleared, java.util.Set<Long> touched) {
				return cleared.equals(new Pos(11, 1, 30)) ? java.util.Optional.of(new SiteRules.Dependent("minecraft:torch", new Pos(11, 1, 29)))
						: java.util.Optional.empty();
			}
		};
		SiteFacts f = new SiteFacts(wall, new Placement(GROUND, Rotation4.NONE), HEART, DIM, Tier.VILLAGE, Rank.STEWARD, -64, 320, withDependent,
				List.of(), List.of(), 0, FLAT);
		assertEquals(Code.SUPPORT, code(f));
	}

	private static Box box(Blueprint bp, Pos ground, Rotation4 r) { return SiteRules.box(bp, new Placement(ground, r)); }

	@Test
	void defencesMayTouchButNeverOverlap() {
		Blueprint wall = DefenceBlueprints.wall(10);
		Box a = box(wall, GROUND, Rotation4.NONE); // x 10..19
		// end to end, gap 0
		assertNull(code(facts(wall, new Pos(20, 0, 30), Rotation4.NONE, ground(), List.of(a), Tier.VILLAGE, FLAT)));
		// a wall starting beside A's end going south (a corner)
		assertNull(code(facts(wall, new Pos(20, 0, 30), Rotation4.CLOCKWISE_90, ground(), List.of(a), Tier.VILLAGE, FLAT)));
		// overlapping by one cell
		assertEquals(Code.BUILD_OVERLAP, code(facts(DefenceBlueprints.gate(7), new Pos(19, 0, 30), Rotation4.NONE, ground(), List.of(a), Tier.VILLAGE, FLAT)));
		// a lamp touching a wall
		assertNull(code(facts(Blueprints.LAMP_POST, new Pos(12, 0, 29), Rotation4.NONE, ground(), List.of(a), Tier.VILLAGE, FLAT)));
	}

	@Test
	void mixedPairsKeepTheOneBlockRule() {
		Blueprint wall = DefenceBlueprints.wall(10);
		Box house = box(Blueprints.SMALL_HOUSE, new Pos(10, 0, 40), Rotation4.NONE); // x 7..13, z 36..43
		// a wall at gap 0 and gap 1 from a pending house is refused; gap 2 is fine
		assertEquals(Code.BUILD_OVERLAP, code(facts(wall, new Pos(10, 0, 44), Rotation4.NONE, ground(), List.of(house), Tier.VILLAGE, FLAT)));
		assertEquals(Code.BUILD_OVERLAP, code(facts(wall, new Pos(10, 0, 45), Rotation4.NONE, ground(), List.of(house), Tier.VILLAGE, FLAT)));
		assertNull(code(facts(wall, new Pos(10, 0, 46), Rotation4.NONE, ground(), List.of(house), Tier.VILLAGE, FLAT)));
		// and a house next to a pending wall
		Box w = box(wall, GROUND, Rotation4.NONE);
		assertEquals(Code.BUILD_OVERLAP, code(facts(Blueprints.SMALL_HOUSE, new Pos(10, 0, 35), Rotation4.NONE, ground(), List.of(w), Tier.VILLAGE, FLAT))); // gap 0
		assertEquals(Code.BUILD_OVERLAP, code(facts(Blueprints.SMALL_HOUSE, new Pos(10, 0, 36), Rotation4.NONE, ground(), List.of(w), Tier.VILLAGE, FLAT))); // gap 1
		assertNull(code(facts(Blueprints.SMALL_HOUSE, new Pos(10, 0, 37), Rotation4.NONE, ground(), List.of(w), Tier.VILLAGE, FLAT)));
	}

	@Test
	void aPendingWallIsItsFullLengthNotTheUnitInstance() {
		Blueprint wall20 = Blueprints.resolve("wall_segment", new BlueprintParams(20)).orElseThrow();
		Blueprint wall1 = Blueprints.byId("wall_segment").orElseThrow();
		Box pending = box(wall20, new Pos(0, 0, 30), Rotation4.NONE); // x 0..19
		Box wrong = box(wall1, new Pos(0, 0, 30), Rotation4.NONE); // x 0..0
		// a house whose box starts at x 15 (anchor 3: ground x 18) overlaps the 20-long wall, but not the unit instance
		Pos houseGround = new Pos(18, 0, 30);
		assertEquals(Code.BUILD_OVERLAP, code(facts(Blueprints.SMALL_HOUSE, houseGround, Rotation4.NONE, ground(), List.of(pending), Tier.VILLAGE, FLAT)));
		assertNull(code(facts(Blueprints.SMALL_HOUSE, houseGround, Rotation4.NONE, ground(), List.of(wrong), Tier.VILLAGE, FLAT)));
	}

	@Test
	void aSandwichedWallHasNoRingLeft() {
		Blueprint wall = DefenceBlueprints.wall(10);
		Box a = box(wall, new Pos(20, 0, 30), Rotation4.NONE); // x 20..29, between B and C
		Box b = box(wall, new Pos(10, 0, 30), Rotation4.NONE); // x 10..19
		Box c = box(wall, new Pos(30, 0, 30), Rotation4.NONE); // x 30..39
		// walls A, B, C in a line: A's ring columns at z = 29 and z = 31 are outside B and C, so A keeps its ring
		StandPoints.Standable avoiding = StandPoints.avoiding(FLAT, List.of(b, c));
		assertTrue(StandPoints.ring(a.min(), a.max(), avoiding).size() > 0);
		// a pending wall that wraps A (a U of three boxes) takes every ring column
		Box north = new Box(new Pos(19, 1, 29), new Pos(30, 3, 29), true);
		Box south = new Box(new Pos(19, 1, 31), new Pos(30, 3, 31), true);
		Box west = new Box(new Pos(19, 1, 30), new Pos(19, 3, 30), true);
		Box east = new Box(new Pos(30, 1, 30), new Pos(30, 3, 30), true);
		StandPoints.Standable none = StandPoints.avoiding(FLAT, List.of(north, south, west, east));
		// ring columns of the 20..29 box: x 19..30, z 29..31: every one lies in one of the four boxes
		assertTrue(StandPoints.ring(a.min(), a.max(), none).isEmpty());
		SiteFacts sandwich = facts(wall, new Pos(20, 0, 30), Rotation4.NONE, ground(), List.of(north, south, west, east), Tier.VILLAGE, none);
		assertEquals(Code.NO_STAND, code(sandwich));
	}

	@Test
	void uncheckedOverrideSetIsExplicit() {
		for (Code c : Code.values()) {
			boolean expected = switch (c) {
				case OVERLAP, BUILD_OVERLAP, FLUID, TREE, BLOCKED, SUPPORT, NO_STAND, FOOTING -> true;
				default -> false;
			};
			assertEquals(expected, SiteRules.overridableByUnchecked(c), c.name());
		}
	}

	@Test
	void appendedCodesKeepTheOrdinals() {
		assertEquals(7, Code.OVERLAP.ordinal());
		assertEquals(13, Code.NO_STAND.ordinal());
		assertEquals(14, Code.UNCHECKED.ordinal());
		assertEquals(15, Code.FOOTING.ordinal());
		assertEquals(18, Code.TOO_SHORT.ordinal());
	}

	@Test
	void watchtowerNeedsTown() {
		Blueprint tower = Blueprints.WATCHTOWER;
		assertEquals(Code.TIER, code(facts(tower, GROUND, Rotation4.NONE, ground(), List.of(), Tier.VILLAGE, FLAT)));
		assertNull(code(facts(tower, GROUND, Rotation4.NONE, ground(), List.of(), Tier.TOWN, FLAT)));
	}
}
