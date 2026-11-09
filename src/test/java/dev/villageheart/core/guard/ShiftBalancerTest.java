package dev.villageheart.core.guard;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Shift assignment (M18 r3 owner design): most guards nights, seniors on nights, new guards start on days. */
class ShiftBalancerTest {
	private static List<UUID> ids(int n) {
		List<UUID> l = new ArrayList<>();
		for (int i = 0; i < n; i++) l.add(new UUID(0, i + 1));
		return l;
	}

	private static List<ShiftBalancer.Guard> guards(int... xps) {
		List<ShiftBalancer.Guard> l = new ArrayList<>();
		for (int i = 0; i < xps.length; i++) l.add(new ShiftBalancer.Guard(new UUID(0, i + 1), 1, xps[i]));
		return l;
	}

	private static UUID id(int i) { return new UUID(0, i + 1); }

	private static long count(Map<UUID, GuardShift> m, Shift s) { return m.values().stream().filter(g -> g.shift() == s).count(); }

	@Test
	void nobodyAndOne() {
		assertTrue(ShiftBalancer.rebalance(List.of(), Map.of()).isEmpty());
		assertEquals(Shift.NIGHT, ShiftBalancer.rebalance(ids(1), Map.of()).get(id(0)).shift(), "a lone guard works nights");
	}

	@Test
	void mostGuardsTakeTheNightAndTheDayKeepsAtLeastOne() {
		int[][] expect = {{1, 0}, {1, 1}, {2, 1}, {3, 1}, {4, 1}, {4, 2}, {5, 2}, {6, 2}, {6, 3}};
		for (int n = 1; n <= 9; n++) {
			Map<UUID, GuardShift> m = ShiftBalancer.rebalance(ids(n), Map.of());
			assertEquals(expect[n - 1][0], count(m, Shift.NIGHT), n + " guards: nights");
			assertEquals(expect[n - 1][1], count(m, Shift.DAY), n + " guards: days");
			if (n >= 2) assertTrue(count(m, Shift.DAY) >= 1 && count(m, Shift.NIGHT) > count(m, Shift.DAY) - 1);
		}
	}

	@Test
	void seniorsTakeTheNights() {
		// 3 guards, the third has the most XP: it and the second-best are on nights
		Map<UUID, GuardShift> m = ShiftBalancer.assign(guards(10, 0, 50), Map.of());
		assertEquals(Shift.NIGHT, m.get(id(2)).shift());
		assertEquals(Shift.NIGHT, m.get(id(0)).shift());
		assertEquals(Shift.DAY, m.get(id(1)).shift());
	}

	@Test
	void levelBeatsXp() {
		List<ShiftBalancer.Guard> g = List.of(new ShiftBalancer.Guard(id(0), 1, 90), new ShiftBalancer.Guard(id(1), 2, 5));
		Map<UUID, GuardShift> m = ShiftBalancer.assign(g, Map.of());
		assertEquals(Shift.NIGHT, m.get(id(1)).shift());
		assertEquals(Shift.DAY, m.get(id(0)).shift());
	}

	@Test
	void aNewGuardStartsOnDaysAndMovesToNightWithXp() {
		Map<UUID, GuardShift> m = ShiftBalancer.assign(guards(30, 20, 0), Map.of());
		assertEquals(Shift.DAY, m.get(id(2)).shift(), "the new guard (no XP) is on days");
		// it gains XP past a night guard's: it moves up, the least senior night guard goes to days
		m = ShiftBalancer.assign(guards(30, 20, 25), m);
		assertEquals(Shift.NIGHT, m.get(id(2)).shift());
		assertEquals(Shift.DAY, m.get(id(1)).shift());
		assertEquals(Shift.NIGHT, m.get(id(0)).shift());
	}

	@Test
	void aFallenNightGuardIsReplacedByTheMostSeniorDayGuard() {
		Map<UUID, GuardShift> m = ShiftBalancer.assign(guards(30, 20, 5, 0), Map.of()); // N N N D
		assertEquals(3, count(m, Shift.NIGHT));
		List<ShiftBalancer.Guard> after = new ArrayList<>(guards(30, 20, 5, 0));
		after.remove(0); // the senior night guard falls: 3 guards = 2 nights + 1 day
		m = ShiftBalancer.assign(after, m);
		assertEquals(Shift.NIGHT, m.get(id(1)).shift());
		assertEquals(Shift.NIGHT, m.get(id(2)).shift());
		assertEquals(Shift.DAY, m.get(id(3)).shift());
		after.remove(0); // and another: 2 guards = 1 + 1, the senior of the two stays on nights
		m = ShiftBalancer.assign(after, m);
		assertEquals(Shift.NIGHT, m.get(id(2)).shift());
		assertEquals(Shift.DAY, m.get(id(3)).shift());
	}

	@Test
	void equalGuardsKeepTheirSideNoFlapping() {
		Map<UUID, GuardShift> m = ShiftBalancer.assign(guards(5, 5, 5, 5), Map.of());
		for (int round = 0; round < 5; round++) assertEquals(m, ShiftBalancer.assign(guards(5, 5, 5, 5), m));
	}

	@Test
	void pinsAreHonouredAndCounted() {
		List<UUID> g = ids(4);
		Map<UUID, GuardShift> cur = Map.of(g.get(0), new GuardShift(Shift.DAY, true), g.get(1), new GuardShift(Shift.DAY, true));
		Map<UUID, GuardShift> m = ShiftBalancer.rebalance(g, cur);
		assertEquals(new GuardShift(Shift.DAY, true), m.get(g.get(0)));
		assertEquals(Shift.NIGHT, m.get(g.get(2)).shift());
		assertEquals(Shift.NIGHT, m.get(g.get(3)).shift());
	}

	@Test
	void allPinnedAreHonouredEvenUnbalanced() {
		List<UUID> g = ids(3);
		Map<UUID, GuardShift> cur = new HashMap<>();
		for (UUID u : g) cur.put(u, new GuardShift(Shift.DAY, true));
		assertEquals(3, count(ShiftBalancer.rebalance(g, cur), Shift.DAY));
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

	@Test
	void randomisedInvariants() {
		Random rnd = new Random(7);
		for (int round = 0; round < 300; round++) {
			int n = 1 + rnd.nextInt(9);
			List<ShiftBalancer.Guard> g = new ArrayList<>();
			for (int i = 0; i < n; i++) g.add(new ShiftBalancer.Guard(id(i), 1 + rnd.nextInt(3), rnd.nextInt(100)));
			Map<UUID, GuardShift> m = ShiftBalancer.assign(g, Map.of());
			assertEquals(n - ShiftBalancer.dayCount(n), count(m, Shift.NIGHT));
			// every night guard is at least as senior as every day guard
			for (var a : g) for (var b : g) {
				if (m.get(a.id()).shift() == Shift.NIGHT && m.get(b.id()).shift() == Shift.DAY) {
					assertTrue(a.level() > b.level() || a.level() == b.level() && a.xp() >= b.xp(), "round " + round);
				}
			}
			assertEquals(m, ShiftBalancer.assign(g, m), "idempotent");
		}
	}
}
