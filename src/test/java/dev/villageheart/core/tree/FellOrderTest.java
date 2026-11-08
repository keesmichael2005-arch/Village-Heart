package dev.villageheart.core.tree;

import static org.junit.jupiter.api.Assertions.*;

import dev.villageheart.core.room.Pos;
import dev.villageheart.core.tree.TreeScan.NotATree;
import dev.villageheart.core.tree.TreeScan.Tree;
import dev.villageheart.core.tree.TreeScan.Why;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class FellOrderTest {
	@Test
	void topDownBaseLastAndDeterministic() {
		List<Pos> logs = List.of(new Pos(0, 1, 0), new Pos(0, 3, 0), new Pos(2, 3, 0), new Pos(0, 2, 0), new Pos(-1, 3, 1));
		List<Pos> a = FellOrder.topDown(logs, List.of(new Pos(0, 1, 0)));
		List<Pos> b = FellOrder.topDown(new ArrayList<>(logs.reversed()), List.of(new Pos(0, 1, 0)));
		assertEquals(a, b);
		assertEquals(new Pos(0, 1, 0), a.get(a.size() - 1));
		for (int i = 1; i < a.size() - 1; i++) assertTrue(a.get(i - 1).y() >= a.get(i).y());
		// equal y: farther from the base first
		assertEquals(new Pos(2, 3, 0), a.get(0));
	}

	@Test
	void resumeFloodsTheRemainderFromTheBaseWithoutLeaves() {
		FakeTreeGrid g = new FakeTreeGrid().oak(5, 5, 6);
		Tree t = (Tree) TreeScanner.scan(g, new Pos(5, 2, 5));
		// the top three logs are felled and the leaves are gone
		for (int i = 0; i < 3; i++) g.remove(t.fellOrder().get(i).x(), t.fellOrder().get(i).y(), t.fellOrder().get(i).z());
		for (int x = 0; x < 12; x++) for (int y = 1; y < 12; y++) for (int z = 0; z < 12; z++) {
			if (g.cell(x, y, z).kind() == TreeCell.Kind.NATURAL_LEAVES) g.remove(x, y, z);
		}
		TreeScan r = TreeScanner.resume(g, t.base(), new Pos(5, 1, 5), new Pos(5, 6, 5));
		Tree rt = assertInstanceOf(Tree.class, r);
		assertEquals(3, rt.fellOrder().size());
		assertEquals(new Pos(5, 3, 5), rt.fellOrder().get(0));
		assertEquals(new Pos(5, 1, 5), rt.fellOrder().get(2));
		// fresh scan of the stump would say NO_LEAVES, resume does not care
		assertEquals(Why.NO_LEAVES, ((NotATree) TreeScanner.scan(g, new Pos(5, 1, 5))).why());
	}

	@Test
	void resumeIgnoresLogsOutsideTheSavedBoxButNotRoomRule() {
		FakeTreeGrid g = new FakeTreeGrid().oak(5, 5, 6);
		Tree t = (Tree) TreeScanner.scan(g, new Pos(5, 2, 5));
		for (int i = 0; i < 4; i++) g.remove(t.fellOrder().get(i).x(), t.fellOrder().get(i).y(), t.fellOrder().get(i).z());
		// a player puts a log against the stump, outside the original extent
		g.set(6, 1, 5, TreeCell.log(Species.OAK));
		Tree rt = assertInstanceOf(Tree.class, TreeScanner.resume(g, t.base(), new Pos(5, 1, 5), new Pos(5, 6, 5)));
		assertFalse(rt.fellOrder().contains(new Pos(6, 1, 5)));
		assertEquals(2, rt.fellOrder().size());
		// the same stump inside a room is refused
		g.room(4, 0, 4, 6, 3, 6);
		assertEquals(Why.IN_ROOM, ((NotATree) TreeScanner.resume(g, t.base(), new Pos(5, 1, 5), new Pos(5, 6, 5))).why());
	}

	@Test
	void resumeOfAFelledTreeIsGone() {
		FakeTreeGrid g = new FakeTreeGrid();
		assertEquals(Why.GONE, ((NotATree) TreeScanner.resume(g, List.of(new Pos(5, 1, 5)), new Pos(5, 1, 5), new Pos(5, 6, 5))).why());
	}
}
