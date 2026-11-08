package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.Balance;
import dev.villageheart.core.blueprint.SiteRules.Code;
import dev.villageheart.core.room.Pos;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** T-L1-1: line generation for axis-aligned start and end, lengths over 32 rejected. */
class WallLinesTest {
	private static final Pos START = new Pos(0, 64, 0);

	private static WallLines.Result line(int x, int y, int z, int min) {
		return WallLines.of(START, new Pos(x, y, z), min, Balance.MAX_WALL_LENGTH);
	}

	@Test
	void fourDirectionsCoverExactlyTheLine() {
		Object[][] cases = {{19, 70, 0, Rotation4.NONE}, {-19, 64, 0, Rotation4.CLOCKWISE_180}, {0, 64, 19, Rotation4.CLOCKWISE_90},
				{0, 64, -19, Rotation4.COUNTERCLOCKWISE_90}};
		for (Object[] c : cases) {
			WallLines.Result r = line((int) c[0], (int) c[1], (int) c[2], 1);
			assertTrue(r.ok());
			assertEquals(START, r.line().ground());
			assertEquals(c[3], r.line().rotation());
			assertEquals(20, r.line().length());
			Blueprint bp = Blueprints.resolve("wall_segment", new BlueprintParams(20)).orElseThrow();
			Placement pl = new Placement(r.line().ground(), r.line().rotation());
			List<Pos> touched = SiteRules.touched(bp, pl);
			Set<Pos> expected = new HashSet<>();
			int dx = Integer.signum((int) c[0]), dz = Integer.signum((int) c[2]);
			for (int i = 0; i < 20; i++) for (int y = 65; y <= 67; y++) expected.add(new Pos(dx * i, y, dz * i));
			assertEquals(60, touched.size());
			assertEquals(expected, new HashSet<>(touched));
			// first column = the start, last column = the end's x/z at the start's y
			assertEquals(new Pos(0, 65, 0), pl.world(bp, 0, 0, 0));
			assertEquals(new Pos((int) c[0], 65, (int) c[2]), pl.world(bp, 19, 0, 0));
		}
	}

	@Test
	void lengthBoundary() {
		assertEquals(32, line(31, 64, 0, 1).line().length());
		assertEquals(32, line(-31, 64, 0, 1).line().length());
		for (int x : new int[] {32, -32}) {
			WallLines.Result r = line(x, 64, 0, 1);
			assertFalse(r.ok());
			assertEquals(Code.TOO_LONG, r.problem().code());
			assertEquals(List.of("33"), r.problem().args());
			assertEquals("Too long: 33 blocks (max 32)", r.problem().english());
		}
		assertEquals(Code.TOO_LONG, line(0, 64, 40, 1).problem().code());
	}

	@Test
	void notStraightAndOrder() {
		assertEquals(Code.NOT_STRAIGHT, line(3, 64, 1, 1).problem().code());
		assertEquals(List.of("1"), line(3, 64, 1, 1).problem().args());
		assertEquals(List.of("2"), line(3, 64, 2, 1).problem().args());
		assertEquals("A wall must run straight along x or z (2 off the line)", line(3, 64, 2, 1).problem().english());
		assertEquals(Code.NOT_STRAIGHT, line(40, 64, 1, 1).problem().code()); // NOT_STRAIGHT is checked before TOO_LONG
		assertEquals(Code.NOT_STRAIGHT, line(3, 64, 3, 5).problem().code()); // and before TOO_SHORT
	}

	@Test
	void shortLines() {
		WallLines.Result one = line(0, 64, 0, 1);
		assertTrue(one.ok());
		assertEquals(1, one.line().length());
		assertEquals(Rotation4.NONE, one.line().rotation());
		WallLines.Result gate4 = line(3, 64, 0, 5);
		assertEquals(Code.TOO_SHORT, gate4.problem().code());
		assertEquals("Too short: 4 blocks (min 5)", gate4.problem().english());
		assertTrue(line(4, 64, 0, 5).ok());
	}

	@Test
	void resolveOnlyInRange() {
		assertTrue(Blueprints.resolve("wall_segment", new BlueprintParams(33)).isEmpty());
		assertTrue(Blueprints.resolve("wall_segment", BlueprintParams.NONE).isEmpty());
		assertTrue(Blueprints.resolve("gate", new BlueprintParams(4)).isEmpty());
		assertTrue(Blueprints.resolve("gate", new BlueprintParams(5)).isPresent());
		assertEquals(32, Blueprints.resolve("wall_segment", new BlueprintParams(32)).orElseThrow().width());
		assertEquals(1, Blueprints.byId("wall_segment").orElseThrow().width());
		assertEquals(5, Blueprints.byId("gate").orElseThrow().width());
		assertEquals(1, Blueprints.minLength(Blueprints.WALL_SEGMENT));
		assertEquals(5, Blueprints.minLength(Blueprints.GATE));
		assertEquals(0, Blueprints.minLength(Blueprints.SMALL_HOUSE));
	}
}
