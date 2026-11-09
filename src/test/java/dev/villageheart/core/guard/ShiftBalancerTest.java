package dev.villageheart.core.guard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ShiftBalancerTest {
	private static List<UUID> ids(int n) {
		List<UUID> l = new ArrayList<>();
		for (int i = 0; i < n; i++) l.add(new UUID(0, i + 1));
		return l;
	}

	private static long count(Map<UUID, GuardShift> m, Shift s) { return m.values().stream().filter(g -> g.shift() == s).count(); }

	@Test
	void nobodyAndOne() {
		assertTrue(ShiftBalancer.rebalance(List.of(), Map.of()).isEmpty());
		Map<UUID, GuardShift> one = ShiftBalancer.rebalance(ids(1), Map.of());
		assertEquals(Shift.NIGHT, one.get(ids(1).get(0)).shift());
	}

	@Test
	void twoAreNightThenDay() {
		List<UUID> g = ids(2);
		Map<UUID, GuardShift> m = ShiftBalancer.rebalance(g, Map.of());
		assertEquals(Shift.NIGHT, m.get(g.get(0)).shift());
		assertEquals(Shift.DAY, m.get(g.get(1)).shift());
	}

	@Test
	void fiveAreThreeNightTwoDay() {
		Map<UUID, GuardShift> m = ShiftBalancer.rebalance(ids(5), Map.of());
		assertEquals(3, count(m, Shift.NIGHT));
		assertEquals(2, count(m, Shift.DAY));
	}

	@Test
	void pinsAreCounted() {
		List<UUID> g = ids(4);
		Map<UUID, GuardShift> cur = Map.of(g.get(0), new GuardShift(Shift.DAY, true), g.get(1), new GuardShift(Shift.DAY, true));
		Map<UUID, GuardShift> m = ShiftBalancer.rebalance(g, cur);
		assertEquals(Shift.NIGHT, m.get(g.get(2)).shift());
		assertEquals(Shift.NIGHT, m.get(g.get(3)).shift());
		assertTrue(m.get(g.get(0)).pinned());
	}

	@Test
	void allPinnedAreHonouredEvenUnbalanced() {
		List<UUID> g = ids(3);
		Map<UUID, GuardShift> cur = new HashMap<>();
		for (UUID u : g) cur.put(u, new GuardShift(Shift.DAY, true));
		Map<UUID, GuardShift> m = ShiftBalancer.rebalance(g, cur);
		assertEquals(3, count(m, Shift.DAY));
	}

	@Test
	void appendingNeverChangesEarlierGuards() {
		Random rnd = new Random(42);
		for (int round = 0; round < 50; round++) {
			Map<UUID, GuardShift> m = new HashMap<>();
			for (int n = 1; n <= 8; n++) {
				List<UUID> g = ids(n);
				Map<UUID, GuardShift> next = ShiftBalancer.rebalance(g, m);
				for (int i = 0; i < n - 1; i++) assertEquals(m.get(g.get(i)), next.get(g.get(i)), "round " + round + " n " + n);
				// pin a random guard to a random side, sometimes
				Map<UUID, GuardShift> pinned = new HashMap<>(next);
				if (rnd.nextInt(3) == 0) pinned.put(g.get(rnd.nextInt(n)), new GuardShift(rnd.nextBoolean() ? Shift.DAY : Shift.NIGHT, true));
				m = ShiftBalancer.rebalance(g, pinned); // as VillageState.withGuardPin does: a pin rebalances at once
			}
		}
	}

	@Test
	void removalMovesAtMostOneTheMostRecentUnpinned() {
		List<UUID> g = ids(5); // N D N D N
		Map<UUID, GuardShift> m = ShiftBalancer.rebalance(g, Map.of());
		List<UUID> without = new ArrayList<>(g);
		without.remove(g.get(1)); // the one day guard of 2 goes: N N D N -> 3 / 1
		Map<UUID, GuardShift> after = ShiftBalancer.rebalance(without, m);
		int moved = 0;
		for (UUID u : without) if (after.get(u).shift() != m.get(u).shift()) moved++;
		assertEquals(1, moved);
		assertEquals(Shift.DAY, after.get(g.get(4)).shift(), "the most recently hired unpinned guard moved");
		assertFalse(after.containsKey(g.get(1)));
	}

	@Test
	void removalOfBalancedSidesMovesNobody() {
		List<UUID> g = ids(4); // N D N D
		Map<UUID, GuardShift> m = ShiftBalancer.rebalance(g, Map.of());
		List<UUID> without = new ArrayList<>(g);
		without.remove(0); // D N D: 1 / 2, fine
		Map<UUID, GuardShift> after = ShiftBalancer.rebalance(without, m);
		for (UUID u : without) assertEquals(m.get(u), after.get(u));
	}

	@Test
	void removingAPinnedGuardWithBalancedUnpinnedMovesNobody() {
		List<UUID> g = ids(3);
		Map<UUID, GuardShift> cur = new HashMap<>();
		cur.put(g.get(0), new GuardShift(Shift.DAY, true));
		cur.put(g.get(1), new GuardShift(Shift.NIGHT, false));
		cur.put(g.get(2), new GuardShift(Shift.DAY, false));
		List<UUID> without = List.of(g.get(1), g.get(2));
		Map<UUID, GuardShift> after = ShiftBalancer.rebalance(without, cur);
		assertEquals(cur.get(g.get(1)), after.get(g.get(1)));
		assertEquals(cur.get(g.get(2)), after.get(g.get(2)));
	}

	@Test
	void unknownUuidsAreIgnored() {
		List<UUID> g = ids(2);
		Map<UUID, GuardShift> cur = new HashMap<>();
		cur.put(new UUID(9, 9), new GuardShift(Shift.DAY, true));
		Map<UUID, GuardShift> m = ShiftBalancer.rebalance(g, cur);
		assertEquals(2, m.size());
		assertFalse(m.containsKey(new UUID(9, 9)));
	}
}
