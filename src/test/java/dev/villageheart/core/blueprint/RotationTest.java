package dev.villageheart.core.blueprint;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Dir;
import dev.villageheart.core.room.Pos;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** T-L1-2 */
class RotationTest {
	@Test
	void matchesVanillaPositionRule() {
		// StructureTemplate.transform: CLOCKWISE_90 sends (dx, dz) to (-dz, dx)
		assertArrayEquals(new int[] {3, 4}, Rotation4.NONE.apply(3, 4));
		assertArrayEquals(new int[] {-4, 3}, Rotation4.CLOCKWISE_90.apply(3, 4));
		assertArrayEquals(new int[] {-3, -4}, Rotation4.CLOCKWISE_180.apply(3, 4));
		assertArrayEquals(new int[] {4, -3}, Rotation4.COUNTERCLOCKWISE_90.apply(3, 4));
		assertArrayEquals(new int[] {1, 0}, Rotation4.CLOCKWISE_90.apply(0, -1)); // north turns to east
		assertArrayEquals(new int[] {0, 5}, Rotation4.CLOCKWISE_180.apply(0, -5));
	}

	@Test
	void turnsDirections() {
		assertEquals(Dir.NORTH, Rotation4.NONE.apply(Dir.NORTH));
		assertEquals(Dir.EAST, Rotation4.CLOCKWISE_90.apply(Dir.NORTH));
		assertEquals(Dir.SOUTH, Rotation4.CLOCKWISE_180.apply(Dir.NORTH));
		assertEquals(Dir.WEST, Rotation4.COUNTERCLOCKWISE_90.apply(Dir.NORTH));
		assertEquals(Dir.UP, Rotation4.CLOCKWISE_90.apply(Dir.UP));
		for (Rotation4 r : Rotation4.values()) {
			assertEquals(Dir.NORTH, r.inverse().apply(r.apply(Dir.NORTH)));
			int[] p = r.apply(3, -2);
			assertArrayEquals(new int[] {3, -2}, r.inverse().apply(p[0], p[1]));
			assertEquals(r, Rotation4.turning(Dir.NORTH, r.apply(Dir.NORTH)));
		}
		Rotation4 r = Rotation4.NONE;
		for (int i = 0; i < 4; i++) r = r.next();
		assertEquals(Rotation4.NONE, r);
		assertEquals(Rotation4.CLOCKWISE_90, Rotation4.NONE.next());
	}

	@Test
	void everyBlueprintSurvivesEveryRotation() {
		for (Blueprint bp : Blueprints.all()) {
			for (Rotation4 r : Rotation4.values()) {
				Placement p = new Placement(new Pos(100, 64, -30), r);
				Pos anchorColumn = p.world(bp, bp.anchorX(), 0, bp.anchorZ());
				assertEquals(100, anchorColumn.x(), bp.id());
				assertEquals(-30, anchorColumn.z(), bp.id());
				assertEquals(64 + bp.base(), anchorColumn.y());
				List<String> entries = new ArrayList<>();
				for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
					Pos w = p.world(bp, x, y, z);
					assertArrayEquals(new int[] {x, y, z}, p.local(bp, w), bp.id() + " " + r);
					assertSame(bp.entry(x, y, z), p.entryAt(bp, w));
					assertTrue(p.inBox(bp, w));
					entries.add(String.valueOf(bp.entry(x, y, z).symbol()));
				}
				Pos min = p.min(bp), max = p.max(bp);
				boolean swap = r == Rotation4.CLOCKWISE_90 || r == Rotation4.COUNTERCLOCKWISE_90;
				assertEquals(swap ? bp.depth() : bp.width(), max.x() - min.x() + 1, bp.id() + " " + r);
				assertEquals(swap ? bp.width() : bp.depth(), max.z() - min.z() + 1, bp.id() + " " + r);
				assertEquals(bp.height(), max.y() - min.y() + 1);
				assertEquals(bp.width() * bp.height() * bp.depth(), entries.size());
				assertEquals(r.apply(bp.front()), p.front(bp));
				assertNull(p.entryAt(bp, new Pos(max.x() + 1, max.y(), max.z())));
			}
		}
	}

	@Test
	void partsStillPointAtTheirPrimaryAfterRotation() {
		Blueprint bp = Blueprints.SMALL_HOUSE;
		for (Rotation4 r : Rotation4.values()) {
			Placement p = new Placement(new Pos(0, 10, 0), r);
			int parts = 0;
			for (int y = 0; y < bp.height(); y++) for (int z = 0; z < bp.depth(); z++) for (int x = 0; x < bp.width(); x++) {
				PaletteEntry e = bp.entry(x, y, z);
				if (e.partOf() == null) continue;
				parts++;
				Pos part = p.world(bp, x, y, z);
				Pos primary = part.offset(r.apply(e.partOf()));
				PaletteEntry at = p.entryAt(bp, primary);
				assertNotNull(at);
				assertTrue(at.primary());
				assertEquals(e.blockId(), at.blockId());
			}
			assertEquals(2, parts);
		}
	}
}
