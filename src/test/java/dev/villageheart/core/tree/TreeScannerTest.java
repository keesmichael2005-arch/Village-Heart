package dev.villageheart.core.tree;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.tree.TreeScan.NotATree;
import dev.villageheart.core.tree.TreeScan.Tree;
import dev.villageheart.core.tree.TreeScan.Why;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TreeScannerTest {
	private static TreeScan scan(FakeTreeGrid g, int x, int y, int z) { return TreeScanner.scan(g, new Pos(x, y, z)); }

	private static Why why(TreeScan s) { return ((NotATree) s).why(); }

	@Test
	void anOakWithNaturalLeavesIsATree() {
		FakeTreeGrid g = new FakeTreeGrid().oak(5, 5, 5);
		Tree t = assertInstanceOf(Tree.class, scan(g, 5, 3, 5));
		assertEquals(Species.OAK, t.species());
		assertEquals(List.of(new Pos(5, 1, 5)), t.base());
		assertEquals(5, t.fellOrder().size());
		assertEquals(new Pos(5, 5, 5), t.fellOrder().get(0));
		assertEquals(new Pos(5, 1, 5), t.fellOrder().get(4));
		assertTrue(t.leafContacts() >= 3);
	}

	@Test
	void sameLogsWithoutLeavesAreNotATree() {
		FakeTreeGrid g = new FakeTreeGrid();
		for (int y = 1; y <= 5; y++) g.set(5, y, 5, TreeCell.log(Species.OAK));
		assertEquals(Why.NO_LEAVES, why(scan(g, 5, 2, 5)));
	}

	@Test
	void persistentLeavesDoNotCount() {
		FakeTreeGrid g = new FakeTreeGrid().placedLeavesTree(5, 5, 5);
		assertEquals(Why.NO_LEAVES, why(scan(g, 5, 2, 5)));
	}

	@Test
	void threeLeafContactsAreEnoughTwoAreNot() {
		assertEquals(Why.NO_LEAVES, why(scan(new FakeTreeGrid().fewLeaves(5, 5, 5, 2), 5, 2, 5)));
		assertInstanceOf(Tree.class, scan(new FakeTreeGrid().fewLeaves(5, 5, 5, 3), 5, 2, 5));
	}

	@Test
	void logsInsideAValidRoomAreNotATree() {
		FakeTreeGrid g = new FakeTreeGrid().oak(5, 5, 5).room(3, 0, 3, 8, 8, 8);
		assertEquals(Why.IN_ROOM, why(scan(g, 5, 2, 5)));
	}

	@Test
	void aFloatingTreeHasNoGround() {
		FakeTreeGrid g = new FakeTreeGrid().floating(5, 10, 5, 5);
		assertEquals(Why.NO_GROUND, why(scan(g, 5, 11, 5)));
	}

	@Test
	void aLogWallWithLeavesIsNotTreeShaped() {
		FakeTreeGrid g = new FakeTreeGrid().logWall(0, 0, 4, 1);
		// natural leaves on top of the wall
		for (int x = 0; x < 4; x++) g.set(x, 2, 0, TreeCell.naturalLeaves(Species.OAK));
		assertEquals(Why.BASE_NOT_TREE_SHAPE, why(scan(g, 1, 1, 0)));
	}

	@Test
	void aCabinWithAnOakTouchingItsWallIsLeftAlone() {
		FakeTreeGrid g = new FakeTreeGrid().cabin(0, 0, 5, 5, 3).oak(5, 2, 5);
		// the oak trunk at x=5 touches the cabin's east wall (x=4)
		assertEquals(Why.BASE_NOT_TREE_SHAPE, why(scan(g, 5, 3, 2)));
		assertEquals(Why.BASE_NOT_TREE_SHAPE, why(scan(g, 0, 1, 0)));
	}

	@Test
	void aDarkOakHasFourBaseLogs() {
		FakeTreeGrid g = new FakeTreeGrid().darkOak(3, 3, 6);
		Tree t = assertInstanceOf(Tree.class, scan(g, 3, 4, 3));
		assertEquals(Species.DARK_OAK, t.species());
		assertEquals(4, t.base().size());
		assertEquals(24, t.fellOrder().size());
		Set<Pos> lastFour = new HashSet<>(t.fellOrder().subList(20, 24));
		assertEquals(new HashSet<>(t.base()), lastFour);
	}

	@Test
	void anAcaciaWithADiagonalTrunkIsOneTree() {
		FakeTreeGrid g = new FakeTreeGrid().acacia(5, 5);
		Tree t = assertInstanceOf(Tree.class, scan(g, 6, 5, 6));
		assertEquals(Species.ACACIA, t.species());
		assertEquals(5, t.fellOrder().size());
		Tree lower = assertInstanceOf(Tree.class, scan(g, 5, 1, 5));
		assertEquals(t.fellOrder(), lower.fellOrder());
	}

	@Test
	void aCornerConnectedLimbIsPartOfTheTree() {
		FakeTreeGrid g = new FakeTreeGrid().fancyOakLimb(5, 5, 6);
		Tree t = assertInstanceOf(Tree.class, scan(g, 5, 2, 5));
		assertEquals(8, t.fellOrder().size());
		assertTrue(t.fellOrder().contains(new Pos(7, 5, 7)));
	}

	@Test
	void aCherryIsATreeToo() {
		assertEquals(Species.CHERRY, assertInstanceOf(Tree.class, scan(new FakeTreeGrid().cherry(2, 2, 5), 2, 2, 2)).species());
	}

	@Test
	void tooManyLogsOrTooWideIsRefused() {
		FakeTreeGrid huge = new FakeTreeGrid();
		for (int y = 1; y <= 48; y++) for (int dx = 0; dx < 5; dx++) huge.set(dx, y, 0, TreeCell.log(Species.OAK));
		assertEquals(Why.TOO_BIG, why(scan(huge, 0, 1, 0)));

		FakeTreeGrid wide = new FakeTreeGrid();
		for (int x = 0; x < 18; x++) wide.set(x, 1, 0, TreeCell.log(Species.OAK));
		assertEquals(Why.TOO_BIG, why(scan(wide, 0, 1, 0)));

		FakeTreeGrid max192 = new FakeTreeGrid();
		int n = 0;
		outer:
		for (int y = 1; y <= 48; y++) for (int dx = 0; dx < 4; dx++) {
			max192.set(dx, y, 0, TreeCell.log(Species.OAK));
			if (++n == 192) break outer;
		}
		assertEquals(192, n);
		assertNotEquals(Why.TOO_BIG, why(scan(max192, 0, 1, 0)));
		max192.set(5, 1, 0, TreeCell.log(Species.OAK));
		max192.set(4, 1, 0, TreeCell.log(Species.OAK));
		assertEquals(Why.TOO_BIG, why(scan(max192, 0, 1, 0)));
	}

	@Test
	void tooTallIsRefused() {
		FakeTreeGrid g = new FakeTreeGrid();
		for (int y = 1; y <= 49; y++) g.set(0, y, 0, TreeCell.log(Species.OAK));
		assertEquals(Why.TOO_BIG, why(scan(g, 0, 1, 0)));
	}

	@Test
	void anUnloadedCellNextToALogMeansUnknown() {
		FakeTreeGrid g = new FakeTreeGrid().oak(5, 5, 5).unload(6, 3, 5);
		assertEquals(Why.UNKNOWN, why(scan(g, 5, 2, 5)));
	}

	@Test
	void oakLogsUnderAzaleaLeavesAreAzalea() {
		FakeTreeGrid g = new FakeTreeGrid().tree(5, 5, 5, Species.OAK, TreeCell.naturalLeaves(Species.AZALEA));
		assertEquals(Species.AZALEA, assertInstanceOf(Tree.class, scan(g, 5, 2, 5)).species());
	}

	@Test
	void twoTreesWhoseCanopyLogsTouchAreAKnownFalseNegative() {
		FakeTreeGrid g = new FakeTreeGrid().oak(0, 0, 6).oak(4, 0, 6);
		g.set(1, 7, 0, TreeCell.log(Species.OAK)).set(2, 8, 0, TreeCell.log(Species.OAK)).set(3, 7, 0, TreeCell.log(Species.OAK));
		assertEquals(Why.BASE_NOT_TREE_SHAPE, why(scan(g, 0, 2, 0)));
	}

	@Test
	void aMissingStartLogIsGone() {
		assertEquals(Why.GONE, why(scan(new FakeTreeGrid(), 0, 5, 0)));
	}
}
